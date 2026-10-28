package games.strategy.engine.lobby.client.ui;

import com.google.common.annotations.VisibleForTesting;
import games.strategy.engine.framework.GameProcess;
import games.strategy.engine.framework.GameRunner;
import games.strategy.engine.framework.lookandfeel.LookAndFeel;
import games.strategy.engine.framework.startup.ui.ServerOptions;
import games.strategy.engine.lobby.client.login.LoginResult;
import games.strategy.engine.lobby.client.ui.LobbyGameTableModel.Column;
import games.strategy.engine.lobby.client.ui.action.ShowPlayersAction;
import games.strategy.triplea.settings.ClientSetting;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import javax.annotation.Nullable;
import javax.swing.AbstractListModel;
import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import org.triplea.swing.JLabelBuilder;
import org.triplea.swing.MouseListenerBuilder;
import org.triplea.swing.SwingAction;
import org.triplea.swing.SwingComponents;

/**
 * The games of the lobby as a list of cards: the map in bold, below it the host, the round, whether
 * a password is needed and the comments, on the right the status and the players. Games waiting for
 * players come first. A double click joins a game; a right click offers more, for moderators also
 * boot and shutdown.
 */
class LobbyGamePanel extends JPanel {
  private static final long serialVersionUID = -2576314388949606337L;

  private static final String LIST = "list";
  private static final String EMPTY = "empty";

  private final JFrame parent;
  private final LobbyGameTableModel gameTableModel;
  private final LobbyGameListingModel gameListingModel;
  private final LoginResult loginResult;
  private final GameListModel listModel;
  private final JList<Integer> gameList;
  private final CardLayout cards = new CardLayout();
  private @Nullable String selectedGameId;
  // the list empties and fills again whenever a game changes; that is no selection by the player
  private boolean listChanging;

  LobbyGamePanel(
      final JFrame parent,
      final LoginResult loginResult,
      final LobbyGameTableModel lobbyGameTableModel,
      final LobbyGameListingModel gameListingModel) {
    setLayout(cards);
    this.parent = parent;
    this.loginResult = loginResult;
    this.gameTableModel = lobbyGameTableModel;
    this.gameListingModel = gameListingModel;

    listModel = new GameListModel(lobbyGameTableModel);
    gameList = new JList<>(listModel);
    gameList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    gameList.setCellRenderer(new GameCardRenderer());
    gameList.addMouseListener(
        new MouseListenerBuilder()
            .mouseClicked(this::mouseClicked)
            .mousePressed(this::mousePressed)
            .mouseReleased(this::showMenuOnRightClick)
            .build());

    final JLabel empty =
        new JLabel(
            "<html><center>No games right now.<br>Host one, players in the lobby can join it."
                + "</center></html>",
            SwingConstants.CENTER);
    empty.setForeground(LookAndFeel.getSecondaryTextColor());
    add(SwingComponents.newJScrollPane(gameList), LIST);
    add(empty, EMPTY);
    showListOrEmpty();
    gameList.addListSelectionListener(
        e -> {
          if (!listChanging) {
            selectedGameId =
                gameList.getSelectedIndex() >= 0
                    ? gameTableModel.getGameIdForRow(selectedRow())
                    : null;
          }
        });
    lobbyGameTableModel.addTableModelListener(e -> gamesChanged());
  }

  /** The games change often; the selected one stays selected wherever it moved. */
  @VisibleForTesting
  void gamesChanged() {
    listChanging = true;
    try {
      listModel.refresh();
      showListOrEmpty();
      IntStream.range(0, listModel.getSize())
          .filter(
              index ->
                  gameTableModel
                      .getGameIdForRow(listModel.getElementAt(index))
                      .equals(selectedGameId))
          .findFirst()
          .ifPresentOrElse(
              gameList::setSelectedIndex,
              () -> {
                // the selected game is gone
                gameList.clearSelection();
                selectedGameId = null;
              });
    } finally {
      listChanging = false;
    }
  }

  @VisibleForTesting
  JList<Integer> getGameList() {
    return gameList;
  }

  private void showListOrEmpty() {
    cards.show(this, listModel.getSize() == 0 ? EMPTY : LIST);
  }

  /** Calls the listener with whether a game is selected, whenever that changes. */
  void addSelectionListener(final Consumer<Boolean> gameSelected) {
    gameList.addListSelectionListener(e -> gameSelected.accept(gameList.getSelectedIndex() >= 0));
  }

  private void mouseClicked(final MouseEvent mouseEvent) {
    if (mouseEvent.getClickCount() == 2) {
      joinGame();
    }
    showMenuOnRightClick(mouseEvent);
  }

