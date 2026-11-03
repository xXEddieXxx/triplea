package games.strategy.engine.framework.startup.ui;

import games.strategy.engine.framework.lookandfeel.LookAndFeel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.io.Serial;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * An area of the main menu's news band, eg the forum, lying on the dark lower part of the map of
 * the day: a {@link RoundedPanel} with its title, below it the rows to click, a short message
 * instead, or the shape of the rows while they load.
 */
class NewsArea extends RoundedPanel {
  @Serial private static final long serialVersionUID = -4981137092385310624L;

  private final JPanel body = new JPanel(new BorderLayout());

  /**
   * @param heading the area's title
   * @param loading the shape of the rows while they load
   */
  NewsArea(final SectionHeading heading, final JComponent loading) {
    super(new BorderLayout());
    body.setOpaque(false);
    // the rows' texts line up with the title, their hover fill reaches a little further out
    body.setBorder(
        BorderFactory.createEmptyBorder(
            MenuSpacing.S,
            MenuSpacing.L - MenuSpacing.S,
            MenuSpacing.M,
            MenuSpacing.L - MenuSpacing.S));
    body.add(loading);
    add(heading, BorderLayout.NORTH);
    add(body, BorderLayout.CENTER);
  }

  /** The rows, one below the other with a line between them. */
  final void showRows(final List<? extends JComponent> rows) {
    showContent(new FeedList(rows));
  }

  /** A short message in place of the rows, eg that they cannot be read right now. */
  final void showMessage(final String text) {
    final JLabel message = new JLabel(text);
    message.setForeground(LookAndFeel.getSecondaryTextColor());
    message.setBorder(BorderFactory.createEmptyBorder(0, MenuSpacing.S, 0, 0));
    showContent(message);
  }

  private void showContent(final JComponent content) {
    body.removeAll();
    body.add(content, BorderLayout.NORTH);
    revalidate();
    repaint();
  }

  /**
   * A label whose long text ends in "…" instead of widening the area, eg a topic's title.
   *
   * @param style the font style, eg {@link Font#BOLD}
   * @param larger how many points larger than the label font
   */
  static JLabel newShrinkingLabel(final String text, final int style, final float larger) {
    final JLabel label = new JLabel();
    // the texts come from the forum and GitHub: shown as text, never as HTML, set after the switch
    label.putClientProperty("html.disable", Boolean.TRUE);
    label.setText(text);
    label.setFont(label.getFont().deriveFont(style, label.getFont().getSize2D() + larger));
    final Dimension shrinkable = new Dimension(0, label.getPreferredSize().height);
    label.setPreferredSize(shrinkable);
    label.setMinimumSize(shrinkable);
    return label;
  }
}
