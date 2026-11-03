package games.strategy.triplea.settings;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.prefs.Preferences;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sonatype.goodies.prefs.memory.MemoryPreferences;

final class LegacyPreferencesTest extends AbstractClientSettingTestCase {
  private final Preferences root = new MemoryPreferences();

  @DisplayName("Switches turned off in the old menus stay off, and the old entries go")
  @Test
  void oldValuesAreMoved() throws Exception {
    root.node("games/strategy/triplea/image").putBoolean("ShowRelief2", false);
    root.node("games/strategy/triplea/ui").putBoolean("ShowTriggeredChanceFailure", false);
    // still in use: the skin chosen for a map
    root.node("games/strategy/triplea/ui").put("MapSkin", "Classic");

    LegacyPreferences.moveToClientSettings(root);

    assertThat(
            List.of(
                ClientSetting.showReliefImages.getSetting(),
                ClientSetting.showTriggerChanceFailure.getSetting()))
        .as("relief and failed chance rolls off as before")
        .containsExactly(false, false);
    assertThat(
            List.of(
                List.of(root.node("games/strategy/triplea/image").keys()),
                List.of(root.node("games/strategy/triplea/ui").keys())))
        .as("the moved entries are gone, so they are not moved again; entries in use stay")
        .containsExactly(List.of(), List.of("MapSkin"));
  }

  @DisplayName("A value changed in the new settings wins over the old one, which still goes")
  @Test
  void changedSettingIsKept() throws Exception {
    ClientSetting.showEndOfTurnReport.setValue(false);
    root.node("games/strategy/triplea/ui").putBoolean("ShowEndOfTurnReport", true);

    LegacyPreferences.moveToClientSettings(root);

    assertThat(ClientSetting.showEndOfTurnReport.getSetting())
        .as("the player switched it off in the settings after the old menu said on")
        .isFalse();
    assertThat(root.node("games/strategy/triplea/ui").keys()).as("the old entry is gone").isEmpty();
  }

  @DisplayName("An old value that is no boolean keeps the default, as the old menus read it")
  @Test
  void oddOldValueKeepsDefault() {
    root.node("games/strategy/triplea/image").put("ShowRelief2", "yes");

    LegacyPreferences.moveToClientSettings(root);

    assertThat(ClientSetting.showReliefImages.getSetting()).as("relief stays on").isTrue();
  }

  @DisplayName("Without old preferences nothing is created")
  @Test
  void noOldPreferences() throws Exception {
    LegacyPreferences.moveToClientSettings(root);

    assertThat(
            List.of(
                root.nodeExists("games/strategy/triplea/image"),
                root.nodeExists("games/strategy/triplea/ui")))
        .as("both old nodes stay missing")
        .containsExactly(false, false);
  }
}
