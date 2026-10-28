package games.strategy.triplea.settings;

import java.util.Optional;
import javax.annotation.Nullable;
import lombok.Getter;

/**
 * A group of related settings within one section of the settings window, shown below a small
 * heading, eg "Battles" in Gameplay. Groups appear in their section in the order of this enum.
 */
enum SettingGroup {
  NOTIFICATIONS("Notifications", SettingType.GAMEPLAY),
  BATTLES("Battles", SettingType.GAMEPLAY),
  BATTLE_CALCULATOR("Battle calculator", SettingType.GAMEPLAY),
  COMPUTER_PLAYERS("Computer players", SettingType.GAMEPLAY),

  LOOK("Look", SettingType.GRAPHICS),
  WINDOW("Window", SettingType.GRAPHICS),

  SOUND("Sound", SettingType.AUDIO),

  ZOOM_AND_SCROLLING("Zoom and scrolling", SettingType.CONTROLS),

  CONNECTION("Connection", SettingType.ONLINE),
  CHAT("Chat", SettingType.ONLINE),
  PLAY_BY_EMAIL_AND_FORUM("Play by email and forum", SettingType.ONLINE),

  FOLDERS("Folders", SettingType.ADVANCED),
  DEVELOPER_AND_TESTING(
      "Developer and testing",
      SettingType.ADVANCED,
      "Only change these if a developer asks you to. They can break games.");

  @Getter private final String title;
  @Getter private final SettingType type;
  private final @Nullable String note;

  SettingGroup(final String title, final SettingType type) {
    this(title, type, null);
  }

  SettingGroup(final String title, final SettingType type, final @Nullable String note) {
    this.title = title;
    this.type = type;
    this.note = note;
  }

  /** A warning shown above the settings of the group, if it has one. */
  Optional<String> getNote() {
    return Optional.ofNullable(note);
  }
}
