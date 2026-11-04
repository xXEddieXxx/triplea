package games.strategy.engine.framework.startup.ui.posted.game.pbf;

import static org.assertj.core.api.Assertions.assertThat;

import games.strategy.engine.framework.startup.ui.posted.game.pbf.GameThreadChooser.Games;
import games.strategy.engine.posted.game.pbf.NodeBbGameThreads.GameThread;
import games.strategy.engine.posted.game.pbf.NodeBbGameThreads.Player;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.function.Predicate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

final class GameThreadChooserTest {
  private static final Player EDDIE = new Player("Eddie", "eddie");
  private static final Predicate<GameThread> NONE_OLD = thread -> false;
  // in the zone the row's date is written in, so the test passes in every time zone
  private static final Instant NOV_3 =
      LocalDate.of(2026, 11, 3).atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant();

  @DisplayName("Own games first, the player's turn on top and watched ones last, then the others")
  @Test
  void ownGamesFirst() {
    final GameThread posted = givenGame(1, "eddie", false);
    final GameThread yourTurn = givenGame(2, "marta", false);
    final GameThread watched = givenGame(3, "marta", true);
    final GameThread other = givenGame(4, "marta", false);

    final Games games =
        GameThreadChooser.inOrder(
            List.of(watched, posted, yourTurn), List.of(yourTurn, other), EDDIE, NONE_OLD);

    assertThat(games.threads())
        .as("the newest games of the forum list an own game only once")
        .containsExactly(yourTurn, posted, watched, other);
  }

  @DisplayName(
      "A row says whose turn, the last turn and the last post, leaving out what is unknown")
  @Test
  void rowDescribesGame() {
    final GameThread yourTurn = givenGame(2, "marta", false);
    final GameThread othersGame = givenGame(5, "marta", false);
    final GameThread unknown = GameThread.of(9, "9/game", "Global 1940");
    final Games games =
        GameThreadChooser.inOrder(List.of(yourTurn), List.of(othersGame, unknown), EDDIE, NONE_OLD);

    assertThat(
            List.of(
                GameThreadChooser.describe(yourTurn, games),
                GameThreadChooser.describe(othersGame, games),
                GameThreadChooser.describe(unknown, games)))
        .as("only an own game is the player's turn; a game known by its address says nothing")
        .containsExactly(
            "Your turn · Germans, round 5 · last post 3 Nov 2026",
            "Germans, round 5 · last post 3 Nov 2026",
            "");
  }

  private static GameThread givenGame(
      final int topicId, final String lastPosterSlug, final boolean onlyWatched) {
    return new GameThread(
        topicId,
        topicId + "/game",
        "Game " + topicId,
        NOV_3,
        12,
        "Germans, round 5",
        lastPosterSlug,
        false,
        onlyWatched);
  }

  @DisplayName("The player's games leave out Old games; the forum's newest list keeps every game")
  @Test
  void oldGamesLeaveOwnGames() {
    final GameThread won = givenGame(1, "marta", false);
    final GameThread going = givenGame(2, "marta", false);
    final GameThread other = givenGame(3, "marta", false);

    final Games games =
        GameThreadChooser.inOrder(
            List.of(won, going), List.of(other), EDDIE, thread -> thread.topicId() == 1);

    assertThat(games.threads())
        .as("the finished game is not among the player's, as on the Play by forum page")
        .extracting(GameThread::topicId)
        .containsExactly(2, 3);
  }
}
