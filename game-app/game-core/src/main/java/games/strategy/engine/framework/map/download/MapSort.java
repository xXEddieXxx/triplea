package games.strategy.engine.framework.map.download;

import java.util.Comparator;
import java.util.Locale;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * The order of the maps in the maps window; the sort button above its lists chooses one. Rating and
 * downloads are examples until the server keeps them (see {@link MapRating}); equal ones are sorted
 * by name.
 */
@AllArgsConstructor
public enum MapSort {
  NAME("Name", "A–Z", Comparator.comparing(MapSort::lowerCaseName)),
  RATING(
      "Rating",
      "highest first",
      Comparator.comparingDouble((ManagedMap map) -> MapRating.exampleAverage(map.getMapName()))
          .reversed()),
  DOWNLOADS(
      "Downloads",
      "most first",
      Comparator.comparingInt((ManagedMap map) -> MapRating.exampleDownloads(map.getMapName()))
          .reversed());

  /** The name on the sort button, eg "Rating". */
  @Getter(AccessLevel.PACKAGE)
  private final String label;

  /** How the maps are ordered, eg "highest first", shown in the menu after the name. */
  @Getter(AccessLevel.PACKAGE)
  private final String order;

  private final Comparator<ManagedMap> comparator;

  private static String lowerCaseName(final ManagedMap map) {
    return map.getMapName().toLowerCase(Locale.ROOT);
  }

  /** The maps in this order, those that are equal in it by name. */
  Comparator<ManagedMap> comparator() {
    return comparator.thenComparing(MapSort::lowerCaseName);
  }
}
