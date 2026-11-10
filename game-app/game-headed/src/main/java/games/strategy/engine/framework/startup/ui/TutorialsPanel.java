package games.strategy.engine.framework.startup.ui;

import com.google.common.annotations.VisibleForTesting;
import games.strategy.engine.framework.I18nResourceBundle;
import games.strategy.engine.framework.lookandfeel.LookAndFeel;
import games.strategy.triplea.ResourceLoader;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.Toolkit;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serial;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JPanel;
import lombok.extern.slf4j.Slf4j;
import org.triplea.swing.SwingComponents;
import org.triplea.swing.jpanel.GridBagConstraintsBuilder;
import org.triplea.swing.jpanel.GridBagConstraintsFill;

/**
 * The tutorial of How to play: the chapters on the left, the chosen one to read on the right. The
 * chapters are HTML pages in the client's resources, one folder per language.
 */
@Slf4j
final class TutorialsPanel extends JPanel {
  @Serial private static final long serialVersionUID = 4197012850360281139L;

  @VisibleForTesting static final int CHAPTERS = 7;
  private static final String PREFIX = "startup.Tutorial.";

  /**
   * A picture of the chapters: the tag's start, "asset" or "art", the path in the assets or the
   * name of the drawing, the tag's end.
   */
  private static final Pattern PICTURE =
      Pattern.compile("(<img[^>]*src=\")(asset|art):([^\"]+)(\"[^>]*>)");

  /** Where the drawn pictures seem to be; no file is there, {@link ArtImages} holds them. */
  private static final String ART_FOLDER = "file:/tutorial-art/";

  private final JEditorPane reader = SwingComponents.newHtmlJEditorPane();
  // the drawn pictures, made once for all chapters
  private final transient Dictionary<URL, Image> pictures = new ArtImages();
  private final List<MenuEntry> chapterCards = new ArrayList<>();
  private final String folder;
  private final String missingChapter;

  /**
   * @param moreHelp links to more help, below the chapters
   */
  TutorialsPanel(final I18nResourceBundle bundle, final JComponent moreHelp) {
    super(new BorderLayout(32, 0));
    folder = bundle.getText(PREFIX + "Folder");
    missingChapter = bundle.getText(PREFIX + "Missing");
    final JPanel chapters = new JPanel(new GridBagLayout());
    // the heading in row 0, chapter n in row n, then more help
    // the headings line up with the titles of the entries, which have a bar left of them
    chapters.add(LookAndFeel.newSectionLabel(bundle.getText(PREFIX + "Chapters")), rowAt(0, 0, 20));
    for (int chapter = 1; chapter <= CHAPTERS; chapter++) {
      final MenuEntry card =
          MenuEntry.newSmall(
              chapter + "   " + bundle.getText(PREFIX + chapter),
              bundle.getText(PREFIX + chapter + ".Desc"));
      final int shown = chapter;
      card.addActionListener(e -> showChapter(shown));
      chapterCards.add(card);
      chapters.add(card, rowAt(chapter, chapter == 1 ? 8 : 2, 0));
    }
    chapters.add(
        LookAndFeel.newSectionLabel(bundle.getText(PREFIX + "More")), rowAt(CHAPTERS + 1, 28, 20));
    chapters.add(moreHelp, rowAt(CHAPTERS + 2, 6, 6));
    chapters.add(
        new JPanel(),
        new GridBagConstraintsBuilder(0, CHAPTERS + 3)
            .weightY(1)
            .fill(GridBagConstraintsFill.BOTH)
            .build());
    chapters.setPreferredSize(new Dimension(420, chapters.getPreferredSize().height));

    // lines of a readable length, on a raised sheet
    reader.setBorder(BorderFactory.createEmptyBorder(16, 40, 32, 40));
    final JComponent readerScroll = SwingComponents.newJScrollPane(reader);
    readerScroll.setPreferredSize(new Dimension(820, 0));
    final JPanel readerColumn = new JPanel(new BorderLayout());
    readerColumn.add(readerScroll, BorderLayout.WEST);
    // the drawn pictures at hand, as the HTML views look them up there; setText keeps the document
    reader.getDocument().putProperty("imageCache", pictures);
    add(chapters, BorderLayout.WEST);
    add(readerColumn, BorderLayout.CENTER);
    showChapter(1);
  }

