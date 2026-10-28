package games.strategy.triplea.ui;

import games.strategy.engine.data.GamePlayer;
import games.strategy.engine.framework.lookandfeel.LookAndFeel;
import java.awt.BorderLayout;
import java.awt.GridBagLayout;
import java.io.Serial;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.Nullable;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.ImageIcon;
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
 * window.
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
  private final JLabel phaseLabel = new JLabelBuilder().bold().adjustFontSize(6).build();
  private final WrappingText hintText = new WrappingText();
  // counts the steps to show; a step shown late, after a slow flag image, must not win
  private final AtomicLong stepsToShow = new AtomicLong();

  TurnGuidePanel(final UiContext uiContext) {
    super(new BorderLayout(32, 0));
    this.uiContext = uiContext;
    hintText.setForeground(LookAndFeel.getSecondaryTextColor());
    setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, LookAndFeel.getSeparatorColor()),
            BorderFactory.createEmptyBorder(8, 16, 8, 16)));
    // a bar over the map, as strategy games show the turn: who and which round, the phase in
    // large, and what to do in it
    final JPanel turnAndPhase = new JPanel(new BorderLayout(0, 2));
    turnAndPhase.setOpaque(false);
    turnAndPhase.add(turnLabel, BorderLayout.NORTH);
    turnAndPhase.add(phaseLabel, BorderLayout.CENTER);
    add(turnAndPhase, BorderLayout.WEST);
    final JPanel hint = new JPanel(new GridBagLayout());
    hint.setOpaque(false);
    hint.add(
        hintText,
        new GridBagConstraintsBuilder(0, 0)
            .weightX(1)
            .fill(GridBagConstraintsFill.HORIZONTAL)
            .build());
    add(hint, BorderLayout.CENTER);
    // nothing to show before the first game step
    setVisible(false);
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
          phaseLabel.setText(stepDisplayName);
          hintText.setText(hint);
          hintText.setVisible(!hint.isEmpty());
          setVisible(true);
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
