package org.triplea.swing;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Insets;
import javax.swing.UIManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

final class WrappingTextTest {
  private static final String LONG_TEXT =
      "A long description that needs several lines when the window is narrow, "
          + "and must never make the window wider than it is.";

  @DisplayName("A long text asks for no width, so it never widens its window")
  @Test
  void preferredWidthIsZero() {
    final WrappingText text = new WrappingText(LONG_TEXT);

    assertThat(text.getPreferredSize().width)
        .as("the layout decides the width, the text wraps into it")
        .isZero();
  }

  @DisplayName("Narrower text grows taller instead of wider")
  @Test
  void narrowTextWrapsIntoMoreLines() {
    final WrappingText wide = givenTextWithWidth(800);
    final WrappingText narrow = givenTextWithWidth(150);

    assertThat(narrow.getPreferredSize().height)
        .as("the same text needs more lines at 150 px than at 800 px")
        .isGreaterThan(wide.getPreferredSize().height);
  }

  @DisplayName("After a theme change the text still looks like a label, not like a text area")
  @Test
  void keepsLabelLookAfterThemeChange() {
    final WrappingText text = new WrappingText(LONG_TEXT);

    text.updateUI();

    assertThat(text.getFont())
        .as("updateUI installs the text area font again")
        .isEqualTo(UIManager.getFont("Label.font"));
    assertThat(text.getInsets()).as("and its border").isEqualTo(new Insets(0, 0, 0, 0));
  }

  private static WrappingText givenTextWithWidth(final int width) {
    final WrappingText text = new WrappingText(LONG_TEXT);
    // the wrapped height is computed for the current size, as after a layout
    text.setSize(width, 1000);
    return text;
  }
}
