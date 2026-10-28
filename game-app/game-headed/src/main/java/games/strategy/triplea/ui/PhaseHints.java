package games.strategy.triplea.ui;

import games.strategy.engine.data.GameStep;
import games.strategy.engine.data.GameStep.PropertyKeys;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.function.Predicate;
import lombok.experimental.UtilityClass;

/**
 * Plain-language hints on what a player does in a phase of their turn, for new players. The phase
 * is told apart like TripleAPlayer does it: by the step name, with step properties deciding between
 * combat and non-combat moves and between bids and normal purchase or placement.
 */
@UtilityClass
class PhaseHints {
  private static final String COMBAT_MOVE =
      "Attack: click units on the map, then click an enemy territory to move them there."
          + " Click Done when all attacks are set.";
  private static final String AIRBORNE_COMBAT_MOVE =
      "Drop airborne units into territories you are attacking, then click Done.";
  private static final String NON_COMBAT_MOVE =
      "Move units that did not fight and land your planes on friendly territory,"
          + " then click Done.";
  private static final String BID = "Spend your bid on extra starting units, then click Done.";
  private static final String PURCHASE =
      "Click Buy to spend your income on new units. You place them later, in the place phase.";
  private static final String BID_PLACE =
      "Place the units you bought with your bid, then click Done.";
  private static final String PLACE = "Click a territory where you can place the units you bought.";

  private static final List<Entry<Predicate<String>, String>> OTHER_PHASES =
      List.of(
          Map.entry(
              GameStep::isTechStepName,
              "Buy research dice to try to discover new technologies, or click Done to skip."),
          Map.entry(
              GameStep::isTechActivationStepName, "Technologies you discovered take effect now."),
          Map.entry(
              GameStep::isPoliticsStepName,
              "Change your relations with other nations, eg declare war, or click Done to skip."),
          Map.entry(
              GameStep::isUserActionsStepName,
              "Choose one of the special actions of this map, or click Done to skip."),
          Map.entry(
              GameStep::isBattleStepName,
              "Fight your battles: click a battle in the list to resolve it."),
          Map.entry(
              GameStep::isEndTurnStepName,
              "Your turn ends and you collect income from your territories."));

  /** Returns the hint for the step, empty for steps without a hint. */
  static Optional<String> forStep(final GameStep step) {
    final String stepName = step.getName();
    if (GameStep.isMoveStepName(stepName)) {
      if (step.isNonCombat()) {
        return Optional.of(NON_COMBAT_MOVE);
      }
      return Optional.of(
          GameStep.isAirborneCombatMoveStepName(stepName) ? AIRBORNE_COMBAT_MOVE : COMBAT_MOVE);
    }
    if (GameStep.isPurchaseOrBidStepName(stepName)) {
      return Optional.of(isBid(step, GameStep::isBidStepName) ? BID : PURCHASE);
    }
    if (GameStep.isPlaceStepName(stepName)) {
      return Optional.of(isBid(step, GameStep::isBidPlaceStepName) ? BID_PLACE : PLACE);
    }
    return OTHER_PHASES.stream()
        .filter(phase -> phase.getKey().test(stepName))
        .findFirst()
        .map(Entry::getValue);
  }

  /** Like GameStepPropertiesHelper.isBid: the "bid" step property wins over the step name. */
  private static boolean isBid(final GameStep step, final Predicate<String> isBidStepName) {
    final String bid = step.getProperties().getProperty(PropertyKeys.BID);
    return bid != null ? Boolean.parseBoolean(bid) : isBidStepName.test(step.getName());
  }
}
