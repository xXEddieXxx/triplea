package org.triplea.sound;

import com.google.common.annotations.VisibleForTesting;
import javazoom.jl.decoder.JavaLayerException;
import javazoom.jl.player.JavaSoundAudioDevice;

/**
 * The sound device of the mp3 player at the volume of the settings: it scales every sample before
 * the sound card gets it; 100 % plays the sound as it is.
 */
final class VolumeAudioDevice extends JavaSoundAudioDevice {
  private final double gain;

  VolumeAudioDevice(final int volumePercent) {
    gain = gainOf(volumePercent);
  }

  /**
   * The factor for the samples, the square of the share: a square curve comes close to how the ear
   * hears loudness, so the steps of the slider sound about even.
   */
  @VisibleForTesting
  static double gainOf(final int volumePercent) {
    final double share = Math.clamp(volumePercent, 0, 100) / 100.0;
    return share * share;
  }

  @Override
  protected void writeImpl(final short[] samples, final int offs, final int len)
      throws JavaLayerException {
    if (gain == 1.0) {
      // full volume, the default: the samples as they are, without a copy per frame
      super.writeImpl(samples, offs, len);
    } else {
      super.writeImpl(scale(samples, offs, len, gain), 0, len);
    }
  }

  /** A copy of the samples from offs on, each multiplied by the gain. */
  @VisibleForTesting
  static short[] scale(final short[] samples, final int offs, final int len, final double gain) {
    final short[] scaled = new short[len];
    for (int index = 0; index < len; index++) {
      scaled[index] = (short) Math.round(samples[offs + index] * gain);
    }
    return scaled;
  }
}
