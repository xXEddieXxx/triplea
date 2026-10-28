package org.triplea.swing.jpanel;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.GridBagConstraints;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

final class GridBagConstraintsAnchorTest {
  @DisplayName("NORTH anchors a component at the top of its cell")
  @Test
  void northIsNorth() {
    final GridBagConstraints constraints =
        new GridBagConstraintsBuilder(0, 0).anchor(GridBagConstraintsAnchor.NORTH).build();

    assertThat(constraints.anchor)
        .as("NONE is no anchor, GridBagLayout refused it as illegal")
        .isEqualTo(GridBagConstraints.NORTH);
  }
}
