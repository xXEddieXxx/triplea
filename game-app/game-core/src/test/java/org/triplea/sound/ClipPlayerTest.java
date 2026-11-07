package org.triplea.sound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import games.strategy.triplea.ResourceLoader;
import games.strategy.triplea.settings.AbstractClientSettingTestCase;
import games.strategy.triplea.settings.ClientSetting;
import games.strategy.triplea.settings.SoundTheme;
import java.net.URI;
import java.net.URL;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

final class ClipPlayerTest extends AbstractClientSettingTestCase {

  @DisplayName("Without an active TripleA window, sounds are muted only if the setting says so")
  @Test
  void mutedInBackgroundOnlyWhenSet() {
    // no window of the test is active, so TripleA counts as being in the background
    final boolean byDefault = ClipPlayer.isMutedInBackground();
    ClientSetting.muteSoundInBackground.setValue(true);

    final boolean whenOn = ClipPlayer.isMutedInBackground();

    assertThat(List.of(byDefault, whenOn))
        .as("off by default, so sounds play; on, they stay silent")
        .containsExactly(false, true);
  }

  @DisplayName("A chosen sound theme replaces the map's era and its clip overrides")
  @Test
  void soundThemeReplacesMapSounds() throws Exception {
    final ClipPlayer clipPlayer = new ClipPlayer(givenMapWithOverride());

    final Optional<URL> followingMap = clipPlayer.loadClipPath("battle_land");
    ClientSetting.soundTheme.setValue(SoundTheme.CLASSICAL);
    final Optional<URL> withTheme = clipPlayer.loadClipPath("battle_land");

    assertThat(List.of(followingMap, withTheme))
        .as("the map's override first, the classical clip once the theme is chosen")
        .containsExactly(
            Optional.of(new URI("file:/sounds/custom/land.mp3").toURL()),
            Optional.of(new URI("file:/sounds/classical/battle_land/land.mp3").toURL()));
  }

  private static ResourceLoader givenMapWithOverride() throws Exception {
    final ResourceLoader resourceLoader = mock(ResourceLoader.class);
    final Properties properties = new Properties();
    properties.setProperty("battle_land", "custom");
    when(resourceLoader.loadPropertyFile("sounds.properties")).thenReturn(properties);
    when(resourceLoader.listResources(anyString())).thenReturn(List.of());
    when(resourceLoader.listResources("sounds/custom"))
        .thenReturn(List.of(new URI("file:/sounds/custom/land.mp3").toURL()));
    when(resourceLoader.listResources("sounds/classical/battle_land"))
        .thenReturn(List.of(new URI("file:/sounds/classical/battle_land/land.mp3").toURL()));
    return resourceLoader;
  }
}
