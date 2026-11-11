package games.strategy.engine.data.export;

import games.strategy.engine.data.DefaultNamed;
import games.strategy.engine.data.GameData;
import games.strategy.engine.data.GamePlayer;
import games.strategy.engine.data.GameStep;
import games.strategy.engine.data.Rule;
import games.strategy.engine.data.TechnologyFrontier;
import games.strategy.engine.data.TestAttachment;
import games.strategy.engine.data.gameparser.GameParser;
import games.strategy.engine.data.gameparser.XmlGameElementMapper;
import games.strategy.triplea.delegate.TestDelegate;
import games.strategy.triplea.xml.TestMapGameData;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import org.triplea.java.collections.IntegerMap;

/**
 * The test maps as files, and a game as a list of its facts, to tell whether a game went through
 * XML unchanged.
 */
@UtilityClass
class GameFacts {

  /** A copy of the test map's XML, which lives in the test fixtures' jar. */
  static Path givenMapFile(final TestMapGameData map, final Path folder) throws IOException {
    final Path copy = folder.resolve("original.xml");
    try (InputStream xml = GameFacts.class.getClassLoader().getResourceAsStream(map.toString())) {
      Files.copy(xml, copy);
    }
    return copy;
  }

  /** The game with its attachments' options as written, and the test maps' own classes. */
  static Optional<GameData> load(final Path xml) {
    return GameParser.parse(
        xml,
        new XmlGameElementMapper(
            Map.of("TestDelegate", TestDelegate::new),
            Map.of("TestAttachment", TestAttachment::new)),
        true);
  }

  /** One line per fact of the game, independent of the order in the XML. */
  static List<String> describe(final GameData data) {
    final List<String> facts = new ArrayList<>();
    facts.add("dice sides " + data.getDiceSides());
    facts.add("round " + data.getSequence().getRound());
    data.getMap()
        .getTerritories()
        .forEach(
            territory -> {
              facts.add(
                  "territory %s water=%s owner=%s neighbors=%s"
                      .formatted(
                          territory.getName(),
                          territory.isWater(),
                          territory.getOwner().getName(),
                          names(data.getMap().getNeighbors(territory))));
              territory
                  .getUnitCollection()
                  .getPlayersWithUnits()
                  .forEach(
                      owner ->
                          facts.add(
                              "units in %s of %s: %s"
                                  .formatted(
                                      territory.getName(),
                                      owner.getName(),
                                      amounts(
                                          territory.getUnitCollection().getUnitsByType(owner)))));
            });
    for (final GamePlayer player : data.getPlayerList()) {
      facts.add(
          "player %s optional=%s hidden=%s canBeDisabled=%s production=%s repair=%s resources=%s"
              .formatted(
                  player.getName(),
                  player.getOptional(),
                  player.isHidden(),
                  player.getCanBeDisabled(),
                  Optional.ofNullable(player.getProductionFrontier()).map(DefaultNamed::getName),
                  Optional.ofNullable(player.getRepairFrontier()).map(DefaultNamed::getName),
                  amounts(player.getResources().getResourcesCopy())));
      player.getTechnologyFrontierList().getFrontiers().stream()
          .map(frontier -> "techs of " + player.getName() + ": " + techs(frontier))
          .forEach(facts::add);
      for (final GamePlayer other : data.getPlayerList()) {
        facts.add(
            "relationship %s %s %s since %s"
                .formatted(
                    player.getName(),
                    other.getName(),
                    data.getRelationshipTracker().getRelationshipType(player, other).getName(),
                    data.getRelationshipTracker().getRoundRelationshipWasCreated(player, other)));
      }
    }
    data.getAllianceTracker()
        .getAlliances()
        .forEach(
            alliance ->
                facts.add(
                    "alliance %s: %s"
                        .formatted(
                            alliance,
                            names(data.getAllianceTracker().getPlayersInAlliance(alliance)))));
    data.getUnitTypeList().forEach(type -> facts.add("unit type " + type.getName()));
    data.getResourceList()
        .getResources()
        .forEach(
            resource ->
                facts.add(
                    "resource %s shown for %s"
                        .formatted(resource.getName(), names(resource.getPlayers()))));
    data.getTerritoryEffectList().keySet().forEach(effect -> facts.add("effect " + effect));
    data.getRelationshipTypeList().getAllRelationshipTypes().stream()
        .map(type -> "relationship type " + type.getName())
        .forEach(facts::add);
    data.getProductionRuleList().getProductionRules().stream()
        .map(rule -> "production rule " + rule(rule))
        .forEach(facts::add);
    data.getRepairRules().getRepairRules().stream()
        .map(rule -> "repair rule " + rule(rule))
        .forEach(facts::add);
    data.getProductionFrontierList().getProductionFrontierNames().stream()
        .map(data.getProductionFrontierList()::getProductionFrontier)
        .map(frontier -> "production frontier " + frontier.getName() + names(frontier.getRules()))
        .forEach(facts::add);
    data.getRepairFrontierList().getRepairFrontierNames().stream()
        .map(data.getRepairFrontierList()::getRepairFrontier)
        .map(frontier -> "repair frontier " + frontier.getName() + names(frontier.getRules()))
        .forEach(facts::add);
    facts.add("techs " + techs(data.getTechnologyFrontier()));
    data.getDelegates().stream()
        .map(
            delegate ->
                "delegate %s %s %s"
                    .formatted(
                        delegate.getName(),
                        delegate.getClass().getName(),
                        delegate.getDisplayName()))
        .forEach(facts::add);
    int index = 0;
    for (final GameStep step : data.getSequence()) {
      facts.add(
          "step %d %s delegate=%s player=%s display=%s maxRunCount=%s"
              .formatted(
                  index++,
                  step.getName(),
                  step.getDelegate().getName(),
                  Optional.ofNullable(step.getPlayerId()).map(DefaultNamed::getName),
                  step.getDisplayName(),
                  step.getMaxRunCount()));
    }
    data.getProperties()
        .getConstantPropertiesByName()
        .forEach((name, value) -> facts.add("property " + name + "=" + value));
    data.getProperties()
        .getEditablePropertiesByName()
        .forEach((name, value) -> facts.add("editable property " + name + "=" + value.getValue()));
    data.getAttachmentOrderAndValues()
        .forEach(
            attachment ->
                facts.add(
                    "attachment %s on %s: %s"
                        .formatted(
                            attachment.getFirst().getName(),
                            // the test maps' TestAttachment keeps nothing it is attached to
                            Optional.ofNullable(attachment.getFirst().getAttachedTo())
                                .map(DefaultNamed.class::cast)
                                .map(DefaultNamed::getName),
                            attachment.getSecond())));
    return facts;
  }

  private static String names(final Collection<? extends DefaultNamed> named) {
    return named.stream()
        .map(DefaultNamed::getName)
        .sorted()
        .collect(Collectors.joining(",", "[", "]"));
  }

  /** The amounts by name; none of something is the same as no entry. */
  private static String amounts(final IntegerMap<? extends DefaultNamed> amounts) {
    return amounts.entrySet().stream()
        .filter(amount -> amount.getValue() != 0)
        .map(amount -> amount.getKey().getName() + "=" + amount.getValue())
        .sorted()
        .collect(Collectors.joining(",", "[", "]"));
  }

  private static String techs(final TechnologyFrontier frontier) {
    return frontier.getName() + names(frontier.getTechs());
  }

  private static String rule(final Rule rule) {
    return "%s costs=%s results=%s"
        .formatted(rule.getName(), amounts(rule.getCosts()), amounts(rule.getResults()));
  }
}
