package games.strategy.triplea.ui.menubar;

import games.strategy.engine.data.GameData;
import games.strategy.engine.data.properties.PropertiesUi;
import games.strategy.engine.framework.ClientGame;
import games.strategy.engine.framework.IGame;
import games.strategy.engine.random.IRandomStats;
import games.strategy.engine.random.PlainRandomSource;
import games.strategy.engine.random.RandomStatsDetails;
import games.strategy.triplea.image.MapImage;
import games.strategy.triplea.image.TileImageFactory;
import games.strategy.triplea.settings.ClientSetting;
import games.strategy.triplea.settings.SettingType;
import games.strategy.triplea.ui.PoliticalStateOverview;
import games.strategy.triplea.ui.TripleAFrame;
import games.strategy.triplea.ui.VerifiedRandomNumbersDialog;
import games.strategy.triplea.ui.statistics.StatisticsDialog;
import java.awt.Dimension;
import java.awt.GridBagLayout;
import java.awt.Toolkit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.UtilityClass;
import org.triplea.swing.IntTextField;
import org.triplea.swing.JMenuItemBuilder;
import org.triplea.swing.jpanel.GridBagConstraintsAnchor;
import org.triplea.swing.jpanel.GridBagConstraintsBuilder;
import org.triplea.swing.jpanel.GridBagConstraintsFill;
import org.triplea.swing.key.binding.KeyCode;

/**
 * What was the Game menu, for the quick bar: the statistics and dice entries, the game's options to
 * look at, the politics panel and the settings. There is no Game menu any more; edit mode,
 * politics, the battle calculator, the history and the settings are icons of the quick bar.
 */
@UtilityClass
final class GameMenu {

  @AllArgsConstructor(access = AccessLevel.PRIVATE)
  @Getter
  public enum Mnemonic {
    SHOW_GAME(KeyCode.G),
    SHOW_HISTORY(KeyCode.H),
    // 'H' hides an app on macOS
    SHOW_HISTORY_MAC(KeyCode.Y),
    GAME_OPTIONS(KeyCode.O),
    SHOW_VERIFIED_DICE(KeyCode.V),
    BATTLE_CALCULATOR(KeyCode.B),
    ENABLE_EDIT_MODE(KeyCode.E),
    SHOW_DICE_STATS(KeyCode.D),
    ROLL_DICE(KeyCode.R),
    GAME_STATISTICS(KeyCode.A);

    private final KeyCode mnemonicCode;

    public int getValue() {
      return mnemonicCode.getInputEventCode();
    }
  }

  /**
   * The statistics and dice of the game, for the quick bar's statistics menu: the game's statistics
   * (a beta feature), the dice stats, the verified dice of a joined game and a dice roll without
   * effect on the game.
   */
  static List<JMenuItem> newStatisticsItems(final TripleAFrame frame) {
    final List<JMenuItem> items = new ArrayList<>();
    if (ClientSetting.showBetaFeatures.getValueOrThrow()) {
      items.add(addStatistics(frame));
    }
    items.add(addShowDiceStats(frame));
    if (frame.getGame() instanceof ClientGame) {
      items.add(
          new JMenuItemBuilder("Show Verified Dice", Mnemonic.SHOW_VERIFIED_DICE.getMnemonicCode())
              .actionListener(
                  () -> new VerifiedRandomNumbersDialog(frame.getRootPane()).setVisible(true))
              .build());
    }
    items.add(addRollDice(frame));
    return items;
  }

