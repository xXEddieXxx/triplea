package games.strategy.engine.framework.startup.ui;

import com.google.common.annotations.VisibleForTesting;
import games.strategy.engine.framework.I18nResourceBundle;
import games.strategy.engine.framework.lookandfeel.LookAndFeel;
import games.strategy.engine.framework.startup.ui.ForumTopics.ForumTopic;
import games.strategy.engine.framework.startup.ui.LineIcon.Kind;
import games.strategy.triplea.UrlConstants;
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.Serial;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingWorker;
import lombok.extern.slf4j.Slf4j;
import org.triplea.awt.OpenFileUtility;

/**
 * The forum's area of the main menu's news band: its title bar with the way to all topics, below it
 * the newest topics, each one row to click. Read in the background; offline a short message shows
 * instead.
 */
@Slf4j
final class CommunityPanel extends NewsArea {
  @Serial private static final long serialVersionUID = 2675391120469503183L;

  private static final String PREFIX = "startup.News.";
  private static final int PLACEHOLDER_WIDTH = 360;

  private final I18nResourceBundle bundle;

  CommunityPanel(final I18nResourceBundle bundle, final MainMenuSources sources) {
    super(
        SectionHeading.newHeading(
            bundle.getText(PREFIX + "Forum"),
            Kind.FORUM,
            newAllTopicsLink(bundle.getText(PREFIX + "AllTopics") + "  ›")),
        LoadingPlaceholder.newTextRows(ForumTopics.SHOWN, PLACEHOLDER_WIDTH));
    this.bundle = bundle;
    readForum(sources.forumTopics());
  }

  private static JButton newAllTopicsLink(final String text) {
    final JButton link = new JButton(text);
    link.addActionListener(e -> OpenFileUtility.openUrl(link, UrlConstants.TRIPLEA_FORUM));
    LookAndFeel.setBorderlessButtonStyle(link);
    link.setMargin(new Insets(0, MenuSpacing.S, 0, MenuSpacing.S));
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

  private void showTopics(final List<ForumTopic> newest) {
    if (newest.isEmpty()) {
      showMessage(bundle.getText(PREFIX + "Offline"));
    } else {
      showRows(newest.stream().map(this::newTopicRow).toList());
    }
  }

  /**
   * A topic as forums list them, one row to click: the title, below it how often and when it was
   * answered; a topic answered within the last day is marked new. A long title ends in "…" instead
   * of widening the column.
   */
  @VisibleForTesting
  JComponent newTopicRow(final ForumTopic topic) {
    final JLabel title = newShrinkingLabel(topic.title(), Font.BOLD, 1);
    final JLabel details =
        new JLabel(
            repliesText(topic.replies())
                + "  ·  "
                + SavedGamesPanel.whenSaved(
                    bundle, topic.lastPost(), LocalDate.now(), ZoneId.systemDefault()));
    details.setForeground(LookAndFeel.getSecondaryTextColor());

    final JPanel titleLine = new JPanel(new BorderLayout(MenuSpacing.S, 0));
    titleLine.setOpaque(false);
    titleLine.add(title, BorderLayout.CENTER);
    if (isNew(topic.lastPost(), Instant.now())) {
      // in its own size on the middle of the title, not stretched to its height
      final JPanel badge = new JPanel(new GridBagLayout());
      badge.setOpaque(false);
      badge.add(Pill.newAccented(bundle.getText(PREFIX + "New")));
      titleLine.add(badge, BorderLayout.WEST);
    }
    final JPanel texts = new JPanel(new BorderLayout());
    texts.setOpaque(false);
    texts.add(titleLine, BorderLayout.NORTH);
    texts.add(details, BorderLayout.CENTER);
    return FeedRow.newRow(
        texts, topic.title(), () -> OpenFileUtility.openUrl(this, topic.page().toString()));
  }

  private String repliesText(final int replies) {
    return switch (replies) {
      case 0 -> bundle.getText(PREFIX + "NoReplies");
      case 1 -> bundle.getText(PREFIX + "OneReply");
      default -> bundle.getText(PREFIX + "Replies", replies);
    };
  }

  /** Whether the topic was answered within the last day. */
  @VisibleForTesting
  static boolean isNew(final Instant lastPost, final Instant now) {
    return lastPost.isAfter(now.minus(Duration.ofDays(1)));
  }
}
