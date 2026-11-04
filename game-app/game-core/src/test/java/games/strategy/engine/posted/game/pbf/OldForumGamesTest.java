package games.strategy.engine.posted.game.pbf;

import static org.assertj.core.api.Assertions.assertThat;

import games.strategy.triplea.settings.AbstractClientSettingTestCase;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

final class OldForumGamesTest extends AbstractClientSettingTestCase {
  private static final String FORUM = "https://forums.triplea-game.org/";

  @DisplayName("A game moves to Old games and back, per forum, as topics count per forum")
  @Test
  void gameMovesAndBack() {
    OldForumGames.move(FORUM, 12);
    final boolean old = OldForumGames.keys().contains(OldForumGames.keyOf(FORUM, 12));
    final boolean otherForum =
        OldForumGames.keys().contains(OldForumGames.keyOf("https://www.axisandallies.org", 12));
    OldForumGames.move(FORUM, 12);

    assertThat(old).as("moved").isTrue();
    assertThat(otherForum).as("topic 12 of another forum is another game").isFalse();
    assertThat(OldForumGames.keys()).as("moved back").isEmpty();
  }

  @DisplayName("Old games tells when it is full instead of failing, and moves nothing then")
  @Test
  void fullOldGamesMovesNothing() {
    final long moved =
        IntStream.range(0, 400).filter(topic -> OldForumGames.move(FORUM, 10_000 + topic)).count();

    assertThat(moved)
        .as("about 200 games fit into the one preference value; the rest stay where they are")
        .isBetween(150L, 399L);
    assertThat(OldForumGames.keys()).hasSize((int) moved);
  }
}
