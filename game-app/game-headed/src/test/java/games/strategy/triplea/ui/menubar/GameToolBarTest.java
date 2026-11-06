package games.strategy.triplea.ui.menubar;

import static org.assertj.core.api.Assertions.assertThat;

import games.strategy.triplea.settings.ClientSetting;
import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import javax.swing.JComponent;
import javax.swing.JRootPane;
import javax.swing.KeyStroke;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sonatype.goodies.prefs.memory.MemoryPreferences;

/**
 * The shortcuts old players know from the menu bar, which the quick bar keeps; Alt and a menu's
 * letter works on its own, the icons being a menu bar.
 */
final class GameToolBarTest {
  private final JRootPane rootPane = new JRootPane();

  @BeforeEach
  void givenSettings() {
    ClientSetting.setPreferences(new MemoryPreferences());
  }

  @AfterEach
  void resetSettings() {
    ClientSetting.resetPreferences();
  }

  @DisplayName("Ctrl+L locks and unlocks the map, as the View menu's Lock Map did")
  @Test
  void ctrlLSwitchesLockMap() {
    final boolean lockedAtFirst = ClientSetting.lockMap.getValueOrThrow();
    GameToolBar.addLockMapShortcut(rootPane);

    press(
        KeyStroke.getKeyStroke(
            KeyEvent.VK_L, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));

    assertThat(ClientSetting.lockMap.getValueOrThrow())
        .as("the switch is in the settings now, the shortcut stays")
        .isEqualTo(!lockedAtFirst);
  }

  /** Runs what the window would on the keys. */
  private void press(final KeyStroke keys) {
    final Object name = rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).get(keys);
    rootPane.getActionMap().get(name).actionPerformed(null);
  }
}
