package games.strategy.engine.framework.ui;

import com.google.common.base.Preconditions;
import games.strategy.engine.framework.lookandfeel.LookAndFeelSwingFrameListener;
import games.strategy.engine.framework.startup.ui.panels.main.HeadedServerSetupModel;
import games.strategy.engine.framework.startup.ui.panels.main.MainPanel;
import games.strategy.engine.framework.startup.ui.panels.main.MainPanelBuilder;
import games.strategy.engine.framework.startup.ui.panels.main.game.selector.GameSelectorModel;
import games.strategy.engine.framework.ui.background.BackgroundTaskRunner;
import games.strategy.triplea.EngineImageLoader;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowEvent;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import org.triplea.game.client.HeadedGameRunner;
import org.triplea.java.timer.Timers;
import org.triplea.swing.JFrameBuilder;

/**
 * Represents the outermost JFrame, maintains the reference to it and controls access. Settings and
 * Maps open as pages of this window while it is showing, so the game stays one window.
 */
public class MainFrame {
  private static MainFrame instance;

  private final JFrame mainJFrame;
  private final MainPanel mainPanel;
  private final HeadedServerSetupModel headedServerSetupModel;
  private final MainFramePages pages;
  private final List<Runnable> quitActions = new ArrayList<>();

  private MainFrame(
      final HeadedServerSetupModel headedServerSetupModel,
      final GameSelectorModel gameSelectorModel) {
    this.headedServerSetupModel = headedServerSetupModel;
    mainJFrame =
        JFrameBuilder.builder()
            .title("TripleA")
            .iconImage(EngineImageLoader.loadFrameIcon())
            .windowClosedAction(HeadedGameRunner::exitGameIfNoWindowsVisible)
            .build();
    // before pack: a frame can drop its window frame only before it is displayable
    WindowPlacement.prepare(mainJFrame);
    BackgroundTaskRunner.setMainFrame(mainJFrame);

    LookAndFeelSwingFrameListener.register(mainJFrame);

    mainPanel =
        new MainPanelBuilder(this::closeWindow)
            .buildMainPanel(headedServerSetupModel, gameSelectorModel);

    headedServerSetupModel.setUi(mainJFrame);
    pages = new MainFramePages(mainPanel, mainJFrame::isVisible, mainPanel::getScreenTitle);
    mainJFrame.add(pages.getScreens());
    mainJFrame.pack();
    // the screen is used: in full screen, maximized, or as the player left the window last time;
    // shown again later, eg after a game, it keeps the size it has then
    WindowPlacement.place(mainJFrame);
    // eg a game or the lobby takes over: the page goes, the setup screen is there on return
    mainJFrame.addComponentListener(
        new ComponentAdapter() {
          @Override
          public void componentHidden(final ComponentEvent e) {
            pages.leavePage();
          }
        });
    MainWindowPages.setMainWindow(pages);
  }

  /** Runs the quit actions, eg leaving the lobby, then closes the window as its frame would. */
  private void closeWindow() {
    quitActions.forEach(Runnable::run);
    mainJFrame.dispatchEvent(new WindowEvent(mainJFrame, WindowEvent.WINDOW_CLOSING));
  }

  /** Quits TripleA as the Quit button does, eg from the main menu. */
  public static void quit() {
    instance.closeWindow();
  }

  public static void buildMainFrame(
      final HeadedServerSetupModel headedServerSetupModel,
      final GameSelectorModel gameSelectorModel) {
    Preconditions.checkState(instance == null);
    instance = new MainFrame(headedServerSetupModel, gameSelectorModel);
  }

  public static void show() {
    SwingUtilities.invokeLater(
        () -> {
          instance.mainJFrame.setVisible(true);
          instance.mainJFrame.toFront();
          instance.mainJFrame.requestFocus();
        });
  }

  public static void startGameDirectly(final HeadedServerSetupModel headedServerSetupModel) {
    Consumer<MainPanel> mainPanelLaunchAction =
        MainPanelBuilder.getMainPanelLaunchAction(headedServerSetupModel);
    SwingUtilities.invokeLater(() -> mainPanelLaunchAction.accept(instance.mainPanel));
  }

  public static void hide() {
    SwingUtilities.invokeLater(() -> instance.mainJFrame.setVisible(false));
  }

  public static void addQuitAction(final Runnable onQuitAction) {
    instance.quitActions.add(onQuitAction);
  }

  public static void loadSaveFile(final Path file) {
    loadSaveFileImpl(file, Instant.now());
  }

  private static void loadSaveFileImpl(final Path file, final Instant startTime) {
    // This may be called at any time, including during start up.
    SwingUtilities.invokeLater(
        () -> {
          // If the MainPanel is up already, load the save file.
          if (instance != null) {
            // a page like Settings would hide the screen the save opens on
            instance.pages.leavePage();
            instance.headedServerSetupModel.openSavedGame(file);
            return;
          }

          if (Duration.between(startTime, Instant.now()).toSeconds() >= 60) {
            throw new IllegalStateException("MainFrame instance not created in 60s");
          }

          // Otherwise "recurse" with a 100ms delay to try again later.
          Timers.executeAfterDelay(
              100, TimeUnit.MILLISECONDS, () -> loadSaveFileImpl(file, startTime));
        });
  }
}
