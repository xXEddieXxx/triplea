package games.strategy.engine.framework.map.download;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import games.strategy.engine.framework.map.file.system.loader.InstalledMapsListing;
import java.util.List;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.triplea.http.client.lobby.maps.listing.MapDownloadItem;

final class ManagedMapStoreTest {
  @DisplayName("A failed download marks its map failed, which offers to install it again")
  @Test
  void failedDownloadMarksMapFailed() throws Exception {
    final MapDownloadItem download = givenDownload();
    final ManagedMapStore store = new ManagedMapStore();
    store.initialize(List.of(download), InstalledMapsListing.builder().build());
    store.updateStatus(
        List.of(store.getMapByMapDownloadItem(download)), ManagedMapStatus.DOWNLOADING);

    store.downloadFailed(download);
    // the status changes on the Swing thread
    SwingUtilities.invokeAndWait(() -> {});

    assertThat(store.getMapByMapDownloadItem(download).getMapStatus())
        .as("no longer downloading, so the row shows 'Download failed' and Install")
        .isEqualTo(ManagedMapStatus.ERROR);
  }

  @DisplayName("A cancel told by the downloads resets a map still downloading")
  @Test
  void cancelResetsMapStillDownloading() throws Exception {
    final MapDownloadItem download = givenDownload();
    final ManagedMapStore store = givenStoreWithMapIn(download, ManagedMapStatus.DOWNLOADING);

    store.downloadCancelled(download);
    SwingUtilities.invokeAndWait(() -> {});

    assertThat(store.getMapByMapDownloadItem(download).getMapStatus())
        .as("eg cancelled while a game waited for it: Install is offered again")
        .isEqualTo(ManagedMapStatus.ERROR);
  }

  @DisplayName("A cancel told by the downloads leaves a map no longer downloading alone")
  @Test
  void cancelLeavesOtherMapAlone() throws Exception {
    final MapDownloadItem download = givenDownload();
    final ManagedMapStore store = givenStoreWithMapIn(download, ManagedMapStatus.AVAILABLE);

    store.downloadCancelled(download);
    SwingUtilities.invokeAndWait(() -> {});

    assertThat(store.getMapByMapDownloadItem(download).getMapStatus())
        .as("a cancel in the maps window set the map's status already")
        .isEqualTo(ManagedMapStatus.AVAILABLE);
  }

  private static ManagedMapStore givenStoreWithMapIn(
      final MapDownloadItem download, final ManagedMapStatus status) {
    final ManagedMapStore store = new ManagedMapStore();
    store.initialize(List.of(download), InstalledMapsListing.builder().build());
    store.updateStatus(List.of(store.getMapByMapDownloadItem(download)), status);
    return store;
  }

  @DisplayName("A download of a map the store does not hold is none of its business")
  @Test
  void downloadOfMapNotHeldIsIgnored() throws Exception {
    final MapDownloadItem download = givenDownload();
    final ManagedMapStore store = new ManagedMapStore();
    store.initialize(List.of(download), InstalledMapsListing.builder().build());
    final MapDownloadItem other =
        MapDownloadItem.builder()
            .downloadUrl("url")
            .previewImageUrl("")
            .mapName("Europe")
            .description("description")
            .lastCommitDateEpochMilli(60L)
            .downloadSizeInBytes(100L)
            .build();

    assertThatCode(() -> store.downloadComplete(other))
        .as("eg downloaded for a game while this store's listing could not be read")
        .doesNotThrowAnyException();
    SwingUtilities.invokeAndWait(() -> {});
    assertThat(store.getMapByMapDownloadItem(download).getMapStatus())
        .as("the store's own map is untouched")
        .isEqualTo(ManagedMapStatus.AVAILABLE);
  }

  private static MapDownloadItem givenDownload() {
    return MapDownloadItem.builder()
        .downloadUrl("url")
        .previewImageUrl("")
        .mapName("Pacific")
        .description("description")
        .lastCommitDateEpochMilli(60L)
        .downloadSizeInBytes(100L)
        .build();
  }
}
