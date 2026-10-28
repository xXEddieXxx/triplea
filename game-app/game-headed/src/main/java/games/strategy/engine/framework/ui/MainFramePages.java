package games.strategy.engine.framework.ui;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import javax.swing.JComponent;
import javax.swing.JPanel;
import lombok.Getter;

/**
 * The screens of the main window: the setup screens, or a page like Settings in their place until
 * the page is closed or left. A page shows below a {@link BackBar} whose link leads back to the
 * setup screen it replaced.
 */
final class MainFramePages implements PageHost {
  private static final String SETUP_SCREEN = "setup";
  private static final String PAGE_SCREEN = "page";

  private final CardLayout screenCards = new CardLayout();
  @Getter private final JPanel screens = new JPanel(screenCards);
  private final JPanel page = new JPanel(new BorderLayout());
  private final BooleanSupplier windowShowing;
  // the title of the setup screen, where the link back of a page leads, eg "Main menu"
  private final Supplier<String> setupScreenTitle;
  private Runnable whenPageLeft = () -> {};

  MainFramePages(
      final JComponent setupScreens,
      final BooleanSupplier windowShowing,
      final Supplier<String> setupScreenTitle) {
    this.windowShowing = windowShowing;
    this.setupScreenTitle = setupScreenTitle;
    screens.add(setupScreens, SETUP_SCREEN);
    screens.add(page, PAGE_SCREEN);
  }

  @Override
  public boolean isShowing() {
    return windowShowing.getAsBoolean();
  }

  @Override
  public void showPage(
      final String title,
      final JComponent content,
      final JComponent actions,
      final Runnable back,
      final Runnable whenLeft) {
    leavePage();
    whenPageLeft = whenLeft;
    page.add(new BackBar(setupScreenTitle.get(), title, back, actions), BorderLayout.NORTH);
    page.add(content, BorderLayout.CENTER);
    screenCards.show(screens, PAGE_SCREEN);
    screens.revalidate();
    screens.repaint();
  }

  /** Closes the page, if one is shown, and lets it clean up, eg when a game takes the window. */
  void leavePage() {
    final Runnable leftPage = whenPageLeft;
    closePage();
    leftPage.run();
  }

  @Override
  public void closePage() {
    whenPageLeft = () -> {};
    screenCards.show(screens, SETUP_SCREEN);
    page.removeAll();
  }
}
