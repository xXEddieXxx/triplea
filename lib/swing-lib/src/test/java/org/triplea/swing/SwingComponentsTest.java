package org.triplea.swing;

import static org.assertj.core.api.Assertions.assertThat;

import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

final class SwingComponentsTest {
  @DisplayName("A scroll pane over a plain panel scrolls a text line per wheel notch, not a pixel")
  @Test
  void scrollPaneScrollsByLines() {
    final JScrollPane scroll = SwingComponents.newJScrollPane(new JPanel());

    assertThat(scroll.getVerticalScrollBar().getUnitIncrement(1))
        .as("Swing's default of 1 px makes long panels tedious to scroll")
        .isEqualTo(16);
  }

  @DisplayName("A table keeps scrolling by its own step, one row")
  @Test
  void scrollableViewKeepsItsStep() {
    final JTable table = new JTable(50, 2);
    final JScrollPane scroll = SwingComponents.newJScrollPane(table);

    assertThat(scroll.getVerticalScrollBar().getUnitIncrement(1))
        .as("a set unit increment would override the table's row height")
        .isEqualTo(table.getRowHeight());
  }
}
