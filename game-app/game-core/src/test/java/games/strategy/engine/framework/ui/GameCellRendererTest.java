package games.strategy.engine.framework.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import games.strategy.engine.framework.map.download.MapBrowserView;
import games.strategy.engine.framework.map.download.MapPreviews;
import games.strategy.engine.framework.map.download.MapPreviews.Size;
import games.strategy.engine.framework.map.file.system.loader.InstalledMap;
import java.awt.Component;
import java.awt.Container;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import javax.swing.JLabel;
import javax.swing.JList;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

final class GameCellRendererTest {
  private final DefaultGameChooserEntry classic = givenGame();
  private final JList<DefaultGameChooserEntry> list = new JList<>();

  @DisplayName("The list view shows a game and its map on one line")
  @Test
  void listShowsOneLine() {
    final Component cell = givenCell(MapBrowserView.LIST, Size.ROW);

    assertThat(texts(cell))
        .as("games of the same name on different maps are told apart")
        .containsExactly("Classic  ·  world_war_ii_classic");
  }

  @DisplayName("Tiles show the map's picture on top, the game and its map below it")
  @Test
  void tilesShowPictureAndNames() {
    final Component cell = givenCell(MapBrowserView.TILES, Size.TILE);

    assertThat(texts(cell)).containsExactly("Classic", "world_war_ii_classic");
    assertThat(cell.getPreferredSize().width)
        .as("at least as wide as the picture of a tile")
        .isGreaterThanOrEqualTo(Size.TILE.getWidth());
  }

  private Component givenCell(final MapBrowserView view, final Size size) {
    return new GameCellRenderer(view, new MapPreviews(size), () -> {})
        .getListCellRendererComponent(list, classic, 0, false, false);
  }

  private static DefaultGameChooserEntry givenGame() {
    final InstalledMap map = mock(InstalledMap.class);
    when(map.getMapName()).thenReturn("world_war_ii_classic");
    when(map.findPreviewImage()).thenReturn(Optional.empty());
    return DefaultGameChooserEntry.builder().installedMap(map).gameName("Classic").build();
  }

  private static List<String> texts(final Component component) {
    return componentsIn(component)
        .filter(JLabel.class::isInstance)
        .map(label -> ((JLabel) label).getText())
        .toList();
  }

  private static Stream<Component> componentsIn(final Component component) {
    return component instanceof Container container
        ? Stream.concat(
            Stream.of(component),
            Arrays.stream(container.getComponents()).flatMap(GameCellRendererTest::componentsIn))
        : Stream.of(component);
  }
}
