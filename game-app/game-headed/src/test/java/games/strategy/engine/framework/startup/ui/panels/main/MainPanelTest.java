package games.strategy.engine.framework.startup.ui.panels.main;

import static org.assertj.core.api.Assertions.assertThat;

import games.strategy.engine.framework.I18nEngineFramework;
import games.strategy.engine.framework.startup.ui.panels.main.game.selector.GameSelectorModel;
import games.strategy.triplea.settings.ClientSetting;
import java.awt.Component;
import java.awt.Container;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import javax.swing.AbstractButton;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sonatype.goodies.prefs.memory.MemoryPreferences;

final class MainPanelTest {
  private final String startGame = text("startup.MainPanel.btn.Play.Lbl");
  // the accessible name and tool tip of the icon-only link back
  private final String backToMainMenu = "Back to Main menu";
  private final String quit = text("startup.MainPanel.btn.Quit.Lbl");
  // the game selection's button to choose another game
  private final String changeGame = "Change Game...";

  private HeadedServerSetupModel model;
  private MainPanel mainPanel;

  @BeforeEach
  void givenMainPanel() {
    ClientSetting.setPreferences(new MemoryPreferences());
    final GameSelectorModel gameSelectorModel = new GameSelectorModel();
    model = new HeadedServerSetupModel(gameSelectorModel);
    mainPanel = new MainPanelBuilder(() -> {}).buildMainPanel(model, gameSelectorModel);
  }

  @AfterEach
  void resetClientSettingPreferences() {
    ClientSetting.resetPreferences();
  }

  @DisplayName("The main menu fills the window: no game selection and no footer buttons")
  @Test
  void mainMenuHidesGameSelectionAndFooter() {
    model.showSelectType();

    assertThat(buttonTitles(mainPanel))
        .as("choosing how to play comes before choosing a game; the window's close button quits")
        .doesNotContain(quit, startGame, backToMainMenu, changeGame);
  }

  @DisplayName("The main menu has no scroll bars, a setup screen scrolls")
  @Test
  void onlySetupScreensScroll() {
    model.showSelectType();
    final boolean menuScrolls = isInScrollPane(model.getPanel());
    model.showLocal();

    assertThat(List.of(menuScrolls, isInScrollPane(model.getPanel())))
        .as("the main menu fits the window by hiding parts; setup screens may be long")
        .containsExactly(false, true);
  }

  private static boolean isInScrollPane(final Component component) {
    return SwingUtilities.getAncestorOfClass(JScrollPane.class, component) != null;
  }

  @DisplayName("Leaving the main menu brings back the game selection and the footer")
  @Test
  void setupScreenAfterMainMenuShowsGameSelection() {
    model.showSelectType();
    model.showLocal();

    assertThat(buttonTitles(mainPanel))
        .as("the local game screen needs the game selection again")
        .contains(quit, startGame, changeGame);
  }

  @DisplayName("A setup screen has the link back at the top, Quit and Start Game in the footer")
  @Test
  void setupScreenShowsBackLinkAndStartGame() {
    model.showLocal();

    assertThat(buttonTitles(mainPanel))
        .as("the bar leaves the screen, the footer keeps only the ways forward")
        .contains(backToMainMenu, quit, startGame)
        .doesNotContain("Cancel");
    assertThat(mainPanel.getScreenTitle())
        .as("the bar and the links back of pages opened here name the screen")
        .isEqualTo(text("startup.SetupPanelModel.btn.StartLocalGame.Lbl"));
  }

  @DisplayName("The link back of a setup screen cancels it and shows the main menu")
  @Test
  void backLinkReturnsToMainMenu() {
    model.showLocal();

    findButton(mainPanel, backToMainMenu).doClick();

    assertThat(model.getPanel().isMainMenu())
        .as("the link does what Cancel did: the screen is cancelled for the main menu")
        .isTrue();
  }

  private static AbstractButton findButton(final Container container, final String title) {
    return Arrays.stream(container.getComponents())
        .flatMap(MainPanelTest::buttonsOf)
        .filter(button -> title.equals(button.getAccessibleContext().getAccessibleName()))
        .findFirst()
        .orElseThrow();
  }

  private static Stream<AbstractButton> buttonsOf(final Component component) {
    if (component instanceof AbstractButton button) {
      return Stream.of(button);
    }
    return component instanceof Container container
        ? Arrays.stream(container.getComponents()).flatMap(MainPanelTest::buttonsOf)
        : Stream.empty();
  }

  private static String text(final String key) {
    return I18nEngineFramework.get().getText(key);
  }

  /**
   * The titles of the buttons a player can see, so not those in hidden panels; the icon-only link
   * back is named by its tool tip, where it leads.
   */
  private static List<String> buttonTitles(final Container container) {
    return Arrays.stream(container.getComponents()).flatMap(MainPanelTest::buttonTitlesOf).toList();
  }

  private static Stream<String> buttonTitlesOf(final Component component) {
    if (!component.isVisible()) {
      return Stream.empty();
    }
    if (component instanceof AbstractButton button) {
      return Stream.ofNullable(
          button.getText().isEmpty() ? button.getToolTipText() : button.getText());
    }
    return component instanceof Container container
        ? buttonTitles(container).stream()
        : Stream.empty();
  }
}
