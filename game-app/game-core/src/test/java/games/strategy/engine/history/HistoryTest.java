package games.strategy.engine.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import games.strategy.engine.data.GameData;
import games.strategy.engine.data.GamePlayer;
import java.util.Optional;
import javax.annotation.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

final class HistoryTest {
  private final GamePlayer me = mock(GamePlayer.class);
  private final GamePlayer ai = mock(GamePlayer.class);
  private final History history = new History(mock(GameData.class));
  private Round round;

  @Test
  @DisplayName("The first step after my last turn that the others' turns follow")
  void firstStepSinceLastTurn() {
    givenRound();
    givenStep("myPurchase", me);
    givenStep("myCombat", me);
    givenRound();
    final Step aiPurchase = givenStep("aiPurchase", ai);
    givenStep("aiCombat", ai);
    givenStep("myPurchaseAgain", me);

    final Optional<Step> start = history.getFirstStepSinceLastTurnOf(me::equals);

    assertThat(start).as("the AI's turn since my last one starts there").containsSame(aiPurchase);
  }

  @Test
  @DisplayName("The turns of every local player count as mine")
  void everyLocalPlayerCounts() {
    final GamePlayer friend = mock(GamePlayer.class);
    givenRound();
    givenStep("myPurchase", me);
    givenStep("friendPurchase", friend);
    final Step aiPurchase = givenStep("aiPurchase", ai);
    givenStep("myPurchaseAgain", me);

    final Optional<Step> start =
        history.getFirstStepSinceLastTurnOf(player -> player == me || player == friend);

    assertThat(start).as("after my friend's turn at this computer").containsSame(aiPurchase);
  }

  @Test
  @DisplayName("Nothing to show when I had no turn before the others'")
  void noTurnBeforeOthers() {
    givenRound();
    givenStep("aiPurchase", ai);
    givenStep("myPurchase", me);

    final Optional<Step> start = history.getFirstStepSinceLastTurnOf(me::equals);

    assertThat(start)
        .as("a new game where the AI moved first has no earlier turn of mine")
        .isEmpty();
  }

  @Test
  @DisplayName("Nothing to show when only I played")
  void onlyLocalTurns() {
    givenRound();
    givenStep("myPurchase", me);
    givenStep("setup", null);
    givenStep("myCombat", me);

    final Optional<Step> start = history.getFirstStepSinceLastTurnOf(me::equals);

    assertThat(start).as("steps without a player are nobody's turn").isEmpty();
  }

  private void givenRound() {
    round = new Round(1, 0);
    ((HistoryNode) history.getRoot()).add(round);
  }

  private Step givenStep(final String name, @Nullable final GamePlayer player) {
    final Step step = new Step(name, "delegate", player, 0, name);
    round.add(step);
    return step;
  }
}
