package games.strategy.triplea.ui;

import static games.strategy.triplea.image.UnitImageFactory.ImageKey;

import games.strategy.engine.data.GameData;
import games.strategy.engine.data.GamePlayer;
import games.strategy.engine.data.Territory;
import games.strategy.engine.framework.lookandfeel.LookAndFeel;
import games.strategy.triplea.attachments.TerritoryAttachment;
import games.strategy.triplea.odds.calculator.BattleCalculatorDialog;
import games.strategy.triplea.util.UnitCategory;
import games.strategy.triplea.util.UnitSeparator;
import games.strategy.ui.OverlayIcon;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import org.triplea.swing.JLabelBuilder;
import org.triplea.swing.key.binding.KeyCode;
import org.triplea.swing.key.binding.SwingKeyBinding;

/**
 * The territory under the mouse: its name and owner as a heading, what is special about it, the
 * battle calculator for it and its units, grouped by their owners.
 */
public class TerritoryDetailPanel extends JPanel {
  private static final long serialVersionUID = 1377022163587438988L;
  // unit tiles per row; four fit the side panel's width
  private static final int UNIT_COLUMNS = 4;
  private GameData gameData;
  private final UiContext uiContext;
  private final JButton showOdds = new JButton("Battle calculator");
  private final JButton addAttackers = new JButton("Add attackers");
  private final JButton addDefenders = new JButton("Add defenders");
  private final JButton findTerritoryButton;
  private final JLabel nameLabel = new JLabelBuilder().bold().adjustFontSize(4).build();
  private final JLabel summaryLabel =
      new JLabelBuilder().foregroundColor(LookAndFeel.getSecondaryTextColor()).build();
  private final JLabel territoryInfo = new JLabel();
  private final JLabel unitInfo =
      new JLabelBuilder().bold().border(BorderFactory.createEmptyBorder(10, 0, 0, 0)).build();
  private final JScrollPane units = new JScrollPane();
  private final JPanel buttons = new JPanel(new GridLayout(2, 2, 6, 6));
  private @Nullable Territory currentTerritory;
  private final TripleAFrame frame;

  TerritoryDetailPanel(final TripleAFrame frame) {
    this.frame = frame;
    this.uiContext = frame.getUiContext();
    this.gameData = frame.getGame().getData();
    frame
        .getMapPanel()
        .addMapSelectionListener(
            new DefaultMapSelectionListener() {
              @Override
              public void mouseEntered(final Territory territory) {
                territoryChanged(territory);
              }
            });

    findTerritoryButton = new JButton(new FindTerritoryAction(frame));
    findTerritoryButton.setText("Find territory");

    initLayout();
  }

