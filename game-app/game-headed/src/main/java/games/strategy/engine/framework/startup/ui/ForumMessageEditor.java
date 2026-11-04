package games.strategy.engine.framework.startup.ui;

import com.google.common.annotations.VisibleForTesting;
import games.strategy.engine.framework.startup.ui.LineIcon.Kind;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Insets;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.InputEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.Serial;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import javax.imageio.ImageIO;
import javax.swing.ButtonGroup;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JToggleButton;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.triplea.swing.SwingComponents;

/**
 * A message as the forum reads it (Markdown), with a bar for bold, italic, a quote, a list and a
 * link, and Write and Preview: the preview shows the message as the forum will, pictures included.
 * Where the screen can upload pictures, a picture is chosen by the bar's button, pasted, eg a
 * screenshot, or dropped onto the message. The Play by forum page writes its answers with it, and a
 * game by forum or email the message at the end of a turn.
 */
@Slf4j
public final class ForumMessageEditor extends JPanel {
  @Serial private static final long serialVersionUID = 5096185236671130925L;
  private static final int FORMAT_ICON = 20;
  private static final Set<String> PICTURE_TYPES = Set.of("png", "jpg", "jpeg", "gif", "webp");
  private static final String WRITE = "write";
  private static final String PREVIEW = "preview";

  /** The message as written. */
  @Getter private final JTextArea textArea = new JTextArea(4, 30);

  private final transient Supplier<URI> forum;
  private final CardLayout cards = new CardLayout();
  private final JPanel shown = new JPanel(cards);
  private final JPanel preview = new JPanel(new BorderLayout());
  private final List<JButton> formatButtons = new ArrayList<>();
  private final JToggleButton write = newMode("Write");
  private final JToggleButton show = newMode("Preview");

  /**
   * Without pictures: the screen cannot upload them.
   *
   * @param forum the forum's address, which links and pictures in the preview are relative to
   */
  public ForumMessageEditor(final String placeholder, final Supplier<URI> forum) {
    this(placeholder, forum, null);
  }

  /**
   * @param attachPicture uploads a picture, chosen, pasted or dropped, and links it at the caret
   */
  ForumMessageEditor(
      final String placeholder,
      final Supplier<URI> forum,
      final @Nullable Consumer<Path> attachPicture) {
    super(new BorderLayout(0, MenuSpacing.S));
    this.forum = forum;
    setOpaque(false);
    textArea.setLineWrap(true);
    textArea.setWrapStyleWord(true);
    textArea.putClientProperty("JTextField.placeholderText", placeholder);
    if (attachPicture != null) {
      textArea.setTransferHandler(
          new PictureTransfer(textArea.getTransferHandler(), attachPicture));
    }
    final JPanel bar = new JPanel(new BorderLayout());
    bar.setOpaque(false);
    bar.add(newFormatBar(attachPicture), BorderLayout.WEST);
    bar.add(newModes(), BorderLayout.EAST);
    add(bar, BorderLayout.NORTH);
    preview.setOpaque(false);
    // the text's own margin, so the preview stands where the written lines stood
    preview.setBorder(new EmptyBorder(textArea.getMargin()));
    shown.setOpaque(false);
    shown.add(new JScrollPane(textArea), WRITE);
    final JScrollPane previewScroll = SwingComponents.newJScrollPane(preview);
    previewScroll.setBorder(new JScrollPane().getBorder());
    shown.add(previewScroll, PREVIEW);
    add(shown, BorderLayout.CENTER);
    // eg a picture's link that came while the preview showed: the preview shows it too
    textArea
        .getDocument()
        .addDocumentListener(
            new DocumentListener() {
              @Override
              public void insertUpdate(final DocumentEvent e) {
                updatePreview();
              }

              @Override
              public void removeUpdate(final DocumentEvent e) {
                updatePreview();
              }

              @Override
              public void changedUpdate(final DocumentEvent e) {
                // attributes only, which a plain text has none of
              }
            });
  }

  /** After the change, as a listener may not change what it listens to while it is told. */
  private void updatePreview() {
    // asked again later: clear() may have gone back to Write meanwhile
    SwingUtilities.invokeLater(
        () -> {
          if (show.isSelected()) {
            showPreview();
          }
        });
  }

  /** The whole editor: the message, its bar and Write and Preview, eg while it is sent. */
  @Override
  public void setEnabled(final boolean enabled) {
    super.setEnabled(enabled);
    textArea.setEnabled(enabled);
    write.setEnabled(enabled);
    show.setEnabled(enabled);
    // the bar only while writing: in the preview it waits for the player to write again
    formatButtons.forEach(button -> button.setEnabled(enabled && write.isSelected()));
  }

