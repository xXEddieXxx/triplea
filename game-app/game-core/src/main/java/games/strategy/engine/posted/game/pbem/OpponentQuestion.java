package games.strategy.engine.posted.game.pbem;

import games.strategy.engine.data.GameData;
import games.strategy.engine.data.GamePlayer;
import games.strategy.engine.framework.IGame;
import games.strategy.engine.posted.game.pbf.IForumPoster;
import games.strategy.triplea.delegate.GameStepPropertiesHelper;
import games.strategy.triplea.ui.history.HistoryLog;
import java.util.Optional;
import java.util.function.Supplier;
import javax.swing.JButton;
import javax.swing.JFrame;

/**
 * A question to the opponent of a game played by forum or email, as its players ask for a decision
 * of the other side during a turn, eg whether to scramble or which units to lose: posted to the
 * game's thread, or sent by email, with the turn so far and the save game, which the opponent can
 * look at. The decision's dialog stays open for the answer.
 */
public final class OpponentQuestion {
  private static final String TITLE = "Decision Request";

  private final IGame game;
  private final JFrame frame;

  /**
   * @param game the game, which is saved for the opponent
   * @param frame the game's window
   */
  public OpponentQuestion(final IGame game, final JFrame frame) {
    this.game = game;
    this.frame = frame;
  }

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
    final JButton ask =
        new JButton(
            game.getData().getProperties().get(IForumPoster.NAME) == null
                ? "Ask by email"
                : "Ask in the forum");
    ask.setToolTipText(
        "Posts the question with the turn so far and the save game for "
            + decider.getName()
            + "; enter the answer here once it came");
    ask.addActionListener(e -> ask(decider, question.get()));
    return Optional.of(ask);
  }

  private void ask(final GamePlayer decider, final String question) {
    final GameData data = game.getData();
    final HistoryLog turn = new HistoryLog(frame);
    turn.append(decider.getName() + ", your decision: " + question + "\n\n");
    turn.printFullTurn(data, false, GameStepPropertiesHelper.getTurnSummaryPlayers(data));
    new PbemMessagePoster(data, decider, data.getSequence().getRound(), TITLE)
        .postTurn(TITLE, turn, true, null, frame, game, null);
  }
}
