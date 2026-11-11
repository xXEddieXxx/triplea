package games.strategy.engine.data.export;

import static games.strategy.engine.data.export.GameFacts.describe;
import static games.strategy.engine.data.export.GameFacts.givenMapFile;
import static games.strategy.engine.data.export.GameFacts.load;
import static org.assertj.core.api.Assertions.assertThat;

import games.strategy.engine.data.GameData;
import games.strategy.triplea.xml.TestMapGameData;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.triplea.generic.xml.reader.XmlMapper;
import org.triplea.generic.xml.reader.exceptions.XmlParsingException;
import org.triplea.map.data.elements.Game;
import org.triplea.map.xml.writer.GameXmlWriter;

/**
 * A map editor, eg Eddie's Map Editor, edits a map as the XML's model, {@link Game}: read it,
 * change it, write it. That only works if reading and writing the model keeps the game as it is.
 */
final class GameXmlModelRoundTripTest {

  @DisplayName("A map read into the XML model and written again is the same game")
  @ParameterizedTest(name = "{0}")
  @EnumSource(TestMapGameData.class)
  void writtenModelIsTheSameGame(final TestMapGameData map, @TempDir final Path folder)
      throws IOException, XmlParsingException {
    final Path original = givenMapFile(map, folder);
    final Path written = folder.resolve("written.xml");

    GameXmlWriter.exportXml(read(original), written);

    final Optional<GameData> after = load(written);
    assertThat(after).as("the written XML is a valid game").isPresent();
    assertThat(describe(after.orElseThrow()))
        .as("nothing of the game lost or changed on the way through the model")
        .containsExactlyInAnyOrderElementsOf(describe(load(original).orElseThrow()));
  }

  @DisplayName("Writing a map that was read from its own writing changes nothing")
  @ParameterizedTest(name = "{0}")
  @EnumSource(TestMapGameData.class)
  void writingIsStable(final TestMapGameData map, @TempDir final Path folder)
      throws IOException, XmlParsingException {
    final Path once = folder.resolve("once.xml");
    final Path twice = folder.resolve("twice.xml");
    GameXmlWriter.exportXml(read(givenMapFile(map, folder)), once);

    GameXmlWriter.exportXml(read(once), twice);

    assertThat(Files.readString(twice))
        .as("saving a map without changes keeps the file as it is, so diffs show only edits")
        .isEqualTo(Files.readString(once));
  }

  private static Game read(final Path xml) throws IOException, XmlParsingException {
    try (InputStream stream = Files.newInputStream(xml)) {
      return new XmlMapper(stream).mapXmlToObject(Game.class);
    }
  }
}
