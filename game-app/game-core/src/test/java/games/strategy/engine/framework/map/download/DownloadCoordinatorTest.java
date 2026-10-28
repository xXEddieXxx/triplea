package games.strategy.engine.framework.map.download;

import static games.strategy.engine.framework.map.download.DownloadFileTest.givenReaderFailingOn;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import games.strategy.engine.framework.map.download.DownloadFile.DownloadState;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.triplea.http.client.lobby.maps.listing.MapDownloadItem;

final class DownloadCoordinatorTest {
  // lets the downloads that wait for it fail, so their threads end with the test
  private final CountDownLatch release = new CountDownLatch(1);

  @AfterEach
  void releaseDownloads() {
    release.countDown();
  }

  @DisplayName("Cancelling a download ends only that one; the others go on")
  @Test
  void cancelEndsOneDownload() {
    final DownloadCoordinator coordinator = new DownloadCoordinator(givenReaderFailingOn(release));
    final MapDownloadItem pacific = givenDownload("Pacific");
    coordinator.accept(pacific);
    coordinator.accept(givenDownload("Europe"));

    coordinator.cancel(pacific);

    assertThat(statesByMapName(coordinator))
        .as("the cancelled map stopped, the other map still downloads")
        .containsExactlyInAnyOrderEntriesOf(
            Map.of("Pacific", DownloadState.CANCELLED, "Europe", DownloadState.DOWNLOADING));
  }

  @DisplayName("A cancelled download can be started again")
  @Test
  void cancelledDownloadCanStartAgain() {
    final DownloadCoordinator coordinator = new DownloadCoordinator(givenReaderFailingOn(release));
    final MapDownloadItem pacific = givenDownload("Pacific");
    coordinator.accept(pacific);
    coordinator.cancel(pacific);
    coordinator.getDownloads();

    coordinator.accept(pacific);

    assertThat(statesByMapName(coordinator))
        .as("Install after Cancel downloads the map anew")
        .containsExactlyEntriesOf(Map.of("Pacific", DownloadState.DOWNLOADING));
  }

  @DisplayName("A failed download leaves the running downloads")
  @Test
  void failedDownloadEnds() {
    final DownloadCoordinator coordinator =
        new DownloadCoordinator(givenReaderFailingOn(new CountDownLatch(0)));
    coordinator.accept(givenDownload("Pacific"));

    await()
        .atMost(Duration.ofSeconds(5))
        .alias("the download thread fails at once")
        .until(() -> coordinator.getDownloads().stream().noneMatch(DownloadFile::isRunning));
  }

  private static Map<String, DownloadState> statesByMapName(final DownloadCoordinator coordinator) {
    return coordinator.getDownloads().stream()
        .collect(
            Collectors.toMap(
                download -> download.getDownload().getMapName(), DownloadFile::getDownloadState));
  }

  private static MapDownloadItem givenDownload(final String mapName) {
    return MapDownloadItem.builder()
        .downloadUrl("url")
        .previewImageUrl("")
        .mapName(mapName)
        .description("description")
        .lastCommitDateEpochMilli(60L)
        .downloadSizeInBytes(100L)
        .build();
  }
}
