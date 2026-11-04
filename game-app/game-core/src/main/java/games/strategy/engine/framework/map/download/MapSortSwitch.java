package games.strategy.engine.framework.map.download;

import java.awt.BorderLayout;
import java.io.Serial;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;

/**
 * The sort button next to the search, eg "Sort: Name" with a small arrow; a click opens the orders
 * to choose from, Name (A–Z), Rating (highest first) or Downloads (most first). Only the player's
 * choice reports a change; {@link #showChosen} does not.
 */
final class MapSortSwitch extends JPanel {
  @Serial private static final long serialVersionUID = 5106447405385262214L;

  private final JButton button;
  private final Map<MapSort, JRadioButtonMenuItem> items = new EnumMap<>(MapSort.class);

  MapSortSwitch(final MapSort sort, final Consumer<MapSort> sortChosen) {
    super(new BorderLayout());
    setOpaque(false);
    setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0));

    final JPopupMenu menu = new JPopupMenu();
    final ButtonGroup group = new ButtonGroup();
    for (final MapSort each : MapSort.values()) {
      final JRadioButtonMenuItem item =
          new JRadioButtonMenuItem(each.getLabel() + " (" + each.getOrder() + ")");
      item.addActionListener(
          e -> {
            showChosen(each);
            sortChosen.accept(each);
          });
      group.add(item);
      items.put(each, item);
      menu.add(item);
    }
    button = MapViewSwitch.newMenuButton(menu);
    button.setToolTipText("Sort the maps");
    add(button, BorderLayout.CENTER);
    showChosen(sort);
  }

  /** Shows the order as the chosen one, eg after the button of the other tab changed it. */
  void showChosen(final MapSort sort) {
    items.get(sort).setSelected(true);
    button.setText("Sort: " + sort.getLabel() + " ▾");
    button.getAccessibleContext().setAccessibleName("Sort: " + sort.getLabel());
  }
}
