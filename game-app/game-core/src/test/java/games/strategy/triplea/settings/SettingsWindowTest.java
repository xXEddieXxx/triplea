package games.strategy.triplea.settings;

import static org.assertj.core.api.Assertions.assertThat;

import games.strategy.engine.framework.ui.MainWindowPages;
import games.strategy.engine.framework.ui.PageHost;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sonatype.goodies.prefs.memory.MemoryPreferences;

final class SettingsWindowTest {
  private final FakeMainWindow mainWindow = new FakeMainWindow();

  @BeforeEach
  void givenMainWindowOnScreen() {
    ClientSetting.setPreferences(new MemoryPreferences());
    MainWindowPages.setMainWindow(mainWindow);
  }

  @AfterEach
  void forgetMainWindow() throws Exception {
    mainWindow.showing = false;
    SwingUtilities.invokeAndWait(SettingsWindow.INSTANCE::close);
    ClientSetting.resetPreferences();
  }

  @DisplayName(
      "While the main window is on screen, Settings are its page, the link of its bar goes back")
  @Test
  void settingsArePageOfTheMainWindow() throws Exception {
    SwingUtilities.invokeAndWait(() -> SettingsWindow.INSTANCE.open(null));

    SwingUtilities.invokeAndWait(mainWindow.back);

    assertThat(mainWindow.events)
        .as("no window of its own: the main window shows the page, then its screen again")
        .containsExactly("show Settings", "close");
    assertThat(findButtonNames(mainWindow.page))
        .as("the main window's bar has the only link back, so the page has none of its own")
        .noneMatch(name -> name.startsWith("Back to"));
  }

  @DisplayName("Settings open again after another page or a game replaced them")
  @Test
  void settingsOpenAgainAfterBeingReplaced() throws Exception {
    SwingUtilities.invokeAndWait(() -> SettingsWindow.INSTANCE.open(null));

    SwingUtilities.invokeAndWait(mainWindow.whenLeft);
    SwingUtilities.invokeAndWait(() -> SettingsWindow.INSTANCE.open(null));

    assertThat(mainWindow.events)
        .as("the replaced page must not block the next one")
        .containsExactly("show Settings", "show Settings");
  }

  /** The accessible names of the buttons, eg "Back to Main menu" of an icon-only link back. */
  private static List<String> findButtonNames(final Container container) {
    return Arrays.stream(container.getComponents())
        .flatMap(SettingsWindowTest::buttonNamesOf)
        .toList();
  }

  private static Stream<String> buttonNamesOf(final Component component) {
    if (component instanceof JButton button) {
      return Stream.ofNullable(button.getAccessibleContext().getAccessibleName());
    }
    return component instanceof Container container
        ? findButtonNames(container).stream()
        : Stream.empty();
  }

  private static final class FakeMainWindow implements PageHost {
    private final List<String> events = new ArrayList<>();
    private boolean showing = true;
    private JComponent page = new JPanel();
    private Runnable back = () -> {};
    private Runnable whenLeft = () -> {};

    @Override
    public boolean isShowing() {
      return showing;
    }

    @Override
    public void showPage(
        final String title,
        final JComponent content,
        final JComponent actions,
        final Runnable backAction,
        final Runnable left) {
      events.add("show " + title);
      page = content;
      back = backAction;
      whenLeft = left;
    }

    @Override
    public void closePage() {
      events.add("close");
    }
  }
}
