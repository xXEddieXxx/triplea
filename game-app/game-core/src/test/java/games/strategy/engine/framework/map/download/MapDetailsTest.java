package games.strategy.engine.framework.map.download;

import static org.assertj.core.api.Assertions.assertThat;

import games.strategy.engine.framework.map.download.MapDetails.Fact;
import games.strategy.engine.framework.map.download.MapPreviews.Size;
import games.strategy.engine.framework.map.download.MapRow.Actions;
import games.strategy.engine.framework.map.file.system.loader.InstalledMap;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.HierarchyEvent;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.triplea.http.client.lobby.maps.listing.MapDownloadItem;
import org.triplea.http.client.lobby.maps.listing.MapTag;
import org.triplea.map.description.file.MapDescriptionYaml;
import org.triplea.map.description.file.MapDescriptionYaml.MapGame;

final class MapDetailsTest {
  private static final Instant OLDER = Instant.parse("2024-03-03T10:00:00Z");
  private static final Instant NEWER = Instant.parse("2025-06-09T10:00:00Z");

  @DisplayName("The details of an outdated map compare the installed and the newest version")
  @Test
  void outdatedMapComparesVersions() {
    final ManagedMap map = new ManagedMap(givenDownload(), givenInstalledMap());

    assertThat(MapDetails.facts(map))
        .as("status first, then both versions and the download size")
        .containsExactly(
            new Fact("Status", "Update available"),
            new Fact("Installed version", "3 Mar 2024"),
            new Fact("Newest version", "9 Jun 2025"),
            new Fact("Size", "2 MB"),
            new Fact("Downloads", MapRating.downloadsText("World War II Global")));
  }

  @DisplayName("The details of a map to get have no installed version")
  @Test
  void mapToGetHasNoInstalledVersion() {
    final ManagedMap map = new ManagedMap(givenDownload(), givenInstalledMap());
    map.setMapStatus(ManagedMapStatus.AVAILABLE);

    assertThat(MapDetails.facts(map))
        .as("a removed map keeps the installed map it was read with, which is gone now")
        .containsExactly(
            new Fact("Status", "Not installed"),
            new Fact("Newest version", "9 Jun 2025"),
            new Fact("Size", "2 MB"),
            new Fact("Downloads", MapRating.downloadsText("World War II Global")));
  }

  @DisplayName("The details end with the tags, the values of one tag on one line")
  @Test
  void tagsAreFacts() {
    final ManagedMap map =
        new ManagedMap(
            MapDownloadItem.builder()
                .downloadUrl("")
                .previewImageUrl("")
                .mapName("World War II Global")
                .description("description")
                .lastCommitDateEpochMilli(NEWER.toEpochMilli())
                .downloadSizeInBytes(2L * 1024 * 1024)
                .mapTags(
                    List.of(
                        MapTag.builder().name("Category").value("Pacific").displayOrder(3).build(),
                        MapTag.builder().name("Era").value("Modern").displayOrder(2).build(),
                        MapTag.builder().name("Category").value("WW2").displayOrder(1).build()))
                .build());

    assertThat(MapDetails.facts(map))
        .as("in display order, so 'WW2' before 'Pacific'")
        .endsWith(new Fact("Category", "WW2, Pacific"), new Fact("Era", "Modern"));
  }

  @DisplayName("In a short window the ratings scroll below the facts and buttons, not over them")
  @Test
  void ratingsStayBelowTheButtons() {
    final MapDetails details = givenDetails();
    details.show(new ManagedMap(givenDownload()));
    final Component top =
        ((BorderLayout) details.getLayout()).getLayoutComponent(BorderLayout.NORTH);
    final Insets insets = details.getInsets();

    // room for the facts and buttons and 120 px below them, less than the ratings need
    details.setSize(480, insets.top + top.getPreferredSize().height + 16 + 120 + insets.bottom);
    layOut(details);

    final MapRating rating = ratingIn(details);
    final Rectangle ratingBounds =
        SwingUtilities.convertRectangle(rating.getParent(), rating.getBounds(), details);
    assertThat(ratingBounds.y)
        .as("the ratings start below the buttons, the rest of them is scrolled to")
        .isGreaterThanOrEqualTo(top.getY() + top.getHeight());
  }

  private static void layOut(final Container container) {
    container.doLayout();
    Arrays.stream(container.getComponents())
        .filter(Container.class::isInstance)
        .map(Container.class::cast)
        .forEach(MapDetailsTest::layOut);
  }

  @DisplayName("Showing the same map again leaves the ratings in place, so typing keeps the focus")
  @Test
  void sameMapAgainKeepsRatingsInPlace() {
    final MapDetails details = givenDetails();
    final ManagedMap global = new ManagedMap(givenDownload());
    details.show(global);
    final List<HierarchyEvent> moves = new ArrayList<>();
    ratingIn(details).addHierarchyListener(moves::add);

    // eg after another map's download changed the list
    details.show(global);

    assertThat(moves)
        .as("taking the ratings out of the window, even for a moment, moves the focus away")
        .isEmpty();
  }

  @DisplayName("A rating not saved yet stays while its map shows again, a saved one comes back")
  @Test
  void ratingsStayWithTheirMap() {
    final MapDetails details = givenDetails();
    final ManagedMap global = new ManagedMap(givenDownload());
    details.show(global);
    ratingIn(details).rate(4);
    ratingIn(details).getComment().setText("Fine");

    // eg after another map's download changed the list, then the player saves and looks around
    details.show(global);
    ratingIn(details).saveRating();
    details.show(new ManagedMap(givenDownload("Pacific")));
    details.show(global);

    assertThat(List.of(ratingIn(details).getStars(), ratingIn(details).getComment().getText()))
        .as("the stars and the comment typed before showing the map again were saved")
        .containsExactly(4, "Fine");
  }

  private static MapDetails givenDetails() {
    return new MapDetails(
        new MapPreviews(Size.DETAILS), new Actions(maps -> {}, map -> {}, map -> {}));
  }

  /** The ratings, which sit in the scrolling part of the details. */
  private static MapRating ratingIn(final Component component) {
    return findRating(component).orElseThrow();
  }

  private static Optional<MapRating> findRating(final Component component) {
    if (component instanceof MapRating rating) {
      return Optional.of(rating);
    }
    return component instanceof Container container
        ? Arrays.stream(container.getComponents())
            .map(MapDetailsTest::findRating)
            .flatMap(Optional::stream)
            .findFirst()
        : Optional.empty();
  }

  private static MapDownloadItem givenDownload() {
    return givenDownload("World War II Global");
  }

  private static MapDownloadItem givenDownload(final String mapName) {
    return MapDownloadItem.builder()
        .downloadUrl("")
        .previewImageUrl("")
        .mapName(mapName)
        .description("description")
        .lastCommitDateEpochMilli(NEWER.toEpochMilli())
        .downloadSizeInBytes(2L * 1024 * 1024)
        .build();
  }

  private static InstalledMap givenInstalledMap() {
    return InstalledMap.builder()
        .lastModifiedDate(OLDER)
        .mapDescriptionYaml(
            MapDescriptionYaml.builder()
                .yamlFileLocation(Path.of("/local/file"))
                .mapName("World War II Global")
                .mapGameList(
                    List.of(MapGame.builder().xmlFileName("path.xml").gameName("game").build()))
                .build())
        .build();
  }
}
