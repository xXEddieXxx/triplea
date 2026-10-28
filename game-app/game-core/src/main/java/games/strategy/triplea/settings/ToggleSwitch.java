package games.strategy.triplea.settings;

import games.strategy.engine.framework.lookandfeel.LookAndFeel;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.io.Serial;
import java.util.Optional;
import javax.swing.JCheckBox;
import javax.swing.UIManager;

/**
 * An on/off switch as settings pages show them: a rounded track, filled in the theme's accent color
 * while on, with a knob at the side of the current state. It is still a check box, so the space bar
 * toggles it and screen readers announce it as one; its name comes from the label set for it.
 */
final class ToggleSwitch extends JCheckBox {
  @Serial private static final long serialVersionUID = -4598810326361592457L;

  private static final int TRACK_WIDTH = 48;
  private static final int TRACK_HEIGHT = 28;
  private static final int KNOB_SIZE = 22;
  private static final int FOCUS_WIDTH = 2;

  ToggleSwitch() {
    setOpaque(false);
    setBorderPainted(false);
    setFocusPainted(false);
  }

  @Override
  public Dimension getPreferredSize() {
    return new Dimension(TRACK_WIDTH + 2 * FOCUS_WIDTH, TRACK_HEIGHT + 2 * FOCUS_WIDTH);
  }

  @Override
  public Dimension getMinimumSize() {
    return getPreferredSize();
  }

  @Override
  public Dimension getMaximumSize() {
    return getPreferredSize();
  }

  @Override
  protected void paintComponent(final Graphics graphics) {
    final Graphics2D g = (Graphics2D) graphics.create();
    try {
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      if (!isEnabled()) {
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f));
      }
      final int x = (getWidth() - TRACK_WIDTH) / 2;
      final int y = (getHeight() - TRACK_HEIGHT) / 2;
      if (isFocusOwner()) {
        g.setColor(themeColor("Component.focusColor"));
        g.setStroke(new BasicStroke(FOCUS_WIDTH));
        g.drawRoundRect(
            x - FOCUS_WIDTH / 2,
            y - FOCUS_WIDTH / 2,
            TRACK_WIDTH + FOCUS_WIDTH,
            TRACK_HEIGHT + FOCUS_WIDTH,
            TRACK_HEIGHT + FOCUS_WIDTH,
            TRACK_HEIGHT + FOCUS_WIDTH);
      }
      g.setColor(isSelected() ? LookAndFeel.getAccentColor() : themeColor("Component.borderColor"));
      g.fillRoundRect(x, y, TRACK_WIDTH, TRACK_HEIGHT, TRACK_HEIGHT, TRACK_HEIGHT);

      final int gap = (TRACK_HEIGHT - KNOB_SIZE) / 2;
      final int knobX = isSelected() ? x + TRACK_WIDTH - gap - KNOB_SIZE : x + gap;
      g.setColor(
          isSelected()
              ? themeColor("CheckBox.icon.checkmarkColor")
              : themeColor("Label.foreground"));
      g.fillOval(knobX, y + gap, KNOB_SIZE, KNOB_SIZE);
    } finally {
      g.dispose();
    }
  }

  private static Color themeColor(final String key) {
    return Optional.ofNullable(UIManager.getColor(key)).orElseGet(LookAndFeel::getSeparatorColor);
  }
}
