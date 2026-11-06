package games.strategy.triplea.ui.menubar;

import games.strategy.engine.framework.startup.ui.InGameLobbyWatcherWrapper;
import games.strategy.engine.lobby.client.ui.action.EditGameCommentAction;
import games.strategy.engine.lobby.client.ui.action.RemoveGameFromLobbyAction;
import games.strategy.triplea.ui.TripleAFrame;
import games.strategy.triplea.ui.menubar.help.HelpMenu;
import java.awt.Component;
import java.util.List;
import java.util.Optional;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.UtilityClass;
import org.triplea.swing.JMenuBuilder;
import org.triplea.swing.JMenuItemBuilder;
import org.triplea.swing.key.binding.KeyCode;

/**
 * The menus of the game window's quick bar, each behind an icon: the files (save, post the turn,
 * leave), statistics and exports, the network game and the lobby (only in network games), and help
 * with the game's options and the AI debug options.
 */
@UtilityClass
public final class TripleAMenuBar {

  @AllArgsConstructor(access = AccessLevel.PUBLIC)
  @Getter
  public enum Mnemonic {
    FILE(KeyCode.F),
    // shortcut: kept only for the unused ViewMenu.java, until that file is deleted
    VIEW(KeyCode.V),
    STATISTICS(KeyCode.S),
    EXPORT(KeyCode.E),
    LOBBY(KeyCode.L),
    NETWORK(KeyCode.N),
    DEBUG(KeyCode.D);

    private final KeyCode mnemonicCode;
  }

  /** Save, post the turn, leave the game and exit. */
  static JMenu fileMenu(final TripleAFrame frame) {
    return FileMenu.get(frame);
  }

  /** The game's statistics and dice, then the exports of stats, charts and the map. */
  static JMenu statisticsMenu(final TripleAFrame frame) {
    final JMenu menu =
        new JMenuBuilder("Statistics", Mnemonic.STATISTICS.getMnemonicCode()).build();
    GameMenu.newStatisticsItems(frame).forEach(menu::add);
    menu.addSeparator();
    moveItems(ExportMenu.get(frame), menu);
    return menu;
  }

  /** The network game's and the lobby's entries, eg who is who; none in a game on this computer. */
  static Optional<JMenu> networkMenu(final TripleAFrame frame) {
    final JMenu menu = new JMenuBuilder("Network", Mnemonic.NETWORK.getMnemonicCode()).build();
    final Optional<InGameLobbyWatcherWrapper> watcher = frame.getInGameLobbyWatcher();
    watcher
        .filter(InGameLobbyWatcherWrapper::isActive)
        .ifPresent(lobby -> lobbyItems(frame, lobby).forEach(menu::add));
    if (frame.getGame().getMessengers().isConnected()) {
      if (menu.getMenuComponentCount() > 0) {
        menu.addSeparator();
      }
      moveItems(NetworkMenu.get(watcher, frame), menu);
    }
    return menu.getMenuComponentCount() == 0 ? Optional.empty() : Optional.of(menu);
  }

  private static List<JMenuItem> lobbyItems(
      final TripleAFrame frame, final InGameLobbyWatcherWrapper watcher) {
    return List.of(
        new JMenuItemBuilder(new EditGameCommentAction(watcher, frame), KeyCode.E).build(),
        new JMenuItemBuilder(new RemoveGameFromLobbyAction(watcher), KeyCode.R).build());
  }

  /** The help of before, the game's options to look at, and at the end the AI debug options. */
  static JMenu helpMenu(final TripleAFrame frame) {
    final JMenu menu = HelpMenu.buildMenu(frame, frame.getUiContext(), frame.getGame().getData());
    GameMenu.newGameOptionsItem(frame).ifPresent(item -> menu.insert(item, 0));
    menu.addSeparator();
    menu.add(DebugMenu.get(frame));
    return menu;
  }

  /** Moves the entries of a menu, eg Export, into another, in their order. */
  private static void moveItems(final JMenu from, final JMenu to) {
    for (final Component item : from.getMenuComponents()) {
      // adding takes the entry out of the menu it was in
      to.add(item);
    }
  }
}
