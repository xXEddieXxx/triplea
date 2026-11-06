package games.strategy.engine.framework.startup.ui;

import static org.assertj.core.api.Assertions.assertThat;

import games.strategy.engine.data.GameData;
import games.strategy.engine.data.GamePlayer;
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
import java.util.stream.Stream;
import javax.swing.AbstractButton;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sonatype.goodies.prefs.memory.MemoryPreferences;
import org.triplea.swing.IntTextField;

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

  @DisplayName("'Play Axis' keeps the AI the player chose for the table, eg Fast AI")
  @Test
  void playAllianceKeepsTheChosenComputer() {
    final String fast = PlayerTypes.FAST_AI.getLabel();
    // as "Set all to: Fast AI" does
    playerRows.forEach(row -> row.setPlayerType(fast));

    clickButton(playButtonTitle("Axis"));

    assertThat(playerTypesByName())
        .as("the other nations stay with the Fast AI instead of jumping to the hard AI")
        .containsEntry("Germans", HUMAN)
        .containsEntry("Russians", fast)
        .containsEntry("Americans", fast);
  }

  @DisplayName("'Play Axis' leaves an AI picked for a single nation as it is")
  @Test
  void playAllianceKeepsSingleNationAis() {
    final String fast = PlayerTypes.FAST_AI.getLabel();
    final String weak = PlayerTypes.WEAK_AI.getLabel();
    playerRows.forEach(
        row -> row.setPlayerType(row.getPlayerName().equals("British") ? fast : weak));

    clickButton(playButtonTitle("Axis"));

    assertThat(playerTypesByName())
        .as("each computer nation keeps the AI chosen for it")
        .containsEntry("British", fast)
        .containsEntry("Russians", weak);
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

  @DisplayName(
      "More than five alliances are a list; choosing one sets the players up like a button")
  @Test
  void manyAlliancesAreAList() {
    final GameData game = givenRevisedWithSixAlliances();
    final List<PlayerSelectorRow> rows = new ArrayList<>();
    final JPanel panel = new JPanel();
    new LocalSetupPanel(new GameSelectorModel())
        .layoutPlayerComponentsWithQuickSetup(panel, rows, game);
    final JComboBox<?> list = findAllianceList(panel);

    list.setSelectedItem(playButtonTitle("Axis"));

    assertThat(findButton(panel, playButtonTitle("Axis")))
        .as("six buttons in a row would grow wider than the screen")
        .isEmpty();
    assertThat(rows.stream().filter(row -> row.getPlayerType().getLabel().equals(HUMAN)))
        .as("the Axis nations to the player, the others to the computer")
        .extracting(PlayerSelectorRow::getPlayerName)
        .containsExactlyInAnyOrder("Germans", "Japanese");
  }

  /** Revised's Axis and Allies, plus four alliances of two nations each. */
  private static GameData givenRevisedWithSixAlliances() {
    final GameData game = TestMapGameData.REVISED.getGameData();
    final List<GamePlayer> players = game.getPlayerList().getPlayers();
    for (int i = 1; i <= 4; i++) {
      game.getAllianceTracker().addToAlliance(players.get(i - 1), "Pact " + i);
      game.getAllianceTracker().addToAlliance(players.get(i), "Pact " + i);
    }
    return game;
  }

  /** The list whose first entry asks to choose an alliance. */
  private static JComboBox<?> findAllianceList(final Container container) {
    final String prompt =
        I18nEngineFramework.get().getText("startup.SetupPanel.quickSetup.ChooseAlliance.Lbl");
    return componentsIn(container)
        .filter(JComboBox.class::isInstance)
        .map(component -> (JComboBox<?>) component)
        .filter(list -> list.getItemCount() > 0 && prompt.equals(list.getItemAt(0)))
        .findFirst()
        .orElseThrow(() -> new AssertionError("No alliance list"));
  }

  private static Stream<Component> componentsIn(final Container container) {
    return Arrays.stream(container.getComponents())
        .flatMap(
            component ->
                component instanceof Container inner
                    ? Stream.concat(Stream.of(component), componentsIn(inner))
                    : Stream.of(component));
  }

  @DisplayName("Resource Modifiers in the game options shows the income fields next to each nation")
  @Test
  void resourceModifiersShowNextToEachNation() {
    final long fieldsAtFirst = countVisibleIncomeFields();

    ClientSetting.showResourceModifiers.setValue(true);

    assertThat(fieldsAtFirst).as("the list of players stays short until asked").isZero();
    assertThat(countVisibleIncomeFields())
        .as("income and bonus for each of Revised's five nations, in their rows")
        .isEqualTo(10);
  }

  private long countVisibleIncomeFields() {
    return componentsIn(playersPanel)
        .filter(IntTextField.class::isInstance)
        .filter(Component::isVisible)
        .count();
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
