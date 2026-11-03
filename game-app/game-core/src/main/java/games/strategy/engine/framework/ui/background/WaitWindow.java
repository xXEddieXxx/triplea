package games.strategy.engine.framework.ui.background;

import java.awt.BorderLayout;
import java.awt.Color;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;
import javax.swing.border.LineBorder;

/**
 * A window that is displayed while loading a game to provide visual feedback to the user during
 * this potentially long-running operation.
 */
public final class WaitWindow extends JWindow {
  private static final long serialVersionUID = -8134956690669346954L;

  /** What the window says while a game loads. */
  public static final String LOADING = "Loading game, please wait...";

  private final WaitPanel mainPanel;
  private final JButton cancel = new JButton("Cancel");

  /** What Cancel does while it shows. */
  private transient Runnable cancelAction = () -> {};

  public WaitWindow() {
    setAlwaysOnTop(true);
    setLocationRelativeTo(null);

    mainPanel = new WaitPanel(LOADING);
    final JPanel content = new JPanel(new BorderLayout());
    content.setBorder(new LineBorder(Color.BLACK));
    content.add(mainPanel, BorderLayout.CENTER);
    final JPanel cancelArea = new JPanel(new BorderLayout());
    cancelArea.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 10));
    cancelArea.add(cancel, BorderLayout.CENTER);
    cancel.setVisible(false);
    cancel.addActionListener(e -> cancelAction.run());
    content.add(cancelArea, BorderLayout.EAST);
    setLayout(new BorderLayout());
    add(content, BorderLayout.CENTER);

    pack();
    setSize(getSize().width, 80);
  }

  /** Shows the wait window. */
  public void showWait() {
    SwingUtilities.invokeLater(() -> setVisible(true));
  }

  /** Says what is being waited for, eg a map's download before the game loads; from any thread. */
  public void setMessage(final String message) {
    SwingUtilities.invokeLater(
        () -> {
          mainPanel.setMessage(message);
          fitWidth();
        });
  }

  /** As wide as the text and the button need, as high as before. */
  private void fitWidth() {
    setSize(Math.max(getWidth(), getPreferredSize().width), getHeight());
  }

  /** Offers Cancel, which runs the action, eg stops a download that is waited for; any thread. */
  public void offerCancel(final Runnable action) {
    SwingUtilities.invokeLater(
        () -> {
          cancelAction = action;
          cancel.setVisible(true);
          fitWidth();
        });
  }

  /** Takes Cancel back once there is nothing to cancel; from any thread. */
  public void withdrawCancel() {
    SwingUtilities.invokeLater(
        () -> {
          cancel.setVisible(false);
          cancelAction = () -> {};
        });
  }

  /** Hides the wait window. */
  public void doneWait() {
    SwingUtilities.invokeLater(
        () -> {
          setVisible(false);
          removeAll();
          dispose();
        });
  }
}
