package games.strategy.engine.framework.startup.ui;

import static org.assertj.core.api.Assertions.assertThat;

import games.strategy.engine.posted.game.pbf.ForumPostsSeen;
import games.strategy.engine.posted.game.pbf.NodeBbGameThreads.GameThread;
import games.strategy.engine.posted.game.pbf.NodeBbGameThreads.Player;
import games.strategy.engine.posted.game.pbf.OldForumGames;
import games.strategy.triplea.settings.ClientSetting;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sonatype.goodies.prefs.memory.MemoryPreferences;

final class ForumTurnNotifierTest {
  private static final String FORUM = "https://forums.triplea-game.org/";
  private static final Player ANN = new Player("Ann", "ann");

  @BeforeEach
  void givenSettings() {
    ClientSetting.setPreferences(new MemoryPreferences());
  }

  @AfterEach
  void forgetSettings() {
    ClientSetting.resetPreferences();
  }

  @DisplayName("Only new turns of the player's active games are told, each once")
  @Test
  void onlyNewTurnsAreTold() {
    final GameThread bobPosted = givenGame(1, "bob", 100);
    final GameThread annPosted = givenGame(2, "ann", 100);
    final GameThread old = givenGame(3, "bob", 100);
    final GameThread read = givenGame(4, "bob", 100);
    final GameThread toldBefore = givenGame(5, "bob", 100);
    ForumPostsSeen.markSeen(FORUM, 4, Instant.ofEpochMilli(100));

    final List<GameThread> told =
        ForumTurnNotifier.newTurns(
            List.of(bobPosted, annPosted, old, read, toldBefore),
            ANN,
            Set.of(OldForumGames.keyOf(FORUM, 3)),
            FORUM,
            Map.of(OldForumGames.keyOf(FORUM, 5), Instant.ofEpochMilli(100)));

    assertThat(told)
        .as(
            "not Ann's own last post, not an old game, not one read here since, not one told of"
                + " before")
        .containsExactly(bobPosted);
  }

  private static GameThread givenGame(final int topic, final String lastPoster, final long time) {
    return new GameThread(
        topic, "", "Game " + topic, Instant.ofEpochMilli(time), 1, "", lastPoster, false, false);
  }
}
