package games.strategy.engine.framework.startup.ui;

import static org.assertj.core.api.Assertions.assertThat;

import games.strategy.engine.data.GameData;
import games.strategy.engine.data.GamePlayer;
import games.strategy.engine.data.properties.NumberProperty;
import games.strategy.engine.framework.startup.mc.HeadedPlayerTypes;
import games.strategy.engine.framework.startup.ui.panels.main.game.selector.GameSelectorModel;
import games.strategy.triplea.Constants;
import games.strategy.triplea.settings.ClientSetting;
import games.strategy.triplea.xml.TestMapGameData;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sonatype.goodies.prefs.memory.MemoryPreferences;

final class PlayerSelectorRowTest {
  private static final String HUMAN = HeadedPlayerTypes.HUMAN_PLAYER.getLabel();

  @BeforeEach
  void initializeClientSettingPreferences() {
    ClientSetting.setPreferences(new MemoryPreferences());
  }

  @AfterEach
  void resetClientSettingPreferences() {
    ClientSetting.resetPreferences();
  }

  @DisplayName("A quick setup keeps a nation that does nothing by default, eg a neutral, passive")
  @Test
  void quickSetupKeepsPassiveNations() {
    final PlayerSelectorRow neutrals = givenRowOf("Neutrals", "DoesNothing");

    neutrals.setQuickSetupType(HUMAN);

    assertThat(neutrals.getPlayerType().getLabel())
        .as("the map author made this nation passive; a quick setup must not let it take turns")
        .isEqualTo(PlayerTypes.DOES_NOTHING_PLAYER_LABEL);
  }

  @DisplayName("A quick setup keeps a nation that the map gives to the computer")
  @Test
  void quickSetupKeepsComputerNations() {
    final PlayerSelectorRow minors = givenRowOf("Minor Axis", "AI");

    minors.setQuickSetupType(HUMAN);

    assertThat(minors.getPlayerType().getLabel())
        .as("'Everyone human' must not give the turns of a computer nation to the players")
        .isEqualTo(PlayerTypes.PRO_AI.getLabel());
  }

  @DisplayName("A quick setup sets the type of every other nation")
  @Test
  void quickSetupSetsOtherNations() {
    final PlayerSelectorRow germans = givenRowOf("Germans", null);

    germans.setQuickSetupType(PlayerTypes.PRO_AI.getLabel());

    assertThat(germans.getPlayerType().getLabel()).isEqualTo(PlayerTypes.PRO_AI.getLabel());
  }

  @DisplayName("A quick setup leaves a hidden nation alone, also one the map gives the computer")
  @Test
  void quickSetupLeavesHiddenNations() {
    final PlayerSelectorRow hidden = givenHiddenRowOf("Hidden Axis", "AI");

    hidden.setQuickSetupType(HUMAN);

    assertThat(hidden.getPlayerType().getLabel())
        .as("the other buttons that set all players leave hidden and disabled nations alone too")
        .isEqualTo(HUMAN);
  }

  private static PlayerSelectorRow givenRowOf(
      final String name, @Nullable final String defaultType) {
    final GameData data = TestMapGameData.REVISED.getGameData();
    return givenRowOf(new GamePlayer(name, false, false, defaultType, false, data), data);
  }

  private static PlayerSelectorRow givenHiddenRowOf(final String name, final String defaultType) {
    final GameData data = TestMapGameData.REVISED.getGameData();
    return givenRowOf(new GamePlayer(name, false, false, defaultType, true, data), data);
  }

  private static PlayerSelectorRow givenRowOf(final GamePlayer player, final GameData data) {
    final String name = player.getName();
    // the row shows the income modifiers of its nation
    data.getProperties()
        .addPlayerProperty(
            new NumberProperty(
                Constants.getPropertyNameIncomePercentageFor(player), null, 999, 0, 100));
    data.getProperties()
        .addPlayerProperty(
            new NumberProperty(Constants.getPropertyNamePuIncomeBonusFor(player), null, 999, 0, 0));
    return new PlayerSelectorRow(
        new ArrayList<>(),
        player,
        Map.of(name, HUMAN),
        List.of(),
        Map.of(name, true),
        List.of(),
        new LocalSetupPanel(new GameSelectorModel()),
        data.getProperties(),
        new PlayerTypes(HeadedPlayerTypes.getPlayerTypes()));
  }
}
