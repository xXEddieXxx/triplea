package games.strategy.engine.framework.startup.ui;

import com.google.common.annotations.VisibleForTesting;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import games.strategy.engine.framework.map.file.system.loader.InstalledMapsListing;
import games.strategy.engine.framework.system.HttpProxy;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.triplea.http.client.lobby.maps.listing.MapDownloadItem;

/**
 * What changed lately: new TripleA versions, from the releases of the game on GitHub, and maps
 * updated in their repositories, from the map listing of the lobby server.
 */
@Slf4j
@UtilityClass
final class UpdatesFeed {
  @VisibleForTesting static final int SHOWN = 4;

  private static final URI RELEASES =
      URI.create("https://api.github.com/repos/triplea-game/triplea/releases?per_page=5");
  private static final int TIMEOUT_MILLIS = 8000;
  private static final String TEST_MAP = InstalledMapsListing.normalizeName("test_map");

  enum Kind {
    GAME,
    MAP
  }

  /** An update: of the game or of a map, its name, when it came out and its page. */
  record Update(Kind kind, String name, Instant when, URI page) {}

  /**
   * The newest TripleA version and the maps updated last, newest first; a version only once, the
   * frequent builds would fill the feed.
   */
  static List<Update> newest(final List<Update> releases, final List<MapDownloadItem> maps) {
    return Stream.concat(releases.stream().limit(1), mapUpdates(maps).stream())
        .sorted(Comparator.comparing(Update::when).reversed())
        .limit(SHOWN)
        .toList();
  }

  /** The maps whose repository changed last, eg "World War II Global, 3 days ago". */
  @VisibleForTesting
  static List<Update> mapUpdates(final List<MapDownloadItem> maps) {
    return maps.stream()
        // the map project's repository for trying out the listing is no news
        .filter(map -> !TEST_MAP.equals(InstalledMapsListing.normalizeName(map.getMapName())))
        .sorted(Comparator.comparing(MapDownloadItem::getLastCommitDateEpochMilli).reversed())
        .limit(SHOWN)
        .map(UpdatesFeed::toMapUpdate)
        .flatMap(Optional::stream)
        .toList();
  }

  private static Optional<Update> toMapUpdate(final MapDownloadItem map) {
    return repositoryOf(map.getDownloadUrl())
        .map(
            page ->
                new Update(
                    Kind.MAP,
                    MapOfTheDayPanel.readableMapName(map.getMapName()),
                    Instant.ofEpochMilli(map.getLastCommitDateEpochMilli()),
                    page));
  }

  /**
   * "https://github.com/triplea-maps/x/archive/master.zip" is in
   * "https://github.com/triplea-maps/x".
   */
  @VisibleForTesting
  static Optional<URI> repositoryOf(final String downloadUrl) {
    final int archive = downloadUrl.indexOf("/archive/");
    try {
      return Optional.of(URI.create(archive > 0 ? downloadUrl.substring(0, archive) : downloadUrl));
    } catch (final IllegalArgumentException e) {
      return Optional.empty();
    }
  }

  /** The newest releases of TripleA; empty when GitHub cannot be reached. Blocks: call off EDT. */
  static List<Update> fetchReleases() {
    try {
      // through the proxy of the settings, as the map downloads
      final URLConnection connection = RELEASES.toURL().openConnection(HttpProxy.getProxy());
      connection.setConnectTimeout(TIMEOUT_MILLIS);
      connection.setReadTimeout(TIMEOUT_MILLIS);
      try (InputStream stream = connection.getInputStream()) {
        return parseReleases(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
      }
    } catch (final IOException e) {
      log.info("GitHub could not be reached: {}", e.getMessage());
      return List.of();
    }
  }

  /** The published releases of GitHub's answer, without drafts and pre-releases. */
  @VisibleForTesting
  static List<Update> parseReleases(final String json) {
    try {
      final JsonElement releases = JsonParser.parseString(json);
      if (!releases.isJsonArray()) {
        return List.of();
      }
      return StreamSupport.stream(releases.getAsJsonArray().spliterator(), false)
          .filter(JsonElement::isJsonObject)
          .map(JsonElement::getAsJsonObject)
          .filter(release -> !isTrue(release, "draft") && !isTrue(release, "prerelease"))
          .map(UpdatesFeed::toRelease)
          .flatMap(Optional::stream)
          .toList();
    } catch (final JsonParseException | IllegalStateException e) {
      log.info("Unexpected answer of GitHub", e);
      return List.of();
    }
  }

  private static boolean isTrue(final JsonObject release, final String flag) {
    final JsonElement value = release.get(flag);
    return value != null
        && value.isJsonPrimitive()
        && value.getAsJsonPrimitive().isBoolean()
        && value.getAsBoolean();
  }

  private static Optional<Update> toRelease(final JsonObject release) {
    final JsonElement tag = release.get("tag_name");
    final JsonElement page = release.get("html_url");
    final JsonElement published = release.get("published_at");
    if (tag == null || page == null || published == null || published.isJsonNull()) {
      return Optional.empty();
    }
    try {
      return Optional.of(
          new Update(
              Kind.GAME,
              tag.getAsString(),
              Instant.parse(published.getAsString()),
              URI.create(page.getAsString())));
    } catch (final DateTimeParseException | IllegalArgumentException | IllegalStateException e) {
      // a release that cannot be shown is left out
      return Optional.empty();
    }
  }
}
