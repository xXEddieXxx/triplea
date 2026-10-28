package games.strategy.engine.lobby.client.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import games.strategy.engine.lobby.client.login.LoginResult;
import games.strategy.engine.lobby.client.ui.LobbyGamePanel.GameListModel;
import games.strategy.engine.lobby.client.ui.LobbyGameTableModel.Column;
import java.awt.Container;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JLabel;
import javax.swing.JList;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.triplea.domain.data.ApiKey;
import org.triplea.domain.data.UserName;

@ExtendWith(MockitoExtension.class)
final class LobbyGamePanelTest {
  @Mock private LobbyGameTableModel tableModel;
  // host and status of each game, in the order of the table model; the host is the game id too
  private List<List<String>> games = List.of();

  @DisplayName("Games waiting for players come first, then the others, each by host")
  @Test
  void openGamesFirst() {
    givenGames(
        List.of(
            List.of("Zed", "In Progress"),
            List.of("Bot_2", "Available"),
            List.of("anna", "Waiting")));

    final GameListModel list = new GameListModel(tableModel);

    assertThat(hosts(list))
        .as("a player looks for a game to join, not for running games")
        .containsExactly("anna", "Bot_2", "Zed");
  }

  @DisplayName("Available and waiting games are open, others are not")
  @Test
  void openStatus() {
    assertThat(
            List.of(
                LobbyGamePanel.isOpen("Available"),
                LobbyGamePanel.isOpen("Waiting For Players"),
                LobbyGamePanel.isOpen("In Progress")))
        .containsExactly(true, true, false);
  }

  @DisplayName("The selected game stays selected when the games change and it moves")
  @Test
  void selectionSurvivesUpdates() {
    givenGames(List.of(List.of("anna", "Available"), List.of("bob", "Available")));
    final LobbyGamePanel panel = givenPanel();
    panel.getGameList().setSelectedIndex(1);

    // bob's game now waits for players, anna's started: bob moves to the top
    games = List.of(List.of("anna", "In Progress"), List.of("bob", "Available"));
    panel.gamesChanged();

    assertThat(gameIdOf(panel, panel.getGameList().getSelectedIndex()))
        .as("the lobby updates its games all the time, the player's choice must stay")
        .isEqualTo("bob");
  }

  @DisplayName("When the selected game is gone, nothing is selected")
  @Test
  void selectionOfGoneGameIsCleared() {
    givenGames(List.of(List.of("anna", "Available"), List.of("bob", "Available")));
    final LobbyGamePanel panel = givenPanel();
    panel.getGameList().setSelectedIndex(1);

    games = List.of(List.of("anna", "Available"));
    panel.gamesChanged();

    assertThat(panel.getGameList().getSelectedIndex())
        .as("Join game must not join another game")
        .isEqualTo(-1);
  }

  @DisplayName("Moderators see on a game card when it started, other players do not")
  @Test
  void startedOnlyForModerators() {
    givenGames(List.of(List.of("anna", "Available")));

    final String moderatorSees = detailsOf(givenModeratorPanel(), 0).getText();
    final String playerSees = detailsOf(givenPanel(), 0).getText();

    assertThat(List.of(moderatorSees.contains("Started 10:30"), playerSees.contains("Started")))
        .as("the start time was a moderator column of the old table")
        .containsExactly(true, false);
  }

  @DisplayName("The comments of a game hosted by a bot are in italic")
  @Test
  void botCommentsInItalic() {
    givenGames(
        List.of(
            List.of("Bot_1", "Available", "automated_host"), List.of("anna", "Available", "hi")));
    final LobbyGamePanel panel = givenPanel();

    // sorted by host: anna, then Bot_1
    assertThat(List.of(commentsOf(panel, 0).isItalic(), commentsOf(panel, 1).isItalic()))
        .as("bots and people host games differently, like in the old table")
        .containsExactly(false, true);
  }

  /** The labels of a rendered card: title, details, comments; on the right status, players. */
  private static JLabel cardLabel(final LobbyGamePanel panel, final int index, final int label) {
    final JList<Integer> list = panel.getGameList();
    final Container card =
        (Container)
            list.getCellRenderer()
                .getListCellRendererComponent(
                    list, list.getModel().getElementAt(index), index, false, false);
    return (JLabel) ((Container) card.getComponent(0)).getComponent(label);
  }

  private static JLabel detailsOf(final LobbyGamePanel panel, final int index) {
    return cardLabel(panel, index, 1);
  }

  private static Font commentsOf(final LobbyGamePanel panel, final int index) {
    return cardLabel(panel, index, 2).getFont();
  }

  /** The games as a guest sees them. */
  private LobbyGamePanel givenPanel() {
    return givenPanel(
        LoginResult.builder()
            .username(UserName.of("guest"))
            .apiKey(ApiKey.of("key"))
            .anonymousLogin(true)
            .build());
  }

  private LobbyGamePanel givenModeratorPanel() {
    return givenPanel(
        LoginResult.builder()
            .username(UserName.of("moderator"))
            .apiKey(ApiKey.of("key"))
            .moderator(true)
            .build());
  }

  private LobbyGamePanel givenPanel(final LoginResult loginResult) {
    return new LobbyGamePanel(null, loginResult, tableModel, mock(LobbyGameListingModel.class));
  }

  private void givenGames(final List<List<String>> hostAndStatus) {
    games = hostAndStatus;
    when(tableModel.getRowCount()).thenAnswer(call -> games.size());
    when(tableModel.getColumnIndex(any()))
        .thenAnswer(call -> ((Column) call.getArgument(0)).ordinal());
    when(tableModel.getValueAt(anyInt(), anyInt()))
        .thenAnswer(
            call -> {
              final List<String> game = games.get(call.getArgument(0));
              final int column = call.getArgument(1);
              if (column == Column.Host.ordinal()) {
                return game.get(0);
              } else if (column == Column.Status.ordinal()) {
                return game.get(1);
              } else if (column == Column.Comments.ordinal()) {
                return game.size() > 2 ? game.get(2) : "";
              }
              return column == Column.Started.ordinal() ? "10:30" : "";
            });
    lenient()
        .when(tableModel.getGameIdForRow(anyInt()))
        .thenAnswer(call -> games.get(call.getArgument(0)).get(0));
  }

  private String gameIdOf(final LobbyGamePanel panel, final int index) {
    return tableModel.getGameIdForRow(panel.getGameList().getModel().getElementAt(index));
  }

  private List<String> hosts(final GameListModel list) {
    final List<String> hosts = new ArrayList<>();
    for (int i = 0; i < list.getSize(); i++) {
      hosts.add(list.text(list.getElementAt(i), Column.Host));
    }
    return hosts;
  }
}
