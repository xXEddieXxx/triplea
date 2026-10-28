package games.strategy.engine.framework.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Component;
import java.awt.Container;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.io.Serial;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

final class BackBarTest {
  private final List<String> backs = new ArrayList<>();

  @DisplayName("The link is an arrow whose tool tip and name say where it leads, title next to it")
  @Test
  void linkNamesDestination() {
    final BackBar bar = givenBar();

    assertThat(buttonsOf(bar).toList())
        .as("one link back, the same on every screen: an icon without text")
        .singleElement()
        .satisfies(
            link -> {
              assertThat(link.getText()).as("no text, the arrow says back").isEmpty();
              assertThat(link.getIcon()).as("the painted arrow").isNotNull();
              assertThat(link.getToolTipText())
                  .as("the mouse learns where it leads")
                  .isEqualTo("Back to Main menu");
              assertThat(link.getAccessibleContext().getAccessibleName())
                  .as("screen readers say the same")
                  .isEqualTo("Back to Main menu");
              assertThat(link.getPreferredSize())
                  .as("large enough to hit easily")
                  .matches(size -> size.width >= 44 && size.height >= 44);
            });
    assertThat(labelsOf(bar).map(JLabel::getText))
        .as("the title of the screen")
        .contains("Settings");
  }

  @DisplayName("The name of the link reads as a sentence: Back to and the destination")
  @Test
  void backToReadsAsSentence() {
    assertThat(List.of(BackBar.backTo("Main menu"), BackBar.backTo("Game"), BackBar.backTo("")))
        .as("the destination keeps its spelling, German nouns stay capitalized; else just Back")
        .containsExactly("Back to Main menu", "Back to Game", "Back");
  }

  @DisplayName("The link and Escape go back the same way")
  @Test
  void linkAndEscapeGoBack() {
    final BackBar bar = givenBar();

    buttonsOf(bar).findFirst().orElseThrow().doClick();
    pressEscape(bar);

    assertThat(backs)
        .as("both run the back of the screen, eg Settings save and close")
        .containsExactly("back", "back");
  }

  @DisplayName("Escape while typing in a field stays on the screen")
  @Test
  void escapeWhileTypingStays() {
    final JTextField chat = new JTextField();
    final JTextField readOnly = new JTextField();
    readOnly.setEditable(false);

    assertThat(
            List.of(
                BackBar.isTyping(chat),
                BackBar.isTyping(readOnly),
                BackBar.isTyping(new JButton()),
                BackBar.isTyping(null)))
        .as("eg Escape in the lobby chat must not leave the lobby")
        .containsExactly(true, false, false, false);
  }

  @DisplayName("Only the release of the mouse's back button goes back")
  @Test
  void mouseBackButtonRelease() {
    final JPanel source = new JPanel();

    assertThat(
            List.of(
                BackBar.isBackButtonRelease(newMouseEvent(source, MouseEvent.MOUSE_RELEASED, 4)),
                BackBar.isBackButtonRelease(newMouseEvent(source, MouseEvent.MOUSE_PRESSED, 4)),
                BackBar.isBackButtonRelease(newMouseEvent(source, MouseEvent.MOUSE_RELEASED, 1))))
        .as("button 4 is the back button; pressing it or a click of the left button do nothing")
        .containsExactly(true, false, false);
  }

  private BackBar givenBar() {
    return new BackBar("Main menu", "Settings", () -> backs.add("back"));
  }

  private static void pressEscape(final JComponent bar) {
    final Object key =
        bar.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
            .get(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0));
    bar.getActionMap()
        .get(key)
        .actionPerformed(new ActionEvent(bar, ActionEvent.ACTION_PERFORMED, "escape"));
  }

  /** A mouse event of the button; without a display the event cannot be made with button 4. */
  private static MouseEvent newMouseEvent(final Component source, final int id, final int button) {
    return new MouseEvent(source, id, 0, 0, 0, 0, 1, false, MouseEvent.NOBUTTON) {
      @Serial private static final long serialVersionUID = 1L;

      @Override
      public int getButton() {
        return button;
      }
    };
  }

  private static Stream<JButton> buttonsOf(final Component component) {
    return componentsOf(component).filter(JButton.class::isInstance).map(JButton.class::cast);
  }

  private static Stream<JLabel> labelsOf(final Component component) {
    return componentsOf(component).filter(JLabel.class::isInstance).map(JLabel.class::cast);
  }

  private static Stream<Component> componentsOf(final Component component) {
    return component instanceof Container container
        ? Stream.concat(
            Stream.of(component),
            Arrays.stream(container.getComponents()).flatMap(BackBarTest::componentsOf))
        : Stream.of(component);
  }
}
