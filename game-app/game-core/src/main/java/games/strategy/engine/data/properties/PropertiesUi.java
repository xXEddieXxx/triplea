package games.strategy.engine.data.properties;

import com.formdev.flatlaf.FlatClientProperties;
import games.strategy.engine.framework.lookandfeel.LookAndFeel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.annotation.Nullable;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import org.triplea.swing.SwingComponents;
import org.triplea.swing.jpanel.GridBagConstraintsAnchor;
import org.triplea.swing.jpanel.GridBagConstraintsBuilder;
import org.triplea.swing.jpanel.GridBagConstraintsFill;
import org.triplea.swing.jpanel.JPanelBuilder;

/**
 * A UI component that displays a collection of game properties and optionally allows them to be
 * edited. Editable on/off properties come first, as check boxes in up to two columns, then the
 * other properties as label and editor rows. Everything is in one column, so a game with many
 * options grows downwards instead of sideways.
 */
public class PropertiesUi extends JPanel {
  private static final long serialVersionUID = 3870459799384582310L;

  /** Width up to which the check boxes stand in two columns, and of the scrollable view. */
  private static final int MAX_WIDTH = 860;

  private static final int MAX_HEIGHT = 560;

  /** Width of a column of grouped options; the room shows as many columns as fit. */
  private static final int COLUMN_WIDTH = 300;

  private static final int GROUP_GAP = 16;

  // the client property of the grouped options' room with the number of columns it shows
  private static final String COLUMN_COUNT = "columnCount";

  /** Number of properties from which the scrollable view offers a filter field. */
  private static final int FILTER_THRESHOLD = 12;

  private final List<Row> rows = new ArrayList<>();
  // game options in a card per group, one switch per line, each value's name on top
  private final boolean grouped;

  public PropertiesUi(final GameProperties gameProperties, final boolean editable) {
    this(gameProperties.getEditableProperties(), editable);
  }

  public PropertiesUi(
      final List<? extends IEditableProperty<?>> properties, final boolean editable) {
    this(properties, editable, false);
  }

  private PropertiesUi(
      final List<? extends IEditableProperty<?>> properties,
      final boolean editable,
      final boolean grouped) {
    super(new GridBagLayout());
    this.grouped = grouped;
    for (final IEditableProperty<?> property : properties) {
      rows.add(
          new Row(
              property.getName(),
              property.getDescription(),
              editable ? property.getEditorComponent() : property.getViewComponent()));
    }
    layoutRows(rows);
  }

  /**
   * The game's options in a card per group, eg Dice & battles, with one switch per line and each
   * value's name on top; the cards stand in as many columns as the width holds.
   */
  public static PropertiesUi newGrouped(
      final GameProperties gameProperties, final boolean editable) {
    return new PropertiesUi(gameProperties.getEditableProperties(), editable, true);
  }

  private void layoutRows(final Collection<Row> visibleRows) {
    removeAll();
    final int y = grouped ? addByCategory(visibleRows) : addByKind(visibleRows);
    // keeps the rows at the top when the panel is taller than they need
    add(
        new JPanel(),
        new GridBagConstraintsBuilder(0, y).weightY(1).fill(GridBagConstraintsFill.BOTH).build());
    revalidate();
    repaint();
  }

  /**
   * The switches, then the values, each under a heading when there are both.
   *
   * @return the next free row
   */
  private int addByKind(final Collection<Row> visibleRows) {
    final List<Row> switches = visibleRows.stream().filter(Row::isSwitch).toList();
    final List<Row> values = visibleRows.stream().filter(row -> !row.isSwitch()).toList();
    // headings only help to tell the two kinds apart
    final boolean bothKinds = !switches.isEmpty() && !values.isEmpty();
    int y = 0;
    if (!switches.isEmpty()) {
      if (bothKinds) {
        add(LookAndFeel.newSectionLabel("On / off"), newRowConstraints(y++, 0));
      }
      add(newSwitchesPanel(switches, MAX_WIDTH), newRowConstraints(y++, 4));
    }
    if (!values.isEmpty()) {
      if (bothKinds) {
        add(LookAndFeel.newSectionLabel("Values"), newRowConstraints(y++, 18));
      }
      add(newValuesPanel(values), newRowConstraints(y++, 4));
    }
    return y;
  }

