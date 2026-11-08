package games.strategy.triplea.ui.menubar;

import static org.assertj.core.api.Assertions.assertThat;

import com.formdev.flatlaf.FlatDarkLaf;
import games.strategy.triplea.settings.ClientSetting;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.KeyStroke;
import javax.swing.LookAndFeel;
import javax.swing.UIManager;
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

  @DisplayName("The icons sit on the top bar's color, not on the menu bar color of FlatLaf")
  @Test
  void iconBarShowsTheTopBarsColor() throws Exception {
    final LookAndFeel before = UIManager.getLookAndFeel();
    UIManager.setLookAndFeel(new FlatDarkLaf());
    try {
      final JPanel topBar = new JPanel(new BorderLayout());
      topBar.setBackground(Color.RED);
      topBar.add(GameToolBar.newIconBar(), BorderLayout.NORTH);
      topBar.setSize(100, 40);
      topBar.doLayout();
      final BufferedImage painted = new BufferedImage(100, 40, BufferedImage.TYPE_INT_RGB);
      final Graphics2D graphics = painted.createGraphics();
      topBar.paint(graphics);
      graphics.dispose();

      assertThat(new Color(painted.getRGB(50, 2)))
          .as("the bar's color shows through the icon bar")
          .isEqualTo(Color.RED);
    } finally {
      UIManager.setLookAndFeel(before);
    }
  }

  /** Runs what the window would on the keys. */
  private void press(final KeyStroke keys) {
    final Object name = rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).get(keys);
    rootPane.getActionMap().get(name).actionPerformed(null);
  }
}