  /**
   * An empty message to write, eg for the next nation's turn; the focus stays, eg in the list of
   * games, where the arrow keys go on choosing.
   */
  public void clear() {
    textArea.setText("");
    write.setSelected(true);
    toWriting();
  }

  /**
   * Bold, italic, a quote, a list and a link, written into the message as the forum reads them, and
   * a picture; letters and icons in the text color, large enough to read at a glance.
   */
  private JComponent newFormatBar(final @Nullable Consumer<Path> attachPicture) {
    final JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
    bar.setOpaque(false);
    final JButton bold = newFormatButton("Bold", () -> wrapSelection("**", "**"));
    final float size = bold.getFont().getSize2D();
    bold.setText("B");
    bold.setFont(bold.getFont().deriveFont(Font.BOLD, size + 3));
    bar.add(bold);
    final JButton italic = newFormatButton("Italic", () -> wrapSelection("*", "*"));
    italic.setText("I");
    // a serif "I" leans so it reads as italic, unlike a sans serif bar
    italic.setFont(new Font(Font.SERIF, Font.BOLD | Font.ITALIC, Math.round(size + 4)));
    bar.add(italic);
    bar.add(newIconButton("Quote", Kind.QUOTE, () -> startLines("> ")));
    bar.add(newIconButton("List", Kind.LIST, () -> startLines("- ")));
    bar.add(newIconButton("Link", Kind.LINK, () -> wrapSelection("[", "](https://)")));
    if (attachPicture != null) {
      bar.add(
          newIconButton(
              "Picture: choose one, or paste or drop it into the message",
              Kind.PICTURE,
              () -> choosePicture(attachPicture)));
    }
    return bar;
  }

