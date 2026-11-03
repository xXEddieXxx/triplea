package games.strategy.engine.framework.startup.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import javax.swing.Icon;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;

/**
 * A small line icon that names an area of the main menu or a kind of formatting at a glance, drawn
 * in the color it is given: a folded map, speech bubbles, a download tray or a light bulb; a quote,
 * a list, a link or a picture. Drawn on a grid of 24 units and scaled to its size, so it stays
 * sharp at every size.
 */
@AllArgsConstructor(access = AccessLevel.PACKAGE)
final class LineIcon implements Icon {
  /** The shapes. */
  enum Kind {
    MAP,
    FORUM,
    UPDATES,
    TIP,
    QUOTE,
    LIST,
    LINK,
    PICTURE
  }

  private static final float GRID = 24f;

  private final Kind kind;
  private final int size;
  private final Color color;

  @Override
  public void paintIcon(final Component component, final Graphics g, final int x, final int y) {
    final Graphics2D g2 = (Graphics2D) g.create();
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
    g2.translate(x, y);
    g2.scale(size / GRID, size / GRID);
    g2.setColor(color);
    g2.setStroke(new BasicStroke(1.7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    g2.draw(shape());
    g2.dispose();
  }

  private Shape shape() {
    return switch (kind) {
      case MAP -> map();
      case FORUM -> forum();
      case UPDATES -> updates();
      case TIP -> tip();
      case QUOTE -> quote();
      case LIST -> list();
      case LINK -> link();
      case PICTURE -> picture();
    };
  }

  /** Three panels folded like a paper map. */
  private static Shape map() {
    final Path2D path = new Path2D.Float();
    path.moveTo(9, 4);
    path.lineTo(3, 6.5);
    path.lineTo(3, 20);
    path.lineTo(9, 17.5);
    path.lineTo(15, 20);
    path.lineTo(21, 17.5);
    path.lineTo(21, 4);
    path.lineTo(15, 6.5);
    path.closePath();
    path.moveTo(9, 4);
    path.lineTo(9, 17.5);
    path.moveTo(15, 6.5);
    path.lineTo(15, 20);
    return path;
  }

  /** A speech bubble in front of a second one. */
  private static Shape forum() {
    final Path2D path = new Path2D.Float();
    path.append(new RoundRectangle2D.Float(2, 4.5f, 15, 9.5f, 4, 4), false);
    path.moveTo(6, 14);
    path.lineTo(5, 17.5f);
    path.lineTo(9, 14);
    path.moveTo(19.5f, 9);
    path.lineTo(20.5f, 9);
    path.quadTo(22, 9, 22, 10.5f);
    path.lineTo(22, 16);
    path.quadTo(22, 17.5f, 20.5f, 17.5f);
    path.lineTo(20, 17.5f);
    path.lineTo(20, 20);
    path.lineTo(16.8f, 17.5f);
    path.lineTo(12, 17.5f);
    return path;
  }

  /** An arrow down into a tray. */
  private static Shape updates() {
    final Path2D path = new Path2D.Float();
    path.moveTo(12, 3.5);
    path.lineTo(12, 14.5);
    path.moveTo(7.5, 10);
    path.lineTo(12, 14.5);
    path.lineTo(16.5, 10);
    path.moveTo(4, 16.5);
    path.lineTo(4, 18.5);
    path.quadTo(4, 20.5, 6, 20.5);
    path.lineTo(18, 20.5);
    path.quadTo(20, 20.5, 20, 18.5);
    path.lineTo(20, 16.5);
    return path;
  }

  /** A light bulb. */
  private static Shape tip() {
    final Path2D path = new Path2D.Float();
    path.moveTo(9.5, 18);
    path.lineTo(14.5, 18);
    path.moveTo(10.5, 21);
    path.lineTo(13.5, 21);
    path.moveTo(9.5, 16);
    path.curveTo(9.5, 15, 9, 14.3, 8.2, 13.6);
    path.curveTo(5.5, 11.3, 5.6, 7, 8.5, 4.6);
    path.curveTo(10.5, 3, 13.5, 3, 15.5, 4.6);
    path.curveTo(18.4, 7, 18.5, 11.3, 15.8, 13.6);
    path.curveTo(15, 14.3, 14.5, 15, 14.5, 16);
    path.closePath();
    return path;
  }

  /** Two quotation marks opening a quote: each a block with its tail curved up. */
  private static Shape quote() {
    final Path2D path = new Path2D.Float();
    for (final float x : new float[] {3.5f, 13.5f}) {
      path.moveTo(x, 11.5);
      path.lineTo(x + 6.5, 11.5);
      path.lineTo(x + 6.5, 18);
      path.lineTo(x, 18);
      path.lineTo(x, 12);
      path.quadTo(x, 7, x + 5, 5.5);
    }
    return path;
  }

  /** Three lines, each after a dot. */
  private static Shape list() {
    final Path2D path = new Path2D.Float();
    for (final float y : new float[] {6.5f, 12, 17.5f}) {
      path.append(new Ellipse2D.Float(4, y - 1, 2, 2), false);
      path.moveTo(9.5, y);
      path.lineTo(20.5, y);
    }
    return path;
  }

  /** Two links of a chain, at a slant. */
  private static Shape link() {
    final Path2D path = new Path2D.Float();
    path.append(new RoundRectangle2D.Float(1.5f, 8.5f, 12.5f, 7, 7, 7), false);
    path.append(new RoundRectangle2D.Float(10, 8.5f, 12.5f, 7, 7, 7), false);
    return AffineTransform.getRotateInstance(-Math.PI / 4, 12, 12).createTransformedShape(path);
  }

  /** A framed picture of a mountain under the sun. */
  private static Shape picture() {
    final Path2D path = new Path2D.Float();
    path.append(new RoundRectangle2D.Float(3, 4.5f, 18, 15, 3, 3), false);
    path.moveTo(3.5, 17);
    path.lineTo(9, 11.5);
    path.lineTo(13, 15.5);
    path.lineTo(15.5, 13);
    path.lineTo(20.5, 18);
    path.append(new Ellipse2D.Float(14.5f, 7.5f, 3, 3), false);
    return path;
  }

  @Override
  public int getIconWidth() {
    return size;
  }

  @Override
  public int getIconHeight() {
    return size;
  }
}
