package games.strategy.triplea.ui;

import games.strategy.engine.data.GamePlayer;
import games.strategy.engine.framework.lookandfeel.LookAndFeel;
import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.GridBagLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.Serial;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import org.triplea.swing.JLabelBuilder;
import org.triplea.swing.WrappingText;
import org.triplea.swing.jpanel.GridBagConstraintsBuilder;
import org.triplea.swing.jpanel.GridBagConstraintsFill;

/**
 * Tells the player whose turn it is, which phase of the turn is running and what to do in it, so
 * that new players need not know every phase by heart. Shown as a bar across the top of the game
 * window, with the game's menus at its left end (the history, save, the settings, ...) and the
 * tools at its right end (the battle calculator, edit mode, ...), which show from the start, also
 * in the history; the turn shows once the first game step began.
 */
@Slf4j
class TurnGuidePanel extends JPanel {
  @Serial private static final long serialVersionUID = -2853441587446108064L;

  private final UiContext uiContext;
  private final JLabel turnLabel =
      new JLabelBuilder()
          .bold()
          .adjustFontSize(-1)
          .foregroundColor(LookAndFeel.getSecondaryTextColor())
          .iconTextGap(6)
          .build();
  private final JLabel phaseLabel = new JLabelBuilder().bold().adjustFontSize(3).build();
  private final WrappingText hintText = new WrappingText();
  // counts the steps to show; a step shown late, after a slow flag image, must not win
  private final AtomicLong stepsToShow = new AtomicLong();
  // the player whose turn shows, set on the EDT
  private @Nullable GamePlayer shownPlayer;

  /**
   * @param leadingTool At the left end of the bar, eg the history (which opens on the left), save
   *     and the settings.
   * @param tools The window's tools, eg the battle calculator, at the right end of the bar.
   * @param showCapital Shows the player's capital on the map, when the player's flag is clicked.
   */
  TurnGuidePanel(
      final UiContext uiContext,
      final JComponent leadingTool,
      final JComponent tools,
      final Consumer<GamePlayer> showCapital) {
    super(new BorderLayout(32, 0));
    this.uiContext = uiContext;
    turnLabel.addMouseListener(
        new MouseAdapter() {
          @Override
          public void mouseClicked(final MouseEvent e) {
            if (shownPlayer != null) {
              showCapital.accept(shownPlayer);
            }
          }
        });
    hintText.setForeground(LookAndFeel.getSecondaryTextColor());
    setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, LookAndFeel.getSeparatorColor()),
            BorderFactory.createEmptyBorder(4, 12, 4, 12)));
    // a slim bar over the map, as strategy games show the turn: who and which round, beside it the
    // phase in larger text, and what to do in it
    final JPanel turnAndPhase = new JPanel(new BorderLayout(14, 0));
    turnAndPhase.setOpaque(false);
    turnAndPhase.add(centeredVertically(turnLabel), BorderLayout.WEST);
    turnAndPhase.add(centeredVertically(phaseLabel), BorderLayout.CENTER);
    final JPanel leading = new JPanel(new BorderLayout(16, 0));
    leading.setOpaque(false);
    leading.add(centeredVertically(leadingTool), BorderLayout.WEST);
    leading.add(turnAndPhase, BorderLayout.CENTER);
    add(leading, BorderLayout.WEST);
    final JPanel hint = new JPanel(new GridBagLayout());
    hint.setOpaque(false);
    hint.add(
        hintText,
        new GridBagConstraintsBuilder(0, 0)
            .weightX(1)
            .fill(GridBagConstraintsFill.HORIZONTAL)
            .build());
    add(hint, BorderLayout.CENTER);
    add(centeredVertically(tools), BorderLayout.EAST);
  }

  /** Centered in the height of the bar, whose phase is in larger text. */
  private static JPanel centeredVertically(final JComponent component) {
    final JPanel centered = new JPanel(new GridBagLayout());
    centered.setOpaque(false);
    centered.add(component);
    return centered;
  }

  /**
   * Shows the game step that just started. Call this off the EDT after {@link
   * UiContext#setCurrentPlayer}, loading the flag image may take a moment.
   *
   * @param phaseHint what to do in the step on the own turn (see {@link PhaseHints}), empty for
   *     none
   */
  void showStep(
      final int round,
      final String phaseHint,
      final String stepDisplayName,
      @Nullable final GamePlayer player) {
    final long step = stepsToShow.incrementAndGet();
    final boolean hasPlayer = player != null && !player.isNull();
    final String turnText = hasPlayer ? player.getName() + "  ·  Round " + round : "Round " + round;
    final @Nullable Icon flag = hasPlayer ? loadFlag(player).orElse(null) : null;
    final String hint = hasPlayer ? newHint(phaseHint, player) : "";
    SwingUtilities.invokeLater(
        () -> {
          if (step != stepsToShow.get()) {
            // a later step is on its way
            return;
          }
          turnLabel.setText(turnText);
          turnLabel.setIcon(flag);
          shownPlayer = hasPlayer ? player : null;
          turnLabel.setToolTipText(hasPlayer ? "Show " + player.getName() + "'s capital" : null);
          turnLabel.setCursor(
              hasPlayer
                  ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                  : Cursor.getDefaultCursor());
          phaseLabel.setText(stepDisplayName);
          hintText.setText(hint);
          hintText.setVisible(!hint.isEmpty());
          revalidate();
        });
  }

  private Optional<Icon> loadFlag(final GamePlayer player) {
    try {
      return Optional.of(new ImageIcon(uiContext.getFlagImageFactory().getFlag(player)));
    } catch (final IllegalStateException e) {
      // not even the "missing flag" image exists; the guide works without a flag
      log.info("No flag image for {}", player.getName(), e);
      return Optional.empty();
    }
  }

  /** What to do now: the phase hint on the own turn, else who is playing. */
  private String newHint(final String phaseHint, final GamePlayer player) {
    return uiContext.isCurrentPlayerRemote()
        ? "Waiting for " + player.getName() + " to finish their turn."
        : phaseHint;
  }
}
