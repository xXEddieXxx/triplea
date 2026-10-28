package games.strategy.engine.framework.map.download;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import games.strategy.engine.framework.map.download.DownloadFile.DownloadState;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.triplea.http.client.lobby.maps.listing.MapDownloadItem;

class DownloadFileTest {
  private static final MapDownloadItem MAP_DOWNLOAD_ITEM =
      MapDownloadItem.builder()
          .downloadUrl("url")
          .previewImageUrl("preview-url")
          .description("description")
          .mapName("mapName")
          .lastCommitDateEpochMilli(60L)
          .downloadSizeInBytes(100L)
          .build();

  // lets the downloads that wait for it fail, so their threads end with the test
  private final CountDownLatch release = new CountDownLatch(1);

  @AfterEach
  void releaseDownloads() {
    release.countDown();
  }

  @Test
  void testBasicStartCancel() {
    final DownloadListener listener = mock(DownloadListener.class);
    final DownloadFile testObj =
        new DownloadFile(MAP_DOWNLOAD_ITEM, listener, givenReaderFailingOn(release));
    assertThat(testObj.getDownloadState()).isEqualTo(DownloadState.NOT_STARTED);

    testObj.startAsyncDownload();
    assertThat(testObj.getDownloadState()).isEqualTo(DownloadState.DOWNLOADING);

    testObj.cancelDownload();
    release.countDown();
    assertThat(testObj.getDownloadState()).isEqualTo(DownloadState.CANCELLED);
    verify(listener, after(500).never()).downloadFailed(MAP_DOWNLOAD_ITEM);
    verify(listener, never()).downloadComplete(MAP_DOWNLOAD_ITEM);
  }

  @DisplayName("A failed download ends and tells its listener, so its map can be installed again")
  @Test
  void failedDownloadEnds() {
    final DownloadListener listener = mock(DownloadListener.class);
    final DownloadFile download =
        new DownloadFile(MAP_DOWNLOAD_ITEM, listener, givenReaderFailingOn(new CountDownLatch(0)));

    download.startAsyncDownload();

    verify(listener, timeout(5000)).downloadFailed(MAP_DOWNLOAD_ITEM);
    assertThat(download.getDownloadState())
        .as("no longer running, so a new maps window does not show it downloading")
        .isEqualTo(DownloadState.FAILED);
    assertThat(download.isRunning()).isFalse();
  }

  /** A reader that fails, eg as the server is unreachable, once the latch is counted down. */
  static ContentReader givenReaderFailingOn(final CountDownLatch latch) {
    return new ContentReader(
        uri -> {
          try {
            latch.await();
          } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
          }
          throw new IOException("server unreachable");
        });
  }

  @Test
  void normalizeMapName() {
    assertThat(DownloadFile.normalizeMapName("valid-name")).isEqualTo("valid-name");
    assertThat(DownloadFile.normalizeMapName("also_valid")).isEqualTo("also_valid");
    assertThat(DownloadFile.normalizeMapName("a&b"))
        .as("Ampersand is a valid map name but scary in a file system, should be stripped")
        .isEqualTo("ab");
  }

  @ParameterizedTest
  @ValueSource(strings = {"*", ".", "\"", "/", "\\", "[", "]", ":", ";", "|", ","})
  void invalidCharactersAreStripped(final String invalidCharacter) {
    assertThat(DownloadFile.normalizeMapName(invalidCharacter)).isEqualTo("");
  }
}
