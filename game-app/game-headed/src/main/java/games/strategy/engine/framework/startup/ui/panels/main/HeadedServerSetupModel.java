package games.strategy.engine.framework.startup.ui.panels.main;

import static games.strategy.engine.framework.CliProperties.SERVER_PASSWORD;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import games.strategy.engine.data.GameData;
import games.strategy.engine.framework.startup.login.ClientLoginValidator;
import games.strategy.engine.framework.startup.mc.ClientModel;
import games.strategy.engine.framework.startup.mc.HeadedLaunchAction;
import games.strategy.engine.framework.startup.mc.HeadedPlayerTypes;
import games.strategy.engine.framework.startup.mc.ServerModel;
import games.strategy.engine.framework.startup.ui.ClientSetupPanel;
import games.strategy.engine.framework.startup.ui.LocalSetupPanel;
import games.strategy.engine.framework.startup.ui.MetaSetupPanel;
import games.strategy.engine.framework.startup.ui.ServerSetupPanel;
import games.strategy.engine.framework.startup.ui.SetupPanel;
import games.strategy.engine.framework.startup.ui.panels.main.game.selector.GameSelectorModel;
import games.strategy.engine.framework.startup.ui.panels.main.game.selector.SavedGameLoader;
import games.strategy.engine.framework.startup.ui.posted.game.pbem.PbemSetupPanel;
import games.strategy.engine.framework.startup.ui.posted.game.pbf.PbfSetupPanel;
import games.strategy.engine.lobby.client.login.LobbyLogin;
import games.strategy.engine.lobby.client.login.LoginMode;
import games.strategy.engine.lobby.client.login.LoginResult;
import games.strategy.engine.lobby.client.ui.LobbyModel;
import games.strategy.engine.lobby.client.ui.LobbyPanel;
import games.strategy.engine.posted.game.pbem.IEmailSender;
import games.strategy.engine.posted.game.pbf.IForumPoster;
import java.awt.Dimension;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import lombok.Getter;
import lombok.Setter;
import org.triplea.game.client.HeadedGameRunner;
import org.triplea.swing.SwingComponents;

/** This class provides a way to switch between different ISetupPanel displays. */
public class HeadedServerSetupModel {
  @Getter protected final GameSelectorModel gameSelectorModel;
  // loads a save into the game selector and says whether it loaded
  private final Predicate<Path> saveGameLoader;
  @Getter protected SetupPanel panel = null;

  @Setter private Consumer<SetupPanel> panelChangeListener;
  @Getter @Setter private JFrame ui;

  public HeadedServerSetupModel(final GameSelectorModel gameSelectorModel) {
    this(gameSelectorModel, save -> SavedGameLoader.load(gameSelectorModel, save));
  }

  @VisibleForTesting
  HeadedServerSetupModel(
      final GameSelectorModel gameSelectorModel, final Predicate<Path> saveGameLoader) {
    this.gameSelectorModel = gameSelectorModel;
    this.saveGameLoader = saveGameLoader;
  }

  public void showSelectType() {
    setGameTypePanel(new MetaSetupPanel(this));
  }

  /**
   * Opens a save from outside the client, eg from the file manager: on the main menu, which does
   * not show the selected game, like Continue; on a setup screen into that screen.
   */
  public void openSavedGame(final Path save) {
    // the main menu and the lobby show no game selection: continue the save on its screen
    if (panel == null || panel.isFullWindow()) {
      // the save would replace the lobby and end its connection: only if the player agrees
      if (panel != null
          && !panel.isMainMenu()
          && JOptionPane.showConfirmDialog(
                  ui,
                  "Opening the saved game leaves the lobby. Open it anyway?",
                  "Leave the lobby?",
                  JOptionPane.OK_CANCEL_OPTION)
              != JOptionPane.OK_OPTION) {
        return;
      }
      continueSavedGame(save);
    } else {
      saveGameLoader.test(save);
    }
  }

