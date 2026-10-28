package org.triplea.swing;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Rectangle;
import javax.swing.SwingConstants;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

final class ScrollableJPanelTest {
  private final ScrollableJPanel panel = new ScrollableJPanel();
  private final Rectangle visible = new Rectangle(0, 0, 300, 200);

  @DisplayName("Page Up and Page Down move by the visible height")
  @Test
  void verticalBlockIsAPage() {
    assertThat(panel.getScrollableBlockIncrement(visible, SwingConstants.VERTICAL, 1))
        .as("a fixed 10 px made long panels, eg the settings, barely move")
        .isEqualTo(200);
  }

  @DisplayName("Sideways a block is the visible width")
  @Test
  void horizontalBlockIsAPageWide() {
    assertThat(panel.getScrollableBlockIncrement(visible, SwingConstants.HORIZONTAL, 1))
        .as("the visible width, like the height vertically")
        .isEqualTo(300);
  }
}
