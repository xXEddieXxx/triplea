package games.strategy.triplea.settings;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.icons.FlatSearchIcon;
import com.formdev.flatlaf.ui.FlatLineBorder;
import com.google.common.annotations.VisibleForTesting;
import games.strategy.engine.framework.lookandfeel.LookAndFeel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.font.TextAttribute;
import java.io.Serial;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import lombok.Getter;
import org.triplea.swing.JButtonBuilder;
import org.triplea.swing.JLabelBuilder;
import org.triplea.swing.ScrollableJPanel;
import org.triplea.swing.SwingComponents;
import org.triplea.swing.WrappingText;
import org.triplea.swing.jpanel.GridBagConstraintsAnchor;
import org.triplea.swing.jpanel.GridBagConstraintsBuilder;
import org.triplea.swing.jpanel.GridBagConstraintsFill;
import org.triplea.swing.jpanel.JPanelBuilder;

/**
 * The content of the settings window, laid out like the settings of other games: below the bar with
 * the way back and the title, on the left a search field above one button per {@code SettingType},
 * on the right the settings of the chosen section in their {@code SettingGroup}s, or the settings
 * matching the search. Each setting is a row with its title and description on the left and its
 * control on the right.
 */
final class SettingsPage {
  private static final int MARGIN = 40;
  private static final int NAVIGATION_WIDTH = 260;
  private static final int SECTION_BUTTON_HEIGHT = 44;
  private static final int MAX_CONTENT_WIDTH = 800;

  private final Map<ClientSettingSwingUiBinding, SelectionComponent<JComponent>>
      selectionComponentsBySetting = new EnumMap<>(ClientSettingSwingUiBinding.class);
  private final Map<ClientSettingSwingUiBinding, JComponent> rowsBySetting =
      new EnumMap<>(ClientSettingSwingUiBinding.class);
  private final Map<SettingType, JToggleButton> sectionButtons = new EnumMap<>(SettingType.class);

  @Getter(onMethod_ = {@VisibleForTesting})
  private final JTextField searchField = new JTextField();

  private final JPanel column = new ContentColumn();
  private final JScrollPane contentScrollPane;
  @Getter private final JComponent component;
  private SettingType selectedSection = SettingType.GAMEPLAY;

  /** Creates the page; the window or page around it shows the {@code BackBar} above it. */
  SettingsPage() {
    Arrays.stream(ClientSettingSwingUiBinding.values())
        .forEach(setting -> rowsBySetting.put(setting, newSettingRow(setting)));
    final JPanel content = new ScrollableJPanel();
    content.setLayout(new GridBagLayout());
    content.setBorder(BorderFactory.createEmptyBorder(32, 48, 40, MARGIN));
    content.add(
        column,
        new GridBagConstraintsBuilder(0, 0)
            .weightX(1)
            .weightY(1)
            .anchor(GridBagConstraintsAnchor.NORTHWEST)
            .build());
    contentScrollPane = SwingComponents.newJScrollPane(content);
    contentScrollPane.setBorder(BorderFactory.createEmptyBorder());
    component =
        new JPanelBuilder()
            .borderLayout()
            .addWest(newNavigation())
            .addCenter(contentScrollPane)
            .build();
    showContent();
  }

