package games.strategy.triplea.settings;

import static games.strategy.triplea.settings.SelectionComponentFactory.booleanCheckBox;
import static games.strategy.triplea.settings.SelectionComponentFactory.button;
import static games.strategy.triplea.settings.SelectionComponentFactory.diceRollerOverrideSelection;
import static games.strategy.triplea.settings.SelectionComponentFactory.folderPath;
import static games.strategy.triplea.settings.SelectionComponentFactory.intSlider;
import static games.strategy.triplea.settings.SelectionComponentFactory.intValueRange;
import static games.strategy.triplea.settings.SelectionComponentFactory.proxySettings;
import static games.strategy.triplea.settings.SelectionComponentFactory.selectionBox;

import games.strategy.engine.framework.lookandfeel.LookAndFeel;
import games.strategy.triplea.UrlConstants;
import games.strategy.triplea.settings.lobby.LobbySelectionViewFactory;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import javax.swing.JComponent;
import javax.swing.UIManager.LookAndFeelInfo;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.triplea.sound.ClipPlayer;
import org.triplea.sound.SoundOptions;

/**
 * Binds a {@code ClientSetting} to a UI component. This is done by adding an enum element. As part
 * of that the corresponding UI component, a {@code SelectionComponent} is specified. This then
 * automatically adds the setting to the settings window, in its {@code SettingGroup} in the order
 * of this enum; the group decides the section.
 *
 * <p>UI component construction is delegated to {@code SelectionComponentFactory}.
 *
 * <p>There is a 1:n between {@code ClientSettingUiBinding} and {@code ClientSetting}, though
 * typically it will be 1:1, and not all {@code ClientSettings} will be available in the UI.
 */
@AllArgsConstructor
enum ClientSettingSwingUiBinding implements GameSettingUiBinding<JComponent> {
  LOOK_AND_FEEL_PREF_BINDING(
      "Theme",
      SettingGroup.LOOK,
      "Colours and style of all windows. Restart TripleA afterwards, also other TripleA windows"
          + " that are open, to avoid display errors.") {
    @Override
    public SelectionComponent<JComponent> newSelectionComponent() {
      final Collection<LookAndFeelInfo> lookAndFeels = LookAndFeel.getAvailableLookAndFeels();
      return selectionBox(
          ClientSetting.lookAndFeel,
          LookAndFeelInfo.class,
          lookAndFeels,
          lookAndFeelClassName ->
              lookAndFeels.stream()
                  .filter(lookAndFeel -> lookAndFeel.getClassName().equals(lookAndFeelClassName))
                  .findAny(),
          LookAndFeelInfo::getClassName,
          LookAndFeelInfo::getName);
    }
  },

  TEXT_SIZE_BINDING(
      "Text size",
      SettingGroup.LOOK,
      "How large the text of all windows is. It changes at once; a few texts of open screens keep"
          + " their size until they are opened again.") {
    @Override
    public SelectionComponent<JComponent> newSelectionComponent() {
      return selectionBox(
          ClientSetting.textSize,
          TextSize.class,
          List.of(TextSize.values()),
          Optional::of,
          textSize -> textSize,
          TextSize::getLabel);
    }
  },

  START_FULL_SCREEN_BINDING(
      "Start in full screen",
      SettingGroup.WINDOW,
      "Opens TripleA without window frame over the whole screen, from the next start on.") {
    @Override
    public SelectionComponent<JComponent> newSelectionComponent() {
      return booleanCheckBox(ClientSetting.startFullScreen);
    }
  },

  SOUND_EFFECTS_BINDING(
      "Sound effects",
      SettingGroup.SOUND,
      "Play the sounds of the game: battles, moves, chat and more.") {
    @Override
    public SelectionComponent<JComponent> newSelectionComponent() {
      return booleanCheckBox(ClientSetting.soundEnabled);
    }
  },

  SINGLE_SOUNDS_BINDING(
      "Single sounds",
      SettingGroup.SOUND,
      "Choose which sounds play, eg no sound for chat messages. Saved at once.") {
    @Override
    public SelectionComponent<JComponent> newSelectionComponent() {
      final SelectionComponent<JComponent> button =
          button("Choose sounds...", SoundOptions::showSoundOptions);
      // as in the game's menu: without an audio device there is nothing to choose
      button.getUiComponent().setEnabled(ClipPlayer.hasAudio());
      return button;
    }
  },

