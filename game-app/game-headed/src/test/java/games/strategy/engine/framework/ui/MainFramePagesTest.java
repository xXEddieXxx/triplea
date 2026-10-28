package games.strategy.engine.framework.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Container;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import javax.swing.JButton;
import javax.swing.JPanel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

final class MainFramePagesTest {
  private final JPanel setupScreens = new JPanel();
  private final MainFramePages pages =
      new MainFramePages(setupScreens, () -> true, () -> "Main menu");
  private final List<String> leftPages = new ArrayList<>();
  private final List<String> backs = new ArrayList<>();

  @DisplayName("Opening a page in place of another lets the other one clean up, once")
  @Test
  void anotherPageLeavesTheFirst() {
    givenPage("Settings");

    pages.showPage("Maps", new JPanel(), () -> backs.add("Maps"), () -> leftPages.add("Maps"));

    assertThat(leftPages)
        .as("Settings saves when Maps replaces it; Maps is still open")
        .containsExactly("Settings");
    assertThat(setupScreens.isVisible()).as("Maps is shown instead of the setup").isFalse();
  }

  @DisplayName("Closing a page shows the setup again and does not run its clean-up")
  @Test
  void closedPageIsNotLeft() {
    givenPage("Settings");

    pages.closePage();

    assertThat(leftPages)
        .as("the page closed itself, eg by Done, and cleaned up already")
        .isEmpty();
    assertThat(setupScreens.isVisible()).as("Done goes back to the setup screen").isTrue();
  }

  @DisplayName("Leaving a page, eg when a game takes the window, runs its clean-up only once")
  @Test
  void leftPageCleansUpOnce() {
    givenPage("Settings");

    pages.leavePage();
    pages.leavePage();

    assertThat(leftPages)
        .as("a second hide of the window finds no page left")
        .containsExactly("Settings");
    assertThat(setupScreens.isVisible()).as("the setup is there on return").isTrue();
  }

  @DisplayName("A page shows a bar whose link names the setup screen and runs the page's back")
  @Test
  void pageBarLeadsBackToTheSetupScreen() {
    givenPage("Settings");

    findButton(pages.getScreens(), "Back to Main menu").orElseThrow().doClick();

    assertThat(backs)
        .as("the link names where it leads and lets the page go back, eg saving first")
        .containsExactly("Settings");
  }

  private void givenPage(final String title) {
    pages.showPage(title, new JPanel(), () -> backs.add(title), () -> leftPages.add(title));
  }

  /** The button with the accessible name, eg "Back to Main menu" of the icon-only link back. */
  private static Optional<JButton> findButton(final Container container, final String name) {
    return Arrays.stream(container.getComponents())
        .flatMap(
            component ->
                component instanceof JButton button
                        && name.equals(button.getAccessibleContext().getAccessibleName())
                    ? Stream.of(button)
                    : component instanceof Container child
                        ? findButton(child, name).stream()
                        : Stream.empty())
        .findAny();
  }
}
