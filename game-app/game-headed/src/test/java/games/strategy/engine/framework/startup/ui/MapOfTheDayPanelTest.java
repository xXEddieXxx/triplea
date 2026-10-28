package games.strategy.engine.framework.startup.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import games.strategy.engine.framework.map.file.system.loader.InstalledMap;
import games.strategy.engine.framework.map.file.system.loader.InstalledMapsListing;
import games.strategy.engine.framework.startup.ui.MapOfTheDayPanel.FeaturedMap;
import games.strategy.triplea.settings.ClientSetting;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonatype.goodies.prefs.memory.MemoryPreferences;
import org.triplea.http.client.lobby.maps.listing.MapDownloadItem;
import org.triplea.map.description.file.MapDescriptionYaml;
import org.triplea.map.description.file.MapDescriptionYaml.MapGame;

final class MapOfTheDayPanelTest {
  @TempDir Path mapsFolder;

  @BeforeEach
  void givenClientSettings() {
    // the maps folder of the settings is asked for when a map's folder is looked up
    ClientSetting.setPreferences(new MemoryPreferences());
  }

  @AfterEach
  void resetClientSettingPreferences() {
    ClientSetting.resetPreferences();
  }

  @DisplayName("Map folder names read as names, roman numerals in capitals")
  @Test
  void readableMapNames() {
    assertThat(
            List.of(
                MapOfTheDayPanel.readableMapName("world_war_ii_global"),
                MapOfTheDayPanel.readableMapName("1914-cow-empires"),
                MapOfTheDayPanel.readableMapName("tutorial")))
        .as("as the player would write them")
        .containsExactly("World War II Global", "1914 Cow Empires", "Tutorial");
  }

  @DisplayName("The summary is the plain text of the description, cut at a word")
  @Test
  void summaryIsPlainAndShort() {
    final String longText = "word ".repeat(100);

    assertThat(MapOfTheDayPanel.summaryOf("<br>World War II<br>  Global &amp; more"))
        .as("no tags, no entities, single spaces")
        .isEqualTo("World War II Global & more");
    assertThat(MapOfTheDayPanel.summaryOf(longText))
        .as("a long description ends with an ellipsis after a whole word")
        .endsWith("word …")
        .hasSizeLessThan(120);
  }

  @DisplayName("The tutorial and maps without a game are no maps of the day; the rest by name")
  @Test
  void featuredMapsLeaveOutTutorial() throws IOException {
    final InstalledMapsListing listing =
        InstalledMapsListing.builder()
            .installedMap(givenMap("world_war_ii_global", "Global 1940"))
            .installedMap(givenMap("tutorial", "Tutorial"))
            .installedMap(givenMap("1914-cow-empires", "Empires"))
            .installedMap(givenMapWithoutGame("broken_map"))
            .build();

    assertThat(MapOfTheDayPanel.featuredMaps(listing, List.of()))
        .as("the tutorial is a lesson; a map without game XML cannot be played")
        .extracting(FeaturedMap::title)
        .containsExactly("1914 Cow Empires", "World War II Global");
  }

  @DisplayName("Maps to download join the installed ones, an installed one only once")
  @Test
  void featuredMapsIncludeMapsToDownload() throws IOException {
    final InstalledMapsListing listing =
        InstalledMapsListing.builder()
            .installedMap(givenMap("world_war_ii_global", "Global 1940"))
            .build();

    final List<FeaturedMap> featured =
        MapOfTheDayPanel.featuredMaps(
            listing,
            List.of(
                givenDownload("World War II Global"),
                givenDownload("270BC"),
                givenDownload("Tutorial")));

    assertThat(featured)
        .as("the installed Global map can be played, 270BC is to install; the tutorial never shows")
        .extracting(FeaturedMap::title, map -> map.gameXml().isPresent())
        .containsExactly(tuple("270BC", false), tuple("World War II Global", true));
  }

  private static MapDownloadItem givenDownload(final String mapName) {
    return MapDownloadItem.builder()
        .mapName(mapName)
        .downloadUrl("https://github.com/triplea-maps/" + mapName + "/archive/master.zip")
        .previewImageUrl("https://example.org/" + mapName.replace(' ', '_') + ".png")
        .description("<b>" + mapName + "</b>")
        .lastCommitDateEpochMilli(1L)
        .mapTags(List.of())
        .downloadSizeInBytes(1_000_000L)
        .build();
  }

  /** A map folder as installed: map.yml, the polygons in map/ and a game XML in map/games/. */
  private InstalledMap givenMap(final String mapName, final String gameName) throws IOException {
    final Path folder = Files.createDirectories(mapsFolder.resolve(mapName));
    final Path games = Files.createDirectories(folder.resolve("map").resolve("games"));
    Files.writeString(folder.resolve("map").resolve("polygons.txt"), "");
    Files.writeString(games.resolve("game.xml"), "<game/>");
    return InstalledMap.builder()
        .mapDescriptionYaml(
            MapDescriptionYaml.builder()
                .yamlFileLocation(folder.resolve("map.yml"))
                .mapName(mapName)
                .game(MapGame.builder().gameName(gameName).xmlFileName("game.xml").build())
                .build())
        .lastModifiedDate(Instant.now())
        .build();
  }

  private InstalledMap givenMapWithoutGame(final String mapName) throws IOException {
    final Path folder = Files.createDirectories(mapsFolder.resolve(mapName));
    return InstalledMap.builder()
        .mapDescriptionYaml(
            MapDescriptionYaml.builder()
                .yamlFileLocation(folder.resolve("map.yml"))
                .mapName(mapName)
                .build())
        .lastModifiedDate(Instant.now())
        .build();
  }

  @DisplayName("The map of the day is the same all day and another one the next day")
  @Test
  void mapOfTheDayChangesDaily() {
    final LocalDate today = LocalDate.of(2026, 10, 28);

    assertThat(
            List.of(
                MapOfTheDayPanel.mapOfTheDay(5, today),
                MapOfTheDayPanel.mapOfTheDay(5, today),
                MapOfTheDayPanel.mapOfTheDay(5, today.plusDays(1))))
        .as("chosen by the date, not at random")
        .satisfies(days -> assertThat(days.get(0)).isEqualTo(days.get(1)))
        .satisfies(days -> assertThat(days.get(2)).isEqualTo((days.get(0) + 1) % 5))
        .allMatch(index -> index >= 0 && index < 5);
  }
}
