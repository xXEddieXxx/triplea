package games.strategy.engine.framework.startup.ui;

import static org.assertj.core.api.Assertions.assertThat;

import games.strategy.engine.framework.I18nEngineFramework;
import games.strategy.engine.framework.startup.mc.HeadedPlayerTypes;
import games.strategy.engine.framework.startup.ui.panels.main.game.selector.GameSelectorModel;
import games.strategy.triplea.settings.ClientSetting;
import games.strategy.triplea.xml.TestMapGameData;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.swing.AbstractButton;
import javax.swing.JPanel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sonatype.goodies.prefs.memory.MemoryPreferences;

final class SetupPanelTest {
  private static final String HUMAN = HeadedPlayerTypes.HUMAN_PLAYER.getLabel();
  private static final String COMPUTER = PlayerTypes.PRO_AI.getLabel();

  private final List<PlayerSelectorRow> playerRows = new ArrayList<>();
  private final JPanel playersPanel = new JPanel();

  @BeforeEach
  void givenRevisedGameWithAxisAndAllies() {
    ClientSetting.setPreferences(new MemoryPreferences());
    new LocalSetupPanel(new GameSelectorModel())
        .layoutPlayerComponentsWithQuickSetup(
            playersPanel, playerRows, TestMapGameData.REVISED.getGameData());
  }

  @AfterEach
  void resetClientSettingPreferences() {
    ClientSetting.resetPreferences();
  }

  @DisplayName("'Play Axis' gives the Axis nations to the player and the others to the computer")
  @Test
  void playAllianceAgainstTheComputer() {
    clickButton(playButtonTitle("Axis"));

    assertThat(playerTypesByName())
        .as("one click sets up a game against the AI, without going through every nation")
        .containsExactlyInAnyOrderEntriesOf(
            Map.of(
                "Germans", HUMAN,
                "Japanese", HUMAN,
                "Russians", COMPUTER,
                "British", COMPUTER,
                "Americans", COMPUTER));
  }

  @DisplayName("'Everyone human' lets all nations be played at this computer")
  @Test
  void everyoneHuman() {
    clickButton(playButtonTitle("Axis"));

    clickButton(I18nEngineFramework.get().getText("startup.SetupPanel.quickSetup.HotSeat.Lbl"));

    assertThat(playerTypesByName().values()).as("hot seat: no computer player").containsOnly(HUMAN);
  }

  @DisplayName(
      "Single-nation alliances like neutrals get no quick button when there are real sides")
  @Test
  void quickButtonsOnlyForSides() {
    final JPanel global1940Panel = new JPanel();
    new LocalSetupPanel(new GameSelectorModel())
        .layoutPlayerComponentsWithQuickSetup(
            global1940Panel, new ArrayList<>(), TestMapGameData.GLOBAL1940.getGameData());

    assertThat(findButton(global1940Panel, playButtonTitle("Axis")))
        .as("Axis and Allies are the sides a player chooses from")
        .isPresent();
    assertThat(findButton(global1940Panel, playButtonTitle("Neutral_True")))
        .as("eight buttons would not fit; nobody plays the neutrals")
        .isEmpty();
  }

  @DisplayName("Play by email or forum shows the player table without the quick setup")
  @Test
  void noQuickSetupForPlayByEmailOrForum() {
    final JPanel pbemPanel = new JPanel();
    new LocalSetupPanel(new GameSelectorModel())
        .layoutPlayerComponents(
            pbemPanel, new ArrayList<>(), TestMapGameData.REVISED.getGameData());

    assertThat(findButton(pbemPanel, playButtonTitle("Axis")))
        .as("the quick setup gives the other nations to the computer of this player")
        .isEmpty();
  }

  private static String playButtonTitle(final String alliance) {
    return I18nEngineFramework.get().getText("startup.SetupPanel.quickSetup.PlayAs.Lbl", alliance);
  }

  private void clickButton(final String title) {
    findButton(playersPanel, title)
        .orElseThrow(() -> new AssertionError("No button " + title))
        .doClick();
  }

  private static Optional<AbstractButton> findButton(
      final Container container, final String title) {
    return Arrays.stream(container.getComponents())
        .flatMap(component -> findButtonIn(component, title).stream())
        .findFirst();
  }

  private static Optional<AbstractButton> findButtonIn(
      final Component component, final String title) {
    // a quick setup card shows its title in a label, its accessible name is the title
    if (component instanceof AbstractButton button
        && (title.equals(button.getText())
            || title.equals(button.getAccessibleContext().getAccessibleName()))) {
      return Optional.of(button);
    }
    return component instanceof Container container
        ? findButton(container, title)
        : Optional.empty();
  }

  private Map<String, String> playerTypesByName() {
    return playerRows.stream()
        .collect(
            Collectors.toMap(
                PlayerSelectorRow::getPlayerName, row -> row.getPlayerType().getLabel()));
  }
}
