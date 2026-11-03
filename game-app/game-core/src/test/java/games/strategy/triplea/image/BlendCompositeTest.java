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

  @DisplayName("A mode or strength the map cannot mean blends normally at half strength")
  @Test
  void invalidBlendFallsBack() {
    final MapData unreadableAlpha = givenMapData("normal", 0);
    when(unreadableAlpha.getMapBlendAlpha()).thenThrow(new NumberFormatException("0,5"));
    final List<MapData> maps =
        List.of(
            givenMapData("screen", 0.3f),
            unreadableAlpha,
            givenMapData("overlay", 1.5f),
            givenMapData("overlay", Float.NaN));

    final List<BlendComposite> blends = maps.stream().map(BlendComposite::newForMap).toList();

    assertThat(blends)
        .as("unknown mode, unreadable, too strong and NaN: the relief still shows")
        .allSatisfy(
            blend ->
                assertThat(blend)
                    .extracting(BlendComposite::getMode, BlendComposite::getAlpha)
                    .containsExactly(BlendingMode.NORMAL, 0.5f));
  }

  private static MapData givenMapData(final String blendMode, final float blendAlpha) {
    final MapData mapData = mock(MapData.class);
    when(mapData.getMapBlendMode()).thenReturn(blendMode);
    when(mapData.getMapBlendAlpha()).thenReturn(blendAlpha);
    return mapData;
  }
}
