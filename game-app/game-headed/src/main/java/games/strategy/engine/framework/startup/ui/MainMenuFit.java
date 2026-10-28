package games.strategy.engine.framework.startup.ui;

import java.awt.Dimension;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Which parts of the main menu fit into the window, so that it never needs scroll bars. The entries
 * of the menu always stay; the other parts go in the order of {@link Part} as space runs out: the
 * parts of the right column one by one when the height is too small and all of them when the width
 * is too small for menu and column side by side, then the parts on the left when its height is too
 * small.
 */
final class MainMenuFit {
  /** The parts that may be hidden, in the order they go; each one in the left or right column. */
  @AllArgsConstructor
  enum Part {
    UPDATES(Column.RIGHT),
    MAP_OF_THE_DAY(Column.RIGHT),
    COMMUNITY(Column.RIGHT),
    LOBBY_STATUS(Column.LEFT),
    TIP(Column.LEFT),
    BRAND(Column.LEFT);

    @Getter private final Column column;
  }

  enum Column {
    LEFT,
    RIGHT
  }

  private MainMenuFit() {}

  /**
   * The parts that fit.
   *
   * @param available The size of the main menu.
   * @param alwaysShown The size of what the left column always shows, the entries and the version,
   *     with their room around them.
   * @param preferred The preferred size of every part with its room around it; the parts of one
   *     column stand one above the other, the two columns side by side.
   */
  static Set<Part> fittingParts(
      final Dimension available,
      final Dimension alwaysShown,
      final Map<Part, Dimension> preferred) {
    final Set<Part> left = fitHeight(Column.LEFT, available.height - alwaysShown.height, preferred);
    final Set<Part> right = fitHeight(Column.RIGHT, available.height, preferred);
    final int leftWidth = Math.max(alwaysShown.width, widestOf(left, preferred));
    final Set<Part> fitting = EnumSet.noneOf(Part.class);
    fitting.addAll(left);
    if (leftWidth + widestOf(right, preferred) <= available.width) {
      fitting.addAll(right);
    }
    return fitting;
  }

  /** The parts of the column that fit into the height, dropping them in their order. */
  private static Set<Part> fitHeight(
      final Column column, final int height, final Map<Part, Dimension> preferred) {
    final Set<Part> shown = EnumSet.noneOf(Part.class);
    for (final Part part : Part.values()) {
      if (part.getColumn() == column) {
        shown.add(part);
      }
    }
    for (final Part part : Part.values()) {
      if (shown.contains(part) && heightOf(shown, preferred) > height) {
        shown.remove(part);
      }
    }
    return shown;
  }

  private static int heightOf(final Set<Part> parts, final Map<Part, Dimension> preferred) {
    return parts.stream().mapToInt(part -> sizeOf(part, preferred).height).sum();
  }

  private static int widestOf(final Set<Part> parts, final Map<Part, Dimension> preferred) {
    return parts.stream().mapToInt(part -> sizeOf(part, preferred).width).max().orElse(0);
  }

  private static Dimension sizeOf(final Part part, final Map<Part, Dimension> preferred) {
    return preferred.getOrDefault(part, new Dimension());
  }
}
