package games.strategy.engine.framework.startup.ui;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.awt.RenderingHints;
import java.io.Serial;
import javax.swing.JPanel;
import javax.swing.UIManager;

/**
 * The panel of the main menu and its pages: rounded, a little lighter than the window, slightly
 * see-through and with a faint line around it; eg the forum and the updates on the map of the day,
 * and the forum games.
 */
class RoundedPanel extends JPanel {
  @Serial private static final long serialVersionUID = 7754513964113592735L;

  private static final int ARC = 16;

  /** How opaque the panel is, 0 to 255: a little of what lies below shows through. */
  private static final int OPACITY = 222;

  RoundedPanel(final LayoutManager layout) {
    super(layout);
    setOpaque(false);
  }

  @Override
  protected void paintComponent(final Graphics g) {
    final Graphics2D g2 = (Graphics2D) g.create();
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g2.setColor(
        MenuColors.withAlpha(MenuColors.mix(MenuColors.windowColor(), Color.WHITE, 0.02), OPACITY));
    g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, ARC, ARC);
    final Color text = UIManager.getColor("Label.foreground");
    g2.setColor(MenuColors.withAlpha(text == null ? Color.GRAY : text, 22));
    g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, ARC, ARC);
    g2.dispose();
  }
}
