package games.strategy.engine.framework.map.download;

import games.strategy.engine.framework.lookandfeel.LookAndFeel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.util.function.DoubleSupplier;
import javax.swing.Icon;
import lombok.AllArgsConstructor;

/**
 * A row of stars in the accent color, filled from the left up to a number of stars, eg 3.5 of 5:
 * three full stars, a half one and an outline.
 */
@AllArgsConstructor
final class StarIcon implements Icon {
  private static final int GAP = 2;
  // the side points of the star reach this share of its radius to the left and right
  private static final double SIDE_REACH = Math.cos(Math.toRadians(18));

  private final int size;
  private final int count;
  private final DoubleSupplier filledStars;

  @Override
  public void paintIcon(final Component component, final Graphics g, final int x, final int y) {
    final double filled = filledStars.getAsDouble();
    for (int index = 0; index < count; index++) {
      paintStar(g, x + index * (size + GAP), y, Math.clamp(filled - index, 0.0, 1.0));
    }
  }

  /** A star with a fill from 0 (outline only) to 1 (full). */
  private void paintStar(final Graphics g, final int x, final int y, final double share) {
    final Graphics2D g2 = (Graphics2D) g.create();
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    final Shape star = star(x, y, size);
    final Color accent = LookAndFeel.getAccentColor();
    if (share > 0) {
      // the share of the star's own width, eg 0.3 of a star for 4.3, not of its square
      final double halfWidth = radius(size) * SIDE_REACH;
      final Graphics2D filled = (Graphics2D) g2.create();
      filled.clip(
          new Rectangle2D.Double(x + size / 2.0 - halfWidth, y, 2 * halfWidth * share, size));
      filled.setColor(accent);
      filled.fill(star);
      filled.dispose();
    }
    g2.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    g2.setColor(share > 0 ? accent : LookAndFeel.getSecondaryTextColor());
    g2.draw(star);
    g2.dispose();
  }

  /** A five-pointed star in the square at x, y, its first point straight up. */
  private static Shape star(final int x, final int y, final int size) {
    final Path2D path = new Path2D.Double();
    final double outer = radius(size);
    final double inner = outer * 0.48;
    for (int point = 0; point < 10; point++) {
      final double radius = point % 2 == 0 ? outer : inner;
      final double angle = Math.toRadians(-90 + point * 36.0);
      final double px = x + size / 2.0 + radius * Math.cos(angle);
      final double py = y + size / 2.0 + radius * Math.sin(angle) + outer * 0.08;
      if (point == 0) {
        path.moveTo(px, py);
      } else {
        path.lineTo(px, py);
      }
    }
    path.closePath();
    return path;
  }

  /** The distance of the star's points from its middle. */
  private static double radius(final int size) {
    return size / 2.0 - 1;
  }

  @Override
  public int getIconWidth() {
    return count * size + (count - 1) * GAP;
  }

  @Override
  public int getIconHeight() {
    return size;
  }
}
