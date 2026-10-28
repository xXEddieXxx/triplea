package games.strategy.engine.framework.ui;

import com.google.common.annotations.VisibleForTesting;
import games.strategy.triplea.settings.ClientSetting;
import java.awt.Frame;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import javax.swing.JFrame;
import lombok.experimental.UtilityClass;

/**
 * Where the main window opens: as it was left the last time, else maximized, so that the screen is
 * used; in full screen, without window frame, when the settings ask for it.
 */
@UtilityClass
final class WindowPlacement {
  private static final int MIN_VISIBLE_SIZE = 100;

  /** Call before the frame is displayable: only then can it drop its window frame. */
  static void prepare(final JFrame frame) {
    frame.setUndecorated(ClientSetting.startFullScreen.getValue().orElse(false));
    frame.addWindowListener(
        new WindowAdapter() {
          @Override
          public void windowClosing(final WindowEvent e) {
            remember(frame);
          }
        });
  }

  /**
   * Places the frame, on its first showing: over the whole screen, where it was left, else
   * maximized.
   */
  static void place(final JFrame frame) {
    if (frame.isUndecorated()) {
      frame.setBounds(frame.getGraphicsConfiguration().getBounds());
      return;
    }
    ClientSetting.mainWindowBounds
        .getValue()
        .flatMap(WindowPlacement::parse)
        // a screen unplugged since, or a lower resolution, must not leave the window out of sight
        .filter(bounds -> isOnScreen(bounds, screenBounds()))
        .ifPresentOrElse(
            frame::setBounds,
            () -> {
              frame.setLocationRelativeTo(null);
              frame.setExtendedState(Frame.MAXIMIZED_BOTH);
            });
  }

  private static List<Rectangle> screenBounds() {
    return Arrays.stream(GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices())
        .map(screen -> screen.getDefaultConfiguration().getBounds())
        .toList();
  }

  /** Whether enough of the window to grab and move it lies on one of the screens. */
  @VisibleForTesting
  static boolean isOnScreen(final Rectangle window, final List<Rectangle> screens) {
    return screens.stream()
        .map(window::intersection)
        .anyMatch(
            visible -> visible.width >= MIN_VISIBLE_SIZE && visible.height >= MIN_VISIBLE_SIZE);
  }

  private static void remember(final JFrame frame) {
    boundsToRemember(frame.getExtendedState(), frame.isUndecorated(), frame.getBounds())
        .ifPresentOrElse(
            ClientSetting.mainWindowBounds::setValue, ClientSetting.mainWindowBounds::resetValue);
    ClientSetting.flush();
  }

  /**
   * The size of a window that is neither maximized nor in full screen; empty for the others, they
   * open maximized or in full screen again.
   */
  @VisibleForTesting
  static Optional<String> boundsToRemember(
      final int extendedState, final boolean undecorated, final Rectangle bounds) {
    final boolean maximized = (extendedState & Frame.MAXIMIZED_BOTH) == Frame.MAXIMIZED_BOTH;
    return maximized || undecorated ? Optional.empty() : Optional.of(format(bounds));
  }

  @VisibleForTesting
  static String format(final Rectangle bounds) {
    return bounds.x + "," + bounds.y + "," + bounds.width + "," + bounds.height;
  }

  /** The bounds written by {@link #format}; empty for anything else, eg an old or broken value. */
  @VisibleForTesting
  static Optional<Rectangle> parse(final String value) {
    final String[] parts = value.split(",");
    if (parts.length != 4) {
      return Optional.empty();
    }
    try {
      final int[] numbers =
          Arrays.stream(parts).mapToInt(part -> Integer.parseInt(part.strip())).toArray();
      return numbers[2] < 200 || numbers[3] < 200
          ? Optional.empty()
          : Optional.of(new Rectangle(numbers[0], numbers[1], numbers[2], numbers[3]));
    } catch (final NumberFormatException e) {
      return Optional.empty();
    }
  }
}
