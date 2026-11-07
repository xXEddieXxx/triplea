package games.strategy.engine.framework.map.file.system.loader;

import static org.assertj.core.api.Assertions.assertThat;

import games.strategy.triplea.settings.AbstractClientSettingTestCase;
import games.strategy.triplea.settings.ClientSetting;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ZippedMapsExtractorTest extends AbstractClientSettingTestCase {
  @TempDir Path maps;
  @TempDir Path downloads;

  @Test
  void testExtractionFolderNaming() {
    assertThat(ZippedMapsExtractor.computeExtractionFolderName("zip")).isEqualTo("zip");
    assertThat(ZippedMapsExtractor.computeExtractionFolderName("zip-master")).isEqualTo("zip");
    assertThat(ZippedMapsExtractor.computeExtractionFolderName("zip-master.zip")).isEqualTo("zip");
    assertThat(ZippedMapsExtractor.computeExtractionFolderName("zip.zip")).isEqualTo("zip");
  }

  @DisplayName("An imported zip is unpacked into the maps folder and the player keeps the file")
  @Test
  void importedZipIsUnpackedAndKept() throws IOException {
    givenMapsFolder();
    final Path zip = givenZip(downloads.resolve("My_Map.ZIP"));

    final Optional<Path> mapFolder = ZippedMapsExtractor.importMapZip(zip);

    assertThat(mapFolder)
        .as("the map's folder in the maps folder, named in lower case")
        .contains(maps.resolve("my_map"));
    assertThat(
            List.of(
                Files.exists(zip),
                Files.exists(maps.resolve("my_map/games/game.xml")),
                Files.exists(maps.resolve("my_map/map.yml"))))
        .as("the zip stays in the downloads, the map is unpacked with a map.yml for the game list")
        .containsExactly(true, true, true);
  }

  @DisplayName("An installed map of the zip's name stays untouched")
  @Test
  void installedMapIsKept() throws IOException {
    givenMapsFolder();
    final Path installed =
        Files.writeString(
            Files.createDirectories(maps.resolve("my_map")).resolve("map.yml"), "installed");
    final Path broken = Files.writeString(downloads.resolve("My_Map.ZIP"), "not a zip");

    final Optional<Path> mapFolder = ZippedMapsExtractor.importMapZip(broken);

    assertThat(mapFolder).as("the import is refused").isEmpty();
    assertThat(installed).as("the installed map is not deleted").hasContent("installed");
  }

  @DisplayName("A broken zip is not imported and leaves nothing in the maps folder")
  @Test
  void brokenZipIsNotImported() throws IOException {
    givenMapsFolder();
    final Path broken = Files.writeString(downloads.resolve("broken.zip"), "not a zip");

    final Optional<Path> mapFolder = ZippedMapsExtractor.importMapZip(broken);

    assertThat(mapFolder).as("nothing could be unpacked").isEmpty();
    try (Stream<Path> files = Files.list(maps)) {
      assertThat(files).as("the copy of the broken zip is gone").isEmpty();
    }
  }

  @DisplayName("A file that is not a zip is not imported")
  @Test
  void otherFileIsNotImported() throws IOException {
    givenMapsFolder();
    final Path text = Files.writeString(downloads.resolve("notes.txt"), "text");

    final Optional<Path> mapFolder = ZippedMapsExtractor.importMapZip(text);

    assertThat(mapFolder).as("only zip files hold maps").isEmpty();
  }

  private void givenMapsFolder() {
    ClientSetting.mapFolderOverride.setValueAndFlush(maps);
  }

  private static Path givenZip(final Path path) throws IOException {
    try (OutputStream out = Files.newOutputStream(path);
        ZipOutputStream zip = new ZipOutputStream(out)) {
      zip.putNextEntry(new ZipEntry("games/game.xml"));
      // a map from before 2.6: a game XML, no map.yml
      zip.write(
          "<?xml version=\"1.0\"?><game><info name=\"My Game\" version=\"1\"/></game>"
              .getBytes(StandardCharsets.UTF_8));
      zip.closeEntry();
    }
    return path;
  }
}
