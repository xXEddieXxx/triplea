package games.strategy.engine.framework.startup.ui;

import games.strategy.engine.framework.lookandfeel.LookAndFeel;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.GridBagLayout;
import java.io.Serial;
import java.util.List;
import java.util.stream.IntStream;
import javax.swing.JComponent;
import javax.swing.JPanel;
import org.triplea.swing.jpanel.GridBagConstraintsBuilder;
import org.triplea.swing.jpanel.GridBagConstraintsFill;

/**
 * The rows of a list in the news band, one below the other with a thin line between them, eg the
 * newest forum topics.
 */
final class FeedList extends JPanel {
  @Serial private static final long serialVersionUID = -6182230476251870390L;

  /** The room of the line between two rows. */
  private static final int LINE = 1;

  FeedList(final List<? extends JComponent> rows) {
    super(new GridBagLayout());
    setOpaque(false);
    IntStream.range(0, rows.size())
        .forEach(
            index ->
                add(
                    rows.get(index),
                    new GridBagConstraintsBuilder(0, index)
                        .weightX(1)
                        .fill(GridBagConstraintsFill.HORIZONTAL)
                        .insets(index == 0 ? 0 : LINE, 0, 0, 0)
                        .build()));
  }

  /** The line above every row but the first, in the room the layout leaves for it. */
  @Override
  protected void paintComponent(final Graphics g) {
    super.paintComponent(g);
    g.setColor(LookAndFeel.getSeparatorColor());
    final Component[] rows = getComponents();
    for (int index = 1; index < rows.length; index++) {
      g.fillRect(0, rows[index].getY() - LINE, getWidth(), LINE);
    }
  }
}