  /**
   * A card per group, eg Dice & battles, so a player finds related options together; in each the
   * switches one per line, then the values. The cards stand in columns of one width, as many as the
   * room holds.
   *
   * @return the next free row
   */
  private int addByCategory(final Collection<Row> visibleRows) {
    final Map<GameOptionCategory, List<Row>> groups =
        visibleRows.stream()
            .collect(
                Collectors.groupingBy(
                    row -> GameOptionCategory.of(row.name),
                    () -> new EnumMap<>(GameOptionCategory.class),
                    Collectors.toList()));
    final List<JPanel> cards =
        groups.entrySet().stream()
            // one group needs no heading
            .map(
                group ->
                    newGroupCard(
                        groups.size() >= 2 ? group.getKey().getTitle() : "", group.getValue()))
            .toList();
    // the columns keep their width on the left; the room on the right decides how many there are
    final JPanel room = new JPanel(new BorderLayout());
    arrangeInColumns(room, cards, 1);
    room.addComponentListener(
        new ComponentAdapter() {
          @Override
          public void componentResized(final ComponentEvent e) {
            final int count =
                Math.clamp(
                    (room.getWidth() + GROUP_GAP) / (COLUMN_WIDTH + GROUP_GAP),
                    1,
                    Math.max(1, cards.size()));
            // laid out again only when the number of columns changes
            if (!Integer.valueOf(count).equals(room.getClientProperty(COLUMN_COUNT))) {
              arrangeInColumns(room, cards, count);
            }
          }
        });
    add(room, newRowConstraints(0, 0));
    return 1;
  }

  private static JPanel newGroupCard(final String title, final List<Row> rows) {
    final JPanel panel = new JPanel(new GridBagLayout());
    LookAndFeel.setCardStyle(panel);
    panel.setBorder(BorderFactory.createEmptyBorder(12, 14, 14, 14));
    int y = 0;
    if (!title.isEmpty()) {
      panel.add(LookAndFeel.newSectionLabel(title), newRowConstraints(y++, 0));
    }
    final List<Row> switches = rows.stream().filter(Row::isSwitch).toList();
    final List<Row> values = rows.stream().filter(row -> !row.isSwitch()).toList();
    if (!switches.isEmpty()) {
      // never two switches side by side in a group
      panel.add(newSwitchesPanel(switches, 0), newRowConstraints(y++, 6));
    }
    if (!values.isEmpty()) {
      panel.add(newStackedValuesPanel(values), newRowConstraints(y, switches.isEmpty() ? 6 : 12));
    }
    // the card's color shows through, else the inner panels stand as dark boxes on it
    for (final Component inner : panel.getComponents()) {
      if (inner instanceof JPanel innerPanel) {
        innerPanel.setOpaque(false);
      }
    }
    return panel;
  }

  /**
   * Puts each card, in order, below the column that is shortest so far, so groups of a few options
   * fill the room beside long ones; all columns start at the top and are one width.
   */
  private static void arrangeInColumns(
      final JPanel room, final List<JPanel> cards, final int count) {
    final List<Box> stacks =
        IntStream.range(0, count).mapToObj(i -> Box.createVerticalBox()).toList();
    final int[] heights = new int[count];
    for (final JPanel card : cards) {
      final int shortest =
          IntStream.range(0, count).reduce((a, b) -> heights[b] < heights[a] ? b : a).orElse(0);
      if (heights[shortest] > 0) {
        stacks.get(shortest).add(Box.createVerticalStrut(GROUP_GAP));
      }
      stacks.get(shortest).add(card);
      heights[shortest] += card.getPreferredSize().height + GROUP_GAP;
    }
    final JPanel columns = new JPanel(new GridLayout(1, count, GROUP_GAP, 0));
    for (final Box stack : stacks) {
      // at the top of its column, not stretched to the longest one; the strut sets the width
      final JPanel column = new JPanel(new BorderLayout());
      column.add(stack, BorderLayout.NORTH);
      column.add(Box.createHorizontalStrut(COLUMN_WIDTH), BorderLayout.SOUTH);
      columns.add(column);
    }
    room.removeAll();
    room.add(columns, BorderLayout.WEST);
    room.putClientProperty(COLUMN_COUNT, count);
    room.revalidate();
    room.repaint();
  }

  private static GridBagConstraints newRowConstraints(final int y, final int spaceAbove) {
    return new GridBagConstraintsBuilder(0, y)
        .weightX(1)
        .fill(GridBagConstraintsFill.HORIZONTAL)
        .insets(spaceAbove, 0, 0, 0)
        .build();
  }

  private static JPanel newSwitchesPanel(final List<Row> switches, final int maxWidth) {
    final JPanel panel = new JPanel(new GridLayout(0, 2, 24, 2));
    switches.forEach(row -> panel.add(row.editor));
    if (panel.getPreferredSize().width > maxWidth) {
      panel.setLayout(new GridLayout(0, 1, 0, 2));
    }
    return panel;
  }

