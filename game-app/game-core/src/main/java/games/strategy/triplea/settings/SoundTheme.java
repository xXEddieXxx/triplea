package games.strategy.triplea.settings;

import java.util.Optional;
import javax.annotation.Nullable;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** Which era of sound clips plays: the one the map asks for, or one the player chose. */
@AllArgsConstructor
public enum SoundTheme {
  MAP("Follow the map", null),
  WW2("WW2", "ww2"),
  CLASSICAL("Classical", "classical"),
  PREINDUSTRIAL("Preindustrial", "preindustrial"),
  FUTURE("Future", "future");

  @Getter private final String label;

  /** The folder under assets/sounds, null to keep the map's choice. */
  @Nullable private final String eraFolder;

  public Optional<String> getEraFolder() {
    return Optional.ofNullable(eraFolder);
  }
}