  private static GridBagConstraints rowAt(final int row, final int spaceAbove, final int indent) {
    return new GridBagConstraintsBuilder(0, row)
        .weightX(1)
        .fill(GridBagConstraintsFill.HORIZONTAL)
        .insets(spaceAbove, indent, 0, 0)
        .build();
  }

  /** Shows the chapter, 1 to {@link #CHAPTERS}, and marks its card. */
  @VisibleForTesting
  void showChapter(final int chapter) {
    reader.setText(readChapter(folder, chapter).orElse("<p>" + missingChapter + "</p>"));
    reader.setCaretPosition(0);
    // the chapter being read is marked like a hovered entry
    chapterCards.forEach(MenuEntry::clearChosen);
    chapterCards.get(chapter - 1).markChosen();
  }

  /**
   * The chapter's HTML in the language folder, else in English, its pictures found: the game's own
   * units and flags among the client's assets, the drawn ones of {@link TutorialArt}.
   */
  @VisibleForTesting
  static Optional<String> readChapter(final String folder, final int chapter) {
    return read("tutorials/" + folder + "/" + chapter + ".html")
        .or(() -> read("tutorials/en/" + chapter + ".html"))
        .map(TutorialsPanel::withPictures);
  }

  private static Optional<String> read(final String path) {
    try (InputStream stream = TutorialsPanel.class.getResourceAsStream(path)) {
      return stream == null
          ? Optional.empty()
          : Optional.of(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
    } catch (final IOException e) {
      log.warn("Could not read the tutorial {}", path, e);
      return Optional.empty();
    }
  }

  /**
   * The HTML with each picture pointing at its image: {@code <img
   * src="asset:units/Germans/infantry.png">} at that file of the assets, {@code <img
   * src="art:route">} at the drawing of {@link TutorialArt}. A picture not found is left out.
   */
  @VisibleForTesting
  static String withPictures(final String html) {
    return PICTURE
        .matcher(html)
        .replaceAll(
            picture ->
                pictureUrl(picture.group(2), picture.group(3))
                    .map(url -> Matcher.quoteReplacement(picture.group(1) + url + picture.group(4)))
                    .orElse(""));
  }

  /** The address of a picture: "art" a drawing of its name, "asset" a file of the assets. */
  private static Optional<String> pictureUrl(final String kind, final String name) {
    return "art".equals(kind)
        ? Optional.of(name).filter(TutorialArt.names()::contains).map(TutorialsPanel::artUrl)
        : assetUrl(name);
  }

  /** The address of the file in the assets folder on the classpath, eg in the client's jar. */
  private static Optional<String> assetUrl(final String path) {
    final ClassLoader classLoader = TutorialsPanel.class.getClassLoader();
    return Optional.ofNullable(classLoader.getResource(ResourceLoader.getAssetsFileLocation(path)))
        // as the image loader of the engine does, for an assets folder on the classpath itself
        .or(() -> Optional.ofNullable(classLoader.getResource(path)))
        .map(URL::toExternalForm);
  }

  /** The made-up address of a drawn picture, under which {@link ArtImages} holds it. */
  private static String artUrl(final String name) {
    return ART_FOLDER + name + ".png";
  }

  /**
   * The images of the HTML views: the drawn pictures under their made-up address, any other address
   * loaded as usual. The views take the images only from here once a document has it.
   */
  private static final class ArtImages extends Hashtable<URL, Image> {
    @Serial private static final long serialVersionUID = -2754326209612183415L;

    ArtImages() {
      for (final String name : TutorialArt.names()) {
        TutorialArt.draw(name).ifPresent(image -> put(toUrl(artUrl(name)), image));
      }
    }

    private static URL toUrl(final String address) {
      try {
        return URI.create(address).toURL();
      } catch (final MalformedURLException e) {
        throw new IllegalStateException(
            "A made-up address of a picture is not a URL: " + address, e);
      }
    }

    @Override
    public synchronized Image get(final Object address) {
      final Image drawn = super.get(address);
      return drawn != null || !(address instanceof URL url)
          ? drawn
          : Toolkit.getDefaultToolkit().createImage(url);
    }
  }
}
