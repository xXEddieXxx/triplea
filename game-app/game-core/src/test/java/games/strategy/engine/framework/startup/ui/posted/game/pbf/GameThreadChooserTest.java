package games.strategy.engine.framework.startup.ui.posted.game.pbf;

import static org.assertj.core.api.Assertions.assertThat;

import games.strategy.engine.framework.startup.ui.posted.game.pbf.GameThreadChooser.Games;
import games.strategy.engine.posted.game.pbf.NodeBbGameThreads.GameThread;
import games.strategy.engine.posted.game.pbf.NodeBbGameThreads.Player;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

final class GameThreadChooserTest {
  private static final Player EDDIE = new Player("Eddie", "eddie");
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
            List.of(watched, posted, yourTurn), List.of(yourTurn, other), EDDIE);

    assertThat(games.threads())
        .as("the newest games of the forum list an own game only once")
        .containsExactly(yourTurn, posted, watched, other);
  }

  @DisplayName(
      "A row says whose turn, the last turn and the last post, leaving out what is unknown")
  @Test
  void rowDescribesGame() {
    final GameThread yourTurn = givenGame(2, "marta", false);
    final GameThread other = GameThread.of(9, "9/game", "Global 1940");
    final Games games = GameThreadChooser.inOrder(List.of(yourTurn), List.of(other), EDDIE);

    assertThat(
            List.of(
                GameThreadChooser.describe(yourTurn, games),
                GameThreadChooser.describe(other, games)))
        .as("the turn and the date from the forum; a game known by its address says nothing")
        .containsExactly("Your turn · Germans, round 5 · last post 3 Nov 2026", "");
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
}
