package games.strategy.triplea.settings;

import static org.assertj.core.api.Assertions.assertThat;

import games.strategy.triplea.ui.screen.UnitsDrawer.UnitFlagDrawMode;
import java.util.Arrays;
import java.util.List;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sonatype.goodies.prefs.memory.MemoryPreferences;

final class ClientSettingSwingUiBindingTest {
  @BeforeEach
  void givenClientSettings() {
    ClientSetting.setPreferences(new MemoryPreferences());
  }

  @AfterEach
  void resetClientSettingPreferences() {
    ClientSetting.resetPreferences();
  }

  @DisplayName("Every section of the settings window has settings")
  @Test
  void everySectionHasSettings() {
    final List<SettingType> usedSections =
        Arrays.stream(ClientSettingSwingUiBinding.values())
            .map(ClientSettingSwingUiBinding::getType)
            .distinct()
            .toList();

    assertThat(usedSections)
        .as("an empty section would be a tab without content")
        .containsExactlyInAnyOrder(SettingType.values());
  }

  @DisplayName("Sound and chat times, before only in the menus of a game, are in Settings")
  @Test
  void soundAndChatTimesAreSettings() {
    assertThat(List.of(sectionOf("Sound effects"), sectionOf("Show times in chat")))
        .as("players can set them before a game starts")
        .containsExactly(SettingType.AUDIO, SettingType.ONLINE);
  }

  @DisplayName("The switches of the View and Game menus are in Settings, in the fitting section")
  @Test
  void menuSwitchesAreSettings() {
    assertThat(
            List.of(
                sectionOf("Unit flags"),
                sectionOf("Relief"),
                sectionOf("Text on the map"),
                sectionOf("End of turn report"),
                sectionOf("Lock the map"),
                sectionOf("Arrow keys with Ctrl")))
        .as("before only reachable during a game, now also before one starts")
        .containsExactly(
            SettingType.GRAPHICS,
            SettingType.GRAPHICS,
            SettingType.GRAPHICS,
            SettingType.GAMEPLAY,
            SettingType.CONTROLS,
            SettingType.CONTROLS);
  }

  @DisplayName("The flags are chosen by name, and the choice is saved")
  @Test
  void flagChoiceIsSaved() {
    final SelectionComponent<JComponent> flags =
        ClientSettingSwingUiBinding.UNIT_FLAGS_BINDING.newSelectionComponent();
    final JComboBox<?> comboBox = (JComboBox<?>) flags.getUiComponent();

    comboBox.setSelectedIndex(2);
    SaveFunction.saveSettings(List.of(flags), () -> {});

    assertThat(ClientSetting.unitFlagDrawMode.getValueOrThrow())
        .as("Off, Small, Large: the third is the large flag")
        .isEqualTo(UnitFlagDrawMode.LARGE_FLAG);
  }

  @DisplayName("The sections are the ones players know from other games, in that order")
  @Test
  void sectionsAsInOtherGames() {
    assertThat(Arrays.stream(SettingType.values()).map(type -> type.title))
        .as("what to change decides the section, not the part of the engine")
        .containsExactly(
            "Gameplay", "Graphics", "Audio", "Controls", "Online & multiplayer", "Advanced");
    assertThat(
            List.of(
                sectionOf("Theme"),
                sectionOf("Text size"),
                sectionOf("Zoom speed"),
                sectionOf("Confirm my defensive dice"),
                sectionOf("Pause between moves")))
        .as("look in Graphics, map handling in Controls, battles and the computer in Gameplay")
        .containsExactly(
            SettingType.GRAPHICS,
            SettingType.GRAPHICS,
            SettingType.CONTROLS,
            SettingType.GAMEPLAY,
            SettingType.GAMEPLAY);
  }

  @DisplayName("Every section has an intro line below its title")
  @Test
  void everySectionHasIntro() {
    assertThat(Arrays.stream(SettingType.values()).map(type -> type.intro))
        .as("the intro says what the section holds")
        .allMatch(intro -> !intro.isBlank());
  }

  @DisplayName("Every group of settings has settings, so no heading stands alone")
  @Test
  void everyGroupHasSettings() {
    final List<SettingGroup> usedGroups =
        Arrays.stream(ClientSettingSwingUiBinding.values())
            .map(ClientSettingSwingUiBinding::getGroup)
            .distinct()
            .toList();

    assertThat(usedGroups)
        .as("a group without settings would be an empty heading")
        .containsExactlyInAnyOrder(SettingGroup.values());
  }

  @DisplayName("The group of a setting decides its section")
  @Test
  void groupDecidesSection() {
    assertThat(
            List.of(
                ClientSettingSwingUiBinding.SHOW_CHAT_TIMES_BINDING.getType(),
                ClientSettingSwingUiBinding.LOBBY_URI_OVERRIDE_BINDING.getType()))
        .as("chat is online, the lobby address a developer setting in Advanced")
        .containsExactly(SettingType.ONLINE, SettingType.ADVANCED);
  }

  @DisplayName("A search finds settings by title or description, ignoring case")
  @Test
  void searchMatchesTitleOrDescription() {
    final ClientSettingSwingUiBinding chatTimes =
        ClientSettingSwingUiBinding.SHOW_CHAT_TIMES_BINDING;

    assertThat(
            List.of(
                chatTimes.matches("TIMES IN"),
                chatTimes.matches("every chat message"),
                chatTimes.matches("proxy")))
        .as("title in other case, description, and a text in neither")
        .containsExactly(true, true, false);
  }

  @DisplayName("An on/off setting is a check box whose state is saved")
  @Test
  void onOffSettingIsSavedFromItsCheckBox() {
    final SelectionComponent<JComponent> soundEffects =
        ClientSettingSwingUiBinding.SOUND_EFFECTS_BINDING.newSelectionComponent();
    final JCheckBox checkBox = (JCheckBox) soundEffects.getUiComponent();

    checkBox.setSelected(false);
    SaveFunction.saveSettings(List.of(soundEffects), () -> {});

    assertThat(ClientSetting.soundEnabled.getValueOrThrow())
        .as("sound is on by default, the cleared check box turned it off")
        .isFalse();
  }

  private static SettingType sectionOf(final String title) {
    return Arrays.stream(ClientSettingSwingUiBinding.values())
        .filter(binding -> binding.getTitle().equals(title))
        .findAny()
        .map(ClientSettingSwingUiBinding::getType)
        .orElseThrow();
  }
}
