package games.strategy.engine.framework.startup.ui;

import games.strategy.engine.framework.lookandfeel.LookAndFeel;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.io.Serial;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.UIManager;

/**
 * A short word on a rounded tint, eg "NEW" on a forum topic or "MAP" on an update: small, bold and
 * a little spaced, it marks a row without competing with its title.
 */
final class Pill extends JLabel {
  @Serial private static final long serialVersionUID = -1548934905267401713L;

  /** The room around the word: the tint hugs it, rounded at the ends. */
  private static final int ROOM_ABOVE = 1;

  private static final int ROOM_BESIDE = 6;

  private final Color tint;

  private Pill(final String text, final Color foreground, final Color tint) {
    super(text);
    this.tint = tint;
    setForeground(foreground);
    setFont(getFont().deriveFont(Font.BOLD, getFont().getSize2D() - 3));
    setBorder(BorderFactory.createEmptyBorder(ROOM_ABOVE, ROOM_BESIDE, ROOM_ABOVE, ROOM_BESIDE));
  }

  /** In the accent color, the one color of the menu, eg "NEW". */
  static Pill newAccented(final String text) {
    final Color accent = UIManager.getColor("TripleA.highlightBackground");
    final Color color = accent == null ? LookAndFeel.getSecondaryTextColor() : accent;
    return new Pill(text, color, MenuColors.withAlpha(color, 38));
  }

  /** In the quiet text color, eg the kind of an update. */
  static Pill newQuiet(final String text) {
    final Color foreground = UIManager.getColor("Label.foreground");
    final Color tint = MenuColors.withAlpha(foreground == null ? Color.GRAY : foreground, 18);
    return new Pill(text, LookAndFeel.getSecondaryTextColor(), tint);
  }

  @Override
  protected void paintComponent(final Graphics g) {
    final Graphics2D g2 = (Graphics2D) g.create();
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g2.setColor(tint);
    g2.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
    g2.dispose();
    super.paintComponent(g);
  }
}
