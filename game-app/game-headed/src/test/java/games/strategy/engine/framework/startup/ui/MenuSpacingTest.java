package games.strategy.engine.framework.startup.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

final class MenuSpacingTest {
  @DisplayName("The gutter is a fortieth of the window width on the 8 px grid, 24 to 64 px")
  @Test
  void gutterGrowsWithWindowWithinBounds() {
    assertThat(
            List.of(
                MenuSpacing.gutter(960),
                MenuSpacing.gutter(1600),
                MenuSpacing.gutter(1920),
                MenuSpacing.gutter(4000)))
        .as("small windows keep 24 px, 1600 px get 40, 1920 px 48, very wide ones stop at 64")
        .containsExactly(24, 40, 48, 64);
    assertThat(IntStream.rangeClosed(800, 3000).map(MenuSpacing::gutter))
        .as("every gutter is a step of the 8 px grid")
        .allMatch(gutter -> gutter % MenuSpacing.S == 0);
  }
}
