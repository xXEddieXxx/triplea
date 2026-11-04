package games.strategy.engine.posted.game.pbf;

import games.strategy.triplea.settings.ClientSetting;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

/**
 * The games by forum the player moved to Old games, eg finished ones, so lists of the player's
 * games leave them out: kept on this computer only, the forum keeps the games. A game is its
 * forum's address and its topic, as topics count per forum.
 */
@UtilityClass
public class OldForumGames {

  /** The games moved to Old games, of every forum; read once per list, not per game. */
  public static Set<String> keys() {
    return Arrays.stream(ClientSetting.oldForumGames.getValue().orElse("").split(" "))
        .filter(key -> !key.isBlank())
        .collect(Collectors.toUnmodifiableSet());
  }

  /** The game as {@link #keys} holds it. */
  public static String keyOf(final String forumUrl, final int topicId) {
    return forumUrl + "#" + topicId;
  }

  /**
   * Moves the game to Old games, or back when it is there.
   *
   * @return false when Old games holds no more, about 200 games, and nothing moved
   */
  public static boolean move(final String forumUrl, final int topicId) {
    final Set<String> old = new HashSet<>(keys());
    final String key = keyOf(forumUrl, topicId);
    if (!old.remove(key)) {
      old.add(key);
    }
    final String joined = String.join(" ", old);
    // one preference value: the setting cannot hold more
    if (joined.length() > Preferences.MAX_VALUE_LENGTH) {
      return false;
    }
    ClientSetting.oldForumGames.setValueAndFlush(joined);
    return true;
  }
}
