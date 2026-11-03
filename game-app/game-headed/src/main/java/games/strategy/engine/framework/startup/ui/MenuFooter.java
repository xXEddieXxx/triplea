package games.strategy.engine.framework.startup.ui;

import com.google.common.annotations.VisibleForTesting;
import games.strategy.engine.framework.I18nResourceBundle;
import games.strategy.engine.framework.lookandfeel.LookAndFeel;
import games.strategy.engine.framework.startup.ui.LineIcon.Kind;
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.Serial;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import lombok.extern.slf4j.Slf4j;
import org.triplea.awt.OpenFileUtility;
import org.triplea.http.client.latest.version.LatestVersionResponse;
import org.triplea.http.client.latest.version.LatestVersionResponse.RecommendedAction;
import org.triplea.swing.JButtonBuilder;
import org.triplea.swing.WrappingText;
import org.triplea.swing.jpanel.GridBagConstraintsBuilder;
import org.triplea.swing.jpanel.GridBagConstraintsFill;

/**
 * The quiet end of the main menu's column, read last: a tip, the version, and below it the way to a
 * newer version when there is one.
 */
@Slf4j
final class MenuFooter extends JPanel {
  @Serial private static final long serialVersionUID = 6112048713092251387L;

  private static final int ICON_SIZE = 14;

  private final I18nResourceBundle bundle;

  /**
   * @param tip "Tip: ..."; wraps in a narrow column
   * @param version "Version 2.7.15012"
   */
  MenuFooter(
      final I18nResourceBundle bundle,
      final String tip,
      final String version,
      final Supplier<Optional<LatestVersionResponse>> latestVersion) {
    super(new GridBagLayout());
    this.bundle = bundle;
    setOpaque(false);
    final JLabel bulb =
        new JLabel(new LineIcon(Kind.TIP, ICON_SIZE, LookAndFeel.getSecondaryTextColor()));
    bulb.setVerticalAlignment(SwingConstants.TOP);
    final WrappingText tipText = new WrappingText(tip);
    tipText.setForeground(LookAndFeel.getSecondaryTextColor());
    final JPanel tipLine = new JPanel(new BorderLayout(MenuSpacing.S, 0));
    tipLine.setOpaque(false);
    tipLine.add(bulb, BorderLayout.WEST);
    tipLine.add(tipText, BorderLayout.CENTER);
    final JLabel versionLabel = new JLabel(version);
    versionLabel.setForeground(LookAndFeel.getSecondaryTextColor());
    versionLabel.setFont(versionLabel.getFont().deriveFont(versionLabel.getFont().getSize2D() - 1));
    add(tipLine, rowAt(0, 0));
    add(versionLabel, rowAt(1, MenuSpacing.S));
    readVersion(latestVersion);
  }

  private static GridBagConstraints rowAt(final int row, final int spaceAbove) {
    return new GridBagConstraintsBuilder(0, row)
        .weightX(1)
        .fill(GridBagConstraintsFill.HORIZONTAL)
        .insets(spaceAbove, 0, 0, 0)
        .build();
  }

  /** Points to a newer version; nothing when this is the latest or the lobby cannot tell. */
  private void readVersion(final Supplier<Optional<LatestVersionResponse>> latestVersion) {
    new SwingWorker<Optional<LatestVersionResponse>, Void>() {
      @Override
      protected Optional<LatestVersionResponse> doInBackground() {
        return latestVersion.get();
      }

      @Override
      protected void done() {
        try {
          get().ifPresent(MenuFooter.this::showVersion);
        } catch (final InterruptedException e) {
          Thread.currentThread().interrupt();
        } catch (final ExecutionException e) {
          log.info("Could not check for a new version", e);
        }
      }
    }.execute();
  }

  /** The way to the newer version below the version, unless the response says there is none. */
  @VisibleForTesting
  void showVersion(final LatestVersionResponse response) {
    if (RecommendedAction.NO_UPDATE.toString().equals(response.getRecommendedUpdateAction())) {
      return;
    }
    final JButton newVersion =
        new JButtonBuilder(bundle.getText("startup.News.NewVersion") + "  ›")
            .actionListener(() -> OpenFileUtility.openUrl(this, response.getDownloadPageUrl()))
            .build();
    LookAndFeel.setBorderlessButtonStyle(newVersion);
    newVersion.setHorizontalAlignment(SwingConstants.LEADING);
    // the text lines up with the version above it
    newVersion.setMargin(new Insets(0, 0, 0, 0));
    newVersion.setFont(newVersion.getFont().deriveFont(Font.BOLD));
    add(newVersion, rowAt(2, MenuSpacing.XS));
    revalidate();
  }
}
