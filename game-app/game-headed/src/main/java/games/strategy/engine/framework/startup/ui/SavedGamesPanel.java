package games.strategy.engine.framework.startup.ui;

import com.formdev.flatlaf.FlatClientProperties;
import com.google.common.annotations.VisibleForTesting;
import games.strategy.engine.framework.AutoSaveFileUtils;
import games.strategy.engine.framework.GameDataFileUtils;
import games.strategy.engine.framework.I18nResourceBundle;
import games.strategy.engine.framework.lookandfeel.LookAndFeel;
import games.strategy.triplea.settings.ClientSetting;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.io.Serial;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.triplea.swing.JButtonBuilder;
import org.triplea.swing.JLabelBuilder;
import org.triplea.swing.SwingAction;
import org.triplea.swing.SwingComponents;
import org.triplea.swing.jpanel.GridBagConstraintsAnchor;
import org.triplea.swing.jpanel.GridBagConstraintsBuilder;
import org.triplea.swing.jpanel.GridBagConstraintsFill;
import org.triplea.swing.jpanel.JPanelBuilder;

/**
 * The saved games of the player, newest first: those of the saved games folder and the auto saves,
 * each as a row with its name, when it was saved and its size. A search finds a save by name. A
 * double click, Enter or Continue goes on with one; "Other file..." picks a save elsewhere. The
 * folders are read in the background, a folder with many saves must not freeze the menu.
 */
@Slf4j
final class SavedGamesPanel extends JPanel {
  @Serial private static final long serialVersionUID = -6203385245541212736L;

  private static final String LIST = "list";
  private static final String MESSAGE = "message";
  private static final String PREFIX = "startup.LoadGamePanel.";
  private static final int LIST_WIDTH = 640;

  private final List<SavedGame> allSavedGames = new ArrayList<>();
  private final DefaultListModel<SavedGame> shownSavedGames = new DefaultListModel<>();
  private final JList<SavedGame> list = new JList<>(shownSavedGames);
  private final JTextField searchField = new JTextField();
  private final JLabel count = new JLabel();
  private final JLabel message = new JLabel("", SwingConstants.CENTER);
  private final CardLayout cards = new CardLayout();
  private final JPanel content = new JPanel(cards);
  private final I18nResourceBundle bundle;
  private final JLabel detailsName = new JLabel();
  private final JLabel detailsWhen = new JLabel();
  private final JLabel detailsFile = new JLabel();
  // the newest reading of the folders; an older one that ends later is ignored
  private transient @Nullable SwingWorker<List<SavedGame>, Void> search;

  SavedGamesPanel(
      final I18nResourceBundle bundle, final Consumer<Path> loadSave, final Runnable otherFile) {
    super(new BorderLayout(0, 14));
    this.bundle = bundle;
    list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    list.setCellRenderer(new SavedGameRenderer());
    list.addMouseListener(
        new MouseAdapter() {
          @Override
          public void mouseClicked(final MouseEvent e) {
            if (e.getClickCount() == 2 && list.getSelectedValue() != null) {
              loadSave.accept(list.getSelectedValue().file());
            }
          }
        });
    list.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "load");
    list.getActionMap()
        .put(
            "load",
            SwingAction.of(
                "load",
                () -> {
                  if (list.getSelectedValue() != null) {
                    loadSave.accept(list.getSelectedValue().file());
                  }
                }));
    message.setForeground(LookAndFeel.getSecondaryTextColor());
    content.add(SwingComponents.newJScrollPane(list), LIST);
    content.add(message, MESSAGE);

    searchField.putClientProperty(
        FlatClientProperties.PLACEHOLDER_TEXT, bundle.getText(PREFIX + "Search"));
    searchField.putClientProperty(FlatClientProperties.TEXT_FIELD_SHOW_CLEAR_BUTTON, true);
    searchField.setPreferredSize(new Dimension(360, searchField.getPreferredSize().height + 8));
    searchField
        .getDocument()
        .addDocumentListener(
            new DocumentListener() {
              @Override
              public void insertUpdate(final DocumentEvent e) {
                showMatching();
              }

              @Override
              public void removeUpdate(final DocumentEvent e) {
                showMatching();
              }

              @Override
              public void changedUpdate(final DocumentEvent e) {
                showMatching();
              }
            });
    count.setForeground(LookAndFeel.getSecondaryTextColor());
    final JButton other =
        new JButtonBuilder(bundle.getText(PREFIX + "OtherFile")).actionListener(otherFile).build();
    LookAndFeel.setBorderlessButtonStyle(other);

