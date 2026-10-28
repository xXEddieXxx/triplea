package games.strategy.engine.data.properties;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import javax.swing.AbstractButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JTextField;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

final class PropertiesUiTest {
  private final PropertiesUi propertiesUi =
      new PropertiesUi(
          List.of(
              new BooleanProperty("Low Luck", "dice", false),
              new BooleanProperty("Kamikaze Airplanes", "", false),
              new NumberProperty("Victory Cities", "", 20, 1, 9)),
          true);

  @DisplayName("On/off options are check boxes named after the option, so the name toggles them")
  @Test
  void switchesAreNamedCheckBoxes() {
    assertThat(checkBoxTexts(propertiesUi))
        .as("clicking the name of an on/off option toggles it")
        .containsExactly("Low Luck", "Kamikaze Airplanes");
    assertThat(labelTexts(propertiesUi))
        .as("only the value options have a label of their own")
        .contains("Victory Cities")
        .doesNotContain("Low Luck", "Kamikaze Airplanes");
  }

  private static List<String> checkBoxTexts(final Container container) {
    return Arrays.stream(container.getComponents())
        .flatMap(
            component ->
                component instanceof JCheckBox checkBox
                    ? Stream.of(checkBox.getText())
                    : component instanceof Container inner
                        ? checkBoxTexts(inner).stream()
                        : Stream.empty())
        .toList();
  }

  @DisplayName("The filter shows only the options whose name contains the text, ignoring case")
  @Test
  void filterHidesOtherOptions() {
    propertiesUi.filter("LUCK");

    assertThat(texts(propertiesUi))
        .as("a game with many options must be searchable")
        .contains("Low Luck")
        .doesNotContain("Kamikaze Airplanes", "Victory Cities");
  }

  @DisplayName("The scrollable view offers a filter field from 12 options on")
  @Test
  void filterFieldForManyOptions() {
    assertThat(hasTextField(PropertiesUi.newScrollableView(givenOptions(11))))
        .as("a few options are seen at a glance")
        .isFalse();
    assertThat(hasTextField(PropertiesUi.newScrollableView(givenOptions(12))))
        .as("many options need a way to find one")
        .isTrue();
  }

  private static PropertiesUi givenOptions(final int count) {
    final List<IEditableProperty<?>> options = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      options.add(new BooleanProperty("Option " + i, "", false));
    }
    return new PropertiesUi(options, true);
  }

  private static boolean hasTextField(final Component component) {
    return component instanceof JTextField
        || (component instanceof Container container
            && Arrays.stream(container.getComponents()).anyMatch(PropertiesUiTest::hasTextField));
  }

  @DisplayName("Read-only on/off options keep a label, so their names are not greyed out")
  @Test
  void readOnlySwitchesKeepLabels() {
    final PropertiesUi readOnly =
        new PropertiesUi(List.of(new BooleanProperty("Low Luck", "dice", true)), false);

    assertThat(labelTexts(readOnly))
        .as("a disabled check box paints its text in the disabled colour")
        .containsExactly("Low Luck");
  }

  private static List<String> labelTexts(final Container container) {
    return Arrays.stream(container.getComponents())
        .flatMap(
            component ->
                component instanceof JLabel label
                    ? Stream.ofNullable(label.getText())
                    : component instanceof Container inner
                        ? labelTexts(inner).stream()
                        : Stream.empty())
        .toList();
  }

  private static List<String> texts(final Container container) {
    return Arrays.stream(container.getComponents()).flatMap(PropertiesUiTest::textsOf).toList();
  }

  private static Stream<String> textsOf(final Component component) {
    if (component instanceof AbstractButton button) {
      return Stream.of(button.getText());
    }
    if (component instanceof JLabel label) {
      return Stream.ofNullable(label.getText());
    }
    return component instanceof Container container ? texts(container).stream() : Stream.empty();
  }
}
