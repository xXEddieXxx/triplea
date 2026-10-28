package games.strategy.engine.framework.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Frame;
import java.awt.Rectangle;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

final class WindowPlacementTest {
  @DisplayName("The remembered window size is read back as written")
  @Test
  void boundsRoundTrip() {
    final Rectangle bounds = new Rectangle(-8, 20, 1600, 900);

    assertThat(WindowPlacement.parse(WindowPlacement.format(bounds)))
        .as("a window on a second screen left of the first has a negative x")
        .contains(bounds);
  }

  @DisplayName("A broken or tiny remembered size is ignored, the window opens maximized")
  @Test
  void brokenBoundsIgnored() {
    assertThat(
            List.of(
                WindowPlacement.parse("garbage"),
                WindowPlacement.parse("1,2,3"),
                WindowPlacement.parse("0,0,50,40")))
        .as("a window too small to use must not come back")
        .allMatch(Optional::isEmpty);
  }

  @DisplayName("A remembered window is used only if it still lies on a connected screen")
  @Test
  void rememberedWindowMustBeOnScreen() {
    final List<Rectangle> oneScreen = List.of(new Rectangle(0, 0, 1920, 1080));

    assertThat(
            List.of(
                WindowPlacement.isOnScreen(new Rectangle(100, 50, 1400, 900), oneScreen),
                WindowPlacement.isOnScreen(new Rectangle(2600, 100, 1400, 900), oneScreen),
                WindowPlacement.isOnScreen(new Rectangle(1880, 100, 1400, 900), oneScreen)))
        .as("a window on an unplugged second screen opens maximized instead, also one barely in")
        .containsExactly(true, false, false);
  }

  @DisplayName("Only a window the player sized is remembered, not a maximized or full screen one")
  @Test
  void onlySizedWindowsRemembered() {
    final Rectangle bounds = new Rectangle(100, 50, 1400, 900);

    assertThat(
            List.of(
                WindowPlacement.boundsToRemember(Frame.NORMAL, false, bounds),
                WindowPlacement.boundsToRemember(Frame.MAXIMIZED_BOTH, false, bounds),
                WindowPlacement.boundsToRemember(Frame.NORMAL, true, bounds)))
        .as("maximized and full screen windows open that way again, at the size of the screen")
        .containsExactly(Optional.of("100,50,1400,900"), Optional.empty(), Optional.empty());
  }
}
