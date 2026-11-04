package games.strategy.engine.framework.map.download;

import static org.assertj.core.api.Assertions.assertThat;

import games.strategy.engine.framework.map.download.MapPreviews.Caches;
import games.strategy.engine.framework.map.download.MapRow.Actions;
import games.strategy.engine.framework.map.download.MapTagFilter.Tag;
import games.strategy.triplea.settings.ClientSetting;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import javax.swing.JPanel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.triplea.http.client.lobby.maps.listing.MapDownloadItem;
import org.triplea.http.client.lobby.maps.listing.MapTag;

final class MapListTest {
  private static final Actions NO_ACTIONS = new Actions(map -> {}, map -> {}, map -> {});

  @DisplayName("The rows are sorted by map name, ignoring case")
  @Test
  void sortedByName() {
    final MapList list =
        givenList(List.of(givenMap("World War II", "WW2"), givenMap("big world", "Other")));

    assertThat(list.getShownMapNames()).containsExactly("big world", "World War II");
  }

  @DisplayName("The search finds maps by name or by a tag, eg the category")
  @Test
  void searchByNameOrTag() {
    final MapList list =
        givenList(
            List.of(
                givenMap("World War II Global", "WW2"),
                givenMap("Napoleonic Empires", "Napoleonic"),
                givenMap("Pacific", "WW2")));

    list.search(" ww2 ");

    assertThat(list.getShownMapNames())
        .as("the category matches, ignoring case and surrounding spaces")
        .containsExactly("Pacific", "World War II Global");
  }

  @DisplayName("Clearing the search shows all maps again")
  @Test
  void clearedSearchShowsAll() {
    final MapList list =
        givenList(List.of(givenMap("Pacific", "WW2"), givenMap("Napoleonic Empires", "Other")));
    list.search("pacific");

    list.search("");

    assertThat(list.getShownMapNames()).containsExactly("Napoleonic Empires", "Pacific");
  }

  @DisplayName("Refreshing shows the maps the list is given now, keeping the search")
  @Test
  void refreshKeepsSearch() {
    final List<ManagedMap> maps = new ArrayList<>(List.of(givenMap("Pacific", "WW2")));
    final MapList list = givenList(maps);
    list.search("ww2");
    maps.add(givenMap("Europe", "WW2"));
    maps.add(givenMap("Napoleonic Empires", "Other"));

    list.refresh();

    assertThat(list.getShownMapNames())
        .as("eg a map was removed and moved into this list")
        .containsExactly("Europe", "Pacific");
  }

  @DisplayName("The first shown map is selected, so the details are never empty")
  @Test
  void firstMapSelected() {
    final MapList list = givenList(List.of(givenMap("Pacific", "WW2"), givenMap("Europe", "WW2")));

    assertThat(list.getSelectedMapName()).as("first by name").contains("Europe");
  }

  @DisplayName("A selected map stays selected after a refresh; a hidden one passes it on")
  @Test
  void selectionFollowsSearch() {
    final MapList list = givenList(List.of(givenMap("Pacific", "WW2"), givenMap("Europe", "WW2")));
    list.select("Pacific");
    list.refresh();
    final String afterRefresh = list.getSelectedMapName().orElseThrow();

    list.search("euro");

    assertThat(List.of(afterRefresh, list.getSelectedMapName().orElseThrow()))
        .as("kept while shown, then the first shown map once the search hides it")
        .containsExactly("Pacific", "Europe");
  }

  @DisplayName("Without shown maps, no map is selected")
  @Test
  void noSelectionWithoutMaps() {
    final MapList list = givenList(List.of(givenMap("Pacific", "WW2")));

    list.search("napoleon");

    assertThat(list.getSelectedMapName()).isEmpty();
  }

