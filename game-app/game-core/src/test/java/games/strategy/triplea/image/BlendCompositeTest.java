package games.strategy.triplea.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import games.strategy.triplea.image.BlendComposite.BlendingMode;
import games.strategy.triplea.ui.mapdata.MapData;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

final class BlendCompositeTest {

  @DisplayName("A map's blend is the mode and strength its properties name")
  @Test
  void blendOfMap() {
    final MapData mapData = givenMapData(" multiply ", 0.3f);

    final BlendComposite blend = BlendComposite.newForMap(mapData);

    assertThat(blend)
        .as("as in map.properties, in any case and with spaces around it")
        .extracting(BlendComposite::getMode, BlendComposite::getAlpha)
        .containsExactly(BlendingMode.MULTIPLY, 0.3f);
  }

  @DisplayName("A mode the map cannot mean blends normally at half strength")
  @Test
  void invalidModeBlendsNormally() {
    final MapData mapData = givenMapData("screen", 1.0f);

    final BlendComposite blend = BlendComposite.newForMap(mapData);

    assertThat(blend)
        .as("the strength was meant for the other mode: at full strength the relief would hide")
        .extracting(BlendComposite::getMode, BlendComposite::getAlpha)
        .containsExactly(BlendingMode.NORMAL, 0.5f);
  }

  @DisplayName("A strength the map cannot mean blends at half strength, in the map's mode")
  @Test
  void invalidStrengthKeepsMode() {
    final MapData unreadable = givenMapData("overlay", 0);
    when(unreadable.getMapBlendAlpha()).thenThrow(new NumberFormatException("0,5"));
    final List<MapData> maps =
        List.of(unreadable, givenMapData("overlay", 1.5f), givenMapData("overlay", Float.NaN));

    final List<BlendComposite> blends = maps.stream().map(BlendComposite::newForMap).toList();

    assertThat(blends)
        .as("unreadable, too strong and NaN: the map's overlay at half strength")
        .allSatisfy(
            blend ->
                assertThat(blend)
                    .extracting(BlendComposite::getMode, BlendComposite::getAlpha)
                    .containsExactly(BlendingMode.OVERLAY, 0.5f));
  }

  private static MapData givenMapData(final String blendMode, final float blendAlpha) {
    final MapData mapData = mock(MapData.class);
    when(mapData.getMapBlendMode()).thenReturn(blendMode);
    when(mapData.getMapBlendAlpha()).thenReturn(blendAlpha);
    return mapData;
  }
}
