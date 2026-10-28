package games.strategy.engine.framework.map.download;

import static org.assertj.core.api.Assertions.assertThat;

import games.strategy.engine.framework.map.download.MapDetails.Fact;
import games.strategy.engine.framework.map.file.system.loader.InstalledMap;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.triplea.http.client.lobby.maps.listing.MapDownloadItem;
import org.triplea.map.description.file.MapDescriptionYaml;
import org.triplea.map.description.file.MapDescriptionYaml.MapGame;

final class MapDetailsTest {
  private static final Instant OLDER = Instant.parse("2024-03-03T10:00:00Z");
  private static final Instant NEWER = Instant.parse("2025-06-09T10:00:00Z");

  @DisplayName("The details of an outdated map compare the installed and the newest version")
  @Test
  void outdatedMapComparesVersions() {
    final ManagedMap map = new ManagedMap(givenDownload(), givenInstalledMap());

    assertThat(MapDetails.facts(map))
        .as("status first, then both versions and the download size")
        .containsExactly(
            new Fact("Status", "Update available"),
            new Fact("Installed version", "3 Mar 2024"),
            new Fact("Newest version", "9 Jun 2025"),
            new Fact("Size", "2 MB"));
  }

  @DisplayName("The details of a map to get have no installed version")
  @Test
  void mapToGetHasNoInstalledVersion() {
    final ManagedMap map = new ManagedMap(givenDownload(), givenInstalledMap());
    map.setMapStatus(ManagedMapStatus.AVAILABLE);

    assertThat(MapDetails.facts(map))
        .as("a removed map keeps the installed map it was read with, which is gone now")
        .containsExactly(
            new Fact("Status", "Not installed"),
            new Fact("Newest version", "9 Jun 2025"),
            new Fact("Size", "2 MB"));
  }

  private static MapDownloadItem givenDownload() {
    return MapDownloadItem.builder()
        .downloadUrl("")
        .previewImageUrl("")
        .mapName("World War II Global")
        .description("description")
        .lastCommitDateEpochMilli(NEWER.toEpochMilli())
        .downloadSizeInBytes(2L * 1024 * 1024)
        .build();
  }

  private static InstalledMap givenInstalledMap() {
    return InstalledMap.builder()
        .lastModifiedDate(OLDER)
        .mapDescriptionYaml(
            MapDescriptionYaml.builder()
                .yamlFileLocation(Path.of("/local/file"))
                .mapName("World War II Global")
                .mapGameList(
                    List.of(MapGame.builder().xmlFileName("path.xml").gameName("game").build()))
                .build())
        .build();
  }
}
