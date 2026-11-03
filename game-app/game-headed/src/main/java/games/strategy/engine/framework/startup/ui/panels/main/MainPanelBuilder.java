package games.strategy.engine.framework.startup.ui.panels.main;

import games.strategy.engine.framework.map.download.DownloadCoordinator;
import games.strategy.engine.framework.map.file.system.loader.InstalledMapsListing;
import games.strategy.engine.framework.map.listing.MapListingFetcher;
import games.strategy.engine.framework.startup.launcher.ILauncher;
import games.strategy.engine.framework.startup.launcher.ServerLauncher;
import games.strategy.engine.framework.startup.ui.panels.main.game.selector.GameSelectorModel;
import games.strategy.engine.framework.startup.ui.panels.main.game.selector.GameSelectorPanel;
import games.strategy.engine.framework.ui.background.WaitWindow;
import games.strategy.triplea.Constants;
import java.awt.Component;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import javax.annotation.Nonnull;
import javax.swing.JOptionPane;
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
    final GameSelectorPanel gameSelectorPanel = new GameSelectorPanel(gameSelectorModel);
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
   * meanwhile; first downloads its map if it is not installed.
   *
   * @param whenCancelled runs when the player cancelled the map's download, before the window of
   *     the component shows again
   */
  static void launch(
      final Component uiPanel,
      final ILauncher launcher,
      final GameSelectorModel selected,
      final Runnable whenCancelled) {
    final String mapName =
        // a hosted game counts its players when its launcher is made: no download comes between
        launcher instanceof ServerLauncher
            ? ""
            : Optional.ofNullable(selected.getGameData())
                // the property itself: GameData.getMapName gives "null" for a game without one
                .map(data -> data.getProperties().get(Constants.MAP_NAME))
                .map(String::valueOf)
                .orElse("");
    final WaitWindow gameLoadingWindow = new WaitWindow();
    final var frame = JOptionPane.getFrameForComponent(uiPanel);
    gameLoadingWindow.setLocationRelativeTo(frame);
    gameLoadingWindow.setVisible(true);
    gameLoadingWindow.showWait();
    frame.setVisible(false);
    ThreadRunner.runInNewThread(
        () -> {
          try {
            final boolean launch = downloadIfMissing(mapName, gameLoadingWindow);
            try {
              if (launch) {
                launcher.launch();
              } else {
                whenCancelled.run();
              }
            } finally {
              if (!launch) {
                // the player cancelled the map's download: back where the game was chosen
                SwingComponents.setFrameFromComponentVisible(uiPanel);
              }
            }
          } finally {
            gameLoadingWindow.doneWait();
          }
        });
  }

  /**
   * Downloads the game's map if it is not installed, so the game starts as if it had been there. A
   * map the server does not list or a failed download is left to the game, which asks for the map.
   *
   * @return whether the game is to launch: not when the player cancelled the map's download
   */
  private static boolean downloadIfMissing(final String mapName, final WaitWindow window) {
    try {
      if (mapName.isBlank() || InstalledMapsListing.searchAllMapsForMapName(mapName).isPresent()) {
        return true;
      }
      window.setMessage("Looking for the map " + mapName + "...");
      final Optional<MapDownloadItem> map = MapListingFetcher.findMap(mapName);
      return map.isEmpty() || download(map.get(), window);
    } catch (final RuntimeException e) {
      // eg the server's listing unreadable: the game asks for the map as before, so no warning
      // dialog of its own
      log.info("The map {} could not be downloaded before the game", mapName, e);
      return true;
    } finally {
      window.setMessage(WaitWindow.LOADING);
    }
  }

  /** Downloads the map; whether the game is to launch: not when the player cancelled. */
  private static boolean download(final MapDownloadItem map, final WaitWindow window) {
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
    try {
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
      return false;
    } finally {
      window.withdrawCancel();
    }
    return !cancelled.get();
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
