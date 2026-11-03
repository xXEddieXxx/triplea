package games.strategy.engine.framework.map.download;

import org.triplea.http.client.lobby.maps.listing.MapDownloadItem;

interface DownloadListener {
  void downloadUpdated(MapDownloadItem download, long bytesReceived);

  void downloadComplete(MapDownloadItem download);

  /** The download could not be completed, eg the server could not be reached. */
  void downloadFailed(MapDownloadItem download);

  /** The download was cancelled; told by the coordinator, eg to one waiting for the map. */
  default void downloadCancelled(final MapDownloadItem download) {
    // a listener that shows downloads, eg a map's row, hears of the next one from its store
  }
}
