package org.triplea.game.client.ui.swing.laf;

import static org.assertj.core.api.Assertions.assertThat;

import com.formdev.flatlaf.FlatLaf;
import games.strategy.engine.framework.lookandfeel.LookAndFeel;
import java.awt.Color;
import java.util.List;
import javax.swing.JButton;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** FlatLaf finds theme properties by file name; a typo there only shows as wrong colors. */
final class TripleaThemesTest {
  private javax.swing.LookAndFeel previousLookAndFeel;

  @BeforeAll
  static void givenTripleaThemesRegistered() {
    new DefaultSubstanceLookAndFeelManager().initialize();
  }

  @AfterAll
  static void unregisterTripleaThemes() {
    // the registration is global, later tests in this JVM must not see it
    FlatLaf.unregisterCustomDefaultsSource(
        DefaultSubstanceLookAndFeelManager.class.getPackageName());
  }

  @BeforeEach
  void rememberLookAndFeel() {
    previousLookAndFeel = UIManager.getLookAndFeel();
  }

  @AfterEach
  void restoreLookAndFeel() throws UnsupportedLookAndFeelException {
    UIManager.setLookAndFeel(previousLookAndFeel);
  }

  @DisplayName("TripleA Dark has a grey accent: no color but the highlight")
  @Test
  void darkThemeLoadsItsColors() throws UnsupportedLookAndFeelException {
    UIManager.setLookAndFeel(new TripleaDarkLaf());

    assertThat(UIManager.getColor("Component.accentColor"))
        .as("focus and selection in grey, from TripleaDarkLaf.properties")
        .isEqualTo(new Color(0x9d9ba3));
  }

  @DisplayName("The highlight is light blue in TripleA Dark and dark indigo in TripleA Light")
  @Test
  void highlightIsReadableOnEachTheme() throws UnsupportedLookAndFeelException {
    UIManager.setLookAndFeel(new TripleaDarkLaf());
    final Color dark = UIManager.getColor("TripleA.highlightBackground");
    UIManager.setLookAndFeel(new TripleaLightLaf());
    final Color light = UIManager.getColor("TripleA.highlightBackground");

    assertThat(List.of(dark, light))
        .as("the text color of Multiplayer, from FlatLaf.properties and TripleaLightLaf.properties")
        .containsExactly(new Color(0xa0c4e8), new Color(0x3b2e8c));
  }

  @DisplayName("A primary button of TripleA Light is a quiet dark fill, not a color")
  @Test
  void primaryButtonIsQuiet() throws UnsupportedLookAndFeelException {
    UIManager.setLookAndFeel(new TripleaLightLaf());
    final JButton button = new JButton("Done");

    LookAndFeel.setPrimaryButtonStyle(button);

    assertThat(button.getBackground())
        .as("style class 'primary' from FlatLaf.properties, color from TripleaLightLaf.properties")
        .isEqualTo(new Color(0x3a3940));
  }
}