  protected void initLayout() {
    setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
    setBorder(BorderFactory.createEmptyBorder(10, 12, 0, 8));

    showOdds.addActionListener(e -> BattleCalculatorDialog.show(frame, currentTerritory, gameData));
    showOdds.setToolTipText("The odds of a battle here (Ctrl+B)");
    addAttackers.addActionListener(e -> BattleCalculatorDialog.addAttackers(currentTerritory));
    addAttackers.setToolTipText(
        "Add the units here as attackers to the battle calculator (Ctrl+A)");
    addDefenders.addActionListener(e -> BattleCalculatorDialog.addDefenders(currentTerritory));
    addDefenders.setToolTipText(
        "Add the units here as defenders to the battle calculator (Ctrl+D)");
    findTerritoryButton.setToolTipText("Find a territory by its name (Ctrl+F)");
    addBattleCalculatorKeyBindings(frame);
    buttons.setBorder(BorderFactory.createEmptyBorder(10, 0, 4, 0));
    buttons.add(showOdds);
    buttons.add(findTerritoryButton);
    buttons.add(addAttackers);
    buttons.add(addDefenders);
    // as wide as the panel, never taller than its buttons
    buttons.setMaximumSize(new Dimension(Integer.MAX_VALUE, buttons.getPreferredSize().height));
    territoryInfo.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));
    units.setBorder(BorderFactory.createEmptyBorder());
    units.getVerticalScrollBar().setUnitIncrement(20);
    for (final JComponent component :
        List.of(nameLabel, summaryLabel, buttons, territoryInfo, unitInfo, units)) {
      component.setAlignmentX(Component.LEFT_ALIGNMENT);
      add(component);
    }
    setElementsVisible(false);
  }

  /**
   * Adds the battle calculator key bindings (CTRL-A, CTRL-D, CTRL-B) to the frame {@code jframe}.
   * When triggered the {@code addAttackers(Territory)}, {@code addDefenders(Territory)} and {@code
   * show(TripleAFrame, Territory, History)} methods of the battle calculator are triggered with the
   * TripleAFrame, current territory and history of this TerritoryDetailPanel.
   *
   * @param jframe the frame to add the key bindings to
   */
  public void addBattleCalculatorKeyBindings(final JFrame jframe) {
    SwingKeyBinding.addKeyBindingWithMetaAndCtrlMasks(
        jframe, KeyCode.B, () -> BattleCalculatorDialog.show(frame, currentTerritory, gameData));
    SwingKeyBinding.addKeyBindingWithMetaAndCtrlMasks(
        jframe, KeyCode.A, () -> BattleCalculatorDialog.addAttackers(currentTerritory));
    SwingKeyBinding.addKeyBindingWithMetaAndCtrlMasks(
        jframe, KeyCode.D, () -> BattleCalculatorDialog.addDefenders(currentTerritory));
  }

  /**
   * Same as {@code addBattleCalculatorKeyBindings(JFrame)} but for {@code JDialog}.
   *
   * @param dialog the dialog to add the key bindings to
   */
  public void addBattleCalculatorKeyBindings(final JDialog dialog) {
    SwingKeyBinding.addKeyBindingWithMetaAndCtrlMasks(
        dialog, KeyCode.B, () -> BattleCalculatorDialog.show(frame, currentTerritory, gameData));
    SwingKeyBinding.addKeyBindingWithMetaAndCtrlMasks(
        dialog, KeyCode.A, () -> BattleCalculatorDialog.addAttackers(currentTerritory));
    SwingKeyBinding.addKeyBindingWithMetaAndCtrlMasks(
        dialog, KeyCode.D, () -> BattleCalculatorDialog.addDefenders(currentTerritory));
  }

  private void setElementsVisible(final boolean visible) {
    nameLabel.setVisible(visible);
    summaryLabel.setVisible(visible);
    buttons.setVisible(visible);
    territoryInfo.setVisible(visible);
    unitInfo.setVisible(visible);
    units.setVisible(visible);
  }

  public void setGameData(final GameData data) {
    gameData = data;
    territoryChanged(null);
  }

  private void territoryChanged(final @Nullable Territory territory) {
    currentTerritory = territory;
    if (territory == null) {
      setElementsVisible(false);
      return;
    }
    setElementsVisible(true);
    final Optional<TerritoryAttachment> attachment = TerritoryAttachment.get(territory);
    nameLabel.setText(territory.getName());
    summaryLabel.setText(
        newSummary(
            territory, attachment.flatMap(TerritoryAttachment::getOriginalOwner).orElse(null)));
    // what is special about it (capital, victory city, ...), without the name and owners above
    final String details =
        attachment
            .map(territoryAttachment -> territoryAttachment.toStringForInfo(true, false))
            .orElse("");
    final String additionalText =
        frame.getAdditionalTerritoryDetails().computeAdditionalText(territory);
    final String info = joinHtml(details, additionalText);
    territoryInfo.setText(info.isEmpty() ? "" : "<html>" + info + "</html>");
    territoryInfo.setVisible(!info.isEmpty());

    final List<UnitCategory> unitsList;
    final long unitCount;

    // Get the unit information under lock as otherwise they may change on the game thread causing a
    // ConcurrentModificationException.
    try (GameData.Unlocker ignored = gameData.acquireReadLock()) {
      unitsList = UnitSeparator.getSortedUnitCategories(territory, uiContext.getMapData());
      unitCount =
          territory.getUnits().stream()
              .filter(u -> uiContext.getMapData().shouldDrawUnit(u.getType().getName()))
              .count();
    }

    unitInfo.setText(unitCount == 0 ? "No units" : "Units (" + unitCount + ")");
    units.setViewportView(unitsInTerritoryPanel(unitsList, uiContext));
  }

  /** Eg "Land territory · Germans, originally Russians". */
  private static String newSummary(
      final Territory territory, final @Nullable GamePlayer originalOwner) {
    final String kind = territory.isWater() ? "Sea zone" : "Land territory";
    final GamePlayer owner = territory.getOwner();
    if (owner == null || owner.isNull()) {
      return kind;
    }
    final String originally =
        originalOwner == null || originalOwner.equals(owner)
            ? ""
            : ", originally " + originalOwner.getName();
    return kind + "  ·  " + owner.getName() + originally;
  }

  private static String joinHtml(final String first, final String second) {
    final String trimmedFirst = first.strip().replaceAll("(<br>)+$", "");
    if (trimmedFirst.isEmpty()) {
      return second;
    }
    return second.isEmpty() ? trimmedFirst : trimmedFirst + "<br>" + second;
  }

  /** The units in a grid of tiles, a heading with flag and name for each owner. */
  private static JPanel unitsInTerritoryPanel(
      final List<UnitCategory> units, final UiContext uiContext) {
    final JPanel panel = new JPanel();
    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

    @Nullable GamePlayer currentPlayer = null;
    @Nullable JPanel tiles = null;
    for (final UnitCategory item : units) {
      if (tiles == null || !item.isOwnedBy(currentPlayer)) {
        currentPlayer = item.getOwner();
        panel.add(newOwnerHeading(currentPlayer, uiContext));
        tiles = new JPanel(new GridLayout(0, UNIT_COLUMNS, 4, 4));
        tiles.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(tiles);
      }
      tiles.add(newUnitTile(item, currentPlayer, uiContext));
    }
    // the tiles at the top, not spread over the height of the scroll pane
    final JPanel top = new JPanel(new BorderLayout());
    top.add(panel, BorderLayout.NORTH);
    return top;
  }

  private static JLabel newOwnerHeading(final GamePlayer owner, final UiContext uiContext) {
    final JLabel heading =
        new JLabelBuilder()
            .text(owner.getName())
            .foregroundColor(LookAndFeel.getSecondaryTextColor())
            .iconTextGap(6)
            .border(BorderFactory.createEmptyBorder(8, 0, 4, 0))
            .build();
    heading.setIcon(new ImageIcon(uiContext.getFlagImageFactory().getSmallFlag(owner)));
    heading.setAlignmentX(Component.LEFT_ALIGNMENT);
    return heading;
  }

  /** The unit's image with its owner's flag at the upper right, the count below. */
  private static JLabel newUnitTile(
      final UnitCategory item, final GamePlayer owner, final UiContext uiContext) {
    final ImageIcon unitIcon = uiContext.getUnitImageFactory().getIcon(ImageKey.of(item));
    final ImageIcon flagIcon =
        new ImageIcon(uiContext.getFlagImageFactory().getSmallFlag(item.getOwner()));
    final Icon flaggedUnitIcon =
        new OverlayIcon(
            unitIcon, flagIcon, unitIcon.getIconWidth() - (flagIcon.getIconWidth() / 2), 0);
    final JLabel tile =
        new JLabel("×" + item.getUnits().size(), flaggedUnitIcon, SwingConstants.CENTER);
    tile.setVerticalTextPosition(SwingConstants.BOTTOM);
    tile.setHorizontalTextPosition(SwingConstants.CENTER);
    tile.setToolTipText(
        "<html>"
            + item.getType().getName()
            + ": "
            + uiContext.getTooltipProperties().getTooltip(item.getType(), owner)
            + "</html>");
    return tile;
  }
}
