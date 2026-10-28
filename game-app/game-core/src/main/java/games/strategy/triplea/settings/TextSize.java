package games.strategy.triplea.settings;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * How large the text of the client is. Even Normal is a little larger than the look and feel's own
 * font, so that nothing is tiring to read.
 */
@AllArgsConstructor
@Getter
public enum TextSize {
  NORMAL("Normal", 2),
  LARGE("Large", 4),
  VERY_LARGE("Very large", 6);

  private final String label;

  /** Points added to the default font of the look and feel. */
  private final int pointsAdded;
}
