package games.strategy.engine.framework.startup.ui;

import com.google.common.annotations.VisibleForTesting;
import games.strategy.engine.framework.I18nResourceBundle;
import games.strategy.engine.framework.lookandfeel.LookAndFeel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serial;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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

  @VisibleForTesting static final int CHAPTERS = 6;
  private static final String PREFIX = "startup.Tutorial.";

  private final JEditorPane reader = SwingComponents.newHtmlJEditorPane();
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

  /** The chapter's HTML in the language folder, else in English. */
  @VisibleForTesting
  static Optional<String> readChapter(final String folder, final int chapter) {
    return read("tutorials/" + folder + "/" + chapter + ".html")
        .or(() -> read("tutorials/en/" + chapter + ".html"));
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
}
