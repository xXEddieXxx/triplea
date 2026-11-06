package games.strategy.engine.data.properties;

import java.util.Arrays;
import java.util.Locale;
import java.util.regex.Pattern;
import lombok.Getter;

/**
 * The groups that the game options are shown in, so that a player finds eg everything about dice
 * together. Maps do not name a group for their options; an option goes to the first group with a
 * word of its name, in the order below, else to {@link #OTHER}.
 */
enum GameOptionCategory {
  RULES("Rules", "\\bww2v", "\\blhtr\\b", "\\brules?\\b"),
  VICTORY("Victory", "victory", "surrender", "\\bvcs?\\b", "objective", "projection of power"),
  TECHNOLOGY("Technology", "\\btech", "heavy bomber", "super sub", "rocket"),
  PRODUCTION(
      "Production & income",
      "\\bbid\\b",
      "produc",
      "place",
      "\\bpus?\\b",
      "income",
      "construction",
      "factor",
      "shipyard",
      "repair",
      "convoy",
      "blockade"),
  BATTLES(
      "Dice & battles",
      "low luck",
      "dice",
      "battle",
      "casualt",
      "retreat",
      "\\baa\\b",
      "antiaircraft",
      "bombard",
      "bombing",
      "\\bsbr\\b",
      "scramble",
      "kamikaze",
      "suicide",
      "sneak",
      "submer",
      "hari-kari",
      "fight",
      "attack",
      "two hit",
      "damage"),
  MOVEMENT(
      "Movement",
      "move",
      "blitz",
      "canal",
      "transport",
      "fly ?over",
      "impassable",
      "paratroop",
      "airborne",
      "fuel",
      "carrier",
      "sea zone",
      "\\bsubs?\\b"),
  POLITICS("Politics & players", "politic", "relationship", "alliance", "neutral", "capital"),
  OTHER("Other");

  @Getter private final String title;
  private final Pattern words;

  GameOptionCategory(final String title, final String... words) {
    this.title = title;
    // no words for OTHER: a pattern that never matches
    this.words = Pattern.compile(words.length == 0 ? "(?!)" : String.join("|", words));
  }

  /** The group of the option with this name. */
  static GameOptionCategory of(final String optionName) {
    final String name = optionName.toLowerCase(Locale.ROOT);
    return Arrays.stream(values())
        .filter(category -> category.words.matcher(name).find())
        .findFirst()
        .orElse(OTHER);
  }
}
