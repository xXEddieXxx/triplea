package games.strategy.engine.posted.game.pbf;

import static org.assertj.core.api.Assertions.assertThat;

import games.strategy.triplea.settings.AbstractClientSettingTestCase;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

final class ForumPostsSeenTest extends AbstractClientSettingTestCase {
  private static final String FORUM = "https://forums.triplea-game.org/";

  @DisplayName("The newest post seen is kept per game and never goes back to an older one")
  @Test
  void newestPostSeenIsKept() {
    ForumPostsSeen.markSeen(FORUM, 12, Instant.ofEpochMilli(2000));
    ForumPostsSeen.markSeen(FORUM, 12, Instant.ofEpochMilli(1000));

    final List<Optional<Instant>> seen =
        List.of(ForumPostsSeen.seen(FORUM, 12), ForumPostsSeen.seen(FORUM, 13));

    assertThat(seen)
        .as("game 12 keeps its newer post; game 13 was never opened")
        .containsExactly(Optional.of(Instant.ofEpochMilli(2000)), Optional.empty());
  }

  @DisplayName("When the setting is full the games seen longest ago are forgotten")
  @Test
  void oldestGamesGoWhenFull() {
    IntStream.range(0, 400)
        .forEach(topic -> ForumPostsSeen.markSeen(FORUM, topic, Instant.ofEpochMilli(1 + topic)));

    final List<Boolean> kept =
        List.of(
            ForumPostsSeen.seen(FORUM, 0).isPresent(), ForumPostsSeen.seen(FORUM, 399).isPresent());

    assertThat(kept).as("the first game went, the newest stays").containsExactly(false, true);
  }
}
