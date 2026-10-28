package games.strategy.engine.framework.ui;

import javax.swing.JComponent;
import javax.swing.JPanel;

/**
 * A window that shows screens like Settings and Maps as one of its pages, in place of its current
 * screen, instead of opening a window for each of them. It shows the page below a {@link BackBar}
 * with the page's title and a link back to the screen it replaced.
 *
 * @see MainWindowPages
 */
public interface PageHost {
  /** Whether the window is on screen; only then do pages open in it. */
  boolean isShowing();

  /**
   * Shows the page as {@link #showPage(String, JComponent, JComponent, Runnable, Runnable)} does.
   */
  default void showPage(
      final String title, final JComponent page, final Runnable back, final Runnable whenLeft) {
    final JPanel noActions = new JPanel();
    noActions.setOpaque(false);
    showPage(title, page, noActions, back, whenLeft);
  }

  /**
   * Shows the page below its bar in place of the current screen.
   *
   * @param actions Shown at the right end of the bar, eg a note.
   * @param back Runs on the link back, Escape or the back button of the mouse; the page then closes
   *     itself with {@link #closePage()}, eg after saving.
   * @param whenLeft Runs if the page goes away without {@link #closePage()}: when another page
   *     replaces it or the window is hidden, eg because a game starts. The page then cleans up, as
   *     if closed.
   */
  void showPage(
      String title, JComponent page, JComponent actions, Runnable back, Runnable whenLeft);

  /** Shows the screen again that the page replaced; called by the page itself, eg on Done. */
  void closePage();
}
