package games.strategy.engine.framework.ui;

import games.strategy.engine.framework.GameDataFileUtils;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.File;
import java.io.IOException;
import java.io.Serial;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * A save game dropped onto the window, eg from the file manager, wherever the player is: it opens
 * and starts. Other files are not taken; fields with drops of their own, eg a message box, keep
 * theirs.
 */
@Slf4j
@RequiredArgsConstructor
final class SaveGameDrop extends TransferHandler {
  @Serial private static final long serialVersionUID = 6232761937268544190L;

  private final transient Consumer<Path> start;

  @Override
  public boolean canImport(final TransferSupport support) {
    return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
  }

  @Override
  public boolean importData(final TransferSupport support) {
    final Optional<Path> save = saveOf(support);
    // after the drop: the file manager the save came from waits until the drop returns, and
    // starting asks questions and loads behind a wait window
    save.ifPresent(file -> SwingUtilities.invokeLater(() -> start.accept(file)));
    return save.isPresent();
  }

  /** The first save game among the files dropped; none if none is one. */
  private static Optional<Path> saveOf(final TransferSupport support) {
    if (!support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
      return Optional.empty();
    }
    try {
      final List<?> files =
          (List<?>) support.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
      return files.stream()
          .filter(File.class::isInstance)
          .map(file -> ((File) file).toPath())
          .filter(file -> GameDataFileUtils.isCandidateFileName(String.valueOf(file.getFileName())))
          .findFirst();
    } catch (final UnsupportedFlavorException | IOException e) {
      log.info("The dropped files could not be read", e);
      return Optional.empty();
    }
  }
}
