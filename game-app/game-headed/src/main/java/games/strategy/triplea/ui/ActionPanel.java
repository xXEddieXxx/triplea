package games.strategy.triplea.ui;

import games.strategy.engine.data.GameData;
import games.strategy.engine.data.GamePlayer;
import games.strategy.engine.framework.lookandfeel.LookAndFeel;
import games.strategy.triplea.ui.panels.map.MapPanel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.io.Serial;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import javax.annotation.Nullable;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.triplea.swing.JButtonBuilder;
import org.triplea.swing.JLabelBuilder;
import org.triplea.swing.SwingComponents;

/** Abstract superclass for all action panels. */
public abstract class ActionPanel extends JPanel {
  @Serial private static final long serialVersionUID = -5954576036704958641L;
  // fired when the panel's buttons change, see getButtons
  static final String BUTTONS_PROPERTY = "buttons";
  // the phase's title, eg "Germans Tech Roll", as a heading over what to do in it
  protected final JLabel actionLabel =
      new JLabelBuilder()
          .bold()
          .adjustFontSize(2)
          .border(BorderFactory.createEmptyBorder(0, 5, 8, 0))
          .build();

  @Getter(AccessLevel.PROTECTED)
  protected final MapPanel map;

  /** Refreshes the action panel. */
  protected final Runnable refresh =
      () -> SwingUtilities.invokeLater(() -> SwingComponents.redraw(this));

  @Getter(AccessLevel.PROTECTED)
  private final GameData data;

  @Getter(AccessLevel.PROTECTED)
  private GamePlayer currentPlayer;

  /** Called when the history panel shows used to disable the panel temporarily. */
  @Setter @Getter private boolean active;

  private CountDownLatch latch;
  private final Object latchLock = new Object();
  private @Nullable JComponent buttons;
  // the action label's text over the buttons in the button bar, see showButtons
  private final JLabel barTitle =
      new JLabelBuilder()
          .bold()
          .adjustFontSize(2)
          .border(BorderFactory.createEmptyBorder(0, 0, 8, 0))
          .build();

  protected ActionPanel(final GameData data, final MapPanel map) {
    this.data = data;
    this.map = map;
    setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
    setBorder(BorderFactory.createEmptyBorder(10, 7, 8, 7));
    setMinimumSize(new Dimension(240, 0));
    // a title changed later, eg "for bid", changes in the bar too
    actionLabel.addPropertyChangeListener("text", e -> barTitle.setText(actionLabel.getText()));
  }

  protected ActionPanel(final TripleAFrame frame) {
    this(frame.getMapPanel().getData(), frame.getMapPanel());
  }

  /**
   * Creates a label suitable for showing text directly in the action panel. This allows for
   * consistent indentation between different panels without requiring other non-label component to
   * be indented.
   */
  public static JLabel createIndentedLabel() {
    return new JLabelBuilder().border(BorderFactory.createEmptyBorder(0, 5, 0, 0)).build();
  }

  /**
   * Shows the phase's title (the action label, eg "Germans Tech Roll") and below it the buttons
   * that go on in this phase (eg Buy and Done) in the game window's button bar, at the same place
   * in every phase, not in this panel; the buttons side by side, of equal width. Call it after
   * setting the action label's text.
   */
  protected void showButtons(final JButton... buttons) {
    final JPanel buttonsPanel = new JPanel(new GridLayout(1, buttons.length, 8, 0));
    for (final JButton button : buttons) {
      buttonsPanel.add(button);
    }
    // the title shows in the bar, not twice
    actionLabel.setVisible(false);
    final JPanel bar = new JPanel(new BorderLayout());
    bar.add(barTitle, BorderLayout.NORTH);
    bar.add(buttonsPanel, BorderLayout.CENTER);
    setButtons(bar);
  }

  private void setButtons(final @Nullable JComponent newButtons) {
    final JComponent old = buttons;
    buttons = newButtons;
    firePropertyChange(BUTTONS_PROPERTY, old, newButtons);
  }

  /** The buttons this phase shows in the button bar, none while it waits for nothing. */
  Optional<JComponent> getButtons() {
    return Optional.ofNullable(buttons);
  }

  /**
   * Also the buttons go: every panel empties itself before it shows a new state, and shows its
   * buttons again with it.
   */
  @Override
  public void removeAll() {
    super.removeAll();
    setButtons(null);
    actionLabel.setVisible(true);
  }

  /**
   * Wait for another thread to call release. If the thread is interrupted, we will return silently.
   *
   * <p>A memory barrier will be crossed both on entering and before exiting this method.
   *
   * <p>This method will return in the event of the game shutting down.
   */
  protected void waitForRelease() {
    if (Thread.currentThread().isInterrupted()) {
      release();
      return;
    }
    synchronized (latchLock) {
      if (latch != null) {
        throw new IllegalStateException("Latch not null");
      }
      latch = new CountDownLatch(1);
      map.getUiContext().addShutdownLatch(latch);
    }
    try {
      latch.await();
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      release();
    }
    synchronized (latchLock) {
      // cross a memory barrier
    }
  }

  /**
   * Release the latch acquired by waitOnNewLatch().
   *
   * <p>This method will be crossed on entering this method.
   */
  protected void release() {
    synchronized (latchLock) {
      // not set up yet
      // this is ok as we set up in one thread and wait in another
      // if the release happens too early, the user will be able to press done again
      if (latch == null) {
        return;
      }
      map.getUiContext().removeShutdownLatch(latch);
      latch.countDown();
      latch = null;
    }
  }

  public void display(final GamePlayer player) {
    currentPlayer = player;
    setActive(true);
  }

  protected JButton createDoneButton() {
    final JButton doneButton =
        new JButtonBuilder()
            .title("Done")
            .actionListener(this::performDone)
            .toolTip(ActionButtonsPanel.DONE_BUTTON_TOOLTIP)
            .build();
    // "Done" ends the phase, the step new players most often look for
    LookAndFeel.setPrimaryButtonStyle(doneButton);
    return doneButton;
  }

  /**
   * Executes the appropriate action when a user clicks the 'done' button. Typically, this will be
   * to end the current turn phase. If the turn phase ends in some other way than a button click,
   * then this should be a no-op. For example, battle phase ends when all battles have been fought
   * and not when the user clicks done (there is no done button during the battle phase).
   */
  public abstract void performDone();
}
