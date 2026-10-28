package games.strategy.engine.framework.startup.ui;

import games.strategy.engine.framework.I18nResourceBundle;
import games.strategy.engine.framework.lookandfeel.LookAndFeel;
import games.strategy.engine.framework.startup.ui.MainMenuSources.LobbyStats;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.io.Serial;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import lombok.extern.slf4j.Slf4j;

/**
 * How busy the lobby is, in one quiet line at the top of the menu: "● 23 players online · 4 open
 * games", the numbers bold. Nothing shows until the numbers are there.
 */
@Slf4j
final class LobbyStatusPanel extends JPanel {
  @Serial private static final long serialVersionUID = 8270436513081296504L;

  private static final String PREFIX = "startup.Lobby.";

  private final I18nResourceBundle bundle;

  LobbyStatusPanel(final I18nResourceBundle bundle, final Supplier<Optional<LobbyStats>> stats) {
    // the flow layout's gaps also before the first label; the border takes them back
    super(new FlowLayout(FlowLayout.LEADING, 6, 0));
    this.bundle = bundle;
    setOpaque(false);
    setBorder(BorderFactory.createEmptyBorder(0, -6, 0, 0));
    readStats(stats);
  }

  private void readStats(final Supplier<Optional<LobbyStats>> stats) {
    new SwingWorker<Optional<LobbyStats>, Void>() {
      @Override
      protected Optional<LobbyStats> doInBackground() {
        return stats.get();
      }

      @Override
      protected void done() {
        try {
          get().ifPresent(LobbyStatusPanel.this::showStats);
        } catch (final InterruptedException e) {
          Thread.currentThread().interrupt();
        } catch (final ExecutionException e) {
          log.info("Could not read the lobby numbers", e);
        }
      }
    }.execute();
  }

  private void showStats(final LobbyStats stats) {
    // a dot in the accent color, as lobbies mark that people are online
    final JLabel online = new JLabel("●");
    final Color accent = UIManager.getColor("TripleA.highlightBackground");
    online.setForeground(accent == null ? LookAndFeel.getSecondaryTextColor() : accent);
    add(online);
    add(newNumber(stats.playersOnline()));
    add(newText(bundle.getText(PREFIX + "Players") + "   ·  "));
    add(newNumber(stats.openGames()));
    add(newText(bundle.getText(PREFIX + "Games")));
    revalidate();
    repaint();
  }

  private static JLabel newNumber(final int number) {
    final JLabel label = new JLabel(String.valueOf(number));
    label.setFont(label.getFont().deriveFont(Font.BOLD));
    return label;
  }

  private static JLabel newText(final String text) {
    final JLabel label = new JLabel(text);
    label.setForeground(LookAndFeel.getSecondaryTextColor());
    return label;
  }
}
