package games.strategy.triplea.settings;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Component;
import java.awt.Container;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import javax.swing.AbstractButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sonatype.goodies.prefs.memory.MemoryPreferences;

final class SettingsPageTest {
  @BeforeEach
  void givenClientSettings() {
    ClientSetting.setPreferences(new MemoryPreferences());
  }

  @AfterEach
  void resetClientSettingPreferences() {
    ClientSetting.resetPreferences();
  }

  @DisplayName("Settings open on Gameplay, its groups in order")
  @Test
  void opensOnGameplay() throws Exception {
    final SettingsPage page = givenSettingsPage();

    assertThat(page.getShownSettingTitles())
        .as("notifications first, the computer players last, nothing of other sections")
        .startsWith("Tell me when all units have moved", "Highlight territory when cycling units")
        .endsWith("Pause between moves", "Pause between battle steps")
        .doesNotContain("Theme");
  }

  @DisplayName("A search shows the matching settings of all sections")
  @Test
  void searchFiltersAcrossSections() throws Exception {
    final SettingsPage page = givenSettingsPage();

    SwingUtilities.invokeAndWait(() -> page.getSearchField().setText("FORUM"));

    assertThat(page.getShownSettingTitles())
        .as("titles and descriptions, in any case: the proxy is for forum games too")
        .containsExactly("Network proxy", "TripleA forum", "Axis & Allies forum", "Dice server");
    assertThat(textsOf(page.getComponent())).contains("Search results");
  }

  @DisplayName("A search without matches says so")
  @Test
  void searchWithoutMatchesSaysSo() throws Exception {
    final SettingsPage page = givenSettingsPage();

    SwingUtilities.invokeAndWait(() -> page.getSearchField().setText("xyzzy"));

    assertThat(page.getShownSettingTitles()).as("no setting matches").isEmpty();
    assertThat(textsOf(page.getComponent())).contains("No setting matches “xyzzy”.");
  }

  @DisplayName("Clearing the search goes back to the selected section")
  @Test
  void clearingSearchShowsSelectedSection() throws Exception {
    final SettingsPage page = givenSettingsPage();
    SwingUtilities.invokeAndWait(() -> page.showSection(SettingType.AUDIO));
    SwingUtilities.invokeAndWait(() -> page.getSearchField().setText("chat"));

    SwingUtilities.invokeAndWait(() -> page.getSearchField().setText(""));

    assertThat(page.getShownSettingTitles())
        .as("Audio was selected before the search")
        .containsExactly("Sound effects", "Single sounds");
  }

  @DisplayName("The reset link resets the settings of the shown section")
  @Test
  void resetLinkResetsShownSection() throws Exception {
    final SettingsPage page = givenSettingsPage();
    SwingUtilities.invokeAndWait(() -> page.showSection(SettingType.AUDIO));
    final JCheckBox soundEffects = givenSoundEffectsTurnedOff(page);

    SwingUtilities.invokeAndWait(
        () -> buttonOf(page.getComponent(), "Reset Audio to defaults").doClick());

    assertThat(soundEffects.isSelected()).as("sound is on by default").isTrue();
  }

  private static SettingsPage givenSettingsPage() throws Exception {
    final AtomicReference<SettingsPage> page = new AtomicReference<>();
    SwingUtilities.invokeAndWait(() -> page.set(new SettingsPage()));
    return page.get();
  }

  private static JCheckBox givenSoundEffectsTurnedOff(final SettingsPage page) {
    final JCheckBox soundEffects =
        componentsOf(page.getComponent())
            .filter(ToggleSwitch.class::isInstance)
            .map(JCheckBox.class::cast)
            .findFirst()
            .orElseThrow();
    soundEffects.setSelected(false);
    return soundEffects;
  }

  private static List<String> textsOf(final JComponent component) {
    return componentsOf(component)
        .flatMap(
            part ->
                part instanceof JLabel label
                    ? Stream.of(label.getText())
                    : part instanceof JTextComponent text
                        ? Stream.of(text.getText())
                        : Stream.empty())
        .toList();
  }

  private static AbstractButton buttonOf(final JComponent component, final String text) {
    return componentsOf(component)
        .filter(AbstractButton.class::isInstance)
        .map(AbstractButton.class::cast)
        .filter(button -> text.equals(button.getText()))
        .findAny()
        .orElseThrow();
  }

  private static Stream<Component> componentsOf(final Component component) {
    return component instanceof Container container
        ? Stream.concat(
            Stream.of(component),
            Arrays.stream(container.getComponents()).flatMap(SettingsPageTest::componentsOf))
        : Stream.of(component);
  }
}
