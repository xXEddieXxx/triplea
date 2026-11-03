package games.strategy.engine.framework.startup.ui;

import static org.assertj.core.api.Assertions.assertThat;

import games.strategy.engine.framework.startup.ui.MainMenuFit.Part;
import java.awt.Dimension;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

final class MainMenuFitTest {
  /** The entries and the menu's footer with their least room. */
  private static final int MENU_ALWAYS = 500;

  /** Stage at least 320 x 280; each area 380 wide, the band 200 high; the logo 100 high. */
  private static final Map<Part, Dimension> LEAST =
      Map.of(
          Part.BRAND, new Dimension(0, 100),
          Part.MAP_OF_THE_DAY, new Dimension(320, 280),
          Part.COMMUNITY, new Dimension(380, 200),
          Part.UPDATES, new Dimension(380, 180));

  @DisplayName("A large window shows every part")
  @Test
  void largeWindowShowsAll() {
    assertThat(fitting(1600, 900))
        .as("room for the stage, the band with two areas and the logo")
        .containsExactlyInAnyOrder(Part.values());
  }

  @DisplayName("A band too narrow for two areas shows only the forum")
  @Test
  void narrowBandDropsUpdates() {
    assertThat(fitting(759, 900))
        .as("380 + 380 do not fit into 759")
        .contains(Part.MAP_OF_THE_DAY, Part.COMMUNITY)
        .doesNotContain(Part.UPDATES);
  }

  @DisplayName("The band goes when the map of the day would get too low above it")
  @Test
  void lowWindowDropsBand() {
    assertThat(fitting(1600, 479))
        .as("the map needs 280 above the band's 200; 479 leaves it 279")
        .contains(Part.MAP_OF_THE_DAY)
        .doesNotContain(Part.COMMUNITY, Part.UPDATES);
  }

  @DisplayName("The updates go when they would make the band too high for the map")
  @Test
  void tallUpdatesDropFirst() {
    final Map<Part, Dimension> tallUpdates =
        Map.of(
            Part.MAP_OF_THE_DAY, new Dimension(320, 280),
            Part.COMMUNITY, new Dimension(380, 200),
            Part.UPDATES, new Dimension(380, 300));

    assertThat(MainMenuFit.fittingParts(new Dimension(1600, 579), MENU_ALWAYS, tallUpdates))
        .as("the forum leaves the map 379, the updates would leave it 279")
        .contains(Part.COMMUNITY)
        .doesNotContain(Part.UPDATES);
  }

  @DisplayName("A stage too narrow or too low goes as a whole, the menu stays")
  @Test
  void smallStageGoes() {
    assertThat(fitting(319, 900)).as("the stage needs 320 width").containsExactly(Part.BRAND);
    assertThat(fitting(1600, 279))
        .as("the stage needs 280 height; the menu is too low for the logo as well")
        .isEmpty();
  }

  @DisplayName("The logo goes when the menu is too low for it above the entries")
  @Test
  void lowMenuDropsLogo() {
    assertThat(fitting(1600, 599))
        .as("the entries need 500, 99 are left for the logo's 100")
        .doesNotContain(Part.BRAND)
        .contains(Part.MAP_OF_THE_DAY);
  }

  private static Set<Part> fitting(final int width, final int height) {
    return MainMenuFit.fittingParts(new Dimension(width, height), MENU_ALWAYS, LEAST);
  }
}