  @DisplayName("Install selected installs the selected maps at once and selects one again")
  @Test
  void installSelected() {
    final List<Collection<ManagedMap>> installs = new ArrayList<>();
    final MapList list =
        givenList(
            List.of(givenMap("Pacific", "WW2"), givenMap("Europe", "WW2")),
            new Actions(installs::add, map -> {}, map -> {}));
    list.select("Pacific");
    list.toggle("Europe");

    list.installSelected();

    assertThat(installs)
        .as("one install of both maps, the same way as their Install buttons")
        .singleElement()
        .satisfies(
            maps ->
                assertThat(maps)
                    .extracting(ManagedMap::getMapName)
                    .containsExactly("Europe", "Pacific"));
    assertThat(list.getSelectedMapNames())
        .as("only the last clicked map")
        .containsExactly("Europe");
  }

  @DisplayName("Install selected leaves out selected maps that cannot be installed")
  @Test
  void installSelectedSkipsInstalled() {
    final List<Collection<ManagedMap>> installs = new ArrayList<>();
    final ManagedMap installed = givenMap("Europe", "WW2");
    installed.setMapStatus(ManagedMapStatus.INSTALLED);
    final MapList list =
        givenList(
            List.of(givenMap("Pacific", "WW2"), installed),
            new Actions(installs::add, map -> {}, map -> {}));
    list.select("Pacific");
    list.toggle("Europe");

    list.installSelected();

    assertThat(installs)
        .singleElement()
        .satisfies(
            maps ->
                assertThat(maps)
                    .as("an installed map has nothing to install")
                    .extracting(ManagedMap::getMapName)
                    .containsExactly("Pacific"));
  }

  @DisplayName("A search keeps only the shown maps selected")
  @Test
  void searchNarrowsSelection() {
    final MapList list =
        givenList(List.of(givenMap("Pacific", "WW2"), givenMap("Europe", "Other")));
    list.select("Pacific");
    list.toggle("Europe");

    list.search("ww2");

    assertThat(list.getSelectedMapNames()).containsExactly("Pacific");
  }

  @DisplayName("Selection and search work the same in all views")
  @Test
  void viewsKeepSelection() {
    final MapList list = givenList(List.of(givenMap("Pacific", "WW2"), givenMap("Europe", "WW2")));
    list.select("Pacific");

    list.showView(MapBrowserView.TILES);
    list.showView(MapBrowserView.LIST);

    assertThat(list.getShownMapNames())
        .as("the compact list shows the same maps, with the same one selected")
        .containsExactly("Europe", "Pacific");
    assertThat(list.getSelectedMapName()).contains("Pacific");
  }

  @DisplayName("Selected maps that are all installed are updated, else installed")
  @Test
  void selectedActionTitle() {
    final ManagedMap update = givenMap("Pacific", "WW2");
    update.setMapStatus(ManagedMapStatus.UPDATE_AVAILABLE);

    assertThat(
            List.of(
                MapList.selectedActionTitle(List.of(update)),
                MapList.selectedActionTitle(List.of(update, givenMap("Europe", "WW2")))))
        .as("the Installed tab selects maps with updates, the other tab new maps")
        .containsExactly("Update selected", "Install selected");
  }

  @DisplayName("The maps window shows the list with pictures until the player switches the view")
  @Test
  void defaultView() {
    assertThat(ClientSetting.mapBrowserView.getDefaultValue())
        .contains(MapBrowserView.LIST_WITH_PICTURES);
  }

  @DisplayName("Sorted by rating, the highest rated map comes first, equal ones by name")
  @Test
  void sortedByRating() {
    final MapList list = givenList(givenMapsToSort());

    list.sortBy(MapSort.RATING);

    assertThat(list.getShownMapNames())
        .as("example ratings 4.6, 4.4, 4.4, 3.5 and 3.1")
        .containsExactly("Iron War", "Europe", "Total World War", "Pacific", "World War II Global");
  }

