package games.strategy.triplea.odds.calculator;

import com.google.common.base.Splitter;
import games.strategy.engine.data.GameData;
import games.strategy.engine.data.Unit;
import games.strategy.engine.data.UnitType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * An order of losses outside the battle calculator, eg one an opponent posted in the forum: the
 * order the units of a side are lost in, as the calculator reads it, eg "*^infantry;2^armour".
 */
public final class OrderOfLosses {
  private OrderOfLosses() {}

  /**
   * The order of losses as the calculator reads it from one written as players post it: unit types
   * in any case, separated by ";" or ",", each with an amount ("2^infantry") or for all its units
   * ("infantry"), eg "Infantry, artillery, 2^armour" for "*^infantry;*^artillery;2^armour"; empty
   * for none, or when a part is not one of the game's unit types.
   */
  public static Optional<String> of(final String written, final GameData data) {
    final Map<String, String> types =
        data.getUnitTypeList().stream()
            .map(UnitType::getName)
            .collect(
                Collectors.toMap(
                    name -> name.toLowerCase(Locale.ROOT), Function.identity(), (a, b) -> a));
    final List<String> sections = new ArrayList<>();
    for (final String section :
        Splitter.onPattern("[;,]").trimResults().omitEmptyStrings().split(written)) {
      final List<String> parts =
          Splitter.on(OrderOfLossesInputPanel.OOL_AMOUNT_DESCRIPTOR)
              .trimResults()
              .splitToList(section);
      final String type = types.get(parts.getLast().toLowerCase(Locale.ROOT));
      if (type == null || parts.size() > 2) {
        return Optional.empty();
      }
      final String amount = parts.size() == 2 ? parts.getFirst() : OrderOfLossesInputPanel.OOL_ALL;
      sections.add(amount + OrderOfLossesInputPanel.OOL_AMOUNT_DESCRIPTOR + type);
    }
    final String orderOfLosses =
        String.join(String.valueOf(OrderOfLossesInputPanel.OOL_SEPARATOR), sections);
    return sections.isEmpty() || !OrderOfLossesInputPanel.isValidOrderOfLoss(orderOfLosses, data)
        ? Optional.empty()
        : Optional.of(orderOfLosses);
  }

  /** The units in the order of losses, the first lost first; none for none. See {@link #of}. */
  public static List<Unit> unitsInOrder(
      final String orderOfLosses, final Collection<Unit> units, final GameData data) {
    return Optional.ofNullable(
            OrderOfLossesInputPanel.getUnitListByOrderOfLoss(orderOfLosses, units, data))
        .orElse(List.of());
  }

  /**
   * The units killed by the order of losses, as the battle calculator kills them: as many as given,
   * the first of the order among those to choose from, then those of the default casualties.
   *
   * @param order the units, the first lost first
   * @param selectFrom the units hit
   * @param count how many are killed
   * @param defaultKilled the casualties by default, which fill up
   */
  public static List<Unit> killedInOrder(
      final List<Unit> order,
      final Collection<Unit> selectFrom,
      final int count,
      final List<Unit> defaultKilled) {
    final List<Unit> killed =
        order.stream()
            .filter(selectFrom::contains)
            .limit(count)
            .collect(Collectors.toCollection(ArrayList::new));
    defaultKilled.stream()
        .filter(unit -> !killed.contains(unit))
        .limit((long) count - killed.size())
        .forEach(killed::add);
    return killed;
  }
}
