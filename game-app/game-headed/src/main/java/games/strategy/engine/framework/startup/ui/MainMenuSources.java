package games.strategy.engine.framework.startup.ui;

import com.google.common.base.Suppliers;
import feign.FeignException;
import games.strategy.engine.framework.map.file.system.loader.InstalledMapsListing;
import games.strategy.engine.framework.startup.ui.ForumTopics.ForumTopic;
import games.strategy.engine.framework.startup.ui.UpdatesFeed.Update;
import games.strategy.triplea.settings.ClientSetting;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.triplea.config.product.ProductVersionReader;
import org.triplea.domain.data.SystemIdLoader;
import org.triplea.http.client.ClientIdentifiers;
import org.triplea.http.client.latest.version.LatestVersionClient;
import org.triplea.http.client.latest.version.LatestVersionResponse;
import org.triplea.http.client.lobby.maps.listing.MapDownloadItem;
import org.triplea.http.client.maps.listing.MapsClient;

/**
 * Where the main menu reads what it shows besides its entries: the installed maps and those to
 * download, the newest topics of the forum, the newest TripleA versions, whether this one is the
 * latest and the numbers of the lobby. Each is called off the EDT.
 *
 * @param lobbyStats players online and open games; the lobby server does not tell them without a
 *     login yet, so the live sources give {@link LobbyStats#EXAMPLE}
 */
record MainMenuSources(
    Supplier<InstalledMapsListing> installedMaps,
    Supplier<List<MapDownloadItem>> mapsToDownload,
    Supplier<List<ForumTopic>> forumTopics,
    Supplier<List<Update>> gameReleases,
    Supplier<Optional<LatestVersionResponse>> latestVersion,
    Supplier<Optional<LobbyStats>> lobbyStats) {

  /** How busy the lobby is. */
  record LobbyStats(int playersOnline, int openGames) {
    /**
     * Example numbers that hold the place of the lobby's numbers in the main menu until the lobby
     * server tells them without a login; the live sources show these, not real ones.
     */
    static final LobbyStats EXAMPLE = new LobbyStats(23, 4);
  }

  /** The maps folder, the lobby server, the forum and GitHub. */
  static MainMenuSources live() {
    return new MainMenuSources(
        InstalledMapsListing::parseMapFiles,
        // read once for the map of the day and the updates both
        Suppliers.memoize(Listing::fetchQuietly),
        ForumTopics::fetchNewest,
        UpdatesFeed::fetchReleases,
        () ->
            LatestVersionClient.fetchLatestVersion(
                ClientSetting.lobbyUri.getValueOrThrow(), ProductVersionReader.getCurrentVersion()),
        () -> Optional.of(LobbyStats.EXAMPLE));
  }

  @Slf4j
  private static final class Listing {
    /**
     * The maps of the lobby server; empty offline. Unlike the maps window, the menu does not report
     * a failure: it shows the installed maps then.
     */
    static List<MapDownloadItem> fetchQuietly() {
      try {
        return MapsClient.newClient(
                ClientSetting.lobbyUri.getValueOrThrow(),
                ClientIdentifiers.builder().systemId(SystemIdLoader.load().getValue()).build())
            .fetchMapListing()
            .getMaps();
      } catch (final FeignException e) {
        log.info("The list of maps could not be read: {}", e.getMessage());
        return List.of();
      }
    }
  }
}
