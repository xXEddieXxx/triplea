package games.strategy.engine.data.properties;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

final class GameOptionCategoryTest {

  @DisplayName("An option goes to the first group with a word of its name")
  @ParameterizedTest(name = "{0} -> {1}")
  @CsvSource({
    "WW2V3 Tech Model, RULES",
    "LHTR Heavy Bombers, RULES",
    "Total Victory, VICTORY",
    "Axis Honorable Victory VCs, VICTORY",
    "National Objectives, VICTORY",
    "Tech Development, TECHNOLOGY",
    "Low Luck for Technology, TECHNOLOGY",
    "Germans bid, PRODUCTION",
    "Unit Placement In Enemy Seas, PRODUCTION",
    "Multiply PUs, PRODUCTION",
    "maxFactoriesPerTerritory, PRODUCTION",
    "Low Luck, BATTLES",
    "Choose AA Casualties, BATTLES",
    "Submersible Subs, BATTLES",
    "Neutrals Are Blitzable, MOVEMENT",
    "Ignore Transport In Movement, MOVEMENT",
    "Use Politics, POLITICS",
    "neutralCharge, POLITICS",
    "Territory Turn Limit, OTHER",
  })
  void optionsGoToTheGroupOfTheirFirstWord(final String option, final GameOptionCategory group) {
    assertThat(GameOptionCategory.of(option))
        .as("the first group, in the order of the enum, with a word of the name")
        .isEqualTo(group);
  }
}