  /** The game's options to look at, for the Help menu; none for a game without options. */
  static Optional<JMenuItem> newGameOptionsItem(final TripleAFrame frame) {
    final GameData gameData = frame.getGame().getData();
    if (gameData.getProperties().getEditableProperties().isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(
        new JMenuItemBuilder("Game Options", Mnemonic.GAME_OPTIONS.getMnemonicCode())
            .actionListener(
                () ->
                    JOptionPane.showMessageDialog(
                        frame,
                        PropertiesUi.newScrollableView(
                            new PropertiesUi(
                                gameData.getProperties().getEditableProperties(), false)),
                        "Game Options",
                        JOptionPane.PLAIN_MESSAGE))
            .build());
  }

  /**
   * Opens the settings on a section, eg In game from the quick bar, and shows what they changed in
   * the game: the map redrawn if its look changed, the comment log shown or hidden, the status bar
   * with or without the zoom and the units of the territory under the mouse.
   */
  static void showSettings(final TripleAFrame frame, final SettingType section) {
    final List<Object> mapLook = mapLook();
    final boolean commentLog = ClientSetting.showCommentLog.getValueOrThrow();
    ClientSetting.showSettingsWindow(frame, section, MapViewSettings.of(frame));
    if (!mapLook.equals(mapLook())) {
      // rebuilds every tile, relief included
      frame.getMapPanel().resetMap();
    }
    frame.getBottomBar().setMapZoomEnabled(ClientSetting.showMapZoom.getValueOrThrow());
    frame.getBottomBar().setTerritory(frame.getMapPanel().getCurrentTerritory());
    if (commentLog != ClientSetting.showCommentLog.getValueOrThrow()) {
      if (ClientSetting.showCommentLog.getValueOrThrow()) {
        frame.showCommentLog();
      } else {
        frame.hideCommentLog();
      }
    }
  }

  /** The values of the settings that change how the map looks. */
  private static List<Object> mapLook() {
    return List.of(
        ClientSetting.unitFlagDrawMode.getValueOrThrow(),
        ClientSetting.showTerritoryEffects.getValueOrThrow(),
        TileImageFactory.getShowReliefImages(),
        TileImageFactory.getShowMapBlends(),
        MapImage.getPropertyMapFont(),
        MapImage.getPropertyTerritoryNameAndPuAndCommentColor(),
        MapImage.getPropertyUnitCountColor(),
        MapImage.getPropertyUnitCountOutline(),
        MapImage.getPropertyUnitFactoryDamageColor(),
        MapImage.getPropertyUnitFactoryDamageOutline(),
        MapImage.getPropertyUnitHitDamageColor(),
        MapImage.getPropertyUnitHitDamageOutline());
  }

  /**
   * Shows the relations between the nations, to look at only, as the quick bar's politics icon and
   * Ctrl+W (Cmd+O on macOS) do.
   */
  static void showPoliticsPanel(final TripleAFrame frame) {
    final PoliticalStateOverview ui =
        new PoliticalStateOverview(frame.getGame().getData(), frame.getUiContext(), false);
    final JScrollPane scroll = new JScrollPane(ui);
    scroll.setBorder(BorderFactory.createEmptyBorder());
    final Dimension screenResolution = Toolkit.getDefaultToolkit().getScreenSize();
    // not only do we have a start bar, but we also have the message dialog to account for
    final int availHeight = screenResolution.height - 120;
    // just the scroll bars plus the window sides
    final int availWidth = screenResolution.width - 40;
    scroll.setPreferredSize(
        new Dimension(
            Math.min(scroll.getPreferredSize().width, availWidth),
            Math.min(scroll.getPreferredSize().height, availHeight)));
    JOptionPane.showMessageDialog(frame, scroll, "Politics Panel", JOptionPane.PLAIN_MESSAGE);
  }

  private static JMenuItem addShowDiceStats(final TripleAFrame frame) {
    return new JMenuItemBuilder("Show Dice Stats", Mnemonic.SHOW_DICE_STATS.getMnemonicCode())
        .actionListener(
            () -> {
              IGame game = frame.getGame();
              final IRandomStats randomStats =
                  (IRandomStats)
                      game.getMessengers().getRemote(IRandomStats.RANDOM_STATS_REMOTE_NAME);
              final RandomStatsDetails stats =
                  randomStats.getRandomStats(game.getData().getDiceSides());
              JOptionPane.showMessageDialog(
                  frame,
                  new JScrollPane(stats.getAllStats()),
                  "Random Stats",
                  JOptionPane.INFORMATION_MESSAGE);
            })
        .build();
  }

  private static JMenuItem addRollDice(final TripleAFrame frame) {
    return new JMenuItemBuilder("Roll Dice", Mnemonic.ROLL_DICE.getMnemonicCode())
        .actionListener(
            () -> {
              IGame game = frame.getGame();
              final IntTextField numberOfText = new IntTextField(0, 100);
              final IntTextField diceSidesText = new IntTextField(1, 200);
              numberOfText.setText(String.valueOf(0));
              diceSidesText.setText(String.valueOf(game.getData().getDiceSides()));
              // Use a local PlainRandomSource: this menu's rolls have no effect on the game and
              // are shown only in a local dialog. Clients (network/bot games) have no
              // game-level random source, so going through game.getRandomSource() would NPE.
              final JPanel panel = new JPanel();
              panel.setLayout(new GridBagLayout());
              panel.add(
                  new JLabel("Number of Dice to Roll: "),
                  new GridBagConstraintsBuilder(0, 0)
                      .anchor(GridBagConstraintsAnchor.WEST)
                      .fill(GridBagConstraintsFill.BOTH)
                      .insets(0, 0, 0, 20)
                      .build());
              panel.add(
                  new JLabel("Sides on the Dice: "),
                  new GridBagConstraintsBuilder(2, 0)
                      .anchor(GridBagConstraintsAnchor.WEST)
                      .fill(GridBagConstraintsFill.BOTH)
                      .insets(0, 20, 0, 10)
                      .build());
              panel.add(
                  numberOfText,
                  new GridBagConstraintsBuilder(0, 1)
                      .anchor(GridBagConstraintsAnchor.WEST)
                      .fill(GridBagConstraintsFill.BOTH)
                      .insets(0, 0, 0, 20)
                      .build());
              panel.add(
                  diceSidesText,
                  new GridBagConstraintsBuilder(2, 1)
                      .anchor(GridBagConstraintsAnchor.WEST)
                      .fill(GridBagConstraintsFill.BOTH)
                      .insets(0, 20, 0, 10)
                      .build());
              JOptionPane.showOptionDialog(
                  frame,
                  panel,
                  "Roll Dice",
                  JOptionPane.YES_NO_OPTION,
                  JOptionPane.INFORMATION_MESSAGE,
                  null,
                  new String[] {"OK"},
                  "OK");
              try {
                final int numberOfDice = Integer.parseInt(numberOfText.getText());
                if (numberOfDice > 0) {
                  final int diceSides = Integer.parseInt(diceSidesText.getText());
                  final int[] dice =
                      new PlainRandomSource()
                          .getRandom(diceSides, numberOfDice, "Rolling Dice, no effect on game.");
                  final JPanel panelDice = new JPanel();
                  final BoxLayout layout = new BoxLayout(panelDice, BoxLayout.Y_AXIS);
                  panelDice.setLayout(layout);
                  final JLabel label = new JLabel("Rolls (no effect on game): ");
                  panelDice.add(label);
                  final StringBuilder diceString = new StringBuilder();
                  for (int i = 0; i < dice.length; i++) {
                    diceString.append((dice[i] + 1)).append((i == dice.length - 1) ? "" : ", ");
                  }
                  final JTextField diceList = new JTextField(diceString.toString());
                  diceList.setEditable(false);
                  panelDice.add(diceList);
                  JOptionPane.showMessageDialog(
                      frame, panelDice, "Dice Rolled", JOptionPane.INFORMATION_MESSAGE);
                }
              } catch (final NumberFormatException ex) {
                // ignore malformed input
              }
            })
        .build();
  }

  private JMenuItem addStatistics(final TripleAFrame frame) {
    return new JMenuItemBuilder("Game Statistics", Mnemonic.GAME_STATISTICS.getMnemonicCode())
        .actionListener(
            () ->
                JOptionPane.showMessageDialog(
                    frame,
                    new StatisticsDialog(
                        frame.getGame().getData(), frame.getUiContext().getMapData()),
                    "Game Statistics",
                    JOptionPane.INFORMATION_MESSAGE))
        .build();
  }
}
