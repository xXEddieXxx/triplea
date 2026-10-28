package org.triplea.game.client.ui.swing.laf;

import com.formdev.flatlaf.FlatDarkLaf;

/**
 * Default TripleA theme: FlatLaf Dark, near-black and without colored accents. FlatLaf reads the
 * colors from {@code TripleaDarkLaf.properties} next to this class.
 */
public class TripleaDarkLaf extends FlatDarkLaf {
  static final String NAME = "TripleA Dark";

  @Override
  public String getName() {
    return NAME;
  }
}
