package games.strategy.engine.framework.startup.ui;

import games.strategy.engine.ClientFileSystemHelper;
import games.strategy.engine.posted.game.pbf.NodeBbGameThreads;
import games.strategy.engine.posted.game.pbf.NodeBbGameThreads.Player;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dialog.ModalityType;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import lombok.extern.slf4j.Slf4j;
import me.friwi.jcefmaven.CefAppBuilder;
import me.friwi.jcefmaven.CefInitializationException;
import me.friwi.jcefmaven.EnumProgress;
import me.friwi.jcefmaven.IProgressHandler;
import me.friwi.jcefmaven.UnsupportedPlatformException;
import org.cef.CefApp;
import org.cef.CefClient;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.handler.CefLoadHandlerAdapter;
import org.cef.network.CefCookieManager;
import org.triplea.swing.SwingComponents;
import org.triplea.util.ExitStatus;

/**
 * The forum's own login page in a browser window of TripleA: the player signs in there as in any
 * browser, CAPTCHA and all, and copies nothing. The browser is Chromium (JCEF), downloaded into
 * TripleA's folder the first time, about 100 MB. After each page the window asks the forum whose
 * session its cookie is; once it is a player's, it hands session and player on and closes. The
 * browser keeps its cookies in memory only, and each sign-in starts without the last session; its
 * other files are in the folder "jcef-profile" of TripleA's folder.
 */
@Slf4j
final class ForumBrowserLogin {
  /** NodeBB's session cookie. */
  private static final String SESSION_COOKIE = "express.sid";

  /** Chromium starts once per run of TripleA and stays for the next sign-in. */
  private static @Nullable CefApp browserApp;

  /** A sign-in: the browser session and the player it is for. */
  record SignedIn(String session, Player player) {}

  private ForumBrowserLogin() {}

