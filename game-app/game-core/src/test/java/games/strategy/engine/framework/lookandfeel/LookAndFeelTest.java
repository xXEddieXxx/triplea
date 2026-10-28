package games.strategy.engine.framework.lookandfeel;

import static org.assertj.core.api.Assertions.assertThat;

import games.strategy.triplea.settings.ClientSetting;
import games.strategy.triplea.settings.TextSize;
import java.awt.Color;
import java.util.List;
import javax.swing.JButton;
import javax.swing.plaf.UIResource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sonatype.goodies.prefs.memory.MemoryPreferences;

final class LookAndFeelTest {
  @Test
  void testIsColorDark() {
    assertThat(LookAndFeel.isColorDark(Color.BLACK)).isTrue();
    assertThat(LookAndFeel.isColorDark(Color.DARK_GRAY)).isTrue();
    assertThat(LookAndFeel.isColorDark(Color.WHITE)).isFalse();
    assertThat(LookAndFeel.isColorDark(Color.YELLOW)).isFalse();
  }

  @DisplayName("Theme colors are UI resources, so a theme change replaces them on components")
  @Test
  void themeColorsAreUiResources() {
    assertThat(LookAndFeel.getSecondaryTextColor())
        .as("updateComponentTreeUI only replaces colors that are UIResources")
        .isInstanceOf(UIResource.class);
    assertThat(LookAndFeel.getSeparatorColor()).isInstanceOf(UIResource.class);
  }

  @DisplayName("A borderless link takes no focus on a click, but Tab still reaches it")
  @Test
  void borderlessButtonTakesNoFocusOnClick() {
    final JButton link = new JButton("All topics");

    LookAndFeel.setBorderlessButtonStyle(link);

    assertThat(List.of(link.isRequestFocusEnabled(), link.isFocusable()))
        .as("no focus ring after a click or on its own, keyboard players still reach it")
        .containsExactly(false, true);
  }

  @DisplayName("The text size setting enlarges FlatLaf's default font by its points")
  @Test
  void textSizeEnlargesTheDefaultFont() {
    ClientSetting.setPreferences(new MemoryPreferences());
    try {
      final String normal = LookAndFeel.textSizeFont();
      ClientSetting.textSize.setValue(TextSize.VERY_LARGE);

      assertThat(List.of(normal, LookAndFeel.textSizeFont()))
          .as("Normal is already larger than the theme's font, for readability")
          .containsExactly("+2", "+6");
    } finally {
      ClientSetting.resetPreferences();
    }
  }
}