  NOTIFY_ALL_UNITS_MOVED(
      "Tell me when all units have moved",
      SettingGroup.NOTIFICATIONS,
      "A message once none of your units can move any more") {
    @Override
    public SelectionComponent<JComponent> newSelectionComponent() {
      return booleanCheckBox(ClientSetting.notifyAllUnitsMoved);
    }
  },

  MAP_ZOOM_FACTOR_BINDING(
      "Zoom speed",
      SettingGroup.ZOOM_AND_SCROLLING,
      "How fast the mouse wheel and the zoom keys zoom the map") {
    @Override
    public SelectionComponent<JComponent> newSelectionComponent() {
      return intSlider(ClientSetting.mapZoomFactor, 1, 100, "");
    }
  },

  WHEEL_SCROLL_AMOUNT_BINDING(
      "Mouse wheel scrolling",
      SettingGroup.ZOOM_AND_SCROLLING,
      "How far one step of the mouse wheel moves the map, in pixels") {
    @Override
    public SelectionComponent<JComponent> newSelectionComponent() {
      return intSlider(ClientSetting.wheelScrollAmount, 10, 300, PIXELS);
    }
  },

  ARROW_KEY_SCROLL_SPEED_BINDING(
      "Arrow key scrolling",
      SettingGroup.ZOOM_AND_SCROLLING,
      "How far one press of an arrow key moves the map, in pixels") {
    @Override
    public SelectionComponent<JComponent> newSelectionComponent() {
      return intSlider(ClientSetting.arrowKeyScrollSpeed, 0, 500, PIXELS);
    }
  },

  MAP_EDGE_SCROLL_SPEED_BINDING(
      "Scrolling at the map edge",
      SettingGroup.ZOOM_AND_SCROLLING,
      "How fast the map moves while the mouse is near its edge, in pixels") {
    @Override
    public SelectionComponent<JComponent> newSelectionComponent() {
      return intSlider(ClientSetting.mapEdgeScrollSpeed, 0, 300, PIXELS);
    }
  },

  MAP_EDGE_SCROLL_ZONE_SIZE_BINDING(
      "Edge zone",
      SettingGroup.ZOOM_AND_SCROLLING,
      "How close to the edge the mouse has to be for the map to move, in pixels") {
    @Override
    public SelectionComponent<JComponent> newSelectionComponent() {
      return intSlider(ClientSetting.mapEdgeScrollZoneSize, 0, 300, PIXELS);
    }
  },

  UNIT_SCROLLER_HIGHLIGHT_TERRITORY(
      "Highlight territory when cycling units",
      SettingGroup.NOTIFICATIONS,
      "While stepping through the units that can still move, also highlight their territory") {
    @Override
    public SelectionComponent<JComponent> newSelectionComponent() {
      return booleanCheckBox(ClientSetting.unitScrollerHighlightTerritory);
    }
  },

  CONFIRM_DEFENSIVE_ROLLS_BINDING(
      "Confirm my defensive dice",
      SettingGroup.BATTLES,
      "Pause the battle until you have confirmed the dice you rolled while defending") {
    @Override
    public SelectionComponent<JComponent> newSelectionComponent() {
      return booleanCheckBox(ClientSetting.confirmDefensiveRolls);
    }
  },

  CONFIRM_ENEMY_CASUALTIES_BINDING(
      "Confirm enemy casualties",
      SettingGroup.BATTLES,
      "Continue a battle only once every player has confirmed the casualties") {
    @Override
    public SelectionComponent<JComponent> newSelectionComponent() {
      return booleanCheckBox(ClientSetting.confirmEnemyCasualties);
    }
  },

  SPACE_BAR_CONFIRMS_CASUALTIES_BINDING(
      "Space bar confirms casualties",
      SettingGroup.BATTLES,
      "Confirm your choice of casualties with the space bar, not only with the button") {
    @Override
    public SelectionComponent<JComponent> newSelectionComponent() {
      return booleanCheckBox(ClientSetting.spaceBarConfirmsCasualties);
    }
  },

  SHOW_AA_FLYOVER_WARNING(
      "Warn about anti-aircraft fire",
      SettingGroup.BATTLES,
      "When a move flies over territories with anti-aircraft guns") {
    @Override
    public SelectionComponent<JComponent> newSelectionComponent() {
      return booleanCheckBox(ClientSetting.showAaFlyoverWarning);
    }
  },

  SHOW_POTENTIAL_SCRAMBLE_WARNING(
      "Warn about possible scrambles",
      SettingGroup.BATTLES,
      "When you attack a territory that planes nearby can fly in to defend") {
    @Override
    public SelectionComponent<JComponent> newSelectionComponent() {
      return booleanCheckBox(ClientSetting.showPotentialScrambleWarning);
    }
  },

