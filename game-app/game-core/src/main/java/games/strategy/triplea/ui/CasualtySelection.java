package games.strategy.triplea.ui;

import com.google.common.annotations.VisibleForTesting;
import games.strategy.engine.data.GameData;
import games.strategy.engine.data.GamePlayer;
import games.strategy.engine.data.Unit;
import games.strategy.engine.posted.game.pbem.OpponentQuestion;
import games.strategy.triplea.Properties;
import games.strategy.triplea.delegate.Matches;
import games.strategy.triplea.delegate.data.CasualtyDetails;
import games.strategy.triplea.delegate.data.CasualtyList;
import games.strategy.triplea.formatter.MyFormatter;
import games.strategy.triplea.odds.calculator.OrderOfLosses;
import games.strategy.triplea.util.UnitOwner;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Toolkit;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;

/**
 * drives the casualty selection process:
 *
 * <p>1. construct a {@code CasualtySelection} object e.g. {@code final var casualtySelection = new
 * CasualtySelection([...]);}
 *
 * <p>2. call {@code showModelDialog} e.g. {@code final CasualtyDetails selectedCasualties =
 * casualtySelection.showModalDialog().orElse(null);}
 *
 * <p>In a game by forum or email where the casualties are the opponent's, the opponent may be asked
 * for them in the forum, and the order of losses they posted picks them.
 */
public class CasualtySelection {
  @VisibleForTesting public UnitChooser chooser;

  private final Collection<Unit> selectFrom;
  private final Map<Unit, Collection<Unit>> dependents;
  private final int hitsToTake;
  private final String title;
  private final GamePlayer player;
  private final CasualtyList defaultCasualties;
  private final boolean allowMultipleHitsPerUnit;
  private final UiContext uiContext;

  private final JDialog dialog;
  private final boolean isEditMode;

  private final JOptionPane optionPane;
  private final JScrollPane chooserScrollPane;

  /**
   * @param opponent asks the opponent for the casualties in a game by forum or email
   * @param place where the battle is, eg "Sea Zone 6", for the question
   */
  public CasualtySelection(
      final Collection<Unit> selectFrom,
      final Map<Unit, Collection<Unit>> dependents,
      final int hitsToTake,
      final String title,
      final GamePlayer player,
      final CasualtyList defaultCasualties,
      final boolean allowMultipleHitsPerUnit,
      final UiContext uiContext,
      final Component dialogParent,
      final boolean isEditMode,
      final OpponentQuestion opponent,
      final String place) {
    this.selectFrom = selectFrom;
    this.dependents = dependents;
    this.hitsToTake = hitsToTake;
    this.title = title;
    this.player = player;
    this.defaultCasualties = defaultCasualties;
    this.allowMultipleHitsPerUnit = allowMultipleHitsPerUnit;
    this.uiContext = uiContext;
    this.isEditMode = isEditMode;

    chooser = newChooser(defaultCasualties);
    chooserScrollPane = new JScrollPane(chooser);
    chooserScrollPane.setBorder(BorderFactory.createEmptyBorder(1, 1, 1, 1));
    final JPanel message = new JPanel(new BorderLayout(0, 6));
    message.add(chooserScrollPane, BorderLayout.CENTER);
    if (!isEditMode && opponent.isOpponentsDecision(player)) {
      message.add(newForumRow(opponent, place), BorderLayout.NORTH);
    }
    optionPane = new JOptionPane(message, JOptionPane.PLAIN_MESSAGE, JOptionPane.OK_CANCEL_OPTION);

    dialog = optionPane.createDialog(dialogParent, player.getName() + " select casualties");
  }

  private UnitChooser newChooser(final CasualtyList selected) {
    final UnitChooser unitChooser =
        new UnitChooser(
            selectFrom,
            selected,
            dependents,
            Properties.getPartialAmphibiousRetreat(player.getData().getProperties()),
            playerMayChooseToDistributeHitsToUnitsWithDifferentMovement(selectFrom),
            allowMultipleHitsPerUnit,
            uiContext);
    unitChooser.setTitle(title);
    if (isEditMode) {
      unitChooser.disableMax();
    } else {
      unitChooser.setMax(hitsToTake);
    }
    return unitChooser;
  }

  /**
   * The order of losses the opponent posted, which picks the casualties, and the question to the
   * opponent, as forum players ask for one.
   */
  private JComponent newForumRow(final OpponentQuestion opponent, final String place) {
    final JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
    row.add(new JLabel("Order of losses:"));
    final JTextField orderOfLosses = new JTextField(22);
    orderOfLosses.setToolTipText(
        "Unit types in the order they are lost, eg: "
            + exampleOrder()
            + "; \"2^infantry\" loses two");
    row.add(orderOfLosses);
    final JButton apply = new JButton("Apply");
    apply.addActionListener(e -> applyOrderOfLosses(orderOfLosses));
    orderOfLosses.addActionListener(e -> applyOrderOfLosses(orderOfLosses));
    row.add(apply);
    opponent
        .newAskButton(
            player,
            () ->
                "which "
                    + hitsToTake
                    + (hitsToTake == 1 ? " unit" : " units")
                    + " do you lose in "
                    + place
                    + "? You have "
                    + MyFormatter.unitsToTextNoOwner(selectFrom)
                    + ". Please answer with your order of losses, eg: "
                    + exampleOrder()
                    + ".")
        .ifPresent(row::add);
    return row;
  }