  /** Write and Preview, as in the forum's own editor. */
  private JComponent newModes() {
    final ButtonGroup modes = new ButtonGroup();
    modes.add(write);
    modes.add(show);
    write.setSelected(true);
    write.addActionListener(e -> showWriting());
    show.addActionListener(e -> showPreview());
    final JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, MenuSpacing.XS, 0));
    panel.setOpaque(false);
    panel.add(write);
    panel.add(show);
    return panel;
  }

  private static JToggleButton newMode(final String title) {
    final JToggleButton mode = new JToggleButton(title);
    mode.putClientProperty("JButton.buttonType", "roundRect");
    mode.setFocusPainted(false);
    return mode;
  }

  /** Write, as the player chose it, or a screen that wrote into the message: on to it. */
  void showWriting() {
    write.setSelected(true);
    toWriting();
    textArea.requestFocusInWindow();
  }

  private void toWriting() {
    formatButtons.forEach(button -> button.setEnabled(isEnabled()));
    cards.show(shown, WRITE);
  }

  /** The message as the forum will show it; the bar waits until the player writes again. */
  private void showPreview() {
    preview.removeAll();
    if (textArea.getText().isBlank()) {
      final JLabel nothing = new JLabel("Nothing to show yet.", SwingConstants.CENTER);
      nothing.setForeground(UIManager.getColor("Label.disabledForeground"));
      preview.add(nothing, BorderLayout.CENTER);
    } else {
      preview.add(
          new ForumPost(ForumMarkdown.toHtml(textArea.getText()), forum.get()), BorderLayout.NORTH);
    }
    formatButtons.forEach(button -> button.setEnabled(false));
    cards.show(shown, PREVIEW);
    preview.revalidate();
    preview.repaint();
  }

  /** A button of the bar: flat until the mouse is over it, as in a tool bar. */
  private JButton newFormatButton(final String what, final Runnable format) {
    final JButton button = new JButton();
    button.putClientProperty("JButton.buttonType", "toolBarButton");
    button.setFocusPainted(false);
    button.setMargin(new Insets(MenuSpacing.XS, MenuSpacing.S, MenuSpacing.XS, MenuSpacing.S));
    button.setToolTipText(what);
    button.getAccessibleContext().setAccessibleName(what);
    button.addActionListener(
        e -> {
          format.run();
          textArea.requestFocusInWindow();
        });
    formatButtons.add(button);
    return button;
  }

  private JButton newIconButton(final String what, final Kind kind, final Runnable format) {
    final JButton button = newFormatButton(what, format);
    final Color color = UIManager.getColor("Label.foreground");
    button.setIcon(new LineIcon(kind, FORMAT_ICON, color == null ? Color.GRAY : color));
    return button;
  }

  /** Lets the player choose a picture on this computer to attach. */
  private void choosePicture(final Consumer<Path> attachPicture) {
    final JFileChooser chooser = new JFileChooser();
    chooser.setFileFilter(
        new FileNameExtensionFilter("Pictures", PICTURE_TYPES.toArray(String[]::new)));
    if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
      attachPicture.accept(chooser.getSelectedFile().toPath());
    }
  }

  /** Puts the marks around the text chosen; with none chosen, the caret between them. */
  private void wrapSelection(final String before, final String after) {
    final int start = textArea.getSelectionStart();
    final String chosen = Optional.ofNullable(textArea.getSelectedText()).orElse("");
    textArea.replaceSelection(before + chosen + after);
    if (chosen.isEmpty()) {
      textArea.setCaretPosition(start + before.length());
    }
  }

  /** Starts each line chosen, or the caret's line, with the mark, eg "> " for a quote. */
  private void startLines(final String mark) {
    final String written = textArea.getText();
    // a negative start finds no line break: the first line
    final int from = written.lastIndexOf('\n', textArea.getSelectionStart() - 1) + 1;
    // a selection that ends with a line break ends on that line, not the next
    final int end = textArea.getSelectionEnd();
    final boolean endsWithBreak =
        end > textArea.getSelectionStart() && written.charAt(end - 1) == '\n';
    final int lineEnd = written.indexOf('\n', endsWithBreak ? end - 1 : end);
    final int to = lineEnd < 0 ? written.length() : lineEnd;
    final String marked = mark + written.substring(from, to).replace("\n", "\n" + mark);
    textArea.replaceRange(marked, from, to);
    textArea.setCaretPosition(from + marked.length());
  }

  /** Whether the file is a picture the forum takes, by its type. */
  @VisibleForTesting
  static boolean isPicture(final Path file) {
    final String name = String.valueOf(file.getFileName()).toLowerCase(Locale.ROOT);
    final int dot = name.lastIndexOf('.');
    return dot > 0 && PICTURE_TYPES.contains(name.substring(dot + 1));
  }

  /**
   * Pasting or dropping a picture attaches it, eg a screenshot from the clipboard or a picture's
   * file; text pastes, drags and copies as in any text field.
   */
  @RequiredArgsConstructor
  private static final class PictureTransfer extends TransferHandler {
    @Serial private static final long serialVersionUID = -3127735411548627341L;

    private final TransferHandler textHandler;
    private final transient Consumer<Path> attachPicture;

    @Override
    public boolean canImport(final TransferSupport support) {
      return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)
          || support.isDataFlavorSupported(DataFlavor.imageFlavor)
          || textHandler.canImport(support);
    }

    @Override
    public boolean importData(final TransferSupport support) {
      try {
        final List<Path> pictures = picturesOf(support);
        if (!pictures.isEmpty()) {
          pictures.forEach(attachPicture);
          return true;
        }
        // text copied from an office program or a browser comes with a picture of it: text wins
        if (support.isDataFlavorSupported(DataFlavor.imageFlavor)
            && !support.isDataFlavorSupported(DataFlavor.stringFlavor)) {
          final Image image =
              (Image) support.getTransferable().getTransferData(DataFlavor.imageFlavor);
          attachPicture.accept(writtenAsPng(image));
          return true;
        }
      } catch (final UnsupportedFlavorException | IOException e) {
        log.info("The pasted or dropped picture could not be read", e);
        return false;
      }
      return textHandler.importData(support);
    }

    /** The pictures among the files pasted or dropped; none for text or other files. */
    private static List<Path> picturesOf(final TransferSupport support)
        throws UnsupportedFlavorException, IOException {
      if (!support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
        return List.of();
      }
      final List<?> files =
          (List<?>) support.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
      return files.stream()
          .filter(File.class::isInstance)
          .map(file -> ((File) file).toPath())
          .filter(ForumMessageEditor::isPicture)
          .toList();
    }

    /** A pasted picture as a file to upload, eg a screenshot. */
    private static Path writtenAsPng(final Image pasted) throws IOException {
      // a picture of the system may still load: ImageIcon waits for it, so its size is known
      final Image image = new ImageIcon(pasted).getImage();
      if (image.getWidth(null) <= 0 || image.getHeight(null) <= 0) {
        throw new IOException("The pasted picture has no size");
      }
      final BufferedImage picture =
          new BufferedImage(
              image.getWidth(null), image.getHeight(null), BufferedImage.TYPE_INT_ARGB);
      final Graphics2D g2 = picture.createGraphics();
      g2.drawImage(image, 0, 0, null);
      g2.dispose();
      final Path file = Files.createTempFile("pasted-picture", ".png");
      file.toFile().deleteOnExit();
      ImageIO.write(picture, "png", file.toFile());
      return file;
    }

    @Override
    public int getSourceActions(final JComponent component) {
      return textHandler.getSourceActions(component);
    }

    @Override
    public void exportAsDrag(final JComponent component, final InputEvent e, final int action) {
      textHandler.exportAsDrag(component, e, action);
    }

    @Override
    public void exportToClipboard(
        final JComponent component, final Clipboard clipboard, final int action) {
      textHandler.exportToClipboard(component, clipboard, action);
    }
  }
}
