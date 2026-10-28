package games.strategy.triplea.settings;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JSpinner.DefaultEditor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sonatype.goodies.prefs.memory.MemoryPreferences;

final class SelectionComponentFactoryTest {
  @BeforeEach
  void givenClientSettings() {
    ClientSetting.setPreferences(new MemoryPreferences());
  }

  @AfterEach
  void resetClientSettingPreferences() {
    ClientSetting.resetPreferences();
  }

  @DisplayName("A number typed but not confirmed yet is saved when the settings close")
  @Test
  void typedNumberIsSaved() {
    final SelectionComponent<JComponent> pause =
        SelectionComponentFactory.intValueRange(ClientSetting.aiMovePauseDuration, 0, 3000);
    givenTyped((JSpinner) pause.getUiComponent(), "500");

    SaveFunction.saveSettings(List.of(pause), () -> {});

    assertThat(ClientSetting.aiMovePauseDuration.getValueOrThrow())
        .as("Escape or the close button do not move the focus, which would confirm the number")
        .isEqualTo(500);
  }

  @DisplayName("A typed number out of range is a failure the settings window shows")
  @Test
  void typedNumberOutOfRangeIsFailure() {
    final SelectionComponent<JComponent> pause =
        SelectionComponentFactory.intValueRange(ClientSetting.aiMovePauseDuration, 0, 3000);
    givenTyped((JSpinner) pause.getUiComponent(), "5000");

    final SaveFunction.SaveResult result = SaveFunction.saveSettings(List.of(pause), () -> {});

    assertThat(result.hasFailures()).as("the settings window asks before closing").isTrue();
  }

  @DisplayName("A slider saves its value and shows it with the unit")
  @Test
  void sliderSavesItsValue() {
    final SelectionComponent<JComponent> pause =
        SelectionComponentFactory.intSlider(ClientSetting.aiMovePauseDuration, 0, 3000, "ms");
    final JPanel panel = (JPanel) pause.getUiComponent();

    ((JSlider) panel.getComponent(0)).setValue(300);
    SaveFunction.saveSettings(List.of(pause), () -> {});

    assertThat(ClientSetting.aiMovePauseDuration.getValueOrThrow())
        .as("the slid value is saved")
        .isEqualTo(300);
    assertThat(((JLabel) panel.getComponent(1)).getText())
        .as("the value right of the slider follows it")
        .isEqualTo("300 ms");
  }

  @DisplayName("Closing the settings keeps a theme that is not in the list, eg of an older version")
  @Test
  void unknownThemeIsKept() {
    ClientSetting.lookAndFeel.setValue("com.example.OldLookAndFeel");
    final SelectionComponent<JComponent> theme =
        ClientSettingSwingUiBinding.LOOK_AND_FEEL_PREF_BINDING.newSelectionComponent();

    SaveFunction.saveSettings(List.of(theme), () -> {});

    assertThat(ClientSetting.lookAndFeel.getValueOrThrow())
        .as("the player did not choose a theme, closing must not switch it")
        .isEqualTo("com.example.OldLookAndFeel");
  }

  @DisplayName("A slider over a wide range moves in round steps")
  @Test
  void wideSlidersMoveInSteps() {
    assertThat(
            List.of(
                SelectionComponentFactory.sliderStep(99),
                SelectionComponentFactory.sliderStep(500),
                SelectionComponentFactory.sliderStep(1440),
                SelectionComponentFactory.sliderStep(3000)))
        .as("zoom by one, pixels by 5, seconds by 10, milliseconds by 50")
        .containsExactly(1, 5, 10, 50);
  }

  private static void givenTyped(final JSpinner spinner, final String text) {
    ((DefaultEditor) spinner.getEditor()).getTextField().setText(text);
  }
}
