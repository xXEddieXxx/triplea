package org.triplea.game.client.ui.swing.laf;

import com.formdev.flatlaf.FlatLightLaf;

/**
 * Light TripleA theme: FlatLaf Light without colored accents. FlatLaf reads the colors from {@code
 * TripleaLightLaf.properties} next to this class.
 */
public class TripleaLightLaf extends FlatLightLaf {
  static final String NAME = "TripleA Light";

  @Override
  public String getName() {
    return NAME;
  }
}
