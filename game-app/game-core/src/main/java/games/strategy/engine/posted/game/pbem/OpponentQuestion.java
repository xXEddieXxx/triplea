package games.strategy.engine.posted.game.pbem;

import games.strategy.engine.data.GameData;
import games.strategy.engine.data.GamePlayer;
import games.strategy.engine.framework.IGame;
import games.strategy.engine.posted.game.pbf.IForumPoster;
import games.strategy.engine.posted.game.pbf.NodeBbForumPoster;
import java.util.Optional;
import java.util.function.Supplier;
import javax.swing.JButton;
import lombok.RequiredArgsConstructor;

/**
 * A question to the opponent of a game played by forum or email, as its players ask for a decision
 * of the other side during a turn, eg whether to scramble or which units to lose: posted to the
 * game's thread, or sent by email, with the save game, which the opponent can look at. The post is
 * short, the game, the round and the question, so the opponent can answer from the forum's page.
 * The decision's dialog stays open for the answer; the button tells how the asking went, so no
 * window opens over the dialog.
 */
@RequiredArgsConstructor
public final class OpponentQuestion {
  private static final String TITLE = "Decision Request";

  /** The game, which is saved for the opponent. */
  private final IGame game;

  /**
   * Whether the decision is the opponent's: the game goes by forum or email, and the deciding
   * player is not the one whose turn it is, eg the defender asked to scramble.
   */
  public boolean isOpponentsDecision(final GamePlayer decider) {
    final GameData data = game.getData();
    final GamePlayer moving = data.getSequence().getStep().getPlayerId();
    return PbemMessagePoster.gameDataHasPlayByEmailOrForumMessengers(data)
        && moving != null
        && !moving.equals(decider);
  }

  /**
   * "Ask in the forum", or "Ask by email" where the game goes by email: posts the question to the
   * deciding player; none where the decision is not the opponent's.
   */
  public Optional<JButton> newAskButton(final GamePlayer decider, final Supplier<String> question) {
    if (!isOpponentsDecision(decider)) {
      return Optional.empty();
    }
    final String label =
        game.getData().getProperties().get(IForumPoster.NAME) == null
            ? "Ask by email"
            : "Ask in the forum";
    final JButton ask = new JButton(label);
    ask.setToolTipText(
        "Posts the question with the save game for "
            + decider.getName()
            + "; enter the answer here once it came");
    ask.addActionListener(e -> ask(ask, label, decider, question.get()));
    return Optional.of(ask);
  }

  private void ask(
      final JButton button, final String label, final GamePlayer decider, final String question) {
    final GameData data = game.getData();
    button.setEnabled(false);
    button.setText("Asking...");
    new PbemMessagePoster(data, decider, data.getSequence().getRound(), TITLE)
        .postInBackground(
            TITLE,
            "",
            decider.getName() + ", your decision: " + question,
            null,
            game,
            answer -> {
              final boolean heldBack = NodeBbForumPoster.isHeldBack(answer);
              button.setText(heldBack ? "Asked, awaits a moderator" : "Asked");
              button.setToolTipText(
                  (heldBack ? answer : "Posted.") + " Enter the answer here once it came");
            },
            why -> {
              button.setEnabled(true);
              button.setText(label + " again");
              button.setToolTipText("Not posted: " + why);
            });
  }
}
