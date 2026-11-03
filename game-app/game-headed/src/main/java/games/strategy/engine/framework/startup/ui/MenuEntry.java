package games.strategy.engine.framework.startup.ui;

import games.strategy.engine.framework.lookandfeel.LookAndFeel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.Serial;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.UIManager;

/**
 * An entry of a game menu, as strategy games show them: a large title as plain text, no box,
 * optionally a short line below it. Hover and keyboard focus light it up and show a bar on its
 * left; a highlighted entry, the one to advertise, is in the accent color all the time.
 */
final class MenuEntry extends JButton {
  @Serial private static final long serialVersionUID = 6427839582612730410L;

  private static final int BAR_WIDTH = 4;

  /**
   * The room left of the texts, for the bar; what stands above or below lines up with the texts.
   */
  static final int INDENT = 20;

  private final JLabel titleLabel;
  private final JLabel descriptionLabel;
  private final JPanel texts = new JPanel(new BorderLayout(0, MenuSpacing.XS));
  private final float titleSize;
  private final float descriptionSize;
  private final boolean highlighted;
  private boolean active;

  private MenuEntry(
      final String title,
      final String description,
      final float titleSize,
      final boolean highlighted) {
    this.highlighted = highlighted;
    setLayout(new BorderLayout());
    setContentAreaFilled(false);
    setBorderPainted(false);
    setFocusPainted(false);
    setOpaque(false);
    setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    setBorder(BorderFactory.createEmptyBorder(MenuSpacing.S, INDENT, MenuSpacing.S, INDENT));
    getAccessibleContext().setAccessibleName(title);
    getAccessibleContext().setAccessibleDescription(description);

    this.titleSize = titleSize;
    titleLabel = new JLabel(title);
    titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, titleSize));
    descriptionLabel = new JLabel(description);
    descriptionLabel.setVisible(!description.isEmpty());
    descriptionSize = descriptionLabel.getFont().getSize2D();
    texts.setOpaque(false);
    texts.add(titleLabel, BorderLayout.NORTH);
    texts.add(descriptionLabel, BorderLayout.CENTER);
    add(texts, BorderLayout.CENTER);

    addMouseListener(
        new MouseAdapter() {
          @Override
          public void mouseEntered(final MouseEvent e) {
            setActive(true);
          }

          @Override
          public void mouseExited(final MouseEvent e) {
            setActive(hasFocus());
          }
        });
    addFocusListener(
        new FocusAdapter() {
          @Override
          public void focusGained(final FocusEvent e) {
            setActive(true);
          }

          @Override
          public void focusLost(final FocusEvent e) {
            setActive(getMousePosition() != null);
          }
        });
    updateColors();
  }

  /** A main entry, eg "Play locally", with an optional line below it ("" for none). */
  static MenuEntry newEntry(final String title, final String description) {
    return new MenuEntry(title, description, titleSize(10), false);
  }

  /** The entry to advertise, eg "Multiplayer": in the accent color all the time. */
  static MenuEntry newHighlighted(final String title, final String description) {
    return new MenuEntry(title, description, titleSize(10), true);
  }

  /**
   * The entry to advertise with a live line below its description, eg how busy the lobby is below
   * Multiplayer: what the click leads to, on the same click.
   */
  static MenuEntry newHighlighted(
      final String title, final String description, final JComponent status) {
    final MenuEntry entry = newHighlighted(title, description);
    final JPanel line = new JPanel(new BorderLayout());
    line.setOpaque(false);
    line.setBorder(BorderFactory.createEmptyBorder(MenuSpacing.XS, 0, 0, 0));
    line.add(status, BorderLayout.WEST);
    entry.texts.add(line, BorderLayout.SOUTH);
    return entry;
  }

  /**
   * A further entry of the main menu, eg "Settings": a step smaller than the ways to play, so those
   * stand out.
   */
  static MenuEntry newSecondary(final String title) {
    return new MenuEntry(title, "", titleSize(6), false);
  }

  /** A smaller entry, eg a chapter of the tutorial. */
  static MenuEntry newSmall(final String title, final String description) {
    return new MenuEntry(title, description, titleSize(3), false);
  }

  private static float titleSize(final int pointsAboveText) {
    final Font font = UIManager.getFont("Label.font");
    return (font == null ? 13f : font.getSize2D()) + pointsAboveText;
  }

  /**
   * Sets title and description the factor larger than they were made, eg a step larger in large
   * windows, where the usual size looks lost; 1 is the size they were made in.
   */
  void setTypeScale(final float scale) {
    titleLabel.setFont(titleLabel.getFont().deriveFont(titleSize * scale));
    descriptionLabel.setFont(descriptionLabel.getFont().deriveFont(descriptionSize * scale));
  }

  /** Marks the entry as the chosen one of a list, eg the chapter being read. */
  void markChosen() {
    setSelected(true);
    updateColors();
    repaint();
  }

  void clearChosen() {
    setSelected(false);
    updateColors();
    repaint();
  }

  private void setActive(final boolean isActive) {
    active = isActive;
    updateColors();
    repaint();
  }

  private void updateColors() {
    final Color accent = UIManager.getColor("TripleA.highlightBackground");
    final Color text = UIManager.getColor("Label.foreground");
    final boolean lit = active || isSelected();
    titleLabel.setForeground(
        highlighted && accent != null ? accent : lit ? text : MenuColors.withAlpha(text, 215));
    descriptionLabel.setForeground(LookAndFeel.getSecondaryTextColor());
  }

  @Override
  protected void paintComponent(final Graphics g) {
    final boolean lit = active || isSelected();
    if (lit || highlighted) {
      final Graphics2D g2 = (Graphics2D) g.create();
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      final Color accent = UIManager.getColor("TripleA.highlightBackground");
      final Color bar =
          highlighted && accent != null ? accent : UIManager.getColor("Label.foreground");
      if (lit) {
        // a faint wash under the entry, fading out to the right
        g2.setPaint(
            new GradientPaint(
                0, 0, MenuColors.withAlpha(bar, 34), getWidth(), 0, MenuColors.withAlpha(bar, 0)));
        g2.fillRect(0, 0, getWidth(), getHeight());
      }
      g2.setColor(lit ? bar : MenuColors.withAlpha(bar, 140));
      g2.fillRoundRect(0, 6, BAR_WIDTH, getHeight() - 12, BAR_WIDTH, BAR_WIDTH);
      g2.dispose();
    }
    super.paintComponent(g);
  }

  // The button UI sizes a button by its text, which is empty here; size it by the labels instead.
  @Override
  public Dimension getPreferredSize() {
    return isPreferredSizeSet() ? super.getPreferredSize() : getLayout().preferredLayoutSize(this);
  }

  @Override
  public Dimension getMinimumSize() {
    return isMinimumSizeSet() ? super.getMinimumSize() : getLayout().minimumLayoutSize(this);
  }
}
