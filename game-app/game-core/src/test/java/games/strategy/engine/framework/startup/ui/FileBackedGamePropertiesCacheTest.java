package games.strategy.engine.framework.startup.ui;

import static org.assertj.core.api.Assertions.assertThat;

import games.strategy.engine.data.GameData;
import games.strategy.engine.data.properties.BooleanProperty;
import games.strategy.engine.data.properties.IEditableProperty;
import games.strategy.triplea.xml.TestMapGameData;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class FileBackedGamePropertiesCacheTest {
  @TempDir Path folder;

  @DisplayName("A rule set keeps the options under its name until it is deleted")
  @Test
  void ruleSetsAreSavedLoadedAndDeleted() {
    final FileBackedGamePropertiesCache cache = new FileBackedGamePropertiesCache(() -> folder);
    final GameData game = TestMapGameData.REVISED.getGameData();
    final BooleanProperty switchOption = givenFirstSwitch(game);
    final boolean saved = !switchOption.getValue();
    switchOption.setValue(saved);
    cache.saveRuleSet(game, "Low luck night");
    cache.saveRuleSet(game, "classic");
    switchOption.setValue(!saved);

    cache.loadRuleSetInto(game, "Low luck night");
    final boolean loaded = switchOption.getValue();
    cache.deleteRuleSet(game, "classic");

    assertThat(loaded)
        .as("the option as it was saved, not as it was changed later")
        .isEqualTo(saved);
    assertThat(cache.getRuleSetNames(game))
        .as("the names as the player gave them, the deleted one gone")
        .containsExactly("Low luck night");
  }

  private static BooleanProperty givenFirstSwitch(final GameData game) {
    for (final IEditableProperty<?> property : game.getProperties().getEditableProperties()) {
      if (property instanceof BooleanProperty switchOption) {
        return switchOption;
      }
    }
    throw new IllegalStateException("Revised has on/off options");
  }

  @DisplayName("A name never leads into another folder, nor gets lost, nor shows for another game")
  @Test
  void namesStayInTheirGamesFolder() {
    final FileBackedGamePropertiesCache cache = new FileBackedGamePropertiesCache(() -> folder);
    final GameData game = TestMapGameData.REVISED.getGameData();
    final GameData other = TestMapGameData.REVISED.getGameData();
    other.setGameName(game.getGameName() + ".Extra");

    cache.saveRuleSet(game, "../a/b");
    cache.saveRuleSet(game, "???");
    cache.saveRuleSet(other, "theirs");

    assertThat(cache.getRuleSetNames(game))
        .as("'/' stripped, a name of only forbidden characters kept as Unnamed")
        .containsExactly("..ab", "Unnamed");
  }

  @DisplayName("Without any rule set there are no names, also without the folder")
  @Test
  void noRuleSets() {
    final FileBackedGamePropertiesCache cache =
        new FileBackedGamePropertiesCache(() -> folder.resolve("missing"));

    assertThat(cache.getRuleSetNames(TestMapGameData.REVISED.getGameData()))
        .as("a player who never saved one")
        .isEmpty();
  }
}
