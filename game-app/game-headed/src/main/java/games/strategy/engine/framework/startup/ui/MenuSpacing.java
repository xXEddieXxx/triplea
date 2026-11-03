package games.strategy.engine.framework.startup.ui;

/**
 * The spacing scale of the main menu, on a grid of 8 px: every gap of the menu is one of these
 * steps, so the same relation always gets the same room. The closer two things belong together, the
 * smaller the step between them.
 */
final class MenuSpacing {
  /** A title and the line below it, eg a menu entry and its description. */
  static final int XS = 4;

  /**
   * Short lines that belong together, eg a map's name and its description; the room around the text
   * of a menu entry. Entries and other rows to click touch each other, this room keeps them apart.
   */
  static final int S = 8;

  /** A heading and its content, a picture and its text, items of two lines, eg forum topics. */
  static final int M = 16;

  /** Groups of one list, eg the ways to play and the other entries; the least gutter. */
  static final int L = 24;

  /** The least and the largest gutter, see {@link #gutter}. */
  private static final int GUTTER_LEAST = L;

  private static final int GUTTER_MOST = 64;

  private MenuSpacing() {}

  /**
   * The one outer distance of the main menu in a window of the width: the menu's inset, the inset
   * of the texts on the map of the day, the room around the news panels on it. A fortieth of the
   * width on the 8 px grid, from 24 px in small windows to 64 px in large ones.
   */
  static int gutter(final int windowWidth) {
    final int steps = Math.round(windowWidth / 40f / S);
    return Math.clamp(steps * S, GUTTER_LEAST, GUTTER_MOST);
  }
}
