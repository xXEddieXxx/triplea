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
  GAMEPLAY("Gameplay", "Battles, the battle calculator and how fast the computer plays."),

  /**
   * While a game runs: the map, units and messages, which the game window's settings button opens.
   */
  IN_GAME("In game", "How the map and units show while you play, and the messages you get."),

  /** How TripleA looks: theme, text size, window, map. */
  GRAPHICS("Graphics", "Theme, text size, the window and how the map looks."),

  AUDIO("Audio", "Sound effects, their volume and which sounds play."),

  /** Mouse and keyboard on the map: zoom and scrolling. */
  CONTROLS("Controls", "How the map zooms, scrolls and follows the game."),

  ONLINE("Online & multiplayer", "Connection, chat and the accounts for email and forum games."),

  ADVANCED("Advanced", "Folders, and switches for testing new features.");

  /** The name of the section in the navigation and above its settings. */
  final String title;

  /** One line below the title saying what the section holds. */
  final String intro;
}