  private static JPanel newValuesPanel(final List<Row> values) {
    final JPanel panel = new JPanel(new GridBagLayout());
    int y = 0;
    for (final Row row : values) {
      // top-aligned, so that the label of a tall editor stays next to its first line
      panel.add(
          row.label,
          new GridBagConstraintsBuilder(0, y)
              .anchor(GridBagConstraintsAnchor.NORTHWEST)
              .insets(y == 0 ? 0 : 8, 0, 0, 16)
              .build());
      panel.add(
          row.editor,
          new GridBagConstraintsBuilder(1, y)
              .weightX(1)
              .anchor(GridBagConstraintsAnchor.NORTHWEST)
              .insets(y == 0 ? 0 : 8, 0, 0, 0)
              .build());
      y++;
    }
    return panel;
  }

  /** Each value's name above its editor, for a column too narrow for both side by side. */
  private static JPanel newStackedValuesPanel(final List<Row> values) {
    final JPanel panel = new JPanel(new GridBagLayout());
    int y = 0;
    for (final Row row : values) {
      panel.add(
          row.label,
          new GridBagConstraintsBuilder(0, y)
              .weightX(1)
              .anchor(GridBagConstraintsAnchor.WEST)
              .insets(y == 0 ? 0 : 10, 0, 0, 0)
              .build());
      panel.add(
          row.editor,
          new GridBagConstraintsBuilder(0, y + 1)
              .anchor(GridBagConstraintsAnchor.WEST)
              .insets(3, 0, 0, 0)
              .build());
      y += 2;
    }
    return panel;
  }

  /**
   * Returns the properties in a scroll pane of at most the size of a dialog, with a filter field
   * above them when there are many.
   */
  public static JComponent newScrollableView(final PropertiesUi propertiesUi) {
    final JScrollPane scroll = SwingComponents.newJScrollPane(propertiesUi);
    final Dimension preferred = propertiesUi.getPreferredSize();
    final int scrollBarWidth = scroll.getVerticalScrollBar().getPreferredSize().width;
    scroll.setPreferredSize(
        new Dimension(
            Math.min(preferred.width + scrollBarWidth, MAX_WIDTH),
            Math.min(preferred.height, MAX_HEIGHT)));
    return withFilter(propertiesUi, scroll);
  }

  /**
   * The view of the properties, eg a scroll pane holding them, with a filter field above it when
   * there are many.
   */
  public static JComponent withFilter(final PropertiesUi propertiesUi, final JComponent view) {
    if (propertiesUi.rows.size() < FILTER_THRESHOLD) {
      return view;
    }
    final JTextField filterField = new JTextField();
    filterField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Filter options");
    // filtering the rows is quick, so it follows every keystroke on the Swing thread
    filterField
        .getDocument()
        .addDocumentListener(
            new DocumentListener() {
              @Override
              public void insertUpdate(final DocumentEvent e) {
                propertiesUi.filter(filterField.getText());
              }

              @Override
              public void removeUpdate(final DocumentEvent e) {
                propertiesUi.filter(filterField.getText());
              }

              @Override
              public void changedUpdate(final DocumentEvent e) {
                propertiesUi.filter(filterField.getText());
              }
            });
    // short enough to read as a field, also above options standing in several columns
    filterField.setColumns(24);
    return new JPanelBuilder()
        .borderLayout()
        .addNorth(
            new JPanelBuilder().border(0, 0, 10, 0).borderLayout().addWest(filterField).build())
        .addCenter(view)
        .build();
  }

  /** Shows only the properties whose name contains the text, ignoring case. */
  void filter(final String text) {
    final String searched = text.strip().toLowerCase(Locale.ROOT);
    layoutRows(
        rows.stream().filter(row -> row.name.toLowerCase(Locale.ROOT).contains(searched)).toList());
  }

  /** One property: its name and its editor, and for all but check boxes a label. */
  private static final class Row {
    private final String name;
    private final JComponent editor;
    // check boxes show the name themselves, so that clicking it toggles them
    private final @Nullable JLabel label;

    Row(final String name, @Nullable final String description, final JComponent editor) {
      this.name = name;
      this.editor = editor;
      // a read-only check box is disabled, it would paint the name grey; it keeps a label
      if (editor instanceof JCheckBox checkBox
          && checkBox.getText().isEmpty()
          && checkBox.isEnabled()) {
        checkBox.setText(name);
        label = null;
      } else {
        label = new JLabel(name);
      }
      if (description != null && !description.isEmpty()) {
        editor.setToolTipText(description);
        if (label != null) {
          label.setToolTipText(description);
        }
      }
    }

    boolean isSwitch() {
      return label == null;
    }
  }
}
