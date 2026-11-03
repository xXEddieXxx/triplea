package org.triplea.sound;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

final class VolumeAudioDeviceTest {

  @DisplayName("The gain is the square of the volume share, clamped to 0 to 100 %")
  @Test
  void gainFollowsLoudness() {
    assertThat(
            List.of(
                VolumeAudioDevice.gainOf(100),
                VolumeAudioDevice.gainOf(50),
                VolumeAudioDevice.gainOf(0),
                VolumeAudioDevice.gainOf(150)))
        .as("full, a quarter of the amplitude for half the volume, silent, and never louder")
        .containsExactly(1.0, 0.25, 0.0, 1.0);
  }

  @DisplayName("Scaling copies the samples from the offset on, each times the gain")
  @Test
  void scalesCopyFromOffset() {
    final short[] samples = {9, 1000, -2000, 9};

    final short[] scaled = VolumeAudioDevice.scale(samples, 1, 2, 0.25);

    assertThat(scaled)
        .as("only the two samples after the offset")
        .containsExactly((short) 250, (short) -500);
    assertThat(samples)
        .as("the decoder's buffer stays as it was")
        .containsExactly((short) 9, (short) 1000, (short) -2000, (short) 9);
  }
}
