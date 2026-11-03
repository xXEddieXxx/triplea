package org.triplea.sound;

import static org.assertj.core.api.Assertions.assertThat;

import games.strategy.triplea.settings.AbstractClientSettingTestCase;
import games.strategy.triplea.settings.ClientSetting;
import java.util.List;
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
}