  /**
   * Opens the window on the forum's login page. Calls back on the EDT, once: with the sign-in, or
   * with the reason there is none, eg the window was closed.
   *
   * @param forumUrl the forum, eg "https://forums.triplea-game.org/"
   */
  static void signIn(
      final Component parent,
      final String forumUrl,
      final Consumer<SignedIn> onSignedIn,
      final Consumer<String> onError) {
    final String forum = forumUrl.endsWith("/") ? forumUrl : forumUrl + "/";
    final JDialog window =
        new JDialog(
            SwingUtilities.getWindowAncestor(parent), "Sign in to " + forum, ModalityType.MODELESS);
    // the close button ends the sign-in, it does not just hide the window
    window.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
    final JLabel progress = new JLabel("Starting the browser...", SwingConstants.CENTER);
    window.add(progress, BorderLayout.CENTER);
    window.setSize(1000, 780);
    window.setLocationRelativeTo(parent);
    // set once the player signed in or the browser failed; closing the window before cancels
    final AtomicBoolean finished = new AtomicBoolean();
    SwingComponents.addWindowClosedListener(
        window,
        () -> {
          if (finished.compareAndSet(false, true)) {
            onError.accept("The sign-in window was closed before signing in.");
          }
        });
    window.setVisible(true);
    final Consumer<String> fail =
        reason -> {
          if (finished.compareAndSet(false, true)) {
            // closed as by its close button, so a browser it holds goes first
            window.dispatchEvent(new WindowEvent(window, WindowEvent.WINDOW_CLOSING));
            onError.accept(reason);
          }
        };
    Thread.ofVirtual()
        .start(
            () -> {
              try {
                final CefApp app =
                    browserApp(
                        (state, percent) ->
                            SwingUtilities.invokeLater(
                                () -> progress.setText(progressText(state, percent))));
                SwingUtilities.invokeLater(
                    () -> showLoginPage(window, app, forum, finished, onSignedIn, fail));
              } catch (final IOException
                  | UnsupportedPlatformException
                  | CefInitializationException
                  | RuntimeException
                  // eg Chromium's libraries missing a library of the system
                  | LinkageError e) {
                log.info("The browser for the forum's login could not start", e);
                SwingUtilities.invokeLater(
                    () -> fail.accept("The browser could not start: " + e.getMessage()));
              } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                SwingUtilities.invokeLater(() -> fail.accept("The browser's start was stopped."));
              }
            });
  }

  private static synchronized CefApp browserApp(final IProgressHandler progress)
      throws IOException,
          UnsupportedPlatformException,
          InterruptedException,
          CefInitializationException {
    if (browserApp == null) {
      final CefAppBuilder builder = new CefAppBuilder();
      builder.setInstallDir(
          ClientFileSystemHelper.getUserRootFolder().resolve("jcef-bundle").toFile());
      builder.setProgressHandler(progress);
      // a window of its own, not drawn by Java
      builder.getCefSettings().windowless_rendering_enabled = false;
      // one folder, used again by each run: Chromium holds its files until after TripleA ended,
      // so a folder per run stayed behind. Without a cache path its cookies stay in memory.
      // shortcut: a second TripleA running at once cannot start the browser, give each run its
      // own folder (deleted once Chromium ended) if players sign in from two at once
      builder.getCefSettings().root_cache_path =
          ClientFileSystemHelper.getUserRootFolder().resolve("jcef-profile").toString();
      builder.getCefSettings().persist_session_cookies = false;
      final CefApp app = builder.build();
      // Chromium's processes end with TripleA; registered on the EDT, where TripleA exits
      SwingUtilities.invokeLater(() -> ExitStatus.addExitAction(app::dispose));
      browserApp = app;
    }
    return browserApp;
  }

  private static String progressText(final EnumProgress state, final float percent) {
    return switch (state) {
      case DOWNLOADING ->
          String.format(
              Locale.ROOT,
              "Downloading the browser for the forum's login, only this once: %.0f %%",
              Math.max(0, percent));
      case EXTRACTING, INSTALL -> "Installing the browser...";
      default -> "Starting the browser...";
    };
  }

  private static void showLoginPage(
      final JDialog window,
      final CefApp app,
      final String forum,
      final AtomicBoolean finished,
      final Consumer<SignedIn> onSignedIn,
      final Consumer<String> fail) {
    if (!window.isDisplayable()) {
      return;
    }
    try {
      // the browser's cookie store exists once there is a client
      final CefClient client = app.createClient();
      // the browser goes before the window that holds it
      SwingComponents.addWindowClosingListener(window, client::dispose);
      // the session of a sign-in before, eg of the player who signed out, would sign in again
      Optional.ofNullable(CefCookieManager.getGlobalManager())
          .ifPresent(cookies -> cookies.deleteCookies(forum, SESSION_COOKIE));
      client.addLoadHandler(
          new CefLoadHandlerAdapter() {
            @Override
            public void onLoadEnd(
                final CefBrowser browser, final CefFrame frame, final int status) {
              if (frame.isMain()) {
                withSessionCookie(
                    forum, session -> checkSession(forum, session, finished, window, onSignedIn));
              }
            }
          });
      final CefBrowser browser = client.createBrowser(forum + "login", false, false);
      window.getContentPane().removeAll();
      window.add(browser.getUIComponent(), BorderLayout.CENTER);
      window.revalidate();
    } catch (final RuntimeException e) {
      log.info("The browser for the forum's login could not show the page", e);
      fail.accept("The browser could not show the forum's page: " + e.getMessage());
    }
  }

  /**
   * Hands the value of the forum's session cookie on, as the browser keeps it, on the EDT: the
   * browser's own threads have no class loader, which the forum's client needs.
   */
  private static void withSessionCookie(final String forum, final Consumer<String> onCookie) {
    Optional.ofNullable(CefCookieManager.getGlobalManager())
        .ifPresent(
            cookies ->
                cookies.visitUrlCookies(
                    forum,
                    true,
                    (cookie, count, total, delete) -> {
                      if (SESSION_COOKIE.equals(cookie.name)) {
                        final String value = cookie.value;
                        SwingUtilities.invokeLater(() -> onCookie.accept(value));
                      }
                      return true;
                    }));
  }

  /**
   * Asks the forum whose session it is; a guest's session is not a sign-in yet. When the forum
   * cannot be asked, the window's title says so; the next page asks again.
   */
  private static void checkSession(
      final String forum,
      final String session,
      final AtomicBoolean finished,
      final JDialog window,
      final Consumer<SignedIn> onSignedIn) {
    Thread.ofVirtual()
        .start(
            () -> {
              final Optional<Player> player;
              try {
                player = NodeBbGameThreads.withSession(forum, 0, session).signedInPlayer();
              } catch (final IOException | RuntimeException e) {
                log.info("The forum could not be asked about the session", e);
                SwingUtilities.invokeLater(
                    () ->
                        window.setTitle(
                            "Sign in to "
                                + forum
                                + " - the forum could not be asked: "
                                + e.getMessage()));
                return;
              }
              if (player.isPresent() && finished.compareAndSet(false, true)) {
                SwingUtilities.invokeLater(
                    () -> {
                      // closed as by its close button: the browser first, then the window
                      window.dispatchEvent(new WindowEvent(window, WindowEvent.WINDOW_CLOSING));
                      onSignedIn.accept(new SignedIn(session, player.get()));
                    });
              }
            });
  }
}
