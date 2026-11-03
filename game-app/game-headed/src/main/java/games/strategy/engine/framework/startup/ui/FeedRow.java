package games.strategy.engine.framework.startup.ui;

import games.strategy.engine.framework.lookandfeel.LookAndFeel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.io.Serial;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.UIManager;

/**
 * A row of a list in the main menu's news band, eg a forum topic: the whole row is one link, with a
 * quiet "›" at its end; the mouse or the keyboard on it gives it a soft fill.
 */
final class FeedRow extends JButton {
  @Serial private static final long serialVersionUID = 4021894409162870413L;

  private static final int ARC = 12;

  private FeedRow(final JComponent content, final String name, final Runnable action) {
    setLayout(new BorderLayout(MenuSpacing.M, 0));
    setContentAreaFilled(false);
    setBorderPainted(false);
    setFocusPainted(false);
    setOpaque(false);
    setRolloverEnabled(true);
    setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    setBorder(BorderFactory.createEmptyBorder(MenuSpacing.S, MenuSpacing.S, MenuSpacing.S, 0));
    getAccessibleContext().setAccessibleName(name);
    addActionListener(e -> action.run());
    final JLabel chevron = new JLabel("›");
    chevron.setForeground(LookAndFeel.getSecondaryTextColor());
    chevron.setFont(chevron.getFont().deriveFont(chevron.getFont().getSize2D() + 3));
    add(content, BorderLayout.CENTER);
    add(chevron, BorderLayout.EAST);
  }

  /**
   * @param content what the row shows, eg the topic's title and how often it was answered
   * @param name the row's name for screen readers, eg the topic's title
   * @param action what a click does, eg opening the topic in the browser
   */
  static FeedRow newRow(final JComponent content, final String name, final Runnable action) {
    return new FeedRow(content, name, action);
  }

  @Override
  protected void paintComponent(final Graphics g) {
    if (getModel().isRollover() || isFocusOwner()) {
      final Graphics2D g2 = (Graphics2D) g.create();
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      final Color fill = UIManager.getColor("TextField.background");
      g2.setColor(fill == null ? getBackground() : fill);
      g2.fillRoundRect(0, 0, getWidth(), getHeight(), ARC, ARC);
      g2.dispose();
    }
    super.paintComponent(g);
  }

  // The button UI sizes a button by its text, which is empty here; size it by the content instead.
  @Override
  public Dimension getPreferredSize() {
    return isPreferredSizeSet() ? super.getPreferredSize() : getLayout().preferredLayoutSize(this);
  }

  @Override
  public Dimension getMinimumSize() {
    return isMinimumSizeSet() ? super.getMinimumSize() : getLayout().minimumLayoutSize(this);
  }
}
