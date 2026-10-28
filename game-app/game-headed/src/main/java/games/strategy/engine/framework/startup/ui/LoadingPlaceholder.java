package games.strategy.engine.framework.startup.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.io.Serial;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.swing.JComponent;
import javax.swing.UIManager;

/**
 * Quiet bars in the shape of what is still loading, as web pages and game launchers show them: the
 * place of the content is there at once and nothing jumps when it arrives.
 */
final class LoadingPlaceholder extends JComponent {
  @Serial private static final long serialVersionUID = -1702381958611309456L;

  private static final int ARC = 8;

  /** The bars from top to bottom: their share of the width and their height. */
  private final transient List<Bar> bars;

  private final int gap;

  private record Bar(double widthShare, int height) {}

  private LoadingPlaceholder(final List<Bar> bars, final int gap, final int width) {
    this.bars = bars;
    this.gap = gap;
    final int height = bars.stream().mapToInt(Bar::height).sum() + gap * (bars.size() - 1);
    setPreferredSize(new Dimension(width, height));
  }

  /** Lines of text, eg the topics of the forum: each a longer and a shorter line. */
  static LoadingPlaceholder newTextRows(final int rows, final int width) {
    final List<Bar> bars =
        IntStream.range(0, rows)
            .boxed()
            .flatMap(row -> Stream.of(new Bar(row % 2 == 0 ? 0.7 : 0.55, 16), new Bar(0.3, 12)))
            .toList();
    return new LoadingPlaceholder(bars, 10, width);
  }

  /** Single lines, eg the updates. */
  static LoadingPlaceholder newLines(final int lines, final int width) {
    final List<Bar> bars =
        IntStream.range(0, lines)
            .mapToObj(line -> new Bar(line % 2 == 0 ? 0.8 : 0.65, 14))
            .toList();
    return new LoadingPlaceholder(bars, 12, width);
  }

  /** A picture with a name and a line below it, eg the map of the day. */
  static LoadingPlaceholder newPicture(final int width, final int pictureHeight) {
    return new LoadingPlaceholder(
        List.of(new Bar(0.74, pictureHeight), new Bar(0.4, 24), new Bar(0.6, 14)), 14, width);
  }

  @Override
  protected void paintComponent(final Graphics g) {
    final Graphics2D g2 = (Graphics2D) g.create();
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    final Color text = UIManager.getColor("Label.foreground");
    // a faint tint of the text color is visible on dark and light themes alike
    g2.setColor(text == null ? new Color(128, 128, 128, 40) : MenuEntry.withAlpha(text, 26));
    int y = 0;
    for (final Bar bar : bars) {
      g2.fillRoundRect(0, y, (int) (getWidth() * bar.widthShare()), bar.height(), ARC, ARC);
      y += bar.height() + gap;
    }
    g2.dispose();
  }
}