  SHOW_BATTLES_WHEN_OBSERVING_BINDING(
      "Show battles when I only watch",
      SettingGroup.BATTLES,
      "Open the battle window also in games you only observe") {
    @Override
    public SelectionComponent<JComponent> newSelectionComponent() {
      return booleanCheckBox(ClientSetting.showBattlesWhenObserving);
    }
  },

  BATTLE_CALC_SIMULATION_COUNT_DICE_BINDING(
      "Battle calculator: runs in dice games",
      SettingGroup.BATTLE_CALCULATOR,
      "How many battles the calculator plays through. More is more exact, but slower.") {
    @Override
    public SelectionComponent<JComponent> newSelectionComponent() {
      return intValueRange(ClientSetting.battleCalcSimulationCountDice, 10, 100_000);
    }
  },

  BATTLE_CALC_SIMULATION_COUNT_LOW_LUCK_BINDING(
      "Battle calculator: runs in low luck games",
      SettingGroup.BATTLE_CALCULATOR,
      "How many battles the calculator plays through. More is more exact, but slower.") {
    @Override
    public SelectionComponent<JComponent> newSelectionComponent() {
      return intValueRange(ClientSetting.battleCalcSimulationCountLowLuck, 10, 100_000);
    }
  },

  AI_MOVE_PAUSE_DURATION_BINDING(
      "Pause between moves",
      SettingGroup.COMPUTER_PLAYERS,
      "Time between two moves of a computer player, in milliseconds, so you can follow them") {
    @Override
    public SelectionComponent<JComponent> newSelectionComponent() {
      return intSlider(ClientSetting.aiMovePauseDuration, 0, 3000, MILLISECONDS);
    }
  },

  AI_COMBAT_STEP_PAUSE_DURATION_BINDING(
      "Pause between battle steps",
      SettingGroup.COMPUTER_PLAYERS,
      "Time between two steps of a battle a computer player fights, in milliseconds") {
    @Override
    public SelectionComponent<JComponent> newSelectionComponent() {
      return intSlider(ClientSetting.aiCombatStepPauseDuration, 0, 3000, MILLISECONDS);
    }
  },

  PROXY_CHOICE(
      "Network proxy",
      SettingGroup.CONNECTION,
      "Only needed in some company or school networks. Used for map downloads, dice servers"
          + " and forum games.") {
    @Override
    public SelectionComponent<JComponent> newSelectionComponent() {
      return proxySettings(
          ClientSetting.proxyChoice, ClientSetting.proxyHost, ClientSetting.proxyPort);
    }
  },

  SHOW_CHAT_TIMES_BINDING(
      "Show times in chat", SettingGroup.CHAT, "Write the time in front of every chat message") {
    @Override
    public SelectionComponent<JComponent> newSelectionComponent() {
      return booleanCheckBox(ClientSetting.showChatTimeSettings);
    }
  },

  EMAIL_SETTINGS(
      "Email account",
      SettingGroup.PLAY_BY_EMAIL_AND_FORUM,
      "The mail server that sends your turns in games played by email") {
    @Override
    public SelectionComponent<JComponent> newSelectionComponent() {
      return SelectionComponentFactory.emailSettings(
          ClientSetting.emailServerHost,
          ClientSetting.emailServerPort,
          ClientSetting.emailServerSecurity,
          ClientSetting.emailUsername,
          ClientSetting.emailPassword);
    }
  },

  TRIPLEA_FORUM_SETTINGS(
      "TripleA forum",
      SettingGroup.PLAY_BY_EMAIL_AND_FORUM,
      "Your login for posting turns to the TripleA forum") {
    @Override
    public SelectionComponent<JComponent> newSelectionComponent() {
      return SelectionComponentFactory.forumPosterSettings(
          UrlConstants.TRIPLEA_FORUM,
          ClientSetting.tripleaForumUserId,
          ClientSetting.tripleaForumUsername,
          ClientSetting.tripleaForumToken);
    }
  },

  AXIS_AND_ALLIES_FORUM_SETTINGS(
      "Axis & Allies forum",
      SettingGroup.PLAY_BY_EMAIL_AND_FORUM,
      "Your login for posting turns to the Axis & Allies forum") {
    @Override
    public SelectionComponent<JComponent> newSelectionComponent() {
      return SelectionComponentFactory.forumPosterSettings(
          UrlConstants.AXIS_AND_ALLIES_FORUM,
          ClientSetting.aaForumUserId,
          ClientSetting.aaForumUsername,
          ClientSetting.aaForumToken);
    }
  },

