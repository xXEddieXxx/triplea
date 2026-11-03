package games.strategy.triplea.settings;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Moves the switches that older versions kept in preferences of their own, only reachable from the
 * game's menus, into the client settings, so the settings window shows them: a value the player
 * chose there is kept, once, and the old entry is removed.
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class LegacyPreferences {
  // the nodes of the packages of TileImageFactory and UiContext, which stored them
  private static final String TILE_IMAGES_NODE = "games/strategy/triplea/image";
  private static final String GAME_UI_NODE = "games/strategy/triplea/ui";

  static void moveToClientSettings(final Preferences root) {
    existingNode(root, TILE_IMAGES_NODE)
        .ifPresent(
            node ->
                move(
                    node,
                    Map.of(
                        "ShowRelief2", ClientSetting.showReliefImages,
                        "ShowBlends", ClientSetting.showMapBlends)));
    existingNode(root, GAME_UI_NODE)
        .ifPresent(
            node ->
                move(
                    node,
                    Map.of(
                        "ShowEndOfTurnReport", ClientSetting.showEndOfTurnReport,
                        "ShowTriggeredNotifications", ClientSetting.showTriggeredNotifications,
                        "ShowTriggeredChanceSuccessful", ClientSetting.showTriggerChanceSuccessful,
                        "ShowTriggeredChanceFailure", ClientSetting.showTriggerChanceFailure)));
  }

  private static Optional<Preferences> existingNode(final Preferences root, final String path) {
    try {
      // a missing node stays missing: node() would create it
      return root.nodeExists(path) ? Optional.of(root.node(path)) : Optional.empty();
    } catch (final BackingStoreException e) {
      // not for the player: nothing is lost, the next start tries again
      log.info("Failed to read the old settings in {}", path, e);
      return Optional.empty();
    }
  }

  /**
   * Copies the old entries of the node into their settings, saves those, and only then removes the
   * old entries; without old entries it writes nothing.
   */
  private static void move(
      final Preferences legacy, final Map<String, ? extends ClientSetting<Boolean>> settings) {
    final List<String> found =
        settings.keySet().stream().filter(key -> legacy.get(key, null) != null).toList();
    if (found.isEmpty()) {
      return;
    }
    found.forEach(key -> copy(legacy, key, settings.get(key)));
    ClientSetting.flush();
    found.forEach(legacy::remove);
    try {
      legacy.flush();
    } catch (final BackingStoreException e) {
      log.info("Failed to remove the old settings in {}", legacy.absolutePath(), e);
    }
  }

  /**
   * Takes the old value unless the setting was changed already (a setting at its default stores
   * nothing); an old value that is no boolean keeps the default, as the old readers did.
   */
  private static void copy(
      final Preferences legacy, final String key, final ClientSetting<Boolean> setting) {
    if (setting.getValue().equals(setting.getDefaultValue())) {
      setting.setValue(legacy.getBoolean(key, setting.getDefaultValue().orElse(false)));
    }
  }
}
