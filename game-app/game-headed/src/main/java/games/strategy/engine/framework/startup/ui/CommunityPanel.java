package games.strategy.engine.framework.startup.ui;

import com.google.common.annotations.VisibleForTesting;
import games.strategy.engine.framework.I18nResourceBundle;
import games.strategy.engine.framework.lookandfeel.LookAndFeel;
import games.strategy.engine.framework.startup.ui.ForumTopics.ForumTopic;
import games.strategy.triplea.UrlConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.Serial;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import lombok.extern.slf4j.Slf4j;
import org.triplea.awt.OpenFileUtility;
import org.triplea.http.client.latest.version.LatestVersionResponse;
import org.triplea.http.client.latest.version.LatestVersionResponse.RecommendedAction;
import org.triplea.swing.JButtonBuilder;
import org.triplea.swing.jpanel.GridBagConstraintsBuilder;
import org.triplea.swing.jpanel.GridBagConstraintsFill;

/**
 * What is new around TripleA: the newest topics of the forum, whether this version is the latest,
 * and the links to the community. Read in the background; offline only the links show.
 */
@Slf4j
final class CommunityPanel extends JPanel {
  @Serial private static final long serialVersionUID = 2675391120469503183L;

  private static final String PREFIX = "startup.News.";
  // no wider than the main menu's right column in small windows
  private static final int PLACEHOLDER_WIDTH = 560;

  private final I18nResourceBundle bundle;
  private final JPanel topics = new JPanel(new GridBagLayout());
  private final JLabel topicsMessage = new JLabel();
  private final JPanel version = new JPanel(new BorderLayout());

  CommunityPanel(final I18nResourceBundle bundle, final MainMenuSources sources) {
    super(new GridBagLayout());
    this.bundle = bundle;
    setOpaque(false);
    topics.setOpaque(false);
    version.setOpaque(false);
    topicsMessage.setForeground(LookAndFeel.getSecondaryTextColor());
    // the shape of the topics while the forum answers
    topics.add(LoadingPlaceholder.newTextRows(ForumTopics.SHOWN, PLACEHOLDER_WIDTH), rowAt(0, 4));

    // the heading with the way to the whole forum, then the topics; a newer version above all
    final JPanel heading = new JPanel(new BorderLayout());
    heading.setOpaque(false);
    heading.add(LookAndFeel.newSectionLabel(bundle.getText(PREFIX + "Forum")), BorderLayout.WEST);
    heading.add(
        newLink(bundle.getText(PREFIX + "AllTopics") + "  ›", UrlConstants.TRIPLEA_FORUM),
        BorderLayout.EAST);
    add(version, rowAt(0, 0));
    add(heading, rowAt(1, 0));
    add(topics, rowAt(2, 8));
    readForum(sources.forumTopics());
    readVersion(sources.latestVersion());
  }

  private static GridBagConstraints rowAt(final int row, final int spaceAbove) {
    return new GridBagConstraintsBuilder(0, row)
        .weightX(1)
        .fill(GridBagConstraintsFill.HORIZONTAL)
        .insets(spaceAbove, 0, 0, 0)
        .build();
  }

  private JButton newLink(final String text, final String url) {
    final JButton link =
        new JButtonBuilder(text).actionListener(() -> OpenFileUtility.openUrl(this, url)).build();
    LookAndFeel.setBorderlessButtonStyle(link);
    link.setHorizontalAlignment(SwingConstants.LEADING);
    // the text lines up with the headings above it
    link.setMargin(new Insets(2, 0, 2, 0));
    return link;
  }

  private void readForum(final Supplier<List<ForumTopic>> forumTopics) {
    new SwingWorker<List<ForumTopic>, Void>() {
      @Override
      protected List<ForumTopic> doInBackground() {
        return forumTopics.get();
      }

      @Override
      protected void done() {
        try {
          showTopics(get());
        } catch (final InterruptedException e) {
          Thread.currentThread().interrupt();
        } catch (final ExecutionException e) {
          log.info("Could not read the forum", e);
          showTopics(List.of());
        }
      }
    }.execute();
  }

  /** A row per topic: its title as a link to it, and when it was last answered. */
  private void showTopics(final List<ForumTopic> newest) {
    topics.removeAll();
    if (newest.isEmpty()) {
      topicsMessage.setText(bundle.getText(PREFIX + "Offline"));
      topics.add(topicsMessage, rowAt(0, 0));
    }
    IntStream.range(0, newest.size())
        .forEach(row -> topics.add(newTopicRow(newest.get(row)), rowAt(row, row == 0 ? 4 : 14)));
    revalidate();
    repaint();
  }

  /**
   * A topic as forums list them: the title to click, below it how often and when it was answered; a
   * topic answered within the last day is marked new.
   */
  private JComponent newTopicRow(final ForumTopic topic) {
    final JButton title = newLink(topic.title(), topic.page().toString());
    title.setFont(title.getFont().deriveFont(Font.BOLD, title.getFont().getSize2D() + 1));
    final JLabel details =
        new JLabel(
            repliesText(topic.replies())
                + "  ·  "
                + SavedGamesPanel.whenSaved(
                    bundle, topic.lastPost(), LocalDate.now(), ZoneId.systemDefault()));
    details.setForeground(LookAndFeel.getSecondaryTextColor());

    final JPanel titleLine = new JPanel(new BorderLayout(10, 0));
    titleLine.setOpaque(false);
    titleLine.add(title, BorderLayout.CENTER);
    if (isNew(topic.lastPost(), Instant.now())) {
      titleLine.add(newBadge(), BorderLayout.WEST);
    }
    final JPanel row = new JPanel(new BorderLayout(0, 0));
    row.setOpaque(false);
    row.add(titleLine, BorderLayout.NORTH);
    row.add(details, BorderLayout.CENTER);
    return row;
  }

  private String repliesText(final int replies) {
    return switch (replies) {
      case 0 -> bundle.getText(PREFIX + "NoReplies");
      case 1 -> bundle.getText(PREFIX + "OneReply");
      default -> bundle.getText(PREFIX + "Replies", replies);
    };
  }

  /** "NEW" in the accent color, the one color of the menu. */
  private JLabel newBadge() {
    final JLabel badge = new JLabel(bundle.getText(PREFIX + "New"));
    badge.setFont(badge.getFont().deriveFont(Font.BOLD, badge.getFont().getSize2D() - 2));
    final Color accent = UIManager.getColor("TripleA.highlightBackground");
    badge.setForeground(accent == null ? LookAndFeel.getSecondaryTextColor() : accent);
    return badge;
  }

  /** Whether the topic was answered within the last day. */
  @VisibleForTesting
  static boolean isNew(final Instant lastPost, final Instant now) {
    return lastPost.isAfter(now.minus(Duration.ofDays(1)));
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
          get().ifPresent(CommunityPanel.this::showVersion);
        } catch (final InterruptedException e) {
          Thread.currentThread().interrupt();
        } catch (final ExecutionException e) {
          log.info("Could not check for a new version", e);
        }
      }
    }.execute();
  }

  private void showVersion(final LatestVersionResponse response) {
    if (RecommendedAction.NO_UPDATE.toString().equals(response.getRecommendedUpdateAction())) {
      return;
    }
    final JButton newVersion =
        newLink(bundle.getText(PREFIX + "NewVersion") + "  ›", response.getDownloadPageUrl());
    newVersion.setFont(newVersion.getFont().deriveFont(Font.BOLD));
    version.add(newVersion, BorderLayout.WEST);
    version.setBorder(BorderFactory.createEmptyBorder(0, 0, 20, 0));
    revalidate();
  }
}