  /**
   * Continues a saved game on its setup screen: an email or forum game on the screen of its kind,
   * which keeps its posting settings, any other game on the screen for games on this computer. If
   * the save does not load, the current screen stays.
   */
  public void continueSavedGame(final Path save) {
    if (!saveGameLoader.test(save)) {
      return;
    }
    final GameData data = gameSelectorModel.getGameData();
    if (data != null && data.getProperties().get(IForumPoster.NAME) != null) {
      showPbf();
    } else if (data != null && data.getProperties().get(IEmailSender.SUBJECT) != null) {
      showPbem();
    } else {
      showLocal();
    }
  }

  public void showLocal() {
    setGameTypePanel(new LocalSetupPanel(gameSelectorModel));
  }

  public void showPbf() {
    setGameTypePanel(new PbfSetupPanel(gameSelectorModel));
  }

  public void showPbem() {
    setGameTypePanel(new PbemSetupPanel(gameSelectorModel));
  }

  /**
   * Starts the game server and then displays the game start screen, awaiting remote game clients.
   */
  public ServerModel showServer() {
    final ServerModel serverModel = new ServerModel(gameSelectorModel, new HeadedLaunchAction(ui));
    if (serverModel.initialize()) {
      onServerMessengerCreated(serverModel);
    }
    return serverModel;
  }

  private void onServerMessengerCreated(final ServerModel serverModel) {

    serverModel
        .getMessenger()
        .setLoginValidator(
            ClientLoginValidator.builder()
                .password(System.getProperty(SERVER_PASSWORD))
                .serverMessenger(serverModel.getMessenger())
                .build());

    SwingUtilities.invokeLater(
        () -> {
          setGameTypePanel(new ServerSetupPanel(serverModel, gameSelectorModel));

          // for whatever reason, the server window is showing very, very small, causing the nation
          // info to be cut and requiring scroll bars
          final int x = Math.max(ui.getPreferredSize().width, 800);
          final int y = Math.max(ui.getPreferredSize().height, 660);
          ui.setPreferredSize(new Dimension(x, y));
          ui.setSize(new Dimension(x, y));
        });
  }

  /**
   * A method that establishes a connection to a remote game and displays the game start screen
   * after the connection was successfully established.
   */
  public void showClient() {
    Preconditions.checkState(!SwingUtilities.isEventDispatchThread());
    final ClientModel model =
        new ClientModel(
            gameSelectorModel,
            this::showSelectType,
            new HeadedLaunchAction(ui),
            HeadedGameRunner::showMainFrame,
            HeadedGameRunner::clientLeftGame,
            HeadedPlayerTypes.CLIENT_PLAYER);
    if (model.createClientMessenger(ui)) {
      SwingUtilities.invokeLater(() -> setGameTypePanel(new ClientSetupPanel(model)));
    } else {
      model.cancel();
    }
  }

  private void setGameTypePanel(final SetupPanel panel) {
    if (this.panel != null) {
      this.panel.cancel();
    }
    this.panel = panel;

    Optional.ofNullable(panelChangeListener).ifPresent(listener -> listener.accept(panel));
  }

  /**
   * Executes a login sequence prompting the user for their lobby username+password and sends it to
   * server. If successful the user is presented with the lobby frame. Failure cases are handled and
   * user is presented with another try, or they can abort. In the abort case this method is a
   * no-op.
   */
  public void login() {
    new LobbyLogin(ui).promptLogin(LoginMode.REGISTRATION_NOT_REQUIRED).ifPresent(this::showLobby);
  }

  /** Shows the lobby in the main window; leaving it shows the main menu again. */
  private void showLobby(final LoginResult loginResult) {
    final var lobbyModel =
        new LobbyModel(
            loginResult,
            error -> SwingComponents.showError(null, "Error communicating with lobby", error));
    setGameTypePanel(new LobbyPanel(lobbyModel, ui, this::showSelectType));
  }
}
