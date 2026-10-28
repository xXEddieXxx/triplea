package games.strategy.engine.lobby.client.ui;

import games.strategy.engine.chat.ChatMessagePanel;
import games.strategy.engine.chat.ChatMessagePanel.ChatSoundProfile;
import games.strategy.engine.chat.ChatPlayerPanel;
import games.strategy.engine.framework.lookandfeel.LookAndFeel;
import games.strategy.engine.framework.startup.launcher.ILauncher;
import games.strategy.engine.framework.startup.ui.MetaSetupPanel;
import games.strategy.engine.framework.startup.ui.SetupPanel;
import games.strategy.engine.framework.ui.BackBar;
import games.strategy.triplea.ui.menubar.LobbyMenu;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagLayout;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;
import org.triplea.http.client.web.socket.WebSocket;
import org.triplea.sound.ClipPlayer;
import org.triplea.swing.JButtonBuilder;
import org.triplea.swing.jpanel.GridBagConstraintsBuilder;

/**
 * The lobby, as a screen of the main window: the games on the left, the players online and the chat
 * on the right; above them the {@link BackBar} of every screen with its link back to the main menu
 * (which leaves the lobby) and the title, on its right the name the player is signed in with, the
 * lobby menu, Join game and Host game. Hosting or joining starts the game in a game window of its
 * own, the lobby stays open. Leaving the lobby, or losing the connection, goes back to the main
 * menu.
 */
public class LobbyPanel extends SetupPanel {
  private static final long serialVersionUID = 6203719273940826121L;

  /** Width of the column with the players and the chat. */
  private static final int SIDE_COLUMN_WIDTH = 360;

  private static final String TITLE = "Lobby";

  private final transient LobbyModel lobbyModel;
  private final transient LobbySession session;
  private final ChatMessagePanel chatMessagePanel;

  /**
   * Shows the lobby of the model; {@code leaveLobby} is called once the lobby is left or the
   * connection lost, eg to show the main menu.
   */
  public LobbyPanel(final LobbyModel lobbyModel, final JFrame frame, final Runnable leaveLobby) {
    this.lobbyModel = lobbyModel;
    final var loginResult = lobbyModel.getLoginResult();
    final var connection = lobbyModel.getConnection();
    final var gameListingModel = lobbyModel.getGameListingModel();

    // Disconnect & Exit of the overlay leaves the lobby
    final ReconnectOverlay reconnectOverlay = new ReconnectOverlay(frame, this::leave);
    session =
        new LobbySession(
            () -> {
              reconnectOverlay.dismiss();
              lobbyModel.shutdown();
            },
            leaveLobby);
    connection.addConnectionClosedListener(() -> SwingUtilities.invokeLater(session::leave));
    connection.addReconnectionListener(
        new WebSocket.ReconnectionHandler() {
          @Override
          public void onReconnecting(final int attempt) {
            reconnectOverlay.show(attempt);
          }

          @Override
          public void onReconnected() {
            gameListingModel.refresh();
            reconnectOverlay.dismiss();
          }
        });

    chatMessagePanel =
        new ChatMessagePanel(lobbyModel.getChat(), ChatSoundProfile.LOBBY, new ClipPlayer());
    // the lobby writes line breaks of its welcome as "\n"
    Optional.ofNullable(loginResult.getLoginMessage())
        .map(message -> message.replace("\\n", "\n"))
        .ifPresent(chatMessagePanel::addServerMessage);
    final ChatPlayerPanel players = new ChatPlayerPanel(lobbyModel.getChat());
    players.addActionFactory(
        clickedChatter ->
            LobbyPlayerActions.buildFor(frame, loginResult, clickedChatter, connection));

    final LobbyGamePanel games =
        new LobbyGamePanel(
            frame,
            loginResult,
            new LobbyGameTableModel(loginResult.isModerator(), gameListingModel),
            gameListingModel);

    // the players above, the chat below them in the corner
    final JSplitPane sideColumn =
        new JSplitPane(
            JSplitPane.VERTICAL_SPLIT,
            newSection("Players online", players),
            newSection("Chat", chatMessagePanel));
    sideColumn.setResizeWeight(0.35);
    sideColumn.setBorder(BorderFactory.createEmptyBorder());
    sideColumn.setPreferredSize(new Dimension(SIDE_COLUMN_WIDTH, 0));

    setLayout(new BorderLayout());
    // the bar reaches the window edges, as on every screen
    add(newBar(frame, games), BorderLayout.NORTH);
    final JPanel body = new JPanel(new BorderLayout(16, 0));
    body.setBorder(BorderFactory.createEmptyBorder(12, 20, 12, 20));
    body.add(newSection("Games", games), BorderLayout.CENTER);
    body.add(sideColumn, BorderLayout.EAST);
    add(body, BorderLayout.CENTER);
  }

