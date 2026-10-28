package games.strategy.engine.framework.startup.ui.panels.main;

import static org.assertj.core.api.Assertions.assertThat;

import games.strategy.engine.data.GameData;
import games.strategy.engine.framework.startup.ui.LocalSetupPanel;
import games.strategy.engine.framework.startup.ui.SetupPanel;
import games.strategy.engine.framework.startup.ui.panels.main.game.selector.GameSelectorModel;
import games.strategy.engine.framework.startup.ui.posted.game.pbem.PbemSetupPanel;
import games.strategy.engine.framework.startup.ui.posted.game.pbf.PbfSetupPanel;
import games.strategy.engine.posted.game.pbem.IEmailSender;
import games.strategy.engine.posted.game.pbf.IForumPoster;
import games.strategy.triplea.settings.ClientSetting;
import games.strategy.triplea.xml.TestMapGameData;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sonatype.goodies.prefs.memory.MemoryPreferences;

final class HeadedServerSetupModelTest {
  private final GameSelectorModel gameSelectorModel = new GameSelectorModel();

  @BeforeEach
  void givenClientSettings() {
    ClientSetting.setPreferences(new MemoryPreferences());
  }

  @AfterEach
  void resetClientSettingPreferences() {
    ClientSetting.resetPreferences();
  }

  @DisplayName("Continuing an email game opens its own screen, which keeps its email settings")
  @Test
  void emailGameContinuesOnEmailScreen() {
    final GameData save = TestMapGameData.REVISED.getGameData();
    save.getProperties().set(IEmailSender.SUBJECT, "Our game");
    final HeadedServerSetupModel model = givenModelLoading(save);

    model.continueSavedGame(Path.of("email game.tsvg"));

    assertThat(model.getPanel())
        .as("the screen for games on this computer would clear the email settings at the start")
        .isInstanceOf(PbemSetupPanel.class);
  }

  @DisplayName("Continuing any other game opens the screen for games on this computer")
  @Test
  void otherGameContinuesOnLocalScreen() {
    final HeadedServerSetupModel model = givenModelLoading(TestMapGameData.REVISED.getGameData());

    model.continueSavedGame(Path.of("my game.tsvg"));

    assertThat(model.getPanel())
        .as("a save without email or forum settings is played on this computer")
        .isInstanceOf(LocalSetupPanel.class);
  }

  @DisplayName("Continuing a forum game opens its own screen, which keeps its forum settings")
  @Test
  void forumGameContinuesOnForumScreen() {
    final GameData save = TestMapGameData.REVISED.getGameData();
    save.getProperties().set(IForumPoster.NAME, "Our forum");
    final HeadedServerSetupModel model = givenModelLoading(save);

    model.continueSavedGame(Path.of("forum game.tsvg"));

    assertThat(model.getPanel())
        .as("the screen for games on this computer would clear the forum settings at the start")
        .isInstanceOf(PbfSetupPanel.class);
  }

  @DisplayName("A save opened from outside, eg the file manager, on the main menu is continued")
  @Test
  void saveOpenedOnMainMenuIsContinued() {
    final HeadedServerSetupModel model = givenModelLoading(TestMapGameData.REVISED.getGameData());
    model.showSelectType();

    model.openSavedGame(Path.of("my game.tsvg"));

    assertThat(model.getPanel())
        .as("the main menu does not show the loaded game, its setup screen does")
        .isInstanceOf(LocalSetupPanel.class);
  }

  @DisplayName("A save opened from outside on a setup screen is loaded into that screen")
  @Test
  void saveOpenedOnSetupScreenStaysThere() {
    final GameData save = TestMapGameData.REVISED.getGameData();
    final HeadedServerSetupModel model = givenModelLoading(save);
    model.showLocal();
    final SetupPanel screen = model.getPanel();

    model.openSavedGame(Path.of("my game.tsvg"));

    assertThat(model.getPanel()).as("eg a hosted game keeps its screen").isSameAs(screen);
    assertThat(gameSelectorModel.getGameData())
        .as("the save is loaded into the screen that is open")
        .isSameAs(save);
  }

  @DisplayName("A save that does not load leaves the player on the current screen")
  @Test
  void failedLoadKeepsScreen() {
    final HeadedServerSetupModel model =
        new HeadedServerSetupModel(gameSelectorModel, save -> false);

    model.continueSavedGame(Path.of("broken.tsvg"));

    assertThat(model.getPanel())
        .as("the error dialog explains the failure; no setup screen opens behind it")
        .isNull();
  }

  private HeadedServerSetupModel givenModelLoading(final GameData save) {
    return new HeadedServerSetupModel(
        gameSelectorModel,
        file -> {
          gameSelectorModel.setGameData(save);
          return true;
        });
  }
}