  private void mousePressed(final MouseEvent mouseEvent) {
    // right clicks do not select a game by default, so select the game at the mouse
    final int index = gameList.locationToIndex(mouseEvent.getPoint());
    if (index >= 0 && gameList.getCellBounds(index, index).contains(mouseEvent.getPoint())) {
      gameList.setSelectedIndex(index);
    } else {
      gameList.clearSelection();
    }
    showMenuOnRightClick(mouseEvent);
  }

  private void showMenuOnRightClick(final MouseEvent mouseEvent) {
    if (!mouseEvent.isPopupTrigger() || !SwingUtilities.isRightMouseButton(mouseEvent)) {
      return;
    }
    if (gameList.getSelectedIndex() == -1) {
      return;
    }

    final JPopupMenu menu = new JPopupMenu();
    List.of(
            SwingAction.of("Join Game", this::joinGame),
            SwingAction.of("Host Game", this::hostGame),
            ShowPlayersAction.builder()
                .parentWindow(parent)
                .gameIdSelection(() -> gameTableModel.getGameListingForRow(selectedRow()))
                .playerToLobbyConnection(gameListingModel.getConnection())
                .build()
                .buildSwingAction())
        .forEach(menu::add);

    if (loginResult.isModerator()) {
      menu.addSeparator();
      List.of(
              SwingAction.of("Boot Game", e -> bootGame()),
              SwingAction.of("Shutdown", e -> shutdownGame()))
          .forEach(menu::add);
    }
    menu.show(gameList, mouseEvent.getX(), mouseEvent.getY());
  }

  /** The row of the selected game in the table model. */
  private int selectedRow() {
    return gameList.getSelectedValue();
  }

  /** Joins the selected game, in a game window of its own; the lobby stays open. */
  void joinGame() {
    if (gameList.getSelectedIndex() == -1) {
      return;
    }
    GameProcess.joinGame(gameTableModel.get(selectedRow()), loginResult.getUsername());
  }

  /** Hosts a game, in a game window of its own; the lobby stays open. */
  void hostGame() {
    final ServerOptions options =
        new ServerOptions(
            JOptionPane.getFrameForComponent(this), loginResult.getUsername(), 3300, true);
    options.setLocationRelativeTo(JOptionPane.getFrameForComponent(this));
    options.setNameEditable(false);
    options.setVisible(true);
    if (!options.getOkPressed()) {
      return;
    }
    GameProcess.hostGame(
        options.getPort(),
        options.getName(),
        options.getComments(),
        options.getPassword(),
        ClientSetting.lobbyUri.getValueOrThrow());
  }

  private void bootGame() {
    if (gameList.getSelectedIndex() == -1) {
      return;
    }
    // read before asking: the game can leave the lobby while the question is open
    final String gameId = gameTableModel.getGameIdForRow(selectedRow());
    final int result =
        JOptionPane.showConfirmDialog(
            null,
            "Are you sure you want to disconnect the selected game?",
            "Remove Game From Lobby",
            JOptionPane.OK_CANCEL_OPTION);
    if (result != JOptionPane.OK_OPTION) {
      return;
    }
    gameListingModel.bootGame(gameId);
    JOptionPane.showMessageDialog(
        null, "The game you selected has been disconnected from the lobby.");
  }

  private void shutdownGame() {
    if (gameList.getSelectedIndex() == -1) {
      return;
    }
    // read before asking: the game can leave the lobby while the question is open
    final String gameId = gameTableModel.getGameIdForRow(selectedRow());
    final int result =
        JOptionPane.showConfirmDialog(
            null,
            "Are you sure you want to shutdown the selected game?",
            "Send Shutdown Signal?",
            JOptionPane.OK_CANCEL_OPTION);
    if (result != JOptionPane.OK_OPTION) {
      return;
    }
    gameListingModel.sendShutdownRequest(gameId);
    JOptionPane.showMessageDialog(parent, "The game you selected was sent a shutdown signal");
  }

  /** A game waiting for players: its status is "Available" or "Waiting For Players". */
  @VisibleForTesting
  static boolean isOpen(final String status) {
    final String lowerCase = status.toLowerCase(Locale.ROOT);
    return lowerCase.contains("available") || lowerCase.contains("waiting");
  }

  /**
   * The rows of the table model as list entries: open games first, then by host, so that a game
   * keeps its place when others change.
   */
  @VisibleForTesting
  static final class GameListModel extends AbstractListModel<Integer> {
    private static final long serialVersionUID = 2340521863425871215L;

    private final transient LobbyGameTableModel tableModel;
    private final List<Integer> sortedRows = new ArrayList<>();

    GameListModel(final LobbyGameTableModel tableModel) {
      this.tableModel = tableModel;
      sort();
    }

