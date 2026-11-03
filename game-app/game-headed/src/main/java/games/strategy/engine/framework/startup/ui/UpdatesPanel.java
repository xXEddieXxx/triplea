package games.strategy.engine.framework.startup.ui;

import games.strategy.engine.framework.I18nResourceBundle;
import games.strategy.engine.framework.lookandfeel.LookAndFeel;
import games.strategy.engine.framework.startup.ui.UpdatesFeed.Kind;
import games.strategy.engine.framework.startup.ui.UpdatesFeed.Update;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagLayout;
import java.io.Serial;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.ExecutionException;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingWorker;
import lombok.extern.slf4j.Slf4j;
import org.triplea.awt.OpenFileUtility;
import org.triplea.http.client.lobby.maps.listing.MapDownloadItem;
import org.triplea.swing.jpanel.GridBagConstraintsAnchor;
import org.triplea.swing.jpanel.GridBagConstraintsBuilder;

/**
 * The updates' area of the main menu's news band: its title bar, below it what changed lately, new
 * TripleA versions and updated maps, one row each to click, the kind in front, the time behind.
 * Read in the background; offline a short message shows instead.
 */
@Slf4j
final class UpdatesPanel extends NewsArea {
  @Serial private static final long serialVersionUID = -3518960219871152062L;

  private static final String PREFIX = "startup.Updates.";

  /** Wide enough for "TRIPLEA" and "KARTE", so the titles start at one edge. */
  private static final int KIND_WIDTH = 72;

  private static final int PLACEHOLDER_WIDTH = 360;

  private final I18nResourceBundle bundle;

  UpdatesPanel(final I18nResourceBundle bundle, final MainMenuSources sources) {
    super(
        SectionHeading.newHeading(bundle.getText(PREFIX + "Title"), LineIcon.Kind.UPDATES),
        LoadingPlaceholder.newLines(UpdatesFeed.SHOWN, PLACEHOLDER_WIDTH));
    this.bundle = bundle;
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
    } else {
      showRows(updates.stream().map(this::newRow).toList());
    }
  }

  /** "TRIPLEA New version 2026-10.16 Yesterday ›" or "MAP World War II Global updated". */
  private JComponent newRow(final Update update) {
    // the kind as a quiet tag in a column of its own width, so the texts start at one edge
    final JPanel kind = new JPanel(new GridBagLayout());
    kind.setOpaque(false);
    kind.add(
        Pill.newQuiet(bundle.getText(PREFIX + (update.kind() == Kind.GAME ? "Game" : "Map"))),
        new GridBagConstraintsBuilder(0, 0)
            .weightX(1)
            .anchor(GridBagConstraintsAnchor.WEST)
            .build());
    kind.setPreferredSize(new Dimension(KIND_WIDTH, kind.getPreferredSize().height));

    final String title =
        bundle.getText(
            PREFIX + (update.kind() == Kind.GAME ? "NewVersion" : "MapUpdated"), update.name());
    final JLabel when =
        new JLabel(
            SavedGamesPanel.whenSaved(
                bundle, update.when(), LocalDate.now(), ZoneId.systemDefault()));
    when.setForeground(LookAndFeel.getSecondaryTextColor());

    final JPanel line = new JPanel(new BorderLayout(MenuSpacing.M, 0));
    line.setOpaque(false);
    line.add(kind, BorderLayout.WEST);
    // a long map name ends in "…" instead of widening the column
    line.add(newShrinkingLabel(title, Font.PLAIN, 0), BorderLayout.CENTER);
    line.add(when, BorderLayout.EAST);
    return FeedRow.newRow(
        line, title, () -> OpenFileUtility.openUrl(this, update.page().toString()));
  }
}
