package games.strategy.engine.framework.map.download;

import static org.assertj.core.api.Assertions.assertThat;

import games.strategy.engine.framework.map.download.MapTagFilter.Tag;
import java.util.List;
import java.util.Set;
import javax.swing.JButton;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.triplea.http.client.lobby.maps.listing.MapDownloadItem;
import org.triplea.http.client.lobby.maps.listing.MapTag;

final class MapTagFilterTest {
  private static final Tag WW2 = new Tag("Category", "WW2");
  private static final Tag NAPOLEONIC = new Tag("Category", "Napoleonic");
  private static final Tag MODERN = new Tag("Era", "Modern");

  @DisplayName("The tags to choose are those of the listed maps, each once, by name and value")
  @Test
  void tagsOfTheListing() {
    final List<ManagedMap> maps =
        List.of(
            givenMap("Pacific", givenTag("Category", "WW2"), givenTag("Players", " ")),
            givenMap("Europe", givenTag("Era", "Modern"), givenTag("Category", "WW2")),
            givenMap("Napoleonic Empires", givenTag("Category", "Napoleonic")),
            givenMap("Untagged"));

    assertThat(MapTagFilter.tagsOf(maps))
        .as("WW2 once, the blank 'Players' left out, 'Napoleonic' before 'WW2'")
        .containsExactly(NAPOLEONIC, WW2, MODERN);
  }

  @DisplayName("A map passes with one of the checked values of every checked tag name")
  @Test
  void anyValueOfEachName() {
    final ManagedMap europe =
        givenMap("Europe", givenTag("Category", "WW2"), givenTag("Era", "Modern"));

    assertThat(
            List.of(
                MapTagFilter.matches(europe, Set.of(WW2, NAPOLEONIC)),
                MapTagFilter.matches(europe, Set.of(WW2, new Tag("Era", "Ancient"))),
                MapTagFilter.matches(europe, Set.of(WW2, MODERN))))
        .as("one of the categories; not with another era; both names")
        .containsExactly(true, false, true);
  }

  @DisplayName("Without checked tags every map passes, with some a map without tags does not")
  @Test
  void untaggedMap() {
    final ManagedMap untagged = givenMap("Untagged");

    assertThat(
            List.of(
                MapTagFilter.matches(untagged, Set.of()),
                MapTagFilter.matches(untagged, Set.of(WW2))))
        .as("a map without tags is no WW2 map, but shows while no tag filters")
        .containsExactly(true, false);
  }

  @DisplayName("The button shows how many tags are checked")
  @Test
  void buttonCountsCheckedTags() {
    final MapTagFilter filter = new MapTagFilter(List.of(NAPOLEONIC, WW2, MODERN), tags -> {});
    final JButton button = (JButton) filter.getComponent(0);
    final String noneChecked = button.getText();

    filter.showChecked(Set.of(WW2, MODERN));

    assertThat(List.of(noneChecked, button.getText()))
        .as("the button tells how many tags filter the maps")
        .containsExactly("Tags ▾", "Tags (2) ▾");
  }

  @DisplayName("Without tags in the listing the tag button is hidden")
  @Test
  void hiddenWithoutTags() {
    assertThat(new MapTagFilter(List.of(), tags -> {}).isVisible())
        .as("nothing to filter by")
        .isFalse();
  }

  private static MapTag givenTag(final String name, final String value) {
    return MapTag.builder().name(name).value(value).displayOrder(1).build();
  }

  private static ManagedMap givenMap(final String name, final MapTag... tags) {
    return new ManagedMap(
        MapDownloadItem.builder()
            .downloadUrl("")
            .previewImageUrl("")
            .mapName(name)
            .description("description")
            .lastCommitDateEpochMilli(60L)
            .downloadSizeInBytes(1024L)
            .mapTags(List.of(tags))
            .build());
  }
}
