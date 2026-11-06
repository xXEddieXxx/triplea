package games.strategy.triplea.ui.menubar;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.util.Optional;
import javax.swing.Icon;
import javax.swing.UIManager;

/**
 * A small line icon of the game window's quick bar, drawn in the text color of the theme: save,
 * battle odds, find, history, politics, edit, statistics, network, help or settings. Drawn on a
 * grid of 24 units and scaled to its size, so it stays sharp at every size.
 */
record ToolIcon(ToolIcon.Kind kind, int size) implements Icon {
  /** The shapes. */
  enum Kind {
    SAVE,
    BATTLE_ODDS,
    FIND,
    HISTORY,
    POLITICS,
    EDIT,
    STATISTICS,
    NETWORK,
    HELP,
    SETTINGS
  }

  private static final float GRID = 24f;

  @Override
  public void paintIcon(final Component component, final Graphics g, final int x, final int y) {
    final Graphics2D g2 = (Graphics2D) g.create();
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
    g2.translate(x, y);
    g2.scale(size / GRID, size / GRID);
    final Color color =
        Optional.ofNullable(UIManager.getColor("Label.foreground"))
            .orElseGet(component::getForeground);
    g2.setColor(component.isEnabled() ? color : color.darker());
    g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    g2.draw(shape());
    g2.dispose();
  }

  private Shape shape() {
    return switch (kind) {
      case SAVE -> save();
      case BATTLE_ODDS -> battleOdds();
      case FIND -> find();
      case HISTORY -> history();
      case POLITICS -> politics();
      case EDIT -> edit();
      case STATISTICS -> statistics();
      case NETWORK -> network();
      case HELP -> help();
      case SETTINGS -> settings();
      default -> throw new IllegalStateException("Unknown icon: " + kind);
    };
  }

  /** A floppy disk: its outline with a cut corner, the shutter and the label. */
  private static Shape save() {
    final Path2D path = new Path2D.Float();
    path.moveTo(4, 5);
    path.lineTo(16, 5);
    path.lineTo(20, 9);
    path.lineTo(20, 19);
    path.lineTo(4, 19);
    path.closePath();
    path.append(new Line2D.Float(8, 5, 8, 9), false);
    path.append(new Line2D.Float(8, 9, 15, 9), false);
    path.append(new Line2D.Float(15, 9, 15, 5), false);
    path.append(new RoundRectangle2D.Float(7.5f, 13, 9, 6, 1, 1), false);
    return path;
  }

  /** A die with five pips, for the odds of a battle. */
  private static Shape battleOdds() {
    final Path2D path = new Path2D.Float();
    path.append(new RoundRectangle2D.Float(4, 4, 16, 16, 4, 4), false);
    for (final float[] pip : new float[][] {{8, 8}, {16, 8}, {12, 12}, {8, 16}, {16, 16}}) {
      path.append(new Ellipse2D.Float(pip[0] - 0.9f, pip[1] - 0.9f, 1.8f, 1.8f), false);
    }
    return path;
  }

  /** A magnifying glass. */
  private static Shape find() {
    final Path2D path = new Path2D.Float();
    path.append(new Ellipse2D.Float(4, 4, 12, 12), false);
    path.append(new Line2D.Float(14.5f, 14.5f, 20, 20), false);
    return path;
  }

  /** An open book, for the history of the game. */
  private static Shape history() {
    final Path2D path = new Path2D.Float();
    // the left page, then the right one, both bound at the spine in the middle
    path.moveTo(12, 7);
    path.curveTo(9.5f, 5, 6, 5, 3, 6);
    path.lineTo(3, 18);
    path.curveTo(6, 17, 9.5f, 17, 12, 19);
    path.curveTo(14.5f, 17, 18, 17, 21, 18);
    path.lineTo(21, 6);
    path.curveTo(18, 5, 14.5f, 5, 12, 7);
    path.lineTo(12, 19);
    return path;
  }

  /** A flag on its pole, for the relations between the nations. */
  private static Shape politics() {
    final Path2D path = new Path2D.Float();
    path.append(new Line2D.Float(5, 21, 5, 3), false);
    path.moveTo(5, 4);
    path.curveTo(9, 2, 12, 6, 19, 4);
    path.lineTo(19, 13);
    path.curveTo(12, 15, 9, 11, 5, 13);
    return path;
  }

  /** A pencil, for editing the game. */
  private static Shape edit() {
    final Path2D path = new Path2D.Float();
    path.moveTo(4, 20);
    path.lineTo(5, 15);
    path.lineTo(16, 4);
    path.lineTo(20, 8);
    path.lineTo(9, 19);
    path.closePath();
    path.append(new Line2D.Float(13.5f, 6.5f, 17.5f, 10.5f), false);
    return path;
  }

  /** Three bars on a base line, for statistics and exports. */
  private static Shape statistics() {
    final Path2D path = new Path2D.Float();
    path.append(new Line2D.Float(3, 20, 21, 20), false);
    path.append(new RoundRectangle2D.Float(5, 12, 3, 8, 1, 1), false);
    path.append(new RoundRectangle2D.Float(10.5f, 6, 3, 14, 1, 1), false);
    path.append(new RoundRectangle2D.Float(16, 9, 3, 11, 1, 1), false);
    return path;
  }

  /** Two people, for the other players of a network game. */
  private static Shape network() {
    final Path2D path = new Path2D.Float();
    path.append(new Ellipse2D.Float(5, 5, 6, 6), false);
    path.moveTo(2, 20);
    path.curveTo(2, 15, 14, 15, 14, 20);
    path.append(new Ellipse2D.Float(14, 6, 5, 5), false);
    path.moveTo(15.5f, 14.5f);
    path.curveTo(19, 14, 22, 16, 22, 19);
    return path;
  }

  /** A question mark in a circle. */
  private static Shape help() {
    final Path2D path = new Path2D.Float();
    path.append(new Ellipse2D.Float(3, 3, 18, 18), false);
    path.moveTo(9.5f, 9.5f);
    path.curveTo(9.5f, 6.5f, 14.5f, 6.5f, 14.5f, 9.5f);
    path.curveTo(14.5f, 11.5f, 12, 11.5f, 12, 14);
    path.append(new Ellipse2D.Float(11.4f, 16.6f, 1.2f, 1.2f), false);
    return path;
  }

  /** A gear: a hub and eight teeth around it. */
  private static Shape settings() {
    final Path2D path = new Path2D.Float();
    path.append(new Ellipse2D.Float(8.5f, 8.5f, 7, 7), false);
    path.append(new Ellipse2D.Float(5.5f, 5.5f, 13, 13), false);
    for (int tooth = 0; tooth < 8; tooth++) {
      final double angle = Math.PI / 4 * tooth;
      path.append(
          new Line2D.Double(
              12 + Math.cos(angle) * 6.5,
              12 + Math.sin(angle) * 6.5,
              12 + Math.cos(angle) * 9,
              12 + Math.sin(angle) * 9),
          false);
    }
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
