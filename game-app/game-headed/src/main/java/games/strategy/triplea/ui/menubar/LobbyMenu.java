package games.strategy.triplea.ui.menubar;

import games.strategy.engine.lobby.client.login.ChangeEmailPanel;
import games.strategy.engine.lobby.client.login.ChangePasswordPanel;
import games.strategy.engine.lobby.client.login.LoginResult;
import games.strategy.engine.lobby.moderator.toolbox.ToolBoxWindow;
import games.strategy.triplea.UrlConstants;
import games.strategy.triplea.settings.ClientSetting;
import javax.swing.JFrame;
import javax.swing.JPopupMenu;
import org.triplea.domain.data.SystemIdLoader;
import org.triplea.http.client.ClientIdentifiers;
import org.triplea.http.client.maps.admin.MapTagAdminClient;
import org.triplea.http.client.maps.listing.MapsClient;
import org.triplea.http.client.web.socket.client.connections.PlayerToLobbyConnection;
import org.triplea.sound.SoundOptions;
import org.triplea.swing.JMenuBuilder;
import org.triplea.swing.JMenuItemCheckBoxBuilder;
import org.triplea.swing.SwingComponents;
import org.triplea.swing.key.binding.KeyCode;

/**
 * The menu of the lobby, behind a button in its header: the account (not for guests), the moderator
 * toolbox (for moderators), the chat sounds and times, and help.
 */
public final class LobbyMenu {
  private LobbyMenu() {}

  public static JPopupMenu newPopupMenu(
      final JFrame frame,
      final LoginResult loginResult,
      final PlayerToLobbyConnection playerToLobbyConnection) {
    final JPopupMenu menu = new JPopupMenu();
    if (!loginResult.isAnonymousLogin()) {
      menu.add(
          new JMenuBuilder("Account", KeyCode.A)
              .addMenuItem(
                  "Update Email",
                  KeyCode.E,
                  () -> ChangeEmailPanel.promptUserForNewEmail(frame, playerToLobbyConnection))
              .addMenuItem(
                  "Update Password",
                  KeyCode.P,
                  () ->
                      ChangePasswordPanel.doPasswordChange(
                          frame,
                          playerToLobbyConnection,
                          ChangePasswordPanel.AllowCancelMode.SHOW_CANCEL_BUTTON))
              .build());
    }

    if (loginResult.isModerator()) {
      final ClientIdentifiers clientIdentifiers =
          ClientIdentifiers.builder()
              .systemId(SystemIdLoader.load().getValue())
              .apiKey(loginResult.getApiKey().getValue())
              .build();
      menu.add(
          new JMenuBuilder("Admin", KeyCode.M)
              .addMenuItem(
                  "Open Toolbox",
                  KeyCode.T,
                  () ->
                      ToolBoxWindow.showWindow(
                          frame,
                          playerToLobbyConnection.getHttpModeratorToolboxClient(),
                          MapsClient.newClient(
                              ClientSetting.lobbyUri.getValueOrThrow(), clientIdentifiers),
                          MapTagAdminClient.newClient(
                              ClientSetting.lobbyUri.getValueOrThrow(), clientIdentifiers)))
              .build());
    }

    menu.add(
        new JMenuBuilder("Sound and chat", KeyCode.S)
            .addMenuItem(SoundOptions.buildGlobalSoundSwitchMenuItem())
            .addMenuItem(SoundOptions.buildSoundOptionsMenuItem())
            .addMenuItem(
                new JMenuItemCheckBoxBuilder("Show Chat Times", KeyCode.C)
                    .bindSetting(ClientSetting.showChatTimeSettings)
                    .build())
            .build());

    menu.add(
        new JMenuBuilder("Help", KeyCode.H)
            .addMenuItem(
                "User Guide",
                KeyCode.U,
                () -> SwingComponents.newOpenUrlConfirmationDialog(frame, UrlConstants.USER_GUIDE))
            .addMenuItem(
                "View Forums",
                KeyCode.F,
                () ->
                    SwingComponents.newOpenUrlConfirmationDialog(frame, UrlConstants.TRIPLEA_FORUM))
            .addMenuItem(
                "Send Bug Report",
                KeyCode.B,
                () ->
                    SwingComponents.newOpenUrlConfirmationDialog(frame, UrlConstants.GITHUB_ISSUES))
            .build());
    return menu;
  }
}
