package games.strategy.engine.framework.startup.ui.panels.main.game.selector;

import static org.assertj.core.api.Assertions.assertThat;

import games.strategy.engine.data.GameData;
import games.strategy.engine.data.properties.IEditableProperty;
import games.strategy.engine.framework.I18nEngineFramework;
import games.strategy.triplea.settings.ClientSetting;
import games.strategy.triplea.xml.TestMapGameData;
import java.awt.Component;
import java.awt.Container;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import javax.swing.AbstractButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sonatype.goodies.prefs.memory.MemoryPreferences;

/** In game-headed although the panel is in game-core: with the game client's look, as it shows. */
final class GameSelectorPanelTest {
  private final GameSelectorModel model = new GameSelectorModel();
  private GameSelectorPanel panel;

  @BeforeEach
  void givenPanel() throws InterruptedException, InvocationTargetException {
    ClientSetting.setPreferences(new MemoryPreferences());
    panel = new GameSelectorPanel(model);
    model.addObserver(panel);
    waitForEdt();
  }

  @AfterEach
  void resetClientSettingPreferences() {
    ClientSetting.resetPreferences();
  }

  @DisplayName("Without a game the sidebar shows neither a round nor a saved game")
  @Test
  void noGameShowsNoRound() {
    assertThat(visibleTexts(panel))
        .as("the model reports '-' for the round, which is no use to show")
        .noneMatch(text -> text.startsWith("Round") || text.startsWith("Saved game"));
  }

  @DisplayName("A game with a known round shows its name and round, but no saved game")
  @Test
  void knownRoundIsShown() throws InterruptedException, InvocationTargetException {
    model.clearDataButKeepGameInfo("World War II", "3");
    waitForEdt();

    assertThat(visibleTexts(panel))
        .as("the game and its round are shown; no save game is loaded")
        .contains("World War II", "Round 3")
        .noneMatch(text -> text.startsWith("Saved game"));
  }

  @DisplayName("Reset in the game options goes back to the options of the game as it was loaded")
  @Test
  void resetRestoresLoadedOptions() {
    final GameData game = TestMapGameData.REVISED.getGameData();
    model.setGameData(game);
    final IEditableProperty<?> option = givenOnOffOption(game);
    final Object loadedValue = option.getValue();
    option.validateAndSet(!(Boolean) loadedValue);

    panel.resetGameOptions();

    assertThat(option.getValue())
        .as("the game was loaded with the model, eg by Continue in the main menu")
        .isEqualTo(loadedValue);
  }

  @DisplayName(
      "Options set behind the editors, eg defaults loaded after the map, show once laid out")
  @Test
  void optionsShowValuesSetAfterTheGameLoaded() throws Exception {
    final GameData game = TestMapGameData.REVISED.getGameData();
    model.setGameData(game);
    waitForEdt();
    final IEditableProperty<?> option = givenOnOffOption(game);
    final boolean setLater = !(Boolean) option.getValue();
    // as "Make default" values are, after the map loaded; none are saved for the test map
    option.validateAndSet(setLater);

    panel.loadDefaultOptions();
    waitForEdt();

    assertThat(checkBoxOf(panel.getOptionsPanel(), option.getName()).isSelected())
        .as("the screen shows what the game starts with")
        .isEqualTo(setLater);
  }

  @DisplayName("Resource Modifiers in the options switches the income fields of the nations on")
  @Test
  void resourceModifiersSwitchShowsTheIncomeFields() throws Exception {
    model.setGameData(TestMapGameData.REVISED.getGameData());
    waitForEdt();
    final JToggleButton resourceModifiers = givenResourceModifiersSwitch();
    final boolean offAtFirst = !resourceModifiers.isSelected();

    resourceModifiers.doClick();

    assertThat(offAtFirst).as("the list of players stays short until asked").isTrue();
    assertThat(ClientSetting.showResourceModifiers.getValueOrThrow())
        .as("the rows of the players show the fields; remembered for the next games")
        .isTrue();
  }

