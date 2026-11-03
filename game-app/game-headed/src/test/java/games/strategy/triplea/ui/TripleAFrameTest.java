package games.strategy.triplea.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import games.strategy.triplea.settings.ClientSetting;
import games.strategy.triplea.ui.TripleAFrame.ScrollStep;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.sonatype.goodies.prefs.memory.MemoryPreferences;

/**
 * Exercises the pure scroll math extracted from {@code ArrowKeyScroller}: the frame-interval clamp
 * and the sub-pixel residual accumulation that carries fractional pan velocity across frames. The
 * timer, key-event wiring, and refresh-rate lookup stay display-bound and are not covered here.
 * Also the decision whether the start of the player's turn flashes the taskbar ({@code
 * isAlertWanted}).
 */
final class TripleAFrameTest {

  // Tolerance for accumulated sub-pixel residuals, which are exact in binary only by luck.
  private static final double RESIDUAL_TOLERANCE = 1.0e-9;

  @Nested
  final class ClampFrameIntervalMs {
    @Test
    void sixtyHertzRoundsAndClampsToSixteenMs() {
      // 1000/60 = 16.67 rounds to 17, clamped down to the 16 ms ceiling.
      assertEquals(16, TripleAFrame.clampFrameIntervalMs(60));
    }

    @Test
    void oneHundredTwentyHertzMapsToEightMs() {
      assertEquals(8, TripleAFrame.clampFrameIntervalMs(120));
    }

    @Test
    void oneHundredFortyFourHertzClampsToEightMsFloor() {
      // 1000/144 = 6.94 rounds to 7, raised to the 8 ms floor.
      assertEquals(8, TripleAFrame.clampFrameIntervalMs(144));
    }

    @Test
    void thirtyHertzClampsToSixteenMsCeiling() {
      assertEquals(16, TripleAFrame.clampFrameIntervalMs(30));
    }

    @Test
    void unknownRateFallsBackToOneHundredHertz() {
      // A non-positive rate stands in for an unknown/headless display; 1000/100 = 10 ms.
      assertEquals(10, TripleAFrame.clampFrameIntervalMs(0));
      assertEquals(10, TripleAFrame.clampFrameIntervalMs(-1));
    }
  }

  @Nested
  final class ComputeScrollStep {
    private static final double SUB_PIXEL_PER_TICK = 0.4;

    @Test
    void subPixelDistanceAccumulatesUntilItYieldsAWholeStep() {
      final Set<Integer> right = Set.of(KeyEvent.VK_RIGHT);

      final ScrollStep first = TripleAFrame.computeScrollStep(0, 0, SUB_PIXEL_PER_TICK, right);
      assertEquals(0, first.stepX());
      assertEquals(0.4, first.residualX(), RESIDUAL_TOLERANCE);

      final ScrollStep second =
          TripleAFrame.computeScrollStep(first.residualX(), 0, SUB_PIXEL_PER_TICK, right);
      assertEquals(0, second.stepX());
      assertEquals(0.8, second.residualX(), RESIDUAL_TOLERANCE);

      final ScrollStep third =
          TripleAFrame.computeScrollStep(second.residualX(), 0, SUB_PIXEL_PER_TICK, right);
      assertEquals(1, third.stepX());
      assertEquals(0.2, third.residualX(), RESIDUAL_TOLERANCE);
    }

    @Test
    void leftPanTruncatesWithSameMagnitudeAsRightPan() {
      // Guards the truncate-toward-zero direction: left and right must travel equal distance for
      // equal held time, or panning would be asymmetric.
      final Set<Integer> left = Set.of(KeyEvent.VK_LEFT);

      ScrollStep step = TripleAFrame.computeScrollStep(0, 0, SUB_PIXEL_PER_TICK, left);
      step = TripleAFrame.computeScrollStep(step.residualX(), 0, SUB_PIXEL_PER_TICK, left);
      step = TripleAFrame.computeScrollStep(step.residualX(), 0, SUB_PIXEL_PER_TICK, left);

      assertEquals(-1, step.stepX());
      assertEquals(-0.2, step.residualX(), RESIDUAL_TOLERANCE);
    }

    @Test
    void upPanTruncatesTowardNegativeYWithSameMagnitudeAsLeftPan() {
      // Guards the VK_UP -> negative-y mapping so vertical panning mirrors the horizontal sign
      // convention; without it only three of the four arrow directions have a direct sign check.
      final Set<Integer> up = Set.of(KeyEvent.VK_UP);

      ScrollStep step = TripleAFrame.computeScrollStep(0, 0, SUB_PIXEL_PER_TICK, up);
      step = TripleAFrame.computeScrollStep(0, step.residualY(), SUB_PIXEL_PER_TICK, up);
      step = TripleAFrame.computeScrollStep(0, step.residualY(), SUB_PIXEL_PER_TICK, up);

      assertEquals(-1, step.stepY());
      assertEquals(-0.2, step.residualY(), RESIDUAL_TOLERANCE);
    }

    @Test
    void diagonalAccumulatesEachAxisIndependently() {
      final ScrollStep step =
          TripleAFrame.computeScrollStep(0, 0, 1.5, Set.of(KeyEvent.VK_RIGHT, KeyEvent.VK_DOWN));

      assertEquals(1, step.stepX());
      assertEquals(1, step.stepY());
      assertEquals(0.5, step.residualX(), RESIDUAL_TOLERANCE);
      assertEquals(0.5, step.residualY(), RESIDUAL_TOLERANCE);
    }

    @Test
    void opposingKeysCancelToNoMotion() {
      final ScrollStep step =
          TripleAFrame.computeScrollStep(0, 0, 5.0, Set.of(KeyEvent.VK_LEFT, KeyEvent.VK_RIGHT));

      assertEquals(0, step.stepX());
      assertEquals(0, step.residualX(), RESIDUAL_TOLERANCE);
    }
  }

  @Nested
  final class IsAlertWanted {
    @BeforeEach
    void givenClientSettings() {
      ClientSetting.setPreferences(new MemoryPreferences());
    }

    @AfterEach
    void resetClientSettings() {
      ClientSetting.resetPreferences();
    }

    @DisplayName("The turn alert flashes only while no TripleA window is active and it is on")
    @Test
    void onlyInBackgroundAndWhenOn() {
      // a mock, not a real window, which a headless test run could not create
      final boolean inTripleA = TripleAFrame.isAlertWanted(mock(Window.class));
      final boolean inBackground = TripleAFrame.isAlertWanted(null);
      ClientSetting.alertOnMyTurn.setValue(false);

      final boolean whenOff = TripleAFrame.isAlertWanted(null);

      assertThat(List.of(inTripleA, inBackground, whenOff))
          .as("not while the player looks at TripleA, by default in the background, never off")
          .containsExactly(false, true, false);
    }
  }
}
