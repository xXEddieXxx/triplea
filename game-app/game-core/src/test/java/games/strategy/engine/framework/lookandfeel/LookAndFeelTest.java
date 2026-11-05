package games.strategy.engine.framework.lookandfeel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import games.strategy.triplea.Constants;
import games.strategy.triplea.attachments.RelationshipTypeAttachment;
import games.strategy.triplea.settings.AbstractClientSettingTestCase;
import games.strategy.triplea.settings.ClientSetting;
import games.strategy.triplea.settings.TextSize;
import java.awt.Color;
import java.util.List;
import javax.swing.JButton;
import javax.swing.plaf.UIResource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

final class LookAndFeelTest extends AbstractClientSettingTestCase {
  // WCAG 2 level AA minimum for normal text
  private static final double MIN_CONTRAST_RATIO = 4.5;

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
    final String normal = LookAndFeel.textSizeFont();
    ClientSetting.textSize.setValue(TextSize.VERY_LARGE);

    assertThat(List.of(normal, LookAndFeel.textSizeFont()))
        .as("Normal is already larger than the theme's font, for readability")
        .containsExactly("+2", "+6");
  }

  @ParameterizedTest
  @EnumSource(RelationshipColorScheme.class)
  @DisplayName("Relationship colors come from the color scheme client setting")
  void relationshipColorsFollowTheSetting(final RelationshipColorScheme scheme) {
    ClientSetting.relationshipColorScheme.setValue(scheme);

    assertThat(colorOf(Constants.RELATIONSHIP_ARCHETYPE_WAR)).isEqualTo(scheme.getWar());
    assertThat(colorOf(Constants.RELATIONSHIP_ARCHETYPE_ALLIED)).isEqualTo(scheme.getAllied());
    assertThat(colorOf(Constants.RELATIONSHIP_ARCHETYPE_NEUTRAL)).isEqualTo(scheme.getNeutral());
  }

  private static Color colorOf(final String archeType) {
    final RelationshipTypeAttachment attachment = mock(RelationshipTypeAttachment.class);
    when(attachment.getArcheType()).thenReturn(archeType);
    return LookAndFeel.getRelationshipTypeAttachmentColor(attachment);
  }

  @ParameterizedTest
  @EnumSource(RelationshipColorScheme.class)
  @DisplayName("Relationship text meets the WCAG AA contrast ratio in every color scheme")
  void relationshipTextIsReadable(final RelationshipColorScheme scheme) {
    final List<Color> backgrounds =
        List.of(scheme.getWar(), scheme.getAllied(), scheme.getNeutral());

    for (final Color background : backgrounds) {
      final Color text = LookAndFeel.getContrastingTextColor(background);

      assertThat(contrastRatio(background, text))
          .as("text %s on background %s", text, background)
          .isGreaterThanOrEqualTo(MIN_CONTRAST_RATIO);
    }
  }

  private static double contrastRatio(final Color first, final Color second) {
    final double lighter = Math.max(relativeLuminance(first), relativeLuminance(second));
    final double darker = Math.min(relativeLuminance(first), relativeLuminance(second));
    return (lighter + 0.05) / (darker + 0.05);
  }

  // https://www.w3.org/TR/WCAG21/#dfn-relative-luminance
  private static double relativeLuminance(final Color color) {
    return 0.2126 * linear(color.getRed())
        + 0.7152 * linear(color.getGreen())
        + 0.0722 * linear(color.getBlue());
  }

  private static double linear(final int channel) {
    final double value = channel / 255.0;
    return value <= 0.040_45 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
  }
}
