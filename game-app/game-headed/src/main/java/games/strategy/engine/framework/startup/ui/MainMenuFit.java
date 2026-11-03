package games.strategy.engine.framework.startup.ui;

import java.awt.Dimension;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Which parts of the main menu fit into the window, so that it never needs scroll bars. The menu's
 * entries always stay. Right of them the map of the day is the stage, with the news band on its
 * lower part: the forum, and beside it the updates. As space runs out, the updates go first, when
 * the band is too narrow for two areas; then the whole band, when the map would get too low above
 * it; then the stage, when it would get too narrow or too low. The logo goes when the menu is too
 * low for it.
 */
final class MainMenuFit {
  /** The parts that may be hidden. */
  enum Part {
    UPDATES,
    COMMUNITY,
    MAP_OF_THE_DAY,
    BRAND
  }

  private MainMenuFit() {}

  /**
   * The parts that fit.
   *
   * @param stage The room right of the menu, as high as the window and the menu.
   * @param menuAlways The height the menu always needs: its entries with their least room.
   * @param least The least size of every part: for the map of the day the least size of the stage,
   *     for forum and updates the width of one area and the height of the band, for the logo its
   *     height.
   */
  static Set<Part> fittingParts(
      final Dimension stage, final int menuAlways, final Map<Part, Dimension> least) {
    final Set<Part> fitting = EnumSet.noneOf(Part.class);
    if (stage.height - menuAlways >= sizeOf(Part.BRAND, least).height) {
      fitting.add(Part.BRAND);
    }
    final Dimension map = sizeOf(Part.MAP_OF_THE_DAY, least);
    if (stage.width < map.width || stage.height < map.height) {
      return fitting;
    }
    fitting.add(Part.MAP_OF_THE_DAY);
    final Dimension community = sizeOf(Part.COMMUNITY, least);
    if (stage.width < community.width || stage.height - community.height < map.height) {
      return fitting;
    }
    fitting.add(Part.COMMUNITY);
    final Dimension updates = sizeOf(Part.UPDATES, least);
    final int bandHeight = Math.max(community.height, updates.height);
    if (stage.width >= community.width + updates.width && stage.height - bandHeight >= map.height) {
      fitting.add(Part.UPDATES);
    }
    return fitting;
  }

  private static Dimension sizeOf(final Part part, final Map<Part, Dimension> least) {
    return least.getOrDefault(part, new Dimension());
  }
}
