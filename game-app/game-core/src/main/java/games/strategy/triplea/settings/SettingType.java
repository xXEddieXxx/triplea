package games.strategy.triplea.settings;

import lombok.AllArgsConstructor;

/**
 * Groups settings together. Each {@link GameSettingUiBinding} will map to one {@code SettingType}.
 * Each {@code SettingType} will have its own section in the SettingsWindow, in this order, and
 * every ui binding with that type will be shown in that section. The sections are the ones players
 * know from the settings of other games, named after what a player wants to change, not after the
 * part of the engine that uses a setting.
 */
@AllArgsConstructor
public enum SettingType {
  /** How a game plays: messages, battles, the computer players. */
  GAMEPLAY("Gameplay", "Messages, battles and how fast the computer plays."),

  /** How TripleA looks: theme, text size, window. */
  GRAPHICS("Graphics", "Theme, text size and the window."),

  AUDIO("Audio", "Sound effects and which sounds play."),

  /** Mouse and keyboard on the map: zoom and scrolling. */
  CONTROLS("Controls", "How the map zooms and scrolls."),

  ONLINE("Online & multiplayer", "Connection, chat and the accounts for email and forum games."),

  ADVANCED("Advanced", "Folders, and switches for testing new features.");

  /** The name of the section in the navigation and above its settings. */
  final String title;

  /** One line below the title saying what the section holds. */
  final String intro;
}
