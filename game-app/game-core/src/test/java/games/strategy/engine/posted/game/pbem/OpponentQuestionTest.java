package games.strategy.engine.posted.game.pbem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import games.strategy.engine.data.GameData;
import games.strategy.engine.data.GamePlayer;
import games.strategy.engine.data.GameStep;
import games.strategy.engine.framework.IGame;
import games.strategy.engine.posted.game.pbf.IForumPoster;
import games.strategy.triplea.xml.TestMapGameData;
import java.nio.file.Path;
import java.time.Duration;
import java.util.stream.StreamSupport;
import javax.swing.AbstractButton;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

final class OpponentQuestionTest {
  private final GameData data = TestMapGameData.REVISED.getGameData();
  private final IGame game = mock(IGame.class);
  private final OpponentQuestion question = new OpponentQuestion(game);
  private GamePlayer moving;
  private GamePlayer other;

  @BeforeEach
  void givenTurnOfAPlayer() {
    when(game.getData()).thenReturn(data);
    final GameStep step =
        StreamSupport.stream(data.getSequence().spliterator(), false)
            .filter(candidate -> candidate.getPlayerId() != null)
            .findFirst()
            .orElseThrow();
    data.getSequence().setRoundAndStep(1, step.getDisplayName(), step.getPlayerId());
    moving = step.getPlayerId();
    other =
        data.getPlayerList().getPlayers().stream()
            .filter(player -> !player.equals(moving))
            .findFirst()
            .orElseThrow();
  }

  @DisplayName("In a game by forum, the decision of another player than the one moving is asked")
  @Test
  void opponentsDecisionIsAskedInTheForum() {
    data.getProperties().set(IForumPoster.NAME, "forums.triplea-game.org");

    assertThat(question.isOpponentsDecision(other)).as("eg the defender's scramble").isTrue();
    assertThat(question.isOpponentsDecision(moving)).as("the moving player's own").isFalse();
    assertThat(question.newAskButton(other, () -> "do you scramble?"))
        .as("a button that asks in the game's thread")
        .map(AbstractButton::getText)
        .contains("Ask in the forum");
    assertThat(question.newAskButton(moving, () -> "?")).as("nobody to ask").isEmpty();
  }

  @DisplayName("A question not posted says so on its button, which asks again; no window opens")
  @Test
  void failedQuestionIsToldOnTheButton() throws Exception {
    data.getProperties().set(IForumPoster.NAME, "forums.triplea-game.org");
    doThrow(new IllegalStateException("disk full")).when(game).saveGame(any(Path.class));
    final AbstractButton ask = question.newAskButton(other, () -> "do you scramble?").orElseThrow();

    ask.doClick();

    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () ->
                assertThat(ask.getText())
                    .as("the decision's dialog stays; the player can ask again")
                    .isEqualTo("Ask in the forum again"));
    assertThat(ask.getToolTipText())
        .as("why, where the player looks")
        .isEqualTo("Not posted: Posting failed: disk full");
    assertThat(ask.isEnabled()).as("asking again is possible").isTrue();
  }

  @DisplayName("A game not by forum or email asks nobody: every player decides at the table")
  @Test
  void localGameAsksNobody() {
    assertThat(question.isOpponentsDecision(other)).as("no forum, no email").isFalse();
    assertThat(question.newAskButton(other, () -> "?")).isEmpty();
  }
}
