package games.strategy.engine.framework.startup.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import games.strategy.engine.framework.map.file.system.loader.InstalledMap;
import games.strategy.engine.framework.map.file.system.loader.InstalledMapsListing;
import games.strategy.engine.framework.startup.ui.MapOfTheDayPanel.FeaturedMap;
import games.strategy.triplea.settings.ClientSetting;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
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
  private static final Dimension STAGE = new Dimension(800, 400);
  private static final Color WINDOW = new Color(20, 19, 24);

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

  @DisplayName("A picture wider than the largest stage is scaled down when read, others stay")
  @Test
  void widePicturesAreLimited() {
    final BufferedImage wide = new BufferedImage(5120, 2000, BufferedImage.TYPE_INT_RGB);
    final BufferedImage small = new BufferedImage(800, 400, BufferedImage.TYPE_INT_RGB);

    assertThat(MapOfTheDayPanel.limited(wide))
        .as("the proportions stay: 5120 x 2000 becomes 2560 x 1000")
        .extracting(BufferedImage::getWidth, BufferedImage::getHeight)
        .containsExactly(2560, 1000);
    assertThat(MapOfTheDayPanel.limited(small))
        .as("a picture that is small enough is not scaled at all")
        .isSameAs(small);
  }

  @DisplayName("A small picture is enlarged at most twice, a large one just enough to cover")
  @Test
  void smallPicturesAreEnlargedAtMostTwice() {
    assertThat(
            List.of(
                MapOfTheDayPanel.pictureScale(400, 200, 1600, 600),
                MapOfTheDayPanel.pictureScale(1280, 600, 1600, 600)))
        .as("400 px would need 4 times for 1600 px and gets 2, 1280 px gets the 1.25 it needs")
        .containsExactly(2.0, 1.25);
  }

  @DisplayName("The stage fades into the window's color at its left edge, the picture shows beside")
  @Test
  void stageFadesIntoMenu() {
    final BufferedImage stage = paintedStage(givenWhitePicture(400, 200), STAGE.height);

    assertThat(new Color(stage.getRGB(0, 200)))
        .as("no seam to the menu: the left edge is the window's color")
        .isEqualTo(WINDOW);
    assertThat(new Color(stage.getRGB(600, 120)).getRed())
        .as("right of the shade the dimmed picture shows")
        .isGreaterThan(100);
  }

  @DisplayName("A picture too small for the stage sits on a blurred copy of itself")
  @Test
  void smallPictureSitsOnBlurredCopy() {
    final BufferedImage stage = paintedStage(givenWhitePicture(200, 100), STAGE.height);

    assertThat(new Color(stage.getRGB(700, 120)).getRed())
        .as("the picture, enlarged twice to 400 px, ends at 600; right of it the dimmed blur shows")
        .isGreaterThan(50);
  }

  @DisplayName("Below the floor's edge the news panels lie on an almost plain ground")
  @Test
  void floorIsAlmostPlain() {
    final BufferedImage stage = paintedStage(givenWhitePicture(400, 200), 240);

    assertThat(new Color(stage.getRGB(600, 80)).getRed())
        .as("above the floor's shade the dimmed picture shows")
        .isGreaterThan(100);
    assertThat(new Color(stage.getRGB(600, 320)).getRed())
        .as("below the floor's edge at 240 the picture barely shows through the window's color")
        .isLessThan(WINDOW.getRed() + 16);
  }

  private static BufferedImage givenWhitePicture(final int width, final int height) {
    final BufferedImage picture = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    final Graphics2D g2 = picture.createGraphics();
    g2.setColor(Color.WHITE);
    g2.fillRect(0, 0, width, height);
    g2.dispose();
    return picture;
  }

  /** The stage painted with the picture in the size of {@link #STAGE} on a dark window. */
  private static BufferedImage paintedStage(final BufferedImage picture, final int floor) {
    final BufferedImage stage =
        new BufferedImage(STAGE.width, STAGE.height, BufferedImage.TYPE_INT_RGB);
    final Graphics2D g2 = stage.createGraphics();
    MapOfTheDayPanel.paintStage(
        g2, MapOfTheDayPanel.StagePicture.of(picture), STAGE, floor, WINDOW);
    g2.dispose();
    return stage;
  }
}
