package games.strategy.engine.framework.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler.TransferSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

final class SaveGameDropTest {
  private final List<Path> started = new ArrayList<>();
  private final SaveGameDrop drop = new SaveGameDrop(started::add);

  @DisplayName("Of the files dropped, the save game starts, after the drop; others are not taken")
  @Test
  void saveAmongFilesStarts() throws Exception {
    final TransferSupport dropped =
        givenDropOf(new File("notes.txt"), new File("Germans round 3.tsvg"), new File("x.png"));

    final boolean taken = drop.importData(dropped);
    final List<Path> startedDuringDrop = List.copyOf(started);
    // the start comes once the drop returned, on the Swing thread
    SwingUtilities.invokeAndWait(() -> {});

    assertThat(taken).as("the drop is taken").isTrue();
    assertThat(startedDuringDrop).as("the file manager does not wait for the start").isEmpty();
    assertThat(started)
        .as("only the save game, once")
        .containsExactly(Path.of("Germans round 3.tsvg"));
  }

  @DisplayName("Files without a save game start nothing, and the drop is not taken")
  @Test
  void noSaveStartsNothing() throws Exception {
    final boolean taken = drop.importData(givenDropOf(new File("map.zip")));
    SwingUtilities.invokeAndWait(() -> {});

    assertThat(taken).as("eg a map's zip").isFalse();
    assertThat(started).as("nothing to start").isEmpty();
  }

  private static TransferSupport givenDropOf(final File... files) {
    final Transferable transferable =
        new Transferable() {
          @Override
          public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[] {DataFlavor.javaFileListFlavor};
          }

          @Override
          public boolean isDataFlavorSupported(final DataFlavor flavor) {
            return DataFlavor.javaFileListFlavor.equals(flavor);
          }

          @Override
          public Object getTransferData(final DataFlavor flavor) {
            return List.of(files);
          }
        };
    return new TransferSupport(new JPanel(), transferable);
  }
}
