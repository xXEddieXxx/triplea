package games.strategy.engine.framework.startup.ui.panels.main;

import games.strategy.engine.framework.map.download.DownloadCoordinator;
import games.strategy.engine.framework.map.file.system.loader.InstalledMapsListing;
import games.strategy.engine.framework.map.listing.MapListingFetcher;
import games.strategy.engine.framework.startup.launcher.ILauncher;
import games.strategy.engine.framework.startup.launcher.ServerLauncher;
import games.strategy.engine.framework.startup.ui.SavedGamesPanel;
import games.strategy.engine.framework.startup.ui.panels.main.game.selector.GameSelectorModel;
import games.strategy.engine.framework.startup.ui.panels.main.game.selector.GameSelectorPanel;
import games.strategy.engine.framework.ui.background.WaitWindow;
import java.awt.Component;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javax.annotation.Nonnull;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.triplea.game.startup.SetupModel;
import org.triplea.http.client.lobby.maps.listing.MapDownloadItem;
import org.triplea.java.ThreadRunner;
import org.triplea.swing.SwingComponents;

/**
 * Can be used to create a {@link MainPanel} UI component class, which is a UI holder. The final
 * contents are added to {@link MainPanel} and we set up listeners so that we can change screens by
 * swapping the contents rendered by {@link MainPanel}.
 */
@AllArgsConstructor
@Slf4j
public class MainPanelBuilder {

  private final Runnable quitAction;

  /** Creates a MainPanel instance and configures screen transition listeners. */
  public MainPanel buildMainPanel(
      final HeadedServerSetupModel headedServerSetupModel,
      final GameSelectorModel gameSelectorModel) {
    // Load game shows the saved games as the main menu does, not a bare file chooser
    final GameSelectorPanel gameSelectorPanel =
        new GameSelectorPanel(gameSelectorModel, SavedGamesPanel::choose);
    gameSelectorModel.addObserver(gameSelectorPanel);

    final MainPanel mainPanel =
        new MainPanel(
            quitAction,
            gameSelectorPanel,
            getMainPanelLaunchAction(headedServerSetupModel),
            Optional.ofNullable(headedServerSetupModel.getPanel())
                .map(SetupModel::getChatModel)
                .orElse(null),
            headedServerSetupModel::showSelectType);
    headedServerSetupModel.setPanelChangeListener(mainPanel::setSetupPanel);
    gameSelectorModel.addObserver((observable, arg) -> mainPanel.updatePlayButtonState());
    return mainPanel;
  }

  public static @Nonnull Consumer<MainPanel> getMainPanelLaunchAction(
      HeadedServerSetupModel headedServerSetupModel) {
    return uiPanel -> {
      final var setupPanel = headedServerSetupModel.getPanel();
      setupPanel
          .getLauncher()
          .ifPresent(
              // a cancelled launch keeps the game chosen on the setup screen
              launcher ->
                  launch(
                      uiPanel, launcher, headedServerSetupModel.getGameSelectorModel(), () -> {}));
      setupPanel.postStartGame();
    };
  }

  /**
   * Hides the window of the component and launches the selected game off the EDT, a wait window
   * meanwhile. A game whose map is not installed asks once whether to look for it in the map
   * repository: then the map is downloaded and the game starts; else, or when the repository has no
   * such map, the player is told that the game could not start, and the window stays.
   *
   * @param whenCancelled runs when the game does not start, eg the player cancelled the map's
   *     download, before the window of the component shows again
   */
  static void launch(
      final Component uiPanel,
      final ILauncher launcher,
      final GameSelectorModel selected,
      final Runnable whenCancelled) {
    final String mapName =
        // a hosted game counts its players when its launcher is made: no download comes between
        launcher instanceof ServerLauncher ? "" : selected.getMapName();
    final var frame = JOptionPane.getFrameForComponent(uiPanel);
    final boolean missing = isMissing(mapName);
    // asked while the window shows: the wait window lies over everything
    if (missing && !askToGetMap(frame, mapName)) {
      tellNotStarted(frame, "the map \"" + mapName + "\" is missing.");
      whenCancelled.run();
      return;
    }
    final WaitWindow gameLoadingWindow = new WaitWindow();
    gameLoadingWindow.setLocationRelativeTo(frame);
    gameLoadingWindow.setVisible(true);
    gameLoadingWindow.showWait();
    frame.setVisible(false);
    ThreadRunner.runInNewThread(
        () -> {
          final AtomicBoolean launched = new AtomicBoolean();
          final AtomicReference<String> notStarted = new AtomicReference<>("");
          try {
            final Optional<String> why =
                missing ? getMap(mapName, gameLoadingWindow) : Optional.empty();
            if (why.isEmpty()) {
              launched.set(true);
              launcher.launch();
            } else {
              notStarted.set(why.get());
              whenCancelled.run();
            }
          } finally {
            gameLoadingWindow.doneWait();
            if (!launched.get()) {
              // back where the game was chosen, also after an error while getting the map
              SwingComponents.setFrameFromComponentVisible(uiPanel);
            }
          }
          // once the wait window is gone, so the message is not behind it; none for a cancel
          if (!notStarted.get().isBlank()) {
            SwingUtilities.invokeLater(() -> tellNotStarted(frame, notStarted.get()));
          }
        });
  }

