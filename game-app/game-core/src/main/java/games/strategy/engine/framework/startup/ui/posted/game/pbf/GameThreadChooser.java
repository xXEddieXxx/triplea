package games.strategy.engine.framework.startup.ui.posted.game.pbf;

import com.google.common.annotations.VisibleForTesting;
import games.strategy.engine.framework.lookandfeel.LookAndFeel;
import games.strategy.engine.framework.ui.background.BackgroundTaskRunner;
import games.strategy.engine.posted.game.pbf.NodeBbGameThreads;
import games.strategy.engine.posted.game.pbf.NodeBbGameThreads.GameThread;
import games.strategy.engine.posted.game.pbf.NodeBbGameThreads.Player;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.io.Serial;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.apache.commons.text.StringEscapeUtils;

/**
 * The forum's current games to take the game's thread from, so nobody copies its address out of the
 * browser: the player's own games first, those where another player posted last ("Your turn") at
 * the top and those they only watch at the end, then the newest games of the forum's Play by Forum
 * category. The forum shows them only to a signed-in player. OK or a double click takes the chosen
 * game.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class GameThreadChooser {
  private static final DateTimeFormatter DATE =
      DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH).withZone(ZoneId.systemDefault());

  /**
   * The games read from the forum, in the order they show, which of them are the player's, and the
   * player the forum signed in.
   */
  @VisibleForTesting
  record Games(List<GameThread> threads, Set<Integer> ownTopics, Player player) {}

  /**
   * Reads the forum's games with the player's session behind a wait window, then lets the player
   * pick one.
   *
   * @param isOld the games the player moved to Old games, which leave the player's own games; the
   *     forum's newest list keeps them, as Browse games on the Play by forum page does
   * @param sessionRanOut tells the screen that the session signs nobody in any more
   * @return the picked game; none if the player cancelled, the session ran out or there are none
   */
  static Optional<GameThread> choose(
      final Component parent,
      final NodeBbGameThreads forum,
      final Predicate<GameThread> isOld,
      final Runnable sessionRanOut) {
    final Optional<Games> read;
    try {
      read =
          BackgroundTaskRunner.runInBackgroundAndReturn(
              "Reading the forum's games...", () -> read(forum, isOld), null, IOException.class);
    } catch (final IOException e) {
      JOptionPane.showMessageDialog(
          parent,
          "The forum did not send its games: "
              + e.getMessage()
              + "\nPaste the thread's address from your browser instead.",
          "No games",
          JOptionPane.WARNING_MESSAGE);
      return Optional.empty();
    } catch (final InterruptedException expected) {
      // the wait window's Cancel, on the EDT: its interrupt is no news for the EDT
      return Optional.empty();
    }
    if (read.isEmpty()) {
      sessionRanOut.run();
      JOptionPane.showMessageDialog(
          parent,
          "You are no longer signed in. Sign in again on Multiplayer > Play by forum.",
          "No games",
          JOptionPane.WARNING_MESSAGE);
      return Optional.empty();
    }
    final Games games = read.get();
    if (games.threads().isEmpty()) {
      JOptionPane.showMessageDialog(
          parent,
          "The forum shows no games. Paste the thread's address from your browser instead.",
          "No games",
          JOptionPane.INFORMATION_MESSAGE);
      return Optional.empty();
    }
    return pick(parent, games);
  }

  /** The games, or none when the session signs nobody in any more: the forum hides them then. */
  private static Optional<Games> read(
      final NodeBbGameThreads forum, final Predicate<GameThread> isOld) throws IOException {
    final Optional<Player> player = forum.signedInPlayer();
    if (player.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(inOrder(forum.threadsOf(player.get()), forum.browse(), player.get(), isOld));
  }

  /**
   * The player's games, theirs to move first and the watched ones last, each group in the forum's
   * order (the last answered first), then the others of the newest games; the player's games leave
   * out those moved to Old games.
   */
  @VisibleForTesting
  static Games inOrder(
      final List<GameThread> own,
      final List<GameThread> newest,
      final Player player,
      final Predicate<GameThread> isOld) {
    // as on the Play by forum page: old games leave the player's games, the forum's list keeps all
    final List<GameThread> current = own.stream().filter(Predicate.not(isOld)).toList();
    final Set<Integer> ownTopics =
        current.stream().map(GameThread::topicId).collect(Collectors.toUnmodifiableSet());
    final Comparator<GameThread> yourTurnFirst =
        Comparator.comparing((GameThread thread) -> !thread.waitsFor(player))
            .thenComparing(GameThread::onlyWatched);
    final List<GameThread> threads =
        Stream.concat(
                current.stream().sorted(yourTurnFirst),
                newest.stream().filter(thread -> !ownTopics.contains(thread.topicId())))
            .toList();
    return new Games(threads, ownTopics, player);
  }

  private static Optional<GameThread> pick(final Component parent, final Games games) {
    final JList<GameThread> list = new JList<>(games.threads().toArray(GameThread[]::new));
    list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    list.setSelectedIndex(0);
    list.setCellRenderer(new Row(games));
    final JScrollPane scroll = new JScrollPane(list);
    scroll.setPreferredSize(new Dimension(560, 360));
    final JPanel content = new JPanel(new BorderLayout(0, 8));
    content.add(
        new JLabel("Your games first, then the newest games of the forum."), BorderLayout.NORTH);
    content.add(scroll, BorderLayout.CENTER);
    final JOptionPane pane =
        new JOptionPane(content, JOptionPane.PLAIN_MESSAGE, JOptionPane.OK_CANCEL_OPTION);
    final JDialog dialog = pane.createDialog(parent, "Choose the game");
    list.addMouseListener(
        new MouseAdapter() {
          @Override
          public void mouseClicked(final MouseEvent e) {
            // on a game only: below the last one, the list would pick the nearest
            final int index = list.locationToIndex(e.getPoint());
            if (e.getClickCount() == 2
                && SwingUtilities.isLeftMouseButton(e)
                && index >= 0
                && list.getCellBounds(index, index).contains(e.getPoint())) {
              // the game clicked, also after a ctrl-click took the selection away
              list.setSelectedIndex(index);
              // closes the dialog as OK does
              pane.setValue(JOptionPane.OK_OPTION);
            }
          }
        });
    dialog.setVisible(true);
    dialog.dispose();
    return Integer.valueOf(JOptionPane.OK_OPTION).equals(pane.getValue())
        ? Optional.ofNullable(list.getSelectedValue())
        : Optional.empty();
  }

  /** A game as two lines: its title in bold, below it {@link #describe} in the secondary color. */
  @RequiredArgsConstructor
  private static final class Row extends DefaultListCellRenderer {
    @Serial private static final long serialVersionUID = 4127863550394482211L;

    private final transient Games games;

    @Override
    public Component getListCellRendererComponent(
        final JList<?> list,
        final Object value,
        final int index,
        final boolean isSelected,
        final boolean cellHasFocus) {
      final GameThread thread = (GameThread) value;
      // on the selection the secondary grey is hard to read: the selection's own color there
      final Color detailColor =
          isSelected ? list.getSelectionForeground() : LookAndFeel.getSecondaryTextColor();
      // titles and turns are written by players: escaped, so they show as written
      final String text =
          "<html><b>"
              + StringEscapeUtils.escapeHtml4(thread.title())
              + "</b><br><span style='color:"
              + LookAndFeel.convertColorToHex(detailColor)
              + "'>"
              + StringEscapeUtils.escapeHtml4(describe(thread, games))
              + "</span></html>";
      final Component row =
          super.getListCellRendererComponent(list, text, index, isSelected, cellHasFocus);
      // padding inside the border the list chose, which marks the keyboard focus
      setBorder(
          BorderFactory.createCompoundBorder(
              getBorder(), BorderFactory.createEmptyBorder(4, 6, 4, 6)));
      return row;
    }
  }

  /**
   * What the row says about a game below its title, eg "Your turn · Germans, round 5 · last post 3
   * Nov 2026"; what is not known is left out. Only the player's own games can be their turn: in the
   * others' games someone else posted last as well.
   */
  @VisibleForTesting
  static String describe(final GameThread thread, final Games games) {
    final boolean own = games.ownTopics().contains(thread.topicId());
    final List<String> parts = new ArrayList<>();
    if (own && thread.waitsFor(games.player())) {
      parts.add("Your turn");
    } else if (thread.onlyWatched()) {
      parts.add("Watching");
    } else if (own) {
      parts.add("Your game");
    }
    if (!thread.lastTurn().isBlank()) {
      parts.add(thread.lastTurn());
    }
    if (thread.lastPost().isAfter(Instant.EPOCH)) {
      parts.add("last post " + DATE.format(thread.lastPost()));
    }
    return String.join(" · ", parts);
  }
}
