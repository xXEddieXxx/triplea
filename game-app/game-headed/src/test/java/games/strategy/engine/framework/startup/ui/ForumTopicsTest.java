package games.strategy.engine.framework.startup.ui;

import static org.assertj.core.api.Assertions.assertThat;

import games.strategy.engine.framework.startup.ui.ForumTopics.ForumTopic;
import java.net.URI;
import java.time.Instant;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

final class ForumTopicsTest {

  @DisplayName("A topic of the forum's answer is read with its title, page and last answer")
  @Test
  void topicRead() {
    final String json =
        """
        {"topics": [{"title": "Axis &amp; Allies 1942", "slug": "4417/axis-allies-1942",
          "lastposttime": 1793144385698, "postcount": 13, "deleted": 0}]}
        """;

    assertThat(ForumTopics.parse(json))
        .as("the title unescaped, the page in the TripleA forum, the first post is no reply")
        .containsExactly(
            new ForumTopic(
                "Axis & Allies 1942",
                URI.create("https://forums.triplea-game.org/topic/4417/axis-allies-1942"),
                Instant.ofEpochMilli(1793144385698L),
                12));
  }

  @DisplayName("Deleted and incomplete topics are left out, at most three are shown")
  @Test
  void onlyFiveShownTopics() {
    final String deleted =
        "{\"title\": \"gone\", \"slug\": \"1/gone\", \"lastposttime\": 1, \"deleted\": 1}";
    final String incomplete = "{\"title\": \"no slug\", \"lastposttime\": 1}";
    final String topics =
        IntStream.rangeClosed(1, 7)
            .mapToObj(
                n ->
                    "{\"title\": \"Topic %d\", \"slug\": \"%d/t\", \"lastposttime\": %d}"
                        .formatted(n, n, n))
            .collect(Collectors.joining(","));

    assertThat(
            ForumTopics.parse("{\"topics\": [" + deleted + "," + incomplete + "," + topics + "]}"))
        .as("the newest three of the topics that can be shown, in the forum's order")
        .extracting(ForumTopic::title)
        .containsExactly("Topic 1", "Topic 2", "Topic 3");
  }

  @DisplayName("An unexpected answer of the forum shows no topics instead of failing")
  @Test
  void unexpectedAnswer() {
    assertThat(ForumTopics.parse("<html>maintenance</html>"))
        .as("eg a maintenance page instead of the list")
        .isEmpty();
    assertThat(ForumTopics.parse("{\"topics\": \"none\"}")).isEmpty();
  }

  @DisplayName("A deleted flag written as true or as text does not break the whole list")
  @Test
  void otherFlagTypes() {
    final String json =
        """
        {"topics": [
          {"title": "gone", "slug": "1/gone", "lastposttime": 1, "deleted": true},
          {"title": "kept", "slug": "2/kept", "lastposttime": 2, "deleted": "no"}]}
        """;

    assertThat(ForumTopics.parse(json))
        .as("true is deleted; a text is no flag, the topic stays")
        .extracting(ForumTopic::title)
        .containsExactly("kept");
  }
}
