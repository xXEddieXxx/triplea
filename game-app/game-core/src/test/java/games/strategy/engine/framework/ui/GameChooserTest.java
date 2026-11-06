package games.strategy.engine.framework.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import games.strategy.engine.framework.map.file.system.loader.InstalledMap;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

final class GameChooserTest {
  private final DefaultGameChooserEntry classic =
      givenGame("Classic", "world_war_ii_classic", Instant.parse("2024-01-01T00:00:00Z"));
  private final DefaultGameChooserEntry pacific =
      givenGame("Pacific 1940", "pacific", Instant.parse("2026-05-01T00:00:00Z"));
  private final DefaultGameChooserEntry bigWorld = givenGame("Big World", "big_world", null);
  private final List<DefaultGameChooserEntry> games = List.of(pacific, classic, bigWorld);

  @DisplayName("The search finds games by their name or their map's name, ignoring case")
  @Test
  void searchFindsGameOrMap() {
    assertThat(GameChooser.matching(games, "PACIFIC", GameChooser.Order.NAME))
        .as("by the game's name")
        .containsExactly(pacific);
    assertThat(GameChooser.matching(games, " classic ", GameChooser.Order.NAME))
        .as("by the map's name too; spaces around the search do not count")
        .containsExactly(classic);
    assertThat(GameChooser.matching(games, "", GameChooser.Order.NAME))
        .as("an empty search shows every game")
        .hasSize(3);
  }

  @DisplayName("The games are sorted by name, by map or with the latest updated maps first")
  @Test
  void ordersSortTheGames() {
    assertThat(GameChooser.matching(games, "", GameChooser.Order.NAME))
        .as("Big World, Classic, Pacific 1940")
        .containsExactly(bigWorld, classic, pacific);
    assertThat(GameChooser.matching(games, "", GameChooser.Order.MAP))
        .as("big_world, pacific, world_war_ii_classic")
        .containsExactly(bigWorld, pacific, classic);
    assertThat(GameChooser.matching(games, "", GameChooser.Order.NEWEST))
        .as("a map whose date cannot be read comes last")
        .containsExactly(pacific, classic, bigWorld);
  }

  private static DefaultGameChooserEntry givenGame(
      final String gameName, final String mapName, @Nullable final Instant updated) {
    final InstalledMap map = mock(InstalledMap.class);
    when(map.getMapName()).thenReturn(mapName);
    when(map.getLastModifiedDate()).thenReturn(Optional.ofNullable(updated));
    return DefaultGameChooserEntry.builder().installedMap(map).gameName(gameName).build();
  }
}
