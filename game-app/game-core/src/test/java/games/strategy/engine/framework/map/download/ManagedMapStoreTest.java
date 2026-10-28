package games.strategy.engine.framework.map.download;

import static org.assertj.core.api.Assertions.assertThat;

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
