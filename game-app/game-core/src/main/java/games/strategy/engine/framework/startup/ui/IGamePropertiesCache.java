package games.strategy.engine.framework.startup.ui;

import games.strategy.engine.data.GameData;
import java.util.List;

/**
 * Keeps a player's rule sets of a game: its options under a name, to use again. The GameProperties
 * can't be replaced, only modified, so loading one modifies those inside GameData.
 */
public interface IGamePropertiesCache {
  /** Keeps the game's options as a rule set of the given name, replacing one of that name. */
  void saveRuleSet(GameData gameData, String name);

  /** The names of the game's rule sets, sorted. */
  List<String> getRuleSetNames(GameData gameData);

  /** Sets the game's options to those of its rule set of the given name. */
  void loadRuleSetInto(GameData gameData, String name);

  void deleteRuleSet(GameData gameData, String name);
}
