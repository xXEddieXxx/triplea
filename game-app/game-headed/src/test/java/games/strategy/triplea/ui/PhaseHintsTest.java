package games.strategy.triplea.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import games.strategy.engine.data.GameData;
import games.strategy.engine.data.GameStep;
import games.strategy.engine.data.GameStep.PropertyKeys;
import games.strategy.engine.delegate.IDelegate;
import java.util.Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
final class PhaseHintsTest {
  @Mock private GameData data;
  @Mock private IDelegate delegate;

  @BeforeEach
  void givenDelegate() {
    when(delegate.getName()).thenReturn("delegate");
  }

  @DisplayName("Step names sharing an ending get the hint of the more specific step")
  @ParameterizedTest(name = "{0} -> hint mentioning \"{1}\"")
  @CsvSource({
    "germansBidPlace, your bid",
    "germansPlace, where you can place",
    "germansBid, your bid",
    "germansPurchase, your income",
    "germansAirborneCombatMove, airborne",
    "germansCombatMove, enemy territory",
    "germansNonCombatMove, did not fight",
    "germansTechActivation, take effect",
    "germansTech, research",
  })
  void hintOfMostSpecificStep(final String stepName, final String expectedHintPart) {
    final GameStep step = givenStep(stepName, new Properties());

    assertThat(PhaseHints.forStep(step))
        .as("the first matching hint is the one of the most specific step")
        .hasValueSatisfying(hint -> assertThat(hint).contains(expectedHintPart));
  }

  @DisplayName("A move step made non-combat by a step property gets the non-combat hint")
  @Test
  void nonCombatMoveByStepProperty() {
    final Properties properties = new Properties();
    properties.setProperty(PropertyKeys.NON_COMBAT_MOVE, "true");
    final GameStep step = givenStep("germansMove", properties);

    assertThat(PhaseHints.forStep(step))
        .as("TripleAPlayer plays this step as a non-combat move, so the hint must say so")
        .hasValueSatisfying(hint -> assertThat(hint).contains("did not fight"));
  }

  @DisplayName("A purchase step made a bid by a step property gets the bid hint")
  @Test
  void bidByStepProperty() {
    final Properties properties = new Properties();
    properties.setProperty(PropertyKeys.BID, "true");
    final GameStep step = givenStep("germansPurchase", properties);

    assertThat(PhaseHints.forStep(step))
        .as("TripleAPlayer plays this step as a bid, so the hint must say so")
        .hasValueSatisfying(hint -> assertThat(hint).contains("your bid"));
  }

  @DisplayName("Steps the engine does not show a phase for have no hint")
  @ParameterizedTest
  @ValueSource(strings = {"endRound", "gameInitDelegate"})
  void noHintForOtherSteps(final String stepName) {
    assertThat(PhaseHints.forStep(givenStep(stepName, new Properties()))).isEmpty();
  }

  private GameStep givenStep(final String stepName, final Properties properties) {
    return new GameStep(stepName, stepName, null, delegate, data, properties);
  }
}
