package games.strategy.engine.data.properties;

import static org.assertj.core.api.Assertions.assertThat;

import games.strategy.triplea.xml.TestMapGameData;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import javax.swing.AbstractButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
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

  @DisplayName("The grouped options stand under headings like Dice & battles")
  @Test
  void groupedOptionsHaveHeadings() {
    final PropertiesUi column =
        PropertiesUi.newGrouped(TestMapGameData.REVISED.getGameData().getProperties(), true);

    assertThat(labelTexts(column))
        .as("section headings are in capitals; Revised has Low Luck and victory options")
        .contains("DICE & BATTLES", "VICTORY")
        .doesNotContain("ON / OFF");
    assertThat(checkBoxTexts(column)).as("the options themselves stay").contains("Low Luck");
  }

  @DisplayName("The grouped options stand in as many columns of one width as the room holds")
  @Test
  void groupsFillColumnsOfOneWidth() throws Exception {
    final PropertiesUi grouped =
        PropertiesUi.newGrouped(TestMapGameData.REVISED.getGameData().getProperties(), true);

    final Dimension narrow = sizeInRoom(grouped, 300);
    final Dimension wide = sizeInRoom(grouped, 1000);

    assertThat(wide.height)
        .as("groups side by side need far less height than one below the other")
        .isLessThan(narrow.height * 2 / 3);
    assertThat(wide.width)
        .as("three columns of 300 px or a long name's width, not stretched to the room's 1000 px")
        .isBetween(3 * 300 + 2 * 16, 999);
  }

  /** The size of the columns the grouped options show in a room of the given width. */
  private static Dimension sizeInRoom(final PropertiesUi grouped, final int width)
      throws Exception {
    final Container room = (Container) grouped.getComponent(0);
    room.setSize(width, 2000);
    // the columns follow the resize event on the Swing thread
    SwingUtilities.invokeAndWait(() -> {});
    return room.getComponent(0).getPreferredSize();
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
