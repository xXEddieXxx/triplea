package games.strategy.engine.framework.map.listing;

import feign.FeignException;
import games.strategy.engine.framework.map.file.system.loader.InstalledMapsListing;
import games.strategy.triplea.settings.ClientSetting;
import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.triplea.domain.data.SystemIdLoader;
import org.triplea.http.client.ClientIdentifiers;
import org.triplea.http.client.lobby.maps.listing.MapDownloadItem;
import org.triplea.http.client.maps.listing.MapsClient;

@UtilityClass
@Slf4j
public class MapListingFetcher {
  /** Fetches the full listing of maps that are available for download. */
  public static List<MapDownloadItem> getMapDownloadList() {
    try {
      return fetchMapDownloadList();
    } catch (FeignException e) {
      log.warn(
"""
Failed to download the list of available maps from TripleA servers.
You can download the needed maps manually into your TripleA maps subfolder from <a href='https://github.com/triplea-maps/'>https://github.com/triplea-maps/</a>.""",
          e);
      return List.of();
    }
  }

  /** The listing, or a FeignException when the server cannot be asked. */
  private static List<MapDownloadItem> fetchMapDownloadList() {
    return MapsClient.newClient(
            ClientSetting.lobbyUri.getValueOrThrow(),
            ClientIdentifiers.builder().systemId(SystemIdLoader.load().getValue()).build())
        .fetchMapListing()
        .getMaps();
  }

  /**
   * The map of the listing, by its name as a game or a save names it; case, spaces, "_" and "-"
   * aside. A server that cannot be asked throws, eg a FeignException, without a warning of its own:
   * the caller tells what then happens, eg a game asks for the map.
   */
  public static Optional<MapDownloadItem> findMap(final String mapName) {
    final String wanted = InstalledMapsListing.normalizeName(mapName);
    return fetchMapDownloadList().stream()
        .filter(map -> InstalledMapsListing.normalizeName(map.getMapName()).equals(wanted))
        .findFirst();
  }
}
