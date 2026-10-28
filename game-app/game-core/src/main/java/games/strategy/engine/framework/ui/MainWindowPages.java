package games.strategy.engine.framework.ui;

import java.util.Optional;
import javax.annotation.Nullable;

/**
 * Knows the main window of the game client, so that Settings and Maps open as its pages while it is
 * showing, and in a window of their own otherwise, eg during a game.
 */
public final class MainWindowPages {
  private static @Nullable PageHost mainWindow;

  private MainWindowPages() {}

  /** Called by the main window once it exists. */
  public static void setMainWindow(final PageHost window) {
    mainWindow = window;
  }

  /** Returns the main window if it is on screen. */
  public static Optional<PageHost> getVisibleMainWindow() {
    return Optional.ofNullable(mainWindow).filter(PageHost::isShowing);
  }
}