    /** Reads the games of the table model again. */
    void refresh() {
      final int oldSize = sortedRows.size();
      sort();
      if (oldSize > 0) {
        fireIntervalRemoved(this, 0, oldSize - 1);
      }
      if (!sortedRows.isEmpty()) {
        fireIntervalAdded(this, 0, sortedRows.size() - 1);
      }
    }

    private void sort() {
      sortedRows.clear();
      IntStream.range(0, tableModel.getRowCount())
          .boxed()
          .sorted(
              Comparator.comparing((Integer row) -> !isOpen(text(row, Column.Status)))
                  .thenComparing(row -> text(row, Column.Host).toLowerCase(Locale.ROOT)))
          .forEach(sortedRows::add);
    }

    String text(final int row, final Column column) {
      final Object value = tableModel.getValueAt(row, tableModel.getColumnIndex(column));
      return value == null ? "" : String.valueOf(value);
    }

    @Override
    public int getSize() {
      return sortedRows.size();
    }

    @Override
    public Integer getElementAt(final int index) {
      return sortedRows.get(index);
    }
  }

  /** A game card: map, host · round · password, comments; status and players on the right. */
  private final class GameCardRenderer implements ListCellRenderer<Integer> {
    private final JLabel title = new JLabelBuilder().bold().adjustFontSize(2).build();
    private final JLabel details = new JLabel();
    private final JLabel comments = new JLabel();
    private final JLabel status = new JLabelBuilder().bold().build();
    private final JLabel players = new JLabel();
    private final JPanel card = new JPanel(new BorderLayout(16, 0));

    GameCardRenderer() {
      final JPanel texts = new JPanel(new BorderLayout(0, 2));
      texts.setOpaque(false);
      texts.add(title, BorderLayout.NORTH);
      texts.add(details, BorderLayout.CENTER);
      texts.add(comments, BorderLayout.SOUTH);
      final JPanel right = new JPanel(new BorderLayout(0, 2));
      right.setOpaque(false);
      status.setHorizontalAlignment(SwingConstants.TRAILING);
      players.setHorizontalAlignment(SwingConstants.TRAILING);
      right.add(status, BorderLayout.NORTH);
      right.add(players, BorderLayout.CENTER);
      card.add(texts, BorderLayout.CENTER);
      card.add(right, BorderLayout.EAST);
    }

    @Override
    public Component getListCellRendererComponent(
        final JList<? extends Integer> list,
        final Integer row,
        final int index,
        final boolean isSelected,
        final boolean cellHasFocus) {
      final boolean passworded = "*".equals(listModel.text(row, Column.P));
      // like GameDescription.isBot, from the shown values
      final boolean bot =
          listModel.text(row, Column.Host).startsWith(GameRunner.BOT_GAME_HOST_NAME_PREFIX)
              && GameRunner.BOT_GAME_HOST_COMMENT.equals(listModel.text(row, Column.Comments));
      title.setText(listModel.text(row, Column.Name));
      final String round = listModel.text(row, Column.Round);
      // a word, not a lock symbol: not every font of the systems has one
      details.setText(
          "Hosted by "
              + listModel.text(row, Column.Host)
              + ("-".equals(round) ? "" : "  ·  Round " + round)
              + (passworded ? "  ·  Password" : "")
              // moderators see when a game started, the table model has that column only for them
              + (loginResult.isModerator()
                  ? "  ·  Started " + listModel.text(row, Column.Started)
                  : ""));
      final String comment = listModel.text(row, Column.Comments);
      comments.setText(comment);
      comments.setVisible(!comment.isBlank());
      final String statusText = listModel.text(row, Column.Status);
      status.setText(statusText);
      final String playerCount = listModel.text(row, Column.Players);
      players.setText(playerCount + ("1".equals(playerCount) ? " player" : " players"));

      final Color secondary = LookAndFeel.getSecondaryTextColor();
      final Color foreground = isSelected ? list.getSelectionForeground() : list.getForeground();
      title.setForeground(foreground);
      details.setForeground(isSelected ? foreground : secondary);
      comments.setForeground(isSelected ? foreground : secondary);
      comments.setFont(comments.getFont().deriveFont(bot ? Font.ITALIC : Font.PLAIN));
      players.setForeground(isSelected ? foreground : secondary);
      status.setForeground(
          isSelected ? foreground : isOpen(statusText) ? LookAndFeel.getAccentColor() : secondary);
      card.setBackground(isSelected ? list.getSelectionBackground() : list.getBackground());
      card.setBorder(
          BorderFactory.createCompoundBorder(
              BorderFactory.createMatteBorder(0, 0, 1, 0, LookAndFeel.getSeparatorColor()),
              BorderFactory.createEmptyBorder(10, 14, 10, 14)));
      return card;
    }
  }
}
