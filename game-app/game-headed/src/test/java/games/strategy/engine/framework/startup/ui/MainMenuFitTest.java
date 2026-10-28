package games.strategy.engine.framework.startup.ui;

import static org.assertj.core.api.Assertions.assertThat;

import games.strategy.engine.framework.startup.ui.MainMenuFit.Part;
import java.awt.Dimension;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

final class MainMenuFitTest {
  /** The entries and the version: 600 wide, 500 high. */
  private static final Dimension ALWAYS_SHOWN = new Dimension(600, 500);

  /** Left 100 + 50 + 200 = 350 high; right 300 + 200 + 400 = 900 high, 800 wide. */
  private static final Map<Part, Dimension> PREFERRED =
      Map.of(
          Part.LOBBY_STATUS, new Dimension(300, 100),
          Part.TIP, new Dimension(0, 50),
          Part.BRAND, new Dimension(400, 200),
          Part.COMMUNITY, new Dimension(800, 300),
          Part.UPDATES, new Dimension(800, 200),
          Part.MAP_OF_THE_DAY, new Dimension(800, 400));

  @DisplayName("A large window shows every part")
  @Test
  void largeWindowShowsAll() {
    assertThat(fitting(1400, 900))
        .as("menu and column side by side, each column fits its height")
        .containsExactlyInAnyOrder(Part.values());
  }

  @DisplayName(
      "Too little height empties the right column: updates, then map of the day, then forum")
  @Test
  void rightColumnEmptiesInItsOrder() {
    assertThat(fitting(1400, 899))
        .as("one pixel short: the updates go first")
        .doesNotContain(Part.UPDATES)
        .contains(Part.MAP_OF_THE_DAY, Part.COMMUNITY);
    assertThat(fitting(1400, 699))
        .as("forum and map of the day do not fit together: only the forum stays")
        .doesNotContain(Part.UPDATES, Part.MAP_OF_THE_DAY)
        .contains(Part.COMMUNITY);
  }

  @DisplayName("Too little width hides the whole right column, the left column stays")
  @Test
  void narrowWindowHidesRightColumn() {
    assertThat(fitting(1399, 900))
        .as("600 for the menu and 800 for the column do not fit into 1399")
        .containsExactlyInAnyOrder(Part.LOBBY_STATUS, Part.TIP, Part.BRAND);
  }

  @DisplayName("Too little height on the left hides lobby status, then tip, then logo and name")
  @Test
  void leftColumnEmptiesInItsOrder() {
    assertThat(fitting(600, 849))
        .as("the entries need 500; 349 are left for 350: the lobby status goes")
        .containsExactlyInAnyOrder(Part.TIP, Part.BRAND);
    assertThat(fitting(600, 749))
        .as("249 left for tip and logo: the tip goes too")
        .containsExactly(Part.BRAND);
    assertThat(fitting(600, 699))
        .as("logo and name need 200; with 199 only the entries stay")
        .isEmpty();
  }

  @DisplayName("A wide lobby status counts to the width of the left column")
  @Test
  void wideLeftPartNeedsRoom() {
    final Map<Part, Dimension> wideStatus =
        Map.of(Part.LOBBY_STATUS, new Dimension(700, 10), Part.COMMUNITY, new Dimension(800, 10));

    assertThat(MainMenuFit.fittingParts(new Dimension(1499, 900), ALWAYS_SHOWN, wideStatus))
        .as("700 + 800 do not fit into 1499, so the column goes, the status stays")
        .contains(Part.LOBBY_STATUS)
        .doesNotContain(Part.COMMUNITY);
  }

  private static Set<Part> fitting(final int width, final int height) {
    return MainMenuFit.fittingParts(new Dimension(width, height), ALWAYS_SHOWN, PREFERRED);
  }
}
