package games.strategy.triplea.odds.calculator;

import static games.strategy.triplea.delegate.GameDataTestUtil.armour;
import static games.strategy.triplea.delegate.GameDataTestUtil.artillery;
import static games.strategy.triplea.delegate.GameDataTestUtil.infantry;
import static games.strategy.triplea.delegate.GameDataTestUtil.russians;
import static org.assertj.core.api.Assertions.assertThat;

import games.strategy.engine.data.GameData;
import games.strategy.engine.data.GamePlayer;
import games.strategy.engine.data.Unit;
import games.strategy.triplea.xml.TestMapGameData;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

final class OrderOfLossesTest {
  private final GameData data = TestMapGameData.REVISED.getGameData();
  private final GamePlayer russians = russians(data);

  @DisplayName("An order of losses as players post it reads as the battle calculator's")
  @Test
  void writtenOrderReadsAsTheCalculators() {
    assertThat(OrderOfLosses.of("Infantry, artillery; 2^ARMOUR", data))
        .as("any case, \",\" or \";\", all of a type without an amount")
        .contains("*^infantry;*^artillery;2^armour");
    assertThat(OrderOfLosses.of("infantry, dragons", data))
        .as("not a unit type of the game")
        .isEmpty();
    assertThat(OrderOfLosses.of(" , ", data)).as("nothing written").isEmpty();
  }

  @DisplayName("The order of losses kills its first units, the default casualties fill up")
  @Test
  void orderKillsItsFirstUnits() {
    final List<Unit> infantry = infantry(data).create(2, russians);
    final List<Unit> armour = armour(data).create(2, russians);
    final List<Unit> artillery = artillery(data).create(1, russians);
    final List<Unit> selectFrom = new ArrayList<>(infantry);
    selectFrom.addAll(armour);
    selectFrom.addAll(artillery);
    final List<Unit> order =
        OrderOfLosses.unitsInOrder(
            OrderOfLosses.of("armour", data).orElseThrow(), selectFrom, data);

    final List<Unit> killed =
        OrderOfLosses.killedInOrder(
            order, selectFrom, 3, List.of(infantry.get(0), infantry.get(1)));

    assertThat(killed.subList(0, 2))
        .as("both armour first, as written")
        .containsExactlyInAnyOrderElementsOf(armour);
    assertThat(killed.get(2)).as("then the first default casualty").isEqualTo(infantry.get(0));
  }
}
