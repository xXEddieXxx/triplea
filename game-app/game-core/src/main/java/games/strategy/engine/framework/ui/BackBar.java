package games.strategy.engine.framework.ui;

import com.google.common.annotations.VisibleForTesting;
import games.strategy.engine.framework.I18nEngineFramework;
import games.strategy.engine.framework.I18nResourceBundle;
import games.strategy.engine.framework.lookandfeel.LookAndFeel;
import java.awt.AWTEvent;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.KeyboardFocusManager;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.AWTEventListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.io.Serial;
import javax.annotation.Nullable;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.text.JTextComponent;
import org.triplea.swing.JLabelBuilder;
import org.triplea.swing.SwingAction;

/**
 * The bar at the top of every screen of the main window except the main menu, and of the windows
 * that stand in for such a screen, eg Settings during a game: on the left the link back, an arrow
 * whose tool tip says where it leads, next to it the title of the screen, on the right room for
 * actions or a note. The link has the same place and size on every screen, so players find it
 * without looking. Escape and the back button of the mouse go back too, while the bar is on screen.
 */
public final class BackBar extends JPanel {
  /** Room between the window edge and the link back, the same on every screen. */
  private static final int LEFT_MARGIN = 16;

  /** The least width and height of the link back, large enough to hit easily. */
  private static final int LINK_SIZE = 44;

  /** The number {@link MouseEvent#getButton()} gives the back button of a mouse. */
  private static final int MOUSE_BACK_BUTTON = 4;

  private static final String BACK_ACTION_KEY = "back";
  @Serial private static final long serialVersionUID = -3384931557205417592L;

  private final transient Runnable back;
  private final transient AWTEventListener mouseBackButton = this::mouseEventDispatched;

  /**
   * Creates the bar.
   *
   * @param destination Where the link leads, eg "Main menu"; its tool tip reads "Back to Main
   *     menu".
   * @param title The title of the screen.
   * @param back Goes back, eg shows the main menu; the link, Escape and the mouse run it.
   */
  public BackBar(final String destination, final String title, final Runnable back) {
    this(destination, title, back, newEmptyActions());
  }

  /**
   * Creates the bar with actions or a note on the right.
   *
   * @param actions Shown at the right end of the bar, eg the buttons of the screen.
   */
  public BackBar(
      final String destination, final String title, final Runnable back, final JComponent actions) {
    super(new BorderLayout());
    this.back = back;
    setOpaque(false);
    setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, LookAndFeel.getSeparatorColor()),
            BorderFactory.createEmptyBorder(4, LEFT_MARGIN, 4, LEFT_MARGIN + 8)));

    final JButton link = new BackLink(backTo(destination));
    link.addActionListener(e -> back.run());
    final JPanel left = new JPanel();
    left.setOpaque(false);
    left.setLayout(new BoxLayout(left, BoxLayout.X_AXIS));
    left.add(link);
    left.add(Box.createHorizontalStrut(12));
    left.add(new JLabelBuilder(title).bold().adjustFontSize(8).build());
    add(left, BorderLayout.WEST);
    add(actions, BorderLayout.EAST);

    getInputMap(WHEN_IN_FOCUSED_WINDOW)
        .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), BACK_ACTION_KEY);
    getActionMap()
        .put(
            BACK_ACTION_KEY,
            SwingAction.of(
                BACK_ACTION_KEY,
                () -> {
                  // Escape while typing, eg in the lobby chat or a search, must not leave the
                  // screen
                  if (!isTyping(
                      KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner())) {
                    back.run();
                  }
                }));
  }

  /** The tool tip and accessible name of the link: "Back to Main menu" for "Main menu". */
  @VisibleForTesting
  static String backTo(final String destination) {
    // from the language files: "Back to Main menu", "Zurück zu: Hauptmenü"; the destination is a
    // title and keeps its spelling, German nouns must stay capitalized
    final I18nResourceBundle bundle = I18nEngineFramework.get();
    return destination.isEmpty()
        ? bundle.getText("startup.BackBar.Back")
        : bundle.getText("startup.BackBar.BackTo", destination);
  }

  /** Whether the focus is in a field to type in, where Escape belongs to the field. */
  @VisibleForTesting
  static boolean isTyping(final @Nullable Component focusOwner) {
    return focusOwner instanceof JTextComponent text && text.isEditable();
  }

  private static JComponent newEmptyActions() {
    final JPanel actions = new JPanel();
    actions.setOpaque(false);
    return actions;
  }

  /** A note in the quiet color, eg for the right end of the bar. */
  public static JLabel newNote(final String text) {
    return new JLabelBuilder(text).foregroundColor(LookAndFeel.getSecondaryTextColor()).build();
  }

  @Override
  public void addNotify() {
    super.addNotify();
    Toolkit.getDefaultToolkit().addAWTEventListener(mouseBackButton, AWTEvent.MOUSE_EVENT_MASK);
  }

  @Override
  public void removeNotify() {
    Toolkit.getDefaultToolkit().removeAWTEventListener(mouseBackButton);
    super.removeNotify();
  }

  /** The back button of the mouse in the window of the bar goes back while the bar is shown. */
  private void mouseEventDispatched(final AWTEvent event) {
    if (event instanceof MouseEvent mouseEvent
        && isBackButtonRelease(mouseEvent)
        && isShowing()
        && windowOf(mouseEvent.getComponent()) == SwingUtilities.getWindowAncestor(this)) {
      mouseEvent.consume();
      back.run();
    }
  }

  @VisibleForTesting
  static boolean isBackButtonRelease(final MouseEvent event) {
    return event.getID() == MouseEvent.MOUSE_RELEASED && event.getButton() == MOUSE_BACK_BUTTON;
  }

  private static Window windowOf(final Component component) {
    return component instanceof Window window
        ? window
        : SwingUtilities.getWindowAncestor(component);
  }

  /**
   * The borderless link back, an arrow without text, at least {@link #LINK_SIZE} wide and high
   * whatever the text size; its tool tip and accessible name say where it leads.
   */
  private static final class BackLink extends JButton {
    @Serial private static final long serialVersionUID = 6519430467386186027L;

    BackLink(final String name) {
      super(new ArrowIcon());
      LookAndFeel.setBorderlessButtonStyle(this);
      setToolTipText(name);
      getAccessibleContext().setAccessibleName(name);
    }

    @Override
    public Dimension getPreferredSize() {
      final Dimension size = super.getPreferredSize();
      return new Dimension(Math.max(size.width, LINK_SIZE), Math.max(size.height, LINK_SIZE));
    }

    @Override
    public Dimension getMaximumSize() {
      return getPreferredSize();
    }
  }

  /** A left arrow, ←, in the color of the theme's text. */
  private static final class ArrowIcon implements Icon {
    private static final int SIZE = 20;

    @Override
    public void paintIcon(final Component c, final Graphics g, final int x, final int y) {
      final Graphics2D g2 = (Graphics2D) g.create();
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
      final Color color = UIManager.getColor("Label.foreground");
      g2.setColor(color == null ? c.getForeground() : color);
      g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
      g2.translate(x, y);
      final Path2D arrow = new Path2D.Float();
      // the shaft, then the head from its upper to its lower end through the tip
      arrow.moveTo(17, 10);
      arrow.lineTo(3, 10);
      arrow.moveTo(9, 4);
      arrow.lineTo(3, 10);
      arrow.lineTo(9, 16);
      g2.draw(arrow);
      g2.dispose();
    }

    @Override
    public int getIconWidth() {
      return SIZE;
    }

    @Override
    public int getIconHeight() {
      return SIZE;
    }
  }
}
