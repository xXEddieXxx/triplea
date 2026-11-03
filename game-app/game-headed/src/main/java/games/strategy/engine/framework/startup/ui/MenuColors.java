package games.strategy.engine.framework.startup.ui;

import java.awt.Color;
import javax.swing.UIManager;

/** The colors the main menu derives from its theme. */
final class MenuColors {
  private MenuColors() {}

  /** The theme's window color, which the menu, the stage's shades and the news panels build on. */
  static Color windowColor() {
    final Color window = UIManager.getColor("Panel.background");
    return window == null ? Color.DARK_GRAY : window;
  }

  /** The color, see-through by the given alpha (0 to 255); for the gradients of the menu too. */
  static Color withAlpha(final Color color, final int alpha) {
    return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
  }

  /** The color, the share of the way to the other. */
  static Color mix(final Color color, final Color other, final double share) {
    return new Color(
        (int) (color.getRed() * (1 - share) + other.getRed() * share),
        (int) (color.getGreen() * (1 - share) + other.getGreen() * share),
        (int) (color.getBlue() * (1 - share) + other.getBlue() * share));
  }
}