  DICE_ROLLER_URI(
      "Dice server",
      SettingGroup.PLAY_BY_EMAIL_AND_FORUM,
      "Rolls the dice in games played by email or forum, so that nobody can cheat") {
    @Override
    public SelectionComponent<JComponent> newSelectionComponent() {
      return diceRollerOverrideSelection();
    }
  },

  SERVER_START_GAME_SYNC_WAIT_TIME_BINDING(
      "Timeout: game start",
      SettingGroup.CONNECTION,
      "When you host: how long to wait for all players to load the game, in seconds") {
    @Override
    public SelectionComponent<JComponent> newSelectionComponent() {
      return intSlider(ClientSetting.serverStartGameSyncWaitTime, 120, 1500, SECONDS);
    }
  },

  SERVER_OBSERVER_JOIN_WAIT_TIME_BINDING(
      "Timeout: players joining",
      SettingGroup.CONNECTION,
      "When you host: how long to wait for players and observers to join, in seconds") {
    @Override
    public SelectionComponent<JComponent> newSelectionComponent() {
      return intSlider(ClientSetting.serverObserverJoinWaitTime, 60, 1500, SECONDS);
    }
  },

  SAVE_GAMES_FOLDER_PATH_BINDING(
      "Saved games folder", SettingGroup.FOLDERS, "Where saved games are stored") {
    @Override
    public SelectionComponent<JComponent> newSelectionComponent() {
      return folderPath(ClientSetting.saveGamesFolderPath);
    }
  },

  USER_MAPS_FOLDER_PATH_BINDING(
      "Maps folder", SettingGroup.FOLDERS, "Where maps are downloaded to and looked for") {
    @Override
    public SelectionComponent<JComponent> newSelectionComponent() {
      return folderPath(ClientSetting.mapFolderOverride);
    }
  },

  SHOW_BETA_FEATURES_BINDING(
      "Beta features",
      SettingGroup.DEVELOPER_AND_TESTING,
      "For testing: show game features that are still in development and may not work yet."
          + " Restart TripleA to turn them fully on or off.") {
    @Override
    public SelectionComponent<JComponent> newSelectionComponent() {
      return booleanCheckBox(ClientSetting.showBetaFeatures);
    }
  },

  SHOW_SERIALIZE_FEATURES_BINDING(
      "New save file format",
      SettingGroup.DEVELOPER_AND_TESTING,
      "For testing: use the new serialization, which is still in development and may break saved"
          + " games and network games. All players of a game need the same value. Restart TripleA"
          + " to turn it fully on or off.") {
    @Override
    public SelectionComponent<JComponent> newSelectionComponent() {
      return booleanCheckBox(ClientSetting.showSerializeFeatures);
    }
  },

  USE_WEBSOCKET_NETWORK(
      "Websocket network",
      SettingGroup.DEVELOPER_AND_TESTING,
      "For testing: use the network code that is still in development") {
    @Override
    public SelectionComponent<JComponent> newSelectionComponent() {
      return booleanCheckBox(ClientSetting.useWebsocketNetwork);
    }
  },

  DO_NOT_REDRAW_MAP(
      "Keep the map after a new technology",
      SettingGroup.DEVELOPER_AND_TESTING,
      "For testing: do not redraw the whole map when a technology is discovered. This removes the"
          + " flash you see then.") {
    @Override
    public SelectionComponent<JComponent> newSelectionComponent() {
      return booleanCheckBox(ClientSetting.doNotRedrawMap);
    }
  },

  LOBBY_URI_OVERRIDE_BINDING(
      "Lobby address",
      SettingGroup.DEVELOPER_AND_TESTING,
      "For testing: connect to another lobby server") {
    @Override
    public SelectionComponent<JComponent> newSelectionComponent() {
      return LobbySelectionViewFactory.build();
    }
  };

  private static final String PIXELS = "px";
  private static final String MILLISECONDS = "ms";
  private static final String SECONDS = "s";

  @Getter(onMethod_ = {@Override})
  private final String title;

  @Getter private final SettingGroup group;

  @Getter private final String description;

  @Override
  public SettingType getType() {
    return group.getType();
  }

  /** Whether the search text is part of the title or the description, ignoring case. */
  boolean matches(final String searchText) {
    final String text = searchText.strip().toLowerCase(Locale.ROOT);
    return title.toLowerCase(Locale.ROOT).contains(text)
        || description.toLowerCase(Locale.ROOT).contains(text);
  }
}