  @DisplayName("Sorted by downloads, the most downloaded map comes first")
  @Test
  void sortedByDownloads() {
    final MapList list = givenList(givenMapsToSort());

    list.sortBy(MapSort.DOWNLOADS);

    assertThat(list.getShownMapNames())
        .as("example downloads 23,544, 12,760, 12,192, 7,084 and 1,892")
        .containsExactly("World War II Global", "Total World War", "Europe", "Pacific", "Iron War");
  }

  @DisplayName("Another order keeps the selected map and the search")
  @Test
  void sortKeepsSelectionAndSearch() {
    final MapList list = givenList(givenMapsToSort());
    list.search("war");
    list.select("Total World War");

    list.sortBy(MapSort.RATING);

    assertThat(list.getShownMapNames())
        .as("only the maps with 'war' in their name, highest rated first")
        .containsExactly("Iron War", "Total World War", "World War II Global");
    assertThat(list.getSelectedMapName())
        .as("the chosen map stays chosen in the new order")
        .contains("Total World War");
  }

  @DisplayName("The maps window sorts by name until the player chooses another order")
  @Test
  void defaultSort() {
    assertThat(ClientSetting.mapBrowserSort.getDefaultValue())
        .as("a first look at the maps finds them by name")
        .contains(MapSort.NAME);
  }

  @DisplayName("Checked tags and the search show only the maps that match both")
  @Test
  void tagsAndSearchCombine() {
    final MapList list =
        givenList(
            List.of(
                givenMap("Pacific", "WW2"),
                givenMap("World War II Europe", "WW2"),
                givenMap("World at War", "Other"),
                givenMap("Napoleonic Empires", "Napoleonic")));

    list.search("world");
    list.filterByTags(Set.of(new Tag("Category", "WW2"), new Tag("Category", "Napoleonic")));

    assertThat(list.getShownMapNames())
        .as("'World at War' matches only the search, 'Pacific' and 'Napoleonic' only the tags")
        .containsExactly("World War II Europe");
  }

  @DisplayName("A map without tags shows only while no tag is checked")
  @Test
  void untaggedMapsOnlyWithoutTagFilter() {
    final MapList list =
        givenList(List.of(givenMap("Pacific", "WW2"), givenMapWithTags("Europe", List.of())));
    list.filterByTags(Set.of(new Tag("Category", "WW2")));
    final List<String> withTag = list.getShownMapNames();

    list.filterByTags(Set.of());

    assertThat(List.of(withTag, list.getShownMapNames()))
        .as("filtered by the tag, then all maps again")
        .containsExactly(List.of("Pacific"), List.of("Europe", "Pacific"));
  }

  /** Maps whose example ratings and downloads are in another order than their names. */
  private static List<ManagedMap> givenMapsToSort() {
    return List.of(
        givenMap("Pacific", "WW2"),
        givenMap("Europe", "WW2"),
        givenMap("World War II Global", "WW2"),
        givenMap("Iron War", "WW1"),
        givenMap("Total World War", "WW2"));
  }

  private static MapList givenList(final List<ManagedMap> maps) {
    return givenList(maps, NO_ACTIONS);
  }

  private static MapList givenList(final List<ManagedMap> maps, final Actions actions) {
    return new MapList(
        () -> List.copyOf(maps),
        Caches.newCaches(),
        actions,
        new JPanel(),
        MapBrowserView.LIST_WITH_PICTURES,
        MapSort.NAME);
  }

  private static ManagedMap givenMap(final String name, final String category) {
    return givenMapWithTags(
        name, List.of(MapTag.builder().name("Category").value(category).displayOrder(1).build()));
  }

  private static ManagedMap givenMapWithTags(final String name, final List<MapTag> tags) {
    return new ManagedMap(
        MapDownloadItem.builder()
            .downloadUrl("")
            .previewImageUrl("")
            .mapName(name)
            .description("description")
            .lastCommitDateEpochMilli(60L)
            .downloadSizeInBytes(1024L)
            .mapTags(tags)
            .build());
  }
}