  /** Whether the game's map is not installed; a scan that fails counts as installed, as before. */
  private static boolean isMissing(final String mapName) {
    try {
      return !mapName.isBlank() && InstalledMapsListing.searchAllMapsForMapName(mapName).isEmpty();
    } catch (final RuntimeException e) {
      // the game then asks for the map itself when it loads it
      log.info("Could not look for the map {} among the installed maps", mapName, e);
      return false;
    }
  }

  /** Whether the player wants the missing map looked for in the map repository. */
  private static boolean askToGetMap(final Component parent, final String mapName) {
    return JOptionPane.showConfirmDialog(
            parent,
            "The map \""
                + mapName
                + "\" of this game is not installed.\n"
                + "Search the map repository for it? TripleA then downloads it and starts the"
                + " game.",
            "Map missing",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE)
        == JOptionPane.YES_OPTION;
  }

  private static void tellNotStarted(final Component parent, final String why) {
    JOptionPane.showMessageDialog(
        parent,
        "The game could not be started: " + why,
        "Game not started",
        JOptionPane.INFORMATION_MESSAGE);
  }

  /**
   * Looks for the game's missing map in the map repository and downloads it, so the game starts as
   * if it had been there.
   *
   * @return why the game does not start, eg the repository has no such map; empty to start it,
   *     blank when the player cancelled the download, which needs no word
   */
  private static Optional<String> getMap(final String mapName, final WaitWindow window) {
    try {
      window.setMessage("Looking for the map " + mapName + "...");
      final Optional<MapDownloadItem> map = MapListingFetcher.findMap(mapName);
      if (map.isEmpty()) {
        return Optional.of("the map repository has no map \"" + mapName + "\".");
      }
      return download(map.get(), window);
    } catch (final RuntimeException e) {
      // eg the server's listing unreadable: told to the player, so no warning dialog of its own
      log.info("The map {} could not be downloaded before the game", mapName, e);
      return Optional.of(
          "the map \"" + mapName + "\" is missing and the map repository could not be read.");
    } finally {
      window.setMessage(WaitWindow.LOADING);
    }
  }

  /**
   * Downloads the map.
   *
   * @return why the game does not start: blank when the player cancelled; empty once downloaded
   */
  private static Optional<String> download(final MapDownloadItem map, final WaitWindow window) {
    final AtomicBoolean cancelled = new AtomicBoolean();
    window.setMessage("Downloading the map " + map.getMapName() + "...");
    // the main window is hidden meanwhile: the one way to stop waiting for a long or stalled
    // download; the coordinator tells the cancel to the wait in any case
    window.offerCancel(
        () -> {
          cancelled.set(true);
          DownloadCoordinator.instance.cancel(map);
        });
    final AtomicLong shownMegabytes = new AtomicLong(-1);
    final boolean downloaded;
    try {
      downloaded =
          DownloadCoordinator.instance.downloadAndWait(
              map,
              bytes -> {
                final long megabytes = bytes / FileUtils.ONE_MB;
                // a new text once a megabyte more arrived: the size is told every few milliseconds
                if (shownMegabytes.getAndSet(megabytes) != megabytes) {
                  window.setMessage(progressText(map, bytes));
                }
              });
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      return Optional.of("");
    } finally {
      window.withdrawCancel();
    }
    if (cancelled.get()) {
      return Optional.of("");
    }
    return downloaded
        ? Optional.empty()
        : Optional.of("the map \"" + map.getMapName() + "\" could not be downloaded.");
  }

  /** The sizes as the maps window shows them. */
  private static String progressText(final MapDownloadItem map, final long bytes) {
    final long size = map.getDownloadSizeInBytes();
    return "Downloading the map "
        + map.getMapName()
        + ": "
        + FileUtils.byteCountToDisplaySize(bytes)
        + (size > 0 ? " of " + FileUtils.byteCountToDisplaySize(size) : "");
  }
}