  /**
   * The bar of every screen: the link back to the main menu and the title on the left, on the right
   * the name the player is signed in with, the lobby menu, Join game and Host game.
   */
  private JComponent newBar(final JFrame frame, final LobbyGamePanel games) {
    final var loginResult = lobbyModel.getLoginResult();
    final JLabel signedInAs =
        BackBar.newNote(
            "Signed in as "
                + loginResult.getUsername()
                + (loginResult.isAnonymousLogin() ? "  ·  guest" : "")
                + (loginResult.isModerator() ? "  ·  moderator" : ""));

    final JButton host =
        new JButtonBuilder("Host game")
            .toolTip("Hosts a game in a window of its own; players in the lobby can join it.")
            .actionListener(games::hostGame)
            .build();
    LookAndFeel.setPrimaryButtonStyle(host);
    final JButton join =
        new JButtonBuilder("Join game")
            .toolTip("Joins the selected game in a window of its own. A double click does too.")
            .actionListener(games::joinGame)
            .build();
    join.setEnabled(false);
    games.addSelectionListener(join::setEnabled);
    final JPopupMenu lobbyMenu =
        LobbyMenu.newPopupMenu(frame, loginResult, lobbyModel.getConnection());
    final JButton menu = new JButtonBuilder("Menu  ▾").build();
    menu.addActionListener(e -> lobbyMenu.show(menu, 0, menu.getHeight()));

    // in a row, centered in the height of the bar
    final List<JComponent> parts = List.of(signedInAs, menu, join, host);
    final JPanel actions = new JPanel(new GridBagLayout());
    actions.setOpaque(false);
    IntStream.range(0, parts.size())
        .forEach(
            column ->
                actions.add(
                    parts.get(column),
                    new GridBagConstraintsBuilder(column, 0)
                        .insets(0, column == 0 ? 0 : 8, 0, 0)
                        .build()));
    // leaving ends the connection and shows the main menu, as the Leave lobby button did
    return new BackBar(MetaSetupPanel.getMainMenuTitle(), TITLE, session::leave, actions);
  }

  /** A small heading above the content. */
  private static JComponent newSection(final String title, final JComponent content) {
    final JPanel section = new JPanel(new BorderLayout(0, 6));
    section.add(LookAndFeel.newSectionLabel(title), BorderLayout.NORTH);
    section.add(content, BorderLayout.CENTER);
    return section;
  }

  @Override
  public void addNotify() {
    super.addNotify();
    SwingUtilities.invokeLater(chatMessagePanel::requestFocusInWindow);
  }

  private void leave() {
    session.leave();
  }

  @Override
  public boolean isFullWindow() {
    return true;
  }

  @Override
  public boolean canGameStart() {
    return false;
  }

  @Override
  public List<Action> getUserActions() {
    return List.of();
  }

  @Override
  public boolean isCancelButtonVisible() {
    return false;
  }

  @Override
  public String getScreenTitle() {
    return TITLE;
  }

  /** Another screen replaces the lobby, eg after a game ended: disconnects. */
  @Override
  public void cancel() {
    session.disconnect();
  }

  @Override
  public Optional<ILauncher> getLauncher() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void postStartGame() {
    // nothing to do
  }
}