  /**
   * One row per setting: the bold title with its description right below it on the left, the
   * control on the right, centered next to the texts. A control taller than the texts, eg a login,
   * sets the height of the row, and the texts stay at its top. Descriptions wrap at the width of
   * the content, so nothing needs a scroll bar of its own.
   */
  private JComponent newSettingRow(final ClientSettingSwingUiBinding setting) {
    final JComponent control =
        selectionComponentsBySetting
            .computeIfAbsent(setting, ClientSettingSwingUiBinding::newSelectionComponent)
            .getUiComponent();
    final JLabel title = JLabelBuilder.builder().text(setting.getTitle()).bold().build();
    // screen readers name a switch or field by its title
    title.setLabelFor(control);
    final WrappingText description = new WrappingText(setting.getDescription());
    description.setForeground(LookAndFeel.getSecondaryTextColor());
    final JPanel texts = new JPanel(new BorderLayout(0, 4));
    texts.setOpaque(false);
    texts.add(title, BorderLayout.NORTH);
    texts.add(description, BorderLayout.CENTER);

    final JPanel row = new JPanel(new GridBagLayout());
    row.setOpaque(false);
    row.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, LookAndFeel.getSeparatorColor()),
            BorderFactory.createEmptyBorder(16, 0, 16, 0)));
    row.add(
        texts,
        new GridBagConstraintsBuilder(0, 0)
            .weightX(1)
            .fill(GridBagConstraintsFill.HORIZONTAL)
            .anchor(GridBagConstraintsAnchor.NORTHWEST)
            .build());
    row.add(
        control,
        new GridBagConstraintsBuilder(1, 0)
            .anchor(GridBagConstraintsAnchor.EAST)
            .insets(0, 32, 0, 0)
            .build());
    return row;
  }

  /** The search field above one button per section, a line before the advanced settings. */
  private JComponent newNavigation() {
    final JPanel navigation = new JPanel(new GridBagLayout());
    navigation.setBorder(BorderFactory.createEmptyBorder(24, MARGIN, 24, 0));
    configureSearchField();
    navigation.add(
        searchField,
        new GridBagConstraintsBuilder(0, 0)
            .fill(GridBagConstraintsFill.HORIZONTAL)
            .insets(0, 0, 16, 0)
            .build());
    final ButtonGroup sections = new ButtonGroup();
    for (final SettingType section : SettingType.values()) {
      if (section == SettingType.ADVANCED) {
        navigation.add(
            new JSeparator(),
            new GridBagConstraintsBuilder(0, navigation.getComponentCount())
                .fill(GridBagConstraintsFill.HORIZONTAL)
                .insets(8, 0, 8, 0)
                .build());
      }
      final JToggleButton button = newSectionButton(section);
      sections.add(button);
      navigation.add(
          button,
          new GridBagConstraintsBuilder(0, navigation.getComponentCount())
              .fill(GridBagConstraintsFill.HORIZONTAL)
              .build());
    }
    // pushes the navigation to the top
    navigation.add(
        Box.createGlue(),
        new GridBagConstraintsBuilder(0, navigation.getComponentCount()).weightY(1).build());
    return navigation;
  }

  /** Filters the settings of all sections while the player types. */
  private void configureSearchField() {
    searchField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Search settings");
    searchField.putClientProperty(
        FlatClientProperties.TEXT_FIELD_LEADING_ICON, new FlatSearchIcon());
    searchField.putClientProperty(FlatClientProperties.TEXT_FIELD_SHOW_CLEAR_BUTTON, true);
    searchField.getAccessibleContext().setAccessibleName("Search settings");
    searchField
        .getDocument()
        .addDocumentListener(
            new DocumentListener() {
              @Override
              public void insertUpdate(final DocumentEvent e) {
                showContent();
              }

              @Override
              public void removeUpdate(final DocumentEvent e) {
                showContent();
              }

              @Override
              public void changedUpdate(final DocumentEvent e) {
                showContent();
              }
            });
  }

  private JToggleButton newSectionButton(final SettingType section) {
    final JToggleButton button = new JToggleButton(section.title);
    button.putClientProperty(
        FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
    button.setHorizontalAlignment(SwingConstants.LEADING);
    button.setMargin(new Insets(0, 14, 0, 14));
    button.setPreferredSize(new Dimension(NAVIGATION_WIDTH, SECTION_BUTTON_HEIGHT));
    button.addActionListener(e -> showSection(section));
    sectionButtons.put(section, button);
    return button;
  }

  /** Shows the section, leaving a search. */
  @VisibleForTesting
  void showSection(final SettingType section) {
    selectedSection = section;
    searchField.setText("");
    showContent();
  }

  /** Shows the selected section, or while there is a search text, the settings matching it. */
  private void showContent() {
    final String searchText = searchField.getText().strip();
    column.removeAll();
    if (searchText.isEmpty()) {
      addSection(selectedSection);
    } else {
      addSearchResults(searchText);
    }
    // while search results show, no section is the one shown
    final boolean sectionShown = searchText.isEmpty();
    sectionButtons.forEach(
        (section, button) -> {
          final boolean shown = sectionShown && section == selectedSection;
          button.setSelected(shown);
          button.putClientProperty(FlatClientProperties.STYLE, shown ? "font: bold" : null);
        });
    column.revalidate();
    column.repaint();
    contentScrollPane.getViewport().setViewPosition(new Point(0, 0));
    // again once the new rows are laid out, else the page can open scrolled below its title
    SwingUtilities.invokeLater(
        () -> contentScrollPane.getViewport().setViewPosition(new Point(0, 0)));
  }

  private void addSection(final SettingType section) {
    addTitle(section.title, section.intro);
    Arrays.stream(SettingGroup.values())
        .filter(group -> group.getType() == section)
        .forEach(
            group -> {
              addGroupHeading(group.getTitle());
              group.getNote().ifPresent(this::addNote);
              addRows(setting -> setting.getGroup() == group);
            });
    final JButton reset =
        new JButtonBuilder("Reset " + section.title + " to defaults")
            .actionListener(() -> resetSection(section))
            .build();
    LookAndFeel.setBorderlessButtonStyle(reset);
    reset.setFont(
        reset.getFont().deriveFont(Map.of(TextAttribute.UNDERLINE, TextAttribute.UNDERLINE_ON)));
    addSpace(24);
    addToColumn(reset);
  }

  private void addTitle(final String title, final String intro) {
    addToColumn(new JLabelBuilder(title).bold().adjustFontSize(12).build());
    addSpace(6);
    final WrappingText introText = new WrappingText(intro);
    introText.setForeground(LookAndFeel.getSecondaryTextColor());
    addToColumn(introText);
  }

  private void addGroupHeading(final String heading) {
    addSpace(28);
    addToColumn(LookAndFeel.newSectionLabel(heading));
    addSpace(4);
  }

  /** A warning above the settings of a group, in a subtle frame. */
  private void addNote(final String note) {
    final WrappingText text = new WrappingText(note);
    text.setForeground(LookAndFeel.getSecondaryTextColor());
    final JPanel box = new JPanel(new BorderLayout());
    box.setOpaque(false);
    box.setBorder(
        new FlatLineBorder(new Insets(10, 12, 10, 12), LookAndFeel.getSeparatorColor(), 1, 8));
    box.add(text, BorderLayout.CENTER);
    addSpace(8);
    addToColumn(box);
    addSpace(4);
  }

  private void addRows(final Predicate<ClientSettingSwingUiBinding> filter) {
    Arrays.stream(ClientSettingSwingUiBinding.values())
        .filter(filter)
        .map(rowsBySetting::get)
        .forEach(this::addToColumn);
  }

  /** The column stacks its parts; all start at its left edge. */
  private void addToColumn(final JComponent part) {
    part.setAlignmentX(Component.LEFT_ALIGNMENT);
    column.add(part);
  }

  private void addSpace(final int height) {
    column.add(Box.createVerticalStrut(height));
  }

  /**
   * Groups the matching settings by section, in the order of the sections; a group's warning, eg of
   * the developer settings, shows above its matches as in its section.
   */
  private void addSearchResults(final String searchText) {
    final List<ClientSettingSwingUiBinding> matches = search(searchText);
    if (matches.isEmpty()) {
      addTitle("Search results", "No setting matches “" + searchText + "”.");
      return;
    }
    addTitle("Search results", "Settings whose name or description contains this text.");
    Arrays.stream(SettingType.values())
        .filter(section -> matches.stream().anyMatch(setting -> setting.getType() == section))
        .forEach(
            section -> {
              addGroupHeading(section.title);
              Arrays.stream(SettingGroup.values())
                  .filter(group -> group.getType() == section)
                  .filter(group -> matches.stream().anyMatch(s -> s.getGroup() == group))
                  .forEach(
                      group -> {
                        group.getNote().ifPresent(this::addNote);
                        addRows(
                            setting -> setting.getGroup() == group && matches.contains(setting));
                      });
            });
  }

  private static List<ClientSettingSwingUiBinding> search(final String searchText) {
    return Arrays.stream(ClientSettingSwingUiBinding.values())
        .filter(setting -> setting.matches(searchText))
        .toList();
  }

  private void resetSection(final SettingType section) {
    Arrays.stream(ClientSettingSwingUiBinding.values())
        .filter(setting -> setting.getType() == section)
        .map(selectionComponentsBySetting::get)
        .forEach(SelectionComponent::resetToDefault);
  }

  Collection<SelectionComponent<JComponent>> getSelectionComponents() {
    return selectionComponentsBySetting.values();
  }

  /** The titles of the settings shown at the moment, top to bottom, eg to check a search. */
  @VisibleForTesting
  List<String> getShownSettingTitles() {
    return Arrays.stream(column.getComponents())
        .flatMap(
            part ->
                rowsBySetting.entrySet().stream()
                    .filter(row -> row.getValue() == part)
                    .map(row -> row.getKey().getTitle()))
        .toList();
  }

  /**
   * The column of the content: as wide as there is room, but at most {@link #MAX_CONTENT_WIDTH}, so
   * lines and rows stay readable in a large window.
   */
  private static final class ContentColumn extends JPanel {
    @Serial private static final long serialVersionUID = 2687915034279460311L;

    ContentColumn() {
      setOpaque(false);
      setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
    }

    @Override
    public Dimension getPreferredSize() {
      final Dimension size = super.getPreferredSize();
      final Container parent = getParent();
      final int available =
          parent == null || parent.getWidth() == 0
              ? MAX_CONTENT_WIDTH
              : parent.getWidth() - parent.getInsets().left - parent.getInsets().right;
      return new Dimension(Math.max(0, Math.min(MAX_CONTENT_WIDTH, available)), size.height);
    }
  }
}
