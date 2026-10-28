package games.strategy.engine.framework.startup.ui;

import games.strategy.engine.framework.I18nResourceBundle;
import games.strategy.engine.framework.lookandfeel.LookAndFeel;
import games.strategy.engine.framework.startup.ui.UpdatesFeed.Kind;
import games.strategy.engine.framework.startup.ui.UpdatesFeed.Update;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.Serial;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.IntStream;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import lombok.extern.slf4j.Slf4j;
import org.triplea.awt.OpenFileUtility;
import org.triplea.http.client.lobby.maps.listing.MapDownloadItem;
import org.triplea.swing.JButtonBuilder;
import org.triplea.swing.jpanel.GridBagConstraintsBuilder;
import org.triplea.swing.jpanel.GridBagConstraintsFill;

/**
 * A short feed of what changed lately: new TripleA versions and updated maps, one line each, the
 * kind in front, the time behind. Read in the background; offline a short message shows instead.
 */
@Slf4j
final class UpdatesPanel extends JPanel {
  @Serial private static final long serialVersionUID = -3518960219871152062L;

  private static final String PREFIX = "startup.Updates.";

  /** Wide enough for "TRIPLEA" and "KARTE", so the titles start at one edge. */
  private static final int KIND_WIDTH = 76;

  // no wider than the main menu's right column in small windows
  private static final int PLACEHOLDER_WIDTH = 560;

  private final I18nResourceBundle bundle;
  private final JPanel rows = new JPanel(new GridBagLayout());

  UpdatesPanel(final I18nResourceBundle bundle, final MainMenuSources sources) {
    super(new BorderLayout(0, 10));
    this.bundle = bundle;
    setOpaque(false);
    rows.setOpaque(false);
    add(LookAndFeel.newSectionLabel(bundle.getText(PREFIX + "Title")), BorderLayout.NORTH);
    add(rows, BorderLayout.CENTER);
    // the shape of the feed while GitHub and the lobby server answer
    rows.add(LoadingPlaceholder.newLines(UpdatesFeed.SHOWN, PLACEHOLDER_WIDTH), rowAt(0, 4));
    readUpdates(sources);
  }

  private void readUpdates(final MainMenuSources sources) {
    new SwingWorker<List<Update>, Void>() {
      @Override
      protected List<Update> doInBackground() {
        final List<MapDownloadItem> maps = sources.mapsToDownload().get();
        return UpdatesFeed.newest(sources.gameReleases().get(), maps);
      }

      @Override
      protected void done() {
        try {
          showUpdates(get());
        } catch (final InterruptedException e) {
          Thread.currentThread().interrupt();
        } catch (final ExecutionException e) {
          log.info("Could not read the updates", e);
          showUpdates(List.of());
        }
      }
    }.execute();
  }

  private void showUpdates(final List<Update> updates) {
    if (updates.isEmpty()) {
      showMessage(bundle.getText(PREFIX + "Offline"));
      return;
    }
    rows.removeAll();
    IntStream.range(0, updates.size())
        .forEach(row -> rows.add(newRow(updates.get(row)), rowAt(row, row == 0 ? 0 : 6)));
    revalidate();
    repaint();
  }

  private void showMessage(final String text) {
    rows.removeAll();
    final JLabel message = new JLabel(text);
    message.setForeground(LookAndFeel.getSecondaryTextColor());
    rows.add(message, rowAt(0, 0));
    revalidate();
    repaint();
  }

  private static GridBagConstraints rowAt(final int row, final int spaceAbove) {
    return new GridBagConstraintsBuilder(0, row)
        .weightX(1)
        .fill(GridBagConstraintsFill.HORIZONTAL)
        .insets(spaceAbove, 0, 0, 0)
        .build();
  }

  /** "TRIPLEA Version 2026-10.16 · Yesterday" or "MAP World War II Global updated". */
  private JComponent newRow(final Update update) {
    final JLabel kind =
        new JLabel(bundle.getText(PREFIX + (update.kind() == Kind.GAME ? "Game" : "Map")));
    kind.setFont(kind.getFont().deriveFont(Font.BOLD, kind.getFont().getSize2D() - 2));
    kind.setForeground(LookAndFeel.getSecondaryTextColor());
    kind.setPreferredSize(new Dimension(KIND_WIDTH, kind.getPreferredSize().height));

    final String title =
        bundle.getText(
            PREFIX + (update.kind() == Kind.GAME ? "NewVersion" : "MapUpdated"), update.name());
    final JButton link =
        new JButtonBuilder(title)
            .actionListener(() -> OpenFileUtility.openUrl(this, update.page().toString()))
            .build();
    LookAndFeel.setBorderlessButtonStyle(link);
    link.setHorizontalAlignment(SwingConstants.LEADING);
    link.setMargin(new Insets(2, 0, 2, 0));

    final JLabel when =
        new JLabel(
            SavedGamesPanel.whenSaved(
                bundle, update.when(), LocalDate.now(), ZoneId.systemDefault()));
    when.setForeground(LookAndFeel.getSecondaryTextColor());
    when.setFont(when.getFont().deriveFont(when.getFont().getSize2D() - 1));

    final JPanel row = new JPanel(new BorderLayout(12, 0));
    row.setOpaque(false);
    row.add(kind, BorderLayout.WEST);
    row.add(link, BorderLayout.CENTER);
    row.add(when, BorderLayout.EAST);
    return row;
  }
}