    // the saves on the left, the chosen one with its action on the right, as games show them
    final JPanel listColumn = new JPanel(new BorderLayout(0, 12));
    listColumn.add(
        new JPanelBuilder().borderLayout().addWest(searchField).addEast(count).build(),
        BorderLayout.NORTH);
    listColumn.add(content, BorderLayout.CENTER);
    listColumn.add(new JPanelBuilder().borderLayout().addWest(other).build(), BorderLayout.SOUTH);
    listColumn.setPreferredSize(new Dimension(LIST_WIDTH, 0));
    add(listColumn, BorderLayout.WEST);
    add(newDetails(loadSave), BorderLayout.CENTER);
  }

  /** The chosen save: its name large, when, how and how large it was saved, and Continue. */
  private JComponent newDetails(final Consumer<Path> loadSave) {
    final JButton load =
        new JButtonBuilder(bundle.getText(PREFIX + "Load") + "  ›")
            .actionListener(() -> loadSave.accept(list.getSelectedValue().file()))
            .build();
    load.setEnabled(false);
    LookAndFeel.setPrimaryButtonStyle(load);
    load.setFont(load.getFont().deriveFont(load.getFont().getSize2D() + 3));
    load.setMargin(new Insets(10, 28, 10, 28));
    detailsName.setFont(detailsName.getFont().deriveFont(Font.BOLD, 26f));
    detailsWhen.setForeground(LookAndFeel.getSecondaryTextColor());
    detailsFile.setForeground(LookAndFeel.getSecondaryTextColor());
    list.addListSelectionListener(
        e -> {
          final SavedGame chosen = list.getSelectedValue();
          load.setEnabled(chosen != null);
          detailsName.setText(chosen == null ? "" : chosen.displayName());
          detailsWhen.setText(chosen == null ? "" : detailsOf(chosen));
          detailsFile.setText(chosen == null ? "" : chosen.file().toAbsolutePath().toString());
        });
    final JPanel details = new JPanel(new GridBagLayout());
    details.setBorder(BorderFactory.createEmptyBorder(52, 48, 0, 0));
    details.add(detailsName, detailsRow(0, 0));
    details.add(detailsWhen, detailsRow(1, 10));
    details.add(detailsFile, detailsRow(2, 4));
    details.add(
        load,
        new GridBagConstraintsBuilder(0, 3)
            .anchor(GridBagConstraintsAnchor.WEST)
            .insets(28, 0, 0, 0)
            .build());
    details.add(
        new JPanel(),
        new GridBagConstraintsBuilder(0, 4).weightY(1).fill(GridBagConstraintsFill.BOTH).build());
    return details;
  }

  private static GridBagConstraints detailsRow(final int row, final int spaceAbove) {
    return new GridBagConstraintsBuilder(0, row)
        .weightX(1)
        .fill(GridBagConstraintsFill.HORIZONTAL)
        .insets(spaceAbove, 0, 0, 0)
        .build();
  }

  /** "Auto save · Today, 18:33 · 85 KB" */
  private String detailsOf(final SavedGame savedGame) {
    final String when =
        whenSaved(
            bundle, savedGame.modified().toInstant(), LocalDate.now(), ZoneId.systemDefault());
    final String size = FileUtils.byteCountToDisplaySize(savedGame.size());
    return savedGame.kind() == SaveKind.AUTO_SAVE
        ? bundle.getText(PREFIX + "AutoSave") + "  ·  " + when + "  ·  " + size
        : when + "  ·  " + size;
  }

  /** Reads the saved games again, eg each time the page is shown. */
  void refresh() {
    showMessage(bundle.getText(PREFIX + "Searching"));
    final Path savesFolder = ClientSetting.saveGamesFolderPath.getValueOrThrow();
    final Path autoSaveFolder = AutoSaveFileUtils.getAutoSaveFolder();
    search =
        new SwingWorker<>() {
          @Override
          protected List<SavedGame> doInBackground() {
            return findSavedGames(savesFolder, autoSaveFolder);
          }

          @Override
          protected void done() {
            if (search != this) {
              // the page was shown again meanwhile, the newer search shows its result
              return;
            }
            try {
              show(get());
            } catch (final InterruptedException e) {
              Thread.currentThread().interrupt();
            } catch (final ExecutionException e) {
              log.warn("Could not list the saved games", e);
              show(List.of());
            }
          }
        };
    search.execute();
  }

  private void show(final List<SavedGame> found) {
    allSavedGames.clear();
    allSavedGames.addAll(found);
    showMatching();
    if (!shownSavedGames.isEmpty()) {
      list.requestFocusInWindow();
    }
  }

  /** Shows and counts the saves found by the search, the newest of them chosen. */
  private void showMatching() {
    final List<SavedGame> matching = matching(allSavedGames, searchField.getText());
    shownSavedGames.clear();
    matching.forEach(shownSavedGames::addElement);
    count.setText(bundle.getText(PREFIX + "Count", matching.size()));
    if (!matching.isEmpty()) {
      list.setSelectedIndex(0);
    }
    if (allSavedGames.isEmpty()) {
      showMessage(bundle.getText(PREFIX + "Empty"));
    } else {
      cards.show(content, LIST);
    }
  }

  /** The saves whose name contains the searched text, ignoring case and surrounding spaces. */
  @VisibleForTesting
  static List<SavedGame> matching(final List<SavedGame> savedGames, final String searched) {
    final String text = searched.strip().toLowerCase(Locale.ROOT);
    return savedGames.stream()
        .filter(savedGame -> savedGame.displayName().toLowerCase(Locale.ROOT).contains(text))
        .toList();
  }

  private void showMessage(final String text) {
    message.setText(text);
    cards.show(content, MESSAGE);
  }

  /** The saves of this version in both folders, newest first. */
  @VisibleForTesting
  static List<SavedGame> findSavedGames(final Path savesFolder, final Path autoSaveFolder) {
    return Stream.concat(
            listSaves(savesFolder).map(file -> SavedGame.of(file, SaveKind.SAVED)),
            listSaves(autoSaveFolder).map(file -> SavedGame.of(file, SaveKind.AUTO_SAVE)))
        .sorted(Comparator.comparing(SavedGame::modified).reversed())
        .toList();
  }

  private static Stream<Path> listSaves(final Path folder) {
    try (Stream<Path> files = Files.list(folder)) {
      // only saves of this version, not eg an old .svg save or a picture with that ending
      return files
          .filter(Files::isRegularFile)
          .filter(
              file ->
                  file.getFileName()
                      .toString()
                      .toLowerCase(Locale.ROOT)
                      .endsWith(GameDataFileUtils.getExtension()))
          .toList()
          .stream();
    } catch (final IOException e) {
      // no such folder yet: nothing has been saved
      return Stream.empty();
    }
  }

  /**
   * When a game was saved, as a player says it: "Today, 18:33", "Yesterday, 9:05", else the date
   * and time.
   */
  @VisibleForTesting
  static String whenSaved(
      final I18nResourceBundle bundle,
      final Instant saved,
      final LocalDate today,
      final ZoneId zone) {
    final ZonedDateTime savedAt = saved.atZone(zone);
    final String time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).format(savedAt);
    if (savedAt.toLocalDate().equals(today)) {
      return bundle.getText(PREFIX + "Today", time);
    }
    if (savedAt.toLocalDate().equals(today.minusDays(1))) {
      return bundle.getText(PREFIX + "Yesterday", time);
    }
    return DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .format(savedAt);
  }

  enum SaveKind {
    SAVED,
    AUTO_SAVE
  }

  /** A save file, when it was written, how large it is and whether the game saved it itself. */
  record SavedGame(Path file, FileTime modified, long size, SaveKind kind) {
    static SavedGame of(final Path file, final SaveKind kind) {
      return new SavedGame(file, lastModified(file), size(file), kind);
    }

    private static FileTime lastModified(final Path file) {
      try {
        return Files.getLastModifiedTime(file);
      } catch (final IOException e) {
        return FileTime.fromMillis(0);
      }
    }

    private static long size(final Path file) {
      try {
        return Files.size(file);
      } catch (final IOException e) {
        return 0;
      }
    }

    /** The file name without the save game extension, eg "Global 1940" of "Global 1940.tsvg". */
    String name() {
      final String fileName = file.getFileName().toString();
      return fileName.substring(0, fileName.length() - GameDataFileUtils.getExtension().length());
    }

    /**
     * The name to show: an auto save's file name says when it was saved, in words, eg "Before end
     * turn" for "autosaveBeforeEndTurn"; the player's own saves keep their name.
     */
    String displayName() {
      return kind == SaveKind.AUTO_SAVE ? readableAutoSaveName(name()) : name();
    }
  }

  /** "autosaveAfterGermansCombatMove" becomes "After germans combat move". */
  @VisibleForTesting
  static String readableAutoSaveName(final String autoSaveName) {
    final String words =
        autoSaveName
            .replaceAll("(?i)autosave", " ")
            .replaceAll("([a-z])([A-Z])", "$1 $2")
            .replace('_', ' ')
            .strip()
            .replaceAll("\\s+", " ")
            .toLowerCase(Locale.ROOT);
    return words.isEmpty()
        ? autoSaveName
        : Character.toUpperCase(words.charAt(0)) + words.substring(1);
  }

  /** A row: the name large and bold, below it when, how and how large it was saved. */
  private final class SavedGameRenderer implements ListCellRenderer<SavedGame> {
    private final JLabel name = new JLabelBuilder().bold().adjustFontSize(3).build();
    private final JLabel details = new JLabel();
    private final JPanel row = new JPanel(new BorderLayout(0, 4));

    SavedGameRenderer() {
      row.add(name, BorderLayout.NORTH);
      row.add(details, BorderLayout.SOUTH);
      row.setBorder(
          BorderFactory.createCompoundBorder(
              BorderFactory.createMatteBorder(0, 0, 1, 0, LookAndFeel.getSeparatorColor()),
              BorderFactory.createEmptyBorder(14, 18, 14, 18)));
    }

    @Override
    public Component getListCellRendererComponent(
        final JList<? extends SavedGame> jlist,
        final SavedGame savedGame,
        final int index,
        final boolean isSelected,
        final boolean cellHasFocus) {
      name.setText(savedGame.displayName());
      details.setText(detailsOf(savedGame));
      row.setBackground(isSelected ? jlist.getSelectionBackground() : jlist.getBackground());
      name.setForeground(isSelected ? jlist.getSelectionForeground() : jlist.getForeground());
      details.setForeground(
          isSelected ? jlist.getSelectionForeground() : LookAndFeel.getSecondaryTextColor());
      return row;
    }
  }
}
