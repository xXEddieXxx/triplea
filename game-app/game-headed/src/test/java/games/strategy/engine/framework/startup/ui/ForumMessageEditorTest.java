package games.strategy.engine.framework.startup.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.awt.Component;
import java.awt.Container;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.image.BufferedImage;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import javax.swing.AbstractButton;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler.TransferSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

final class ForumMessageEditorTest {
  private final List<Path> attached = new ArrayList<>();
  private final ForumMessageEditor editor =
      new ForumMessageEditor(
          "message", () -> URI.create("https://forums.triplea-game.org/"), attached::add);

  @DisplayName("Text copied from an office program pastes as text, not as the picture with it")
  @Test
  void pastedTextWithPictureStaysText() {
    final Transferable copied = givenTextWithPictureOfIt("Moscow holds");

    editor
        .getTextArea()
        .getTransferHandler()
        .importData(new TransferSupport(editor.getTextArea(), copied));

    assertThat(editor.getTextArea().getText())
        .as("the quote the player copied is in the message")
        .isEqualTo("Moscow holds");
    assertThat(attached).as("no picture of the text goes up").isEmpty();
  }

  @DisplayName("Preview shows the message as the forum will, the bar waits; clear goes to Write")
  @Test
  void previewThenClear() {
    editor.getTextArea().setText("");

    buttonOf("Preview").doClick();
    final boolean nothingToShow =
        componentsOf(editor).stream()
            .anyMatch(
                component ->
                    component instanceof JLabel label
                        && "Nothing to show yet.".equals(label.getText()));
    final boolean barWhilePreviewing = buttonOf("Bold").isEnabled();
    editor.clear();

    assertThat(nothingToShow).as("an empty message says so instead of an empty page").isTrue();
    assertThat(barWhilePreviewing).as("the bar formats the text, not its preview").isFalse();
    assertThat(List.of(buttonOf("Write").isSelected(), buttonOf("Bold").isEnabled()))
        .as("eg the next turn's message is written again")
        .containsExactly(true, true);
  }

  @DisplayName("Clearing a message shown in Preview goes back to Write, eg after Send")
  @Test
  void clearWhilePreviewingWrites() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          editor.getTextArea().setText("Moscow holds");
          buttonOf("Preview").doClick();
          editor.clear();
        });

    // what the change of the text queued has run
    SwingUtilities.invokeAndWait(() -> {});

    assertThat(List.of(buttonOf("Write").isSelected(), buttonOf("Bold").isEnabled()))
        .as("the next message is written, not previewed")
        .containsExactly(true, true);
  }

  @DisplayName("The preview follows the text, eg a picture's link that came while it showed")
  @Test
  void previewFollowsText() throws Exception {
    SwingUtilities.invokeAndWait(() -> buttonOf("Preview").doClick());

    SwingUtilities.invokeAndWait(() -> editor.getTextArea().setText("**Moscow** holds"));

    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () ->
                assertThat(componentsOf(editor))
                    .as("the preview shows the new text, not 'Nothing to show yet.'")
                    .anyMatch(ForumPost.class::isInstance));
  }

  /** The button of the editor with the text or tool tip. */
  private AbstractButton buttonOf(final String name) {
    return componentsOf(editor).stream()
        .filter(AbstractButton.class::isInstance)
        .map(AbstractButton.class::cast)
        .filter(button -> name.equals(button.getText()) || name.equals(button.getToolTipText()))
        .findFirst()
        .orElseThrow();
  }

  private static List<Component> componentsOf(final Container container) {
    final List<Component> all = new ArrayList<>();
    for (final Component component : container.getComponents()) {
      all.add(component);
      if (component instanceof Container inner) {
        all.addAll(componentsOf(inner));
      }
    }
    return all;
  }

  @DisplayName("Pictures are recognized by their type, so a pasted or dropped file can go up")
  @Test
  void picturesByType() {
    assertThat(
            List.of(
                ForumMessageEditor.isPicture(Path.of("front.PNG")),
                ForumMessageEditor.isPicture(Path.of("turn.tsvg")),
                ForumMessageEditor.isPicture(Path.of("png"))))
        .as("a picture in any case, not a save game, not a name without type")
        .containsExactly(true, false, false);
  }

  /** As Word, Excel or a browser copy it: the text, and a picture of it. */
  private static Transferable givenTextWithPictureOfIt(final String text) {
    return new Transferable() {
      @Override
      public DataFlavor[] getTransferDataFlavors() {
        return new DataFlavor[] {DataFlavor.stringFlavor, DataFlavor.imageFlavor};
      }

      @Override
      public boolean isDataFlavorSupported(final DataFlavor flavor) {
        return List.of(getTransferDataFlavors()).contains(flavor);
      }

      @Override
      public Object getTransferData(final DataFlavor flavor) {
        return DataFlavor.stringFlavor.equals(flavor)
            ? text
            : new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
      }
    };
  }
}
