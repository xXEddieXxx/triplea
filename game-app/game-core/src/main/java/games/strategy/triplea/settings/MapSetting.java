package games.strategy.triplea.settings;

import java.util.Locale;
import javax.swing.JComponent;

/**
 * A setting of the running game's map, eg its zoom or the size of its units, which the game window
 * adds to the In game section when it opens the settings. Its control changes the map at once, as
 * the View menu did; the map keeps the value itself, eg per map, so the settings window neither
 * saves nor resets it.
 *
 * @param title What the setting changes, in a few words, eg "Unit size".
 * @param description A sentence below the title.
 * @param control The switch, list or field that changes the map.
 */
public record MapSetting(String title, String description, JComponent control) {

  /** Whether the search text is part of the title or the description, ignoring case. */
  boolean matches(final String searchText) {
    final String text = searchText.strip().toLowerCase(Locale.ROOT);
    return title.toLowerCase(Locale.ROOT).contains(text)
        || description.toLowerCase(Locale.ROOT).contains(text);
  }
}
