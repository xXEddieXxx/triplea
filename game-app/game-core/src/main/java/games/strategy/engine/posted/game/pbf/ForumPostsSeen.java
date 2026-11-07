package games.strategy.engine.posted.game.pbf;

import games.strategy.triplea.settings.ClientSetting;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

/**
 * The newest post the player saw of each game by forum, on this computer, so that the posts after
 * it show as new. A game never opened here has no time: its posts are not all new.
 */
@UtilityClass
public class ForumPostsSeen {

  /** When the newest post the player saw of the game was written; empty if never opened here. */
  public static Optional<Instant> seen(final String forumUrl, final int topicId) {
    return Optional.ofNullable(read().get(OldForumGames.keyOf(forumUrl, topicId)))
        .map(Instant::ofEpochMilli);
  }

  /** Keeps the newest post shown, unless a newer one was seen before. */
  public static void markSeen(final String forumUrl, final int topicId, final Instant newest) {
    final Map<String, Long> seen = read();
    final String key = OldForumGames.keyOf(forumUrl, topicId);
    if (seen.getOrDefault(key, Long.MIN_VALUE) >= newest.toEpochMilli()) {
      return;
    }
    seen.put(key, newest.toEpochMilli());
    String joined = join(seen);
    // one preference value: the games seen longest ago go first
    while (joined.length() > Preferences.MAX_VALUE_LENGTH) {
      seen.entrySet().stream()
          .min(Map.Entry.comparingByValue())
          .map(Map.Entry::getKey)
          .ifPresent(seen::remove);
      joined = join(seen);
    }
    ClientSetting.forumPostsSeen.setValueAndFlush(joined);
  }

  private static Map<String, Long> read() {
    final Map<String, Long> seen = new HashMap<>();
    Arrays.stream(ClientSetting.forumPostsSeen.getValue().orElse("").split(" "))
        .map(entry -> entry.split("=", 2))
        .filter(parts -> parts.length == 2 && parts[1].matches("\\d{1,18}"))
        .forEach(parts -> seen.put(parts[0], Long.parseLong(parts[1])));
    return seen;
  }

  private static String join(final Map<String, Long> seen) {
    return seen.entrySet().stream()
        .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
        .map(entry -> entry.getKey() + "=" + entry.getValue())
        .collect(Collectors.joining(" "));
  }
}
