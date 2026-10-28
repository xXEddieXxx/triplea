package games.strategy.engine.framework.map.download;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import org.triplea.http.client.lobby.maps.listing.MapDownloadItem;

/// Class that accepts and queues download requests. Download requests are started in background
/// thread, this class ensures N are in progress until all are done.
public final class DownloadCoordinator {
  public static final DownloadCoordinator instance =
      new DownloadCoordinator(DownloadConfiguration.contentReader());

  private static final int MAX_CONCURRENT_DOWNLOADS = 3;

  private final Object lock = new Object();
  private final ContentReader contentReader;

  private final Queue<DownloadFile> pendingDownloads = new ArrayDeque<>();
  private final Set<DownloadFile> activeDownloads = new HashSet<>();
  private final Set<DownloadFile> terminatedDownloads = new HashSet<>();

  private final List<DownloadListener> downloadListeners = new CopyOnWriteArrayList<>();

  DownloadCoordinator(final ContentReader contentReader) {
    this.contentReader = contentReader;
  }

  void addDownloadListener(final DownloadListener downloadListener) {
    downloadListeners.add(downloadListener);
  }

  void removeDownloadListener(final DownloadListener downloadListener) {
    downloadListeners.remove(downloadListener);
  }

  void accept(final MapDownloadItem download) {
    synchronized (lock) {
      if (isNewDownload(download)) {
        pendingDownloads.add(new DownloadFile(download, new Listener(), contentReader));
        updateQueue();
      }
    }
  }

  private boolean isNewDownload(final MapDownloadItem download) {
    assert Thread.holdsLock(lock);

    return !containsDownload(pendingDownloads, download)
        && !containsDownload(activeDownloads, download);
  }

  private static boolean containsDownload(
      final Collection<DownloadFile> downloadFiles, final MapDownloadItem download) {
    return downloadFiles.stream().map(DownloadFile::getDownload).anyMatch(download::equals);
  }

  /// Starts any downloads that can be started.
  private void updateQueue() {
    assert Thread.holdsLock(lock);

    if (activeDownloads.size() < MAX_CONCURRENT_DOWNLOADS && !pendingDownloads.isEmpty()) {
      final DownloadFile downloadFile = pendingDownloads.remove();
      downloadFile.startAsyncDownload();
      activeDownloads.add(downloadFile);
    }
  }

  /// Cancels the download of the map, whether it waits or runs; the next waiting one may start.
  void cancel(final MapDownloadItem download) {
    synchronized (lock) {
      cancelIn(pendingDownloads, download);
      cancelIn(activeDownloads, download);
      updateQueue();
    }
  }

  private void cancelIn(
      final Collection<DownloadFile> downloadFiles, final MapDownloadItem download) {
    assert Thread.holdsLock(lock);

    final Iterator<DownloadFile> iterator = downloadFiles.iterator();
    while (iterator.hasNext()) {
      final DownloadFile downloadFile = iterator.next();
      if (downloadFile.getDownload().equals(download)) {
        iterator.remove();
        downloadFile.cancelDownload();
        terminatedDownloads.add(downloadFile);
      }
    }
  }

  Set<DownloadFile> pollTerminatedDownloads() {
    synchronized (lock) {
      final Set<DownloadFile> set = new HashSet<>(terminatedDownloads);
      terminatedDownloads.clear();
      return set;
    }
  }

  Set<DownloadFile> getPendingDownloads() {
    synchronized (lock) {
      return new HashSet<>(pendingDownloads);
    }
  }

  Set<DownloadFile> getDownloads() {
    synchronized (lock) {
      final Set<DownloadFile> result = getPendingDownloads();
      result.addAll(activeDownloads);
      result.addAll(pollTerminatedDownloads());
      return result;
    }
  }

  private final class Listener implements DownloadListener {
    @Override
    public void downloadUpdated(final MapDownloadItem download, final long bytesReceived) {
      downloadListeners.forEach(it -> it.downloadUpdated(download, bytesReceived));
    }

    @Override
    public void downloadComplete(final MapDownloadItem download) {
      downloadListeners.forEach(it -> it.downloadComplete(download));
      startNextDownload();
    }

    @Override
    public void downloadFailed(final MapDownloadItem download) {
      downloadListeners.forEach(it -> it.downloadFailed(download));
      startNextDownload();
    }

    /** Moves a finished download out of the running ones, which makes room for the next one. */
    private void startNextDownload() {
      synchronized (lock) {
        final Iterator<DownloadFile> iterator = activeDownloads.iterator();
        while (iterator.hasNext()) {
          final DownloadFile downloadFile = iterator.next();
          if (downloadFile.isDone()) {
            iterator.remove();
            terminatedDownloads.add(downloadFile);
            break;
          }
        }
        updateQueue();
      }
    }
  }
}
