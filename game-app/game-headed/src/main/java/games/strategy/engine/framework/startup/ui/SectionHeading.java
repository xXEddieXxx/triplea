package games.strategy.engine.framework.startup.ui;

import games.strategy.engine.framework.lookandfeel.LookAndFeel;
import games.strategy.engine.framework.startup.ui.LineIcon.Kind;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.font.TextAttribute;
import java.io.Serial;
import java.util.Locale;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.SwingConstants;

/**
 * The title of an area of the main menu's news band, eg the forum: an icon and the area's name on
 * the left, optionally a way on the right, eg to all topics, and a fine line below. Every title has
 * the same height, so the rows of both areas start on one line.
 */
final class SectionHeading extends JComponent {
  @Serial private static final long serialVersionUID = -2215893405612278733L;

  private static final int ICON_SIZE = 16;

  private static final int LINE = 1;

  private final JLabel name;

  private SectionHeading(final String title, final Kind icon) {
    setLayout(new BorderLayout(MenuSpacing.M, 0));
    name = newLabel(title, icon, LookAndFeel.getSecondaryTextColor());
    add(name, BorderLayout.WEST);
    setBorder(BorderFactory.createEmptyBorder(0, MenuSpacing.L, LINE, MenuSpacing.L));
  }

  static SectionHeading newHeading(final String title, final Kind icon) {
    return new SectionHeading(title, icon);
  }

  /** A title bar with a way on its right, eg a link. */
  static SectionHeading newHeading(final String title, final Kind icon, final JComponent action) {
    final SectionHeading heading = new SectionHeading(title, icon);
    heading.add(action, BorderLayout.EAST);
    return heading;
  }

  /**
   * An area's name as the title bars show it: in capitals, bold and a little spaced, after its icon
   * in the color given; eg also on the map of the day.
   */
  static JLabel newLabel(final String title, final Kind icon, final Color iconColor) {
    final JLabel label =
        new JLabel(
            title.toUpperCase(Locale.ROOT),
            new LineIcon(icon, ICON_SIZE, iconColor),
            SwingConstants.LEADING);
    label.setIconTextGap(MenuSpacing.S + 2);
    final Font font = label.getFont();
    label.setFont(
        font.deriveFont(Font.BOLD, font.getSize2D() - 1)
            .deriveFont(Map.of(TextAttribute.TRACKING, 0.07f)));
    return label;
  }

  /** The fine line below the title. */
  @Override
  protected void paintComponent(final Graphics g) {
    g.setColor(LookAndFeel.getSeparatorColor());
    g.fillRect(0, getHeight() - LINE, getWidth(), LINE);
  }

  /** The name with room above and below it, whatever stands beside it, and the line below. */
  @Override
  public Dimension getPreferredSize() {
    final Dimension size = super.getPreferredSize();
    return new Dimension(
        size.width,
        Math.max(size.height, name.getPreferredSize().height + 2 * MenuSpacing.M + LINE));
  }
}
