package games.strategy.engine.framework.startup.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
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
}