  private JToggleButton givenResourceModifiersSwitch() {
    final String title =
        I18nEngineFramework.get().getText("startup.SetupPanel.resourceModifiers.Lbl");
    return componentsIn(panel.getOptionsPanel())
        .filter(JToggleButton.class::isInstance)
        .map(JToggleButton.class::cast)
        .filter(toggle -> title.equals(toggle.getText()))
        .findFirst()
        .orElseThrow();
  }

  @DisplayName("Load game lets the player choose the save the way the client gives it")
  @Test
  void loadGameAsksTheChooser() throws Exception {
    final List<Component> askedFrom = new ArrayList<>();
    // a local game's screen, where the player picks the game
    model.setCanSelect(true);
    final GameSelectorPanel withChooser =
        new GameSelectorPanel(model, (parent, load) -> askedFrom.add(parent));
    waitForEdt();

    componentsIn(withChooser)
        .filter(AbstractButton.class::isInstance)
        .map(AbstractButton.class::cast)
        .filter(button -> "Load game...".equals(button.getText()))
        .findFirst()
        .orElseThrow()
        .doClick();

    assertThat(askedFrom)
        .as("eg the saved games page of the main window instead of a bare file chooser")
        .containsExactly(withChooser);
  }

  @DisplayName("A joined game's host sets its options; a bot's host is asked to change them")
  @Test
  void joinedGameTellsWhoSetsTheOptions() throws Exception {
    model.setCanSelect(false);
    model.clearDataButKeepGameInfo("World War II", "3");
    model.setIsHostHeadlessBot(true);
    waitForEdt();
    final List<String> ofBot = textsIn(panel.getOptionsPanel());
    model.setIsHostHeadlessBot(false);
    waitForEdt();

    assertThat(ofBot)
        .as("the data of a joined game is at its host only")
        .contains("The host sets the game's options.", "Change game options...");
    assertThat(textsIn(panel.getOptionsPanel()))
        .as("no button to a bot that is not there")
        .doesNotContain("Change game options...");
  }

  private static JCheckBox checkBoxOf(final Container container, final String text) {
    return componentsIn(container)
        .filter(JCheckBox.class::isInstance)
        .map(JCheckBox.class::cast)
        .filter(box -> text.equals(box.getText()))
        .findFirst()
        .orElseThrow();
  }

  private static List<String> textsIn(final Container container) {
    return componentsIn(container)
        .flatMap(
            component ->
                component instanceof JLabel label
                    ? Stream.ofNullable(label.getText())
                    : component instanceof AbstractButton button
                        ? Stream.ofNullable(button.getText())
                        : Stream.empty())
        .toList();
  }

  private static Stream<Component> componentsIn(final Container container) {
    return Arrays.stream(container.getComponents())
        .flatMap(
            component ->
                component instanceof Container inner
                    ? Stream.concat(Stream.of(component), componentsIn(inner))
                    : Stream.of(component));
  }

  private static IEditableProperty<?> givenOnOffOption(final GameData game) {
    return game.getProperties().getEditableProperties().stream()
        .filter(property -> property.getValue() instanceof Boolean)
        .findFirst()
        .orElseThrow();
  }

  /** The panel updates its labels on the EDT. */
  private static void waitForEdt() throws InterruptedException, InvocationTargetException {
    SwingUtilities.invokeAndWait(() -> {});
  }

  private static List<String> visibleTexts(final Container container) {
    return Arrays.stream(container.getComponents())
        .filter(Component::isVisible)
        .flatMap(GameSelectorPanelTest::textsOf)
        .toList();
  }

  private static Stream<String> textsOf(final Component component) {
    if (component instanceof JLabel label) {
      return Stream.ofNullable(label.getText());
    }
    return component instanceof Container container
        ? visibleTexts(container).stream()
        : Stream.empty();
  }
}