  /** The unit types to choose from, those lost by default first, eg "infantry, armour". */
  private String exampleOrder() {
    return Stream.concat(defaultCasualties.getKilled().stream(), selectFrom.stream())
        .map(unit -> unit.getType().getName())
        .distinct()
        .collect(Collectors.joining(", "));
  }

  /** Picks the casualties by the order of losses written; tells when it is none of this game's. */
  private void applyOrderOfLosses(final JTextField written) {
    final GameData data = player.getData();
    final Optional<String> orderOfLosses = OrderOfLosses.of(written.getText(), data);
    if (orderOfLosses.isEmpty()) {
      JOptionPane.showMessageDialog(
          dialog,
          "Not an order of losses of this game's unit types, eg: " + exampleOrder(),
          "Order of losses",
          JOptionPane.WARNING_MESSAGE);
      return;
    }
    final List<Unit> killed =
        OrderOfLosses.killedInOrder(
            OrderOfLosses.unitsInOrder(orderOfLosses.get(), selectFrom, data),
            selectFrom,
            defaultCasualties.getKilled().size(),
            defaultCasualties.getKilled());
    chooser = newChooser(new CasualtyList(killed, defaultCasualties.getDamaged()));
    chooserScrollPane.setViewportView(chooser);
  }

  /**
   * @return the CasualtyDetails that describe which casualties the player selected or empty iff
   *     something went weirdly wrong
   */
  public Optional<CasualtyDetails> showModalDialog() {
    chooserScrollPane.setFont(
        BattleDisplay.getPlayerComponent(player).getFont().deriveFont(Font.BOLD, 14));
    final Dimension size = chooserScrollPane.getPreferredSize();
    final Dimension screenResolution = Toolkit.getDefaultToolkit().getScreenSize();
    final int availHeight = screenResolution.height - 130;
    final int availWidth = screenResolution.width - 30;
    chooserScrollPane.setPreferredSize(
        new Dimension(
            Math.min(
                availWidth,
                size.width
                    + (size.height > availHeight
                        ? chooserScrollPane.getVerticalScrollBar().getPreferredSize().width
                        : 0)),
            Math.min(availHeight, size.height)));

    dialog.setVisible(true);
    final Object option = optionPane.getValue();
    final int ioption = option instanceof Integer ? (Integer) option : -1;

    if (ioption != JOptionPane.OK_OPTION) {
      return Optional.empty();
    }

    final List<Unit> killed = chooser.getSelected(false);
    final List<Unit> damaged = chooser.getSelectedDamagedMultipleHitPointUnits();
    if (!isEditMode && (killed.size() + damaged.size() != hitsToTake)) {
      JOptionPane.showMessageDialog(
          dialog /*.getParent()*/,
          "Wrong number of casualties selected",
          player.getName() + " select casualties",
          JOptionPane.ERROR_MESSAGE);

      return Optional.empty();
    }

    return Optional.of(new CasualtyDetails(killed, damaged, false));
  }

  /**
   * This method determines whether the system should let the player choose how to distribute hits
   * between units of the same owner and type differentiating by the movement points left.
   *
   * <p>This is only considered to be the case if there are - air units - that can take damage
   * without bing killed (i.e. that have more than one hitpoint left) and - that have different
   * movement points left.
   *
   * @param units among which the hits have to distributed
   * @return {@code true} iff the system should let the player choose
   */
  static boolean playerMayChooseToDistributeHitsToUnitsWithDifferentMovement(
      final Collection<Unit> units) {
    final Map<UnitOwner, List<Unit>> unitsGroupedByOwnerAndType =
        units.stream()
            .filter(Matches.unitIsAir())
            .filter(Unit::canTakeHitWithoutBeingKilled)
            .collect(Collectors.groupingBy(UnitOwner::new, Collectors.toList()));

    for (final UnitOwner ownerAndType : unitsGroupedByOwnerAndType.keySet()) {
      final Iterator<Unit> unitsOfSameOwnerAndType =
          unitsGroupedByOwnerAndType.get(ownerAndType).iterator();

      final BigDecimal movementOfFirstUnit = unitsOfSameOwnerAndType.next().getMovementLeft();

      while (unitsOfSameOwnerAndType.hasNext()) {
        final Unit next = unitsOfSameOwnerAndType.next();

        if (!next.getMovementLeft().equals(movementOfFirstUnit)) {
          return true;
        }
      }
    }

    return false;
  }
}
