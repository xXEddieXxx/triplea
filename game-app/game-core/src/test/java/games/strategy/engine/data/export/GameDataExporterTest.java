package games.strategy.engine.data.export;

import static games.strategy.engine.data.export.GameFacts.describe;
import static games.strategy.engine.data.export.GameFacts.givenMapFile;
import static games.strategy.engine.data.export.GameFacts.load;
import static org.assertj.core.api.Assertions.assertThat;

import games.strategy.engine.data.GameData;
import games.strategy.engine.data.Territory;
import games.strategy.triplea.xml.TestMapGameData;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.triplea.map.xml.writer.GameXmlWriter;

final class GameDataExporterTest {
  @Nested
  final class ConnectionTest {
    @Nested
    final class EqualsAndHashCodeTest {
      @Test
      void shouldBeEquatableAndHashable() {
        final GameData gameData = new GameData();
        EqualsVerifier.forClass(GameDataExporter.Connection.class)
            .withPrefabValues(
                Territory.class,
                new Territory("redTerritory", gameData),
                new Territory("blackTerritory", gameData))
            .verify();
      }
    }
  }

  @DisplayName("A map exported to XML and loaded again is the same game")
  @ParameterizedTest(name = "{0}")
  // the exporter reads the attachments again from the map's XML, which knows no test classes
  @EnumSource(
      value = TestMapGameData.class,
      mode = EnumSource.Mode.EXCLUDE,
      names = {"TEST", "GAME_EXAMPLE"})
  void exportedMapLoadsAsTheSameGame(final TestMapGameData map, @TempDir final Path folder)
      throws IOException {
    final Path original = givenMapFile(map, folder);
    final GameData before = load(original).orElseThrow();
    final Path exported = folder.resolve("exported.xml");

    GameXmlWriter.exportXml(GameDataExporter.convertToXmlModel(before, original), exported);

    final Optional<GameData> after = load(exported);
    assertThat(after).as("the exported XML is a valid game").isPresent();
    assertThat(describe(after.orElseThrow()))
        .as("nothing of the game lost or changed on the way through XML")
        .containsExactlyInAnyOrderElementsOf(describe(before));
  }
}
