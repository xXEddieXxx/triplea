package games.strategy.engine.framework.startup.ui.panels.main.game.selector;

import static org.assertj.core.api.Assertions.assertThat;

import games.strategy.engine.data.GameData;
import games.strategy.engine.data.properties.IEditableProperty;
import games.strategy.engine.framework.I18nEngineFramework;
import games.strategy.engine.framework.startup.ui.IGamePropertiesCache;
import games.strategy.triplea.settings.ClientSetting;
import games.strategy.triplea.xml.TestMapGameData;
import java.awt.Component;
import java.awt.Container;
import java.awt.GridBagLayout;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import javax.swing.AbstractButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTextArea;
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

  @DisplayName("Without changed options the column says so at once")
  @Test
  void noChangesSayNone() throws Exception {
    model.setGameData(TestMapGameData.REVISED.getGameData());
    waitForEdt();

    assertThat(visibleTexts(panel))
        .as("an empty heading looks broken; the hint tells the rules are the map's own")
        .contains("None: the map's own rules.");
  }

  @DisplayName("The column lists the options changed from the game as it was loaded")
  @Test
  void changedOptionsAreListed() throws Exception {
    final GameData game = TestMapGameData.REVISED.getGameData();
    model.setGameData(game);
    waitForEdt();
    final List<String> asLoaded = panel.describeChanges();
    final IEditableProperty<?> option = givenOnOffOption(game);
    final boolean changed = !(Boolean) option.getValue();

    option.validateAndSet(changed);

    assertThat(asLoaded).as("the map's own rules: nothing to list").isEmpty();
    assertThat(panel.describeChanges())
        .as("the player sees at a glance what differs from the map's rules")
        .containsExactly(option.getName() + ": " + (changed ? "on" : "off"));
  }

  @DisplayName("A rule set is kept only with changed options and a name of its own")
  @Test
  void ruleSetNeedsChangesAndANewName() throws Exception {
    final List<String> kept = new ArrayList<>();
    final GameSelectorPanel withRuleSets =
        new GameSelectorPanel(model, (parent, load) -> {}, new RuleSetsInMemory(kept));
    // as the screen's panel does, so it knows the options the game was loaded with
    model.addObserver(withRuleSets);
    final GameData game = TestMapGameData.REVISED.getGameData();
    model.setGameData(game);
    waitForEdt();
    final boolean asLoaded = withRuleSets.whyNotSaved("Low luck night").isEmpty();
    final IEditableProperty<?> option = givenOnOffOption(game);
    option.validateAndSet(!(Boolean) option.getValue());
    final boolean changed = withRuleSets.whyNotSaved("Low luck night").isEmpty();

    withRuleSets.saveRuleSet("Low luck night");

    assertThat(List.of(asLoaded, changed))
        .as("the map's own rules need no set; changed ones may be kept")
        .containsExactly(false, true);
    assertThat(withRuleSets.whyNotSaved("LOW LUCK NIGHT"))
        .as("no second set of the same name, also not in other letters' case")
        .isPresent();
    assertThat(withRuleSets.whyNotSaved("Low/luck"))
        .as("a character a file name cannot have would be dropped and could meet another name")
        .isPresent();
    assertThat(kept).as("one more entry in the list").containsExactly("Low luck night");
  }

  @DisplayName("The popup lists each saved rule set to load, with a trash can to delete it")
  @Test
  void savedRuleSetsAreListed() throws Exception {
    final List<String> kept = new ArrayList<>(List.of("Classic"));
    final GameSelectorPanel withRuleSets =
        new GameSelectorPanel(model, (parent, load) -> {}, new RuleSetsInMemory(kept));
    model.setGameData(TestMapGameData.REVISED.getGameData());
    waitForEdt();
    // as the popup holds it
    final JPanel saved = new JPanel(new GridBagLayout());

    withRuleSets.listRuleSets(saved, new JPopupMenu());

    assertThat(
            componentsIn(saved)
                .filter(AbstractButton.class::isInstance)
                .map(AbstractButton.class::cast)
                .map(button -> button.getText() + "|" + button.getToolTipText())
                .toList())
        .as("the name loads the set; the icon-only button deletes it, its hint says so")
        .containsExactly("Classic|Load", "|Delete");
  }

  /** Rule sets kept in a list, so tests stay off the player's folder. */
  private record RuleSetsInMemory(List<String> names) implements IGamePropertiesCache {
    @Override
    public void saveRuleSet(final GameData gameData, final String name) {
      names.add(name);
    }

    @Override
    public List<String> getRuleSetNames(final GameData gameData) {
      return List.copyOf(names);
    }

    @Override
    public void loadRuleSetInto(final GameData gameData, final String name) {
      // the test does not load
    }

    @Override
    public void deleteRuleSet(final GameData gameData, final String name) {
      names.remove(name);
    }
  }

  @DisplayName("Resource Modifiers in the column switches the income fields of the nations on")
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
    // in the column below the card, beside whichever tab shows
    return componentsIn(panel)
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
    // eg the wrapping hints and lines of the column
    if (component instanceof JTextArea text) {
      return Stream.of(text.getText());
    }
    return component instanceof Container container
        ? visibleTexts(container).stream()
        : Stream.empty();
  }
}
