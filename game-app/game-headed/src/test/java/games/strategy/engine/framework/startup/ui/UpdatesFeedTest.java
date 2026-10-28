package games.strategy.engine.framework.startup.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import games.strategy.engine.framework.startup.ui.UpdatesFeed.Kind;
import games.strategy.engine.framework.startup.ui.UpdatesFeed.Update;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.triplea.http.client.lobby.maps.listing.MapDownloadItem;

final class UpdatesFeedTest {

  @DisplayName("Published releases of GitHub are read, drafts and pre-releases left out")
  @Test
  void releasesRead() {
    final String json =
        """
        [{"tag_name": "2026-10.16.15824", "html_url": "https://github.com/r/1",
          "published_at": "2026-10-17T16:45:54Z", "draft": false, "prerelease": false},
         {"tag_name": "beta", "html_url": "https://github.com/r/2",
          "published_at": "2026-10-18T10:00:00Z", "prerelease": true},
         {"tag_name": "draft", "html_url": "https://github.com/r/3",
          "published_at": null, "draft": true}]
        """;

    assertThat(UpdatesFeed.parseReleases(json))
        .as("players get the versions everybody can install")
        .containsExactly(
            new Update(
                Kind.GAME,
                "2026-10.16.15824",
                Instant.parse("2026-10-17T16:45:54Z"),
                URI.create("https://github.com/r/1")));
    assertThat(UpdatesFeed.parseReleases("{\"message\": \"rate limit\"}"))
        .as("an error answer of GitHub shows no updates instead of failing")
        .isEmpty();
  }

  @DisplayName("The feed shows the newest version once and the maps updated last, newest first")
  @Test
  void newestFirst() {
    final List<Update> releases =
        List.of(
            new Update(Kind.GAME, "v3", Instant.ofEpochMilli(5000), URI.create("https://g/3")),
            new Update(Kind.GAME, "v2", Instant.ofEpochMilli(4000), URI.create("https://g/2")));

    assertThat(
            UpdatesFeed.newest(
                releases,
                List.of(
                    givenMap("world_war_ii_global", 6000),
                    givenMap("270BC", 1000),
                    givenMap("1941", 3000))))
        .as("frequent builds must not push the map updates out of the feed")
        .extracting(Update::kind, Update::name)
        .containsExactly(
            tuple(Kind.MAP, "World War II Global"),
            tuple(Kind.GAME, "v3"),
            tuple(Kind.MAP, "1941"),
            tuple(Kind.MAP, "270BC"));
  }

  @DisplayName("A map update links to the map's repository, not to the download")
  @Test
  void mapUpdateLinksToRepository() {
    assertThat(
            UpdatesFeed.repositoryOf(
                "https://github.com/triplea-maps/world_war_ii_global/archive/master.zip"))
        .as("the repository shows what changed")
        .contains(URI.create("https://github.com/triplea-maps/world_war_ii_global"));
  }

  private static MapDownloadItem givenMap(final String mapName, final long lastCommit) {
    return MapDownloadItem.builder()
        .mapName(mapName)
        .downloadUrl("https://github.com/triplea-maps/" + mapName + "/archive/master.zip")
        .previewImageUrl("https://example.org/preview.png")
        .description("")
        .lastCommitDateEpochMilli(lastCommit)
        .mapTags(List.of())
        .downloadSizeInBytes(1L)
        .build();
  }
}
