package games.strategy.engine.framework.startup.ui;

import com.google.common.annotations.VisibleForTesting;
import games.strategy.engine.framework.startup.ui.ForumGamesPanel.Forum;
import games.strategy.engine.posted.game.pbf.ForumPostsSeen;
import games.strategy.engine.posted.game.pbf.NodeBbGameThreads;
import games.strategy.engine.posted.game.pbf.NodeBbGameThreads.GameThread;
import games.strategy.engine.posted.game.pbf.NodeBbGameThreads.Player;
import games.strategy.engine.posted.game.pbf.OldForumGames;
import games.strategy.triplea.EngineImageLoader;
import games.strategy.triplea.settings.ClientSetting;
import java.awt.AWTException;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nullable;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import org.triplea.awt.OpenFileUtility;

/**
 * Tells the player of their turn in play by forum games while TripleA is open: every 30 minutes it
 * asks each forum the player signed in to for the player's games, and shows a message from the
 * system tray for a game where another player posted last since TripleA told or the player read it,
 * one message for several games. A click on the message opens the game's thread, or the forum, in
 * the browser, as the main window may be hidden behind a running game.
 */
@Slf4j
public final class ForumTurnNotifier {
  private static final long FIRST_CHECK_MINUTES = 1;
  private static final long CHECK_EVERY_MINUTES = 30;

  private final List<Forum> forums;

  /** The last post told of, by game: one message per post. */
  private final Map<String, Instant> told = new HashMap<>();

  /** The thread of the newest message, which a click on it opens. */
  private final AtomicReference<String> lastThreadPage = new AtomicReference<>();

  private @Nullable TrayIcon trayIcon;

  private ForumTurnNotifier(final List<Forum> forums) {
    this.forums = forums;
  }

  /** Starts asking the forums, if the system has a tray to show messages from. */
  public static void start() {
    if (!SystemTray.isSupported()) {
      log.info("No system tray: forum turns are not told");
      return;
    }
    final ForumTurnNotifier notifier = new ForumTurnNotifier(ForumGamesPanel.FORUMS);
    final ScheduledExecutorService checks =
        Executors.newSingleThreadScheduledExecutor(
            Thread.ofVirtual().name("forum-turn-notifier").factory());
    checks.scheduleWithFixedDelay(
        notifier::check, FIRST_CHECK_MINUTES, CHECK_EVERY_MINUTES, TimeUnit.MINUTES);
  }

  private void check() {
    if (!ClientSetting.notifyForumTurns.getSetting()) {
      return;
    }
    for (final Forum forum : forums) {
      final Optional<String> session =
          forum.session().getValue().filter(value -> value.length > 0).map(String::valueOf);
      if (session.isPresent()) {
        try {
          checkForum(forum, session.get());
        } catch (final IOException | RuntimeException e) {
          // the next check tries again, eg once the network is back
          log.info("Forum turns of {} not read: {}", forum.name(), e.getMessage());
        }
      }
    }
  }

  private void checkForum(final Forum forum, final String session) throws IOException {
    final NodeBbGameThreads reader =
        NodeBbGameThreads.withSession(forum.url(), forum.playByForumCategory(), session);
    final Optional<Player> player = reader.signedInPlayer();
    if (player.isEmpty()) {
      return;
    }
    final List<GameThread> turns =
        newTurns(
            reader.threadsOf(player.get()), player.get(), OldForumGames.keys(), forum.url(), told);
    if (turns.isEmpty()) {
      return;
    }
    turns.forEach(
        game -> told.put(OldForumGames.keyOf(forum.url(), game.topicId()), game.lastPost()));
    final int games = turns.size();
    if (games == 1) {
      final GameThread game = turns.getFirst();
      lastThreadPage.set(reader.browserPage(game).toString());
      SwingUtilities.invokeLater(
          () ->
              show(
                  "Your turn in " + game.title(),
                  game.lastTurn().isEmpty()
                      ? "A new post on " + forum.name()
                      : "After " + game.lastTurn() + " on " + forum.name()));
    } else {
      // one message for several games, eg at the first check after TripleA started
      lastThreadPage.set(forum.url());
      SwingUtilities.invokeLater(
          () ->
              show(
                  "Your turn in " + games + " forum games",
                  "On " + forum.name() + ": open TripleA's Play by forum page to see them"));
    }
  }

  /**
   * The games where it is likely the player's turn and whose last post is newer than the one the
   * player read here and the one already told of; not the games moved to Old games.
   */
  @VisibleForTesting
  static List<GameThread> newTurns(
      final List<GameThread> games,
      final Player player,
      final Set<String> oldGames,
      final String forumUrl,
      final Map<String, Instant> told) {
    return games.stream()
        .filter(game -> game.waitsFor(player))
        .filter(game -> !oldGames.contains(OldForumGames.keyOf(forumUrl, game.topicId())))
        .filter(
            game ->
                ForumPostsSeen.seen(forumUrl, game.topicId())
                    .map(seen -> game.lastPost().isAfter(seen))
                    .orElse(true))
        .filter(
            game ->
                game.lastPost()
                    .isAfter(
                        told.getOrDefault(
                            OldForumGames.keyOf(forumUrl, game.topicId()), Instant.MIN)))
        .toList();
  }

  private void show(final String caption, final String text) {
    trayIcon().ifPresent(icon -> icon.displayMessage(caption, text, TrayIcon.MessageType.INFO));
  }

  /** The tray icon, added with the first message; empty if the tray does not take it. */
  private Optional<TrayIcon> trayIcon() {
    if (trayIcon == null) {
      final TrayIcon icon = new TrayIcon(EngineImageLoader.loadFrameIcon(), "TripleA");
      icon.setImageAutoSize(true);
      icon.addActionListener(
          e -> {
            final String page = lastThreadPage.get();
            if (page != null) {
              OpenFileUtility.openUrl(null, page);
            }
          });
      try {
        SystemTray.getSystemTray().add(icon);
        trayIcon = icon;
      } catch (final AWTException e) {
        log.info("The system tray did not take TripleA's icon", e);
      }
    }
    return Optional.ofNullable(trayIcon);
  }
}
