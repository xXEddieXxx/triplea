package games.strategy.engine.framework.startup.ui;

import games.strategy.triplea.EngineImageLoader;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;

/**
 * The drawn pictures of the tutorial, as the mobile port draws them: territories with their value
 * and a planned move from one territory to the next. A chapter shows one with {@code <img
 * src="art:territory-3">}.
 */
@Slf4j
final class TutorialArt {
  private static final Color SAND = new Color(0xC7A25A);
  private static final Color GREY = new Color(0x9E9E9E);
  private static final Color RUST = new Color(0xB86B4B);
  private static final Color MOVE_RED = new Color(0xDC281E);
  private static final Color BADGE = new Color(0x14, 0x14, 0x14, 0xAA);
  private static final Color BADGE_TEXT = new Color(0xFFE178);
  private static final Color BORDER = new Color(0, 0, 0, 0x99);

  private static final Map<String, Supplier<BufferedImage>> PICTURES =
      Map.of(
          "territory-3",
          () -> territory(3, 84, 56),
          "territory-2",
          () -> territory(2, 70, 56),
          "route",
          TutorialArt::route);

  private TutorialArt() {}

  /** The names of all pictures. */
  static Set<String> names() {
    return PICTURES.keySet();
  }

  /** The picture of the name, eg "territory-3"; none for an unknown name. */
  static Optional<BufferedImage> draw(final String name) {
    return Optional.ofNullable(PICTURES.get(name)).map(Supplier::get);
  }

  /** A territory as the map shows one: a sand-colored blob, its value in a badge. */
  private static BufferedImage territory(final int value, final int width, final int height) {
    final BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    final Graphics2D g = newGraphics(image);
    drawTerritory(g, SAND, value, 0, 0, width, height);
    g.dispose();
    return image;
  }

  private static Graphics2D newGraphics(final BufferedImage image) {
    final Graphics2D g = image.createGraphics();
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g.setRenderingHint(
        RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    g.setRenderingHint(
        RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
    return g;
  }

  private static void drawTerritory(
      final Graphics2D g,
      final Color color,
      final int value,
      final int x,
      final int y,
      final int width,
      final int height) {
    final double[][] corners = {
      {0.1, 0.3}, {0.45, 0.05}, {0.9, 0.2}, {0.95, 0.7}, {0.6, 0.95}, {0.15, 0.8}
    };
    final Polygon blob = new Polygon();
    for (final double[] corner : corners) {
      blob.addPoint(x + (int) (width * corner[0]), y + (int) (height * corner[1]));
    }
    g.setColor(color);
    g.fill(blob);
    g.setColor(BORDER);
    g.setStroke(new BasicStroke(1.5f));
    g.draw(blob);
    g.setFont(g.getFont().deriveFont(Font.BOLD, 12f));
    final FontMetrics metrics = g.getFontMetrics();
    final String text = String.valueOf(value);
    final int badgeWidth = metrics.stringWidth(text) + 14;
    final int badgeHeight = metrics.getHeight();
    final int badgeX = x + (width - badgeWidth) / 2;
    final int badgeY = y + (height - badgeHeight) / 2;
    g.setColor(BADGE);
    g.fillRoundRect(badgeX, badgeY, badgeWidth, badgeHeight, badgeHeight, badgeHeight);
    g.setColor(BADGE_TEXT);
    g.drawString(text, badgeX + 7, badgeY + metrics.getAscent());
  }

  /** A planned move: armour on a territory worth 2, the red arrow to an enemy one worth 3. */
  private static BufferedImage route() {
    final int width = 250;
    final int height = 90;
    final BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    final Graphics2D g = newGraphics(image);
    drawTerritory(g, GREY, 2, 0, 10, 96, 70);
    drawTerritory(g, RUST, 3, width - 96, 10, 96, 70);
    final int startX = (int) (width * 0.3);
    final int endX = (int) (width * 0.72);
    final int middle = height / 2;
    g.setStroke(new BasicStroke(6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    g.setColor(Color.WHITE);
    g.drawLine(startX, middle, endX, middle);
    g.setStroke(new BasicStroke(3.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    g.setColor(MOVE_RED);
    g.drawLine(startX, middle, endX, middle);
    final Path2D head = new Path2D.Double();
    head.moveTo(endX + 5, middle);
    head.lineTo(endX - 8, middle - 7);
    head.lineTo(endX - 8, middle + 7);
    head.closePath();
    g.setColor(Color.WHITE);
    g.setStroke(new BasicStroke(3f));
    g.draw(head);
    g.setColor(MOVE_RED);
    g.fill(head);
    g.setColor(Color.WHITE);
    g.fillOval(startX - 5, middle - 5, 10, 10);
    g.setColor(MOVE_RED);
    g.fillOval(startX - 3, middle - 3, 6, 6);
    unit("armour").ifPresent(armour -> g.drawImage(armour, 6, middle - 15, 30, 30, null));
    g.dispose();
    return image;
  }

  /** The unit's picture of the Germans, which every bundled set of assets has; none if missing. */
  private static Optional<BufferedImage> unit(final String unit) {
    try {
      return Optional.of(EngineImageLoader.loadImage("units", "Germans", unit + ".png"));
    } catch (final RuntimeException e) {
      log.info("No picture of the unit {} for the tutorial", unit, e);
      return Optional.empty();
    }
  }
}
