package games.strategy.engine.framework.startup.ui.panels.main.game.selector;

import static org.assertj.core.api.Assertions.assertThat;

import games.strategy.engine.data.GameData;
import games.strategy.engine.data.properties.IEditableProperty;
import games.strategy.triplea.settings.ClientSetting;
import games.strategy.triplea.xml.TestMapGameData;
import java.awt.Component;
import java.awt.Container;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sonatype.goodies.prefs.memory.MemoryPreferences;

/** In game-headed although the panel is in game-core: the panel shows the logo from its assets. */
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
