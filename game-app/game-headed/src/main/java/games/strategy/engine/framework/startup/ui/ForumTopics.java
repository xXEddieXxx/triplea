package games.strategy.engine.framework.startup.ui;

import com.google.common.annotations.VisibleForTesting;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import games.strategy.engine.framework.system.HttpProxy;
import games.strategy.triplea.UrlConstants;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.StringEscapeUtils;

/**
 * The newest topics of the TripleA forum, read from its public list of recent topics. The forum
 * (NodeBB) needs no login for it.
 */
@Slf4j
@UtilityClass
final class ForumTopics {
  @VisibleForTesting static final int SHOWN = 3;

  private static final URI RECENT = URI.create(UrlConstants.TRIPLEA_FORUM + "api/recent");
  private static final int TIMEOUT_MILLIS = 8000;

  /** A topic: its title, its page in the forum, when it was last answered and how often. */
  record ForumTopic(String title, URI page, Instant lastPost, int replies) {}

  /**
   * The newest topics; empty when the forum cannot be reached, eg offline. Blocks: call off EDT.
   */
  static List<ForumTopic> fetchNewest() {
    try {
      // through the proxy of the settings, as the map downloads
      final URLConnection connection = RECENT.toURL().openConnection(HttpProxy.getProxy());
      connection.setConnectTimeout(TIMEOUT_MILLIS);
      connection.setReadTimeout(TIMEOUT_MILLIS);
      try (InputStream stream = connection.getInputStream()) {
        return parse(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
      }
    } catch (final IOException e) {
      log.info("The forum could not be reached: {}", e.getMessage());
      return List.of();
    }
  }

  /** The first {@link #SHOWN} topics of the forum's answer that are neither deleted nor broken. */
  @VisibleForTesting
  static List<ForumTopic> parse(final String json) {
    try {
      final JsonElement topics = JsonParser.parseString(json).getAsJsonObject().get("topics");
      if (topics == null || !topics.isJsonArray()) {
        return List.of();
      }
      return StreamSupport.stream(topics.getAsJsonArray().spliterator(), false)
          .filter(JsonElement::isJsonObject)
          .map(JsonElement::getAsJsonObject)
          .filter(topic -> !isSet(topic, "deleted"))
          .map(ForumTopics::toTopic)
          .flatMap(Optional::stream)
          .limit(SHOWN)
          .toList();
    } catch (final JsonParseException | IllegalStateException e) {
      log.info("Unexpected answer of the forum", e);
      return List.of();
    }
  }

  /** The answers to the first post: the forum counts the posts of a topic, the first one too. */
  private static int replies(final JsonObject topic) {
    final JsonElement posts = topic.get("postcount");
    return posts != null && posts.isJsonPrimitive() && posts.getAsJsonPrimitive().isNumber()
        ? Math.max(0, posts.getAsInt() - 1)
        : 0;
  }

  /** Whether the flag is 1 or true; anything else, eg missing or text, counts as not set. */
  private static boolean isSet(final JsonObject topic, final String flag) {
    final JsonElement value = topic.get(flag);
    if (value == null || !value.isJsonPrimitive()) {
      return false;
    }
    final JsonPrimitive primitive = value.getAsJsonPrimitive();
    return primitive.isBoolean()
        ? primitive.getAsBoolean()
        : primitive.isNumber() && primitive.getAsInt() != 0;
  }

  private static Optional<ForumTopic> toTopic(final JsonObject topic) {
    final JsonElement title = topic.get("title");
    final JsonElement slug = topic.get("slug");
    final JsonElement lastPost = topic.get("lastposttime");
    if (title == null || slug == null || lastPost == null) {
      return Optional.empty();
    }
    try {
      return Optional.of(
          new ForumTopic(
              // the forum sends titles escaped for HTML, eg "Axis &amp; Allies"
              StringEscapeUtils.unescapeHtml4(title.getAsString()),
              URI.create(UrlConstants.TRIPLEA_FORUM + "topic/" + slug.getAsString()),
              Instant.ofEpochMilli(lastPost.getAsLong()),
              replies(topic)));
    } catch (final IllegalArgumentException | IllegalStateException e) {
      // eg a slug that is no valid link: leave that one topic out
      return Optional.empty();
    }
  }
}
