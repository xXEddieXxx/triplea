package games.strategy.engine.framework.startup.ui;

import static org.assertj.core.api.Assertions.assertThat;

import games.strategy.engine.framework.I18nEngineFramework;
import games.strategy.engine.framework.map.file.system.loader.InstalledMapsListing;
import games.strategy.engine.framework.startup.ui.ForumTopics.ForumTopic;
import java.awt.Component;
import java.awt.Container;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.plaf.basic.BasicHTML;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

final class CommunityPanelTest {
  private static final Instant NOW = Instant.parse("2026-10-28T10:00:00Z");

  @DisplayName("A topic answered within the last day is marked new, an older one is not")
  @Test
  void newMeansAnsweredToday() {
    assertThat(
            List.of(
                CommunityPanel.isNew(NOW.minus(Duration.ofHours(2)), NOW),
                CommunityPanel.isNew(NOW.minus(Duration.ofHours(23)), NOW),
                CommunityPanel.isNew(NOW.minus(Duration.ofHours(25)), NOW)))
        .as("the mark shows what happened since yesterday, not what is merely in the list")
        .containsExactly(true, true, false);
  }

  @DisplayName("A long topic title does not widen the column, it ends in an ellipsis")
  @Test
  void longTitleKeepsColumnWidth() {
    final CommunityPanel community = givenOfflineCommunity();
    final ForumTopic longTopic =
        new ForumTopic(
            "A very long topic title ".repeat(10),
            URI.create("https://forums.triplea-game.org/topic/1"),
            NOW,
            3);

    assertThat(community.newTopicRow(longTopic).getPreferredSize().width)
        .as("wider than one area of the news band, it would push the updates out of the window")
        .isLessThan(MetaSetupPanel.AREA_LEAST_WIDTH);
  }

  @DisplayName("A topic title is shown as written, never read as HTML")
  @Test
  void titleIsNeverHtml() {
    final String title = "<html><img src=https://example.org/x.png width=2000>";
    final JComponent row =
        givenOfflineCommunity()
            .newTopicRow(
                new ForumTopic(
                    title, URI.create("https://forums.triplea-game.org/topic/1"), NOW, 3));

    assertThat(labelsOf(row).filter(label -> title.equals(label.getText())))
        .as("anyone can write a title; as HTML it could load pictures from other servers")
        .singleElement()
        .satisfies(label -> assertThat(label.getClientProperty(BasicHTML.propertyKey)).isNull());
  }

  private static Stream<JLabel> labelsOf(final Component component) {
    if (component instanceof JLabel label) {
      return Stream.of(label);
    }
    return component instanceof Container container
        ? Arrays.stream(container.getComponents()).flatMap(CommunityPanelTest::labelsOf)
        : Stream.empty();
  }

  /** No maps, no forum, no lobby server: the test stays off the disk and the network. */
  private static CommunityPanel givenOfflineCommunity() {
    return new CommunityPanel(
        I18nEngineFramework.get(),
        new MainMenuSources(
            () -> InstalledMapsListing.builder().build(),
            List::of,
            List::of,
            List::of,
            Optional::empty,
            Optional::empty));
  }
}
