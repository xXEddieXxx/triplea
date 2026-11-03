package games.strategy.triplea.ui.menubar;

import games.strategy.engine.data.GameData;
import games.strategy.engine.data.properties.PropertiesUi;
import games.strategy.engine.framework.ClientGame;
import games.strategy.engine.framework.IGame;
import games.strategy.engine.framework.system.SystemProperties;
import games.strategy.engine.random.IRandomStats;
import games.strategy.engine.random.PlainRandomSource;
import games.strategy.engine.random.RandomStatsDetails;
import games.strategy.triplea.image.MapImage;
import games.strategy.triplea.image.TileImageFactory;
import games.strategy.triplea.odds.calculator.BattleCalculatorDialog;
import games.strategy.triplea.settings.ClientSetting;
import games.strategy.triplea.ui.PoliticalStateOverview;
import games.strategy.triplea.ui.TripleAFrame;
import games.strategy.triplea.ui.VerifiedRandomNumbersDialog;
import games.strategy.triplea.ui.statistics.StatisticsDialog;
import java.awt.Dimension;
import java.awt.GridBagLayout;
import java.awt.Toolkit;
import java.util.List;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.UtilityClass;
import org.triplea.sound.SoundOptions;
import org.triplea.swing.IntTextField;
import org.triplea.swing.JMenuBuilder;
import org.triplea.swing.JMenuItemBuilder;
import org.triplea.swing.JMenuItemCheckBoxBuilder;
import org.triplea.swing.jpanel.GridBagConstraintsAnchor;
import org.triplea.swing.jpanel.GridBagConstraintsBuilder;
import org.triplea.swing.jpanel.GridBagConstraintsFill;
import org.triplea.swing.key.binding.KeyCode;

@UtilityClass
final class GameMenu {

  @AllArgsConstructor(access = AccessLevel.PRIVATE)
  @Getter
  public enum Mnemonic {
    ENGINE_SETTINGS(KeyCode.E),
    SHOW_GAME(KeyCode.G),
    SHOW_HISTORY(KeyCode.H),
    SHOW_HISTORY_MAC(KeyCode.Y),
    GAME_OPTIONS(KeyCode.O),
    SHOW_VERIFIED_DICE(KeyCode.V),
    BATTLE_CALCULATOR(KeyCode.B),
    ENABLE_EDIT_MODE(KeyCode.E),
    SHOW_POLITICS_PANEL(KeyCode.P),
    USER_NOTIFICATIONS(KeyCode.U),
    SHOW_DICE_STATS(KeyCode.D),
    ROLL_DICE(KeyCode.R),
    GAME_STATISTICS(KeyCode.A);

    private final KeyCode mnemonicCode;

    public int getValue() {
      return mnemonicCode.getInputEventCode();
    }
  }

  static JMenu get(final TripleAFrame frame) {
    final GameData gameData = frame.getGame().getData();
    return new JMenuBuilder("Game", TripleAMenuBar.Mnemonic.GAME.getMnemonicCode())
        .addMenuItem(addEditMode(frame))
        .addSeparator()
        .addMenuItem(
            new JMenuItemBuilder("Settings", Mnemonic.ENGINE_SETTINGS.getMnemonicCode())
                .actionListener(() -> showSettings(frame)))
        .addMenuItem(SoundOptions.buildGlobalSoundSwitchMenuItem())
        .addMenuItem(SoundOptions.buildSoundOptionsMenuItem())
        .addSeparator()
        .addMenuItem(
            addMenuItemWithHotkey(frame.getShowGameAction(), Mnemonic.SHOW_GAME.getMnemonicCode()))
        .addMenuItem(
            addMenuItemWithHotkey(
                frame.getShowHistoryAction(),
                // 'H' is a reserved hotkey in Mac, used to minimize apps use 'Y' instead for macOS.
                (SystemProperties.isMac() ? Mnemonic.SHOW_HISTORY_MAC : Mnemonic.SHOW_HISTORY)
                    .getMnemonicCode()))
        .addSeparator()
        .addMenuItemIf(
            !gameData.getProperties().getEditableProperties().isEmpty(),
            () ->
                new JMenuItemBuilder("Game Options", Mnemonic.GAME_OPTIONS.getMnemonicCode())
                    .actionListener(
                        () -> {
                          final PropertiesUi ui =
                              new PropertiesUi(
                                  gameData.getProperties().getEditableProperties(), false);
                          JOptionPane.showMessageDialog(
                              frame,
                              PropertiesUi.newScrollableView(ui),
                              "Game Options",
                              JOptionPane.PLAIN_MESSAGE);
                        })
                    .build())
        .addMenuItemIf(
            frame.getGame() instanceof ClientGame,
            () ->
                new JMenuItemBuilder(
                        "Show Verified Dice", Mnemonic.SHOW_VERIFIED_DICE.getMnemonicCode())
                    .actionListener(
                        () -> new VerifiedRandomNumbersDialog(frame.getRootPane()).setVisible(true))
                    .build())
        .addMenuItem(getShowPoliticsPanel(frame))
        .addMenuItem(getNotificationSettings())
        .addMenuItem(addShowDiceStats(frame))
        .addMenuItem(addRollDice(frame))
        .addMenuItemIf(ClientSetting.showBetaFeatures.getValueOrThrow(), () -> addStatistics(frame))
        .addMenuItem(
            addMenuItemWithHotkey(
                "Battle Calculator",
                () -> BattleCalculatorDialog.show(frame, null, frame.getGame().getData()),
                Mnemonic.BATTLE_CALCULATOR.getMnemonicCode()))
        .build();
  }

  /**
   * Opens the settings, which return once the player leaves them, then redraws the map if they
   * changed how it looks: its flags, relief, blends, text or colours.
   */
  private static void showSettings(final TripleAFrame frame) {
    final List<Object> mapLook = mapLook();
    ClientSetting.showSettingsWindow(frame);
    if (!mapLook.equals(mapLook())) {
      // rebuilds every tile, relief included
      frame.getMapPanel().resetMap();
    }
  }

  /** The values of the settings that change how the map looks. */
  private static List<Object> mapLook() {
    return List.of(
        ClientSetting.unitFlagDrawMode.getValueOrThrow(),
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

  private static JMenuItem addMenuItemWithHotkey(Action action, KeyCode keyCode) {
    return new JMenuItemBuilder(action, keyCode).accelerator(keyCode).build();
  }

  private static JMenuItem addMenuItemWithHotkey(
      final String title, final Runnable runnable, final KeyCode keyCode) {
    return new JMenuItemBuilder(title, keyCode)
        .accelerator(keyCode)
        .actionListener(runnable)
        .build();
  }

  private static JCheckBoxMenuItem addEditMode(final TripleAFrame frame) {
    KeyCode mnemonicAndAccelerator = Mnemonic.ENABLE_EDIT_MODE.getMnemonicCode();
    final JCheckBoxMenuItem editMode =
        new JMenuItemCheckBoxBuilder("Enable Edit Mode", mnemonicAndAccelerator)
            // dummy action as menu checkbox button model is linked to the frame
            .actionListener(selected -> {})
            .accelerator(mnemonicAndAccelerator)
            .build();
    editMode.setModel(frame.getEditModeButtonModel());
    return editMode;
  }

  /**
   * Add a Politics Panel button to the game menu, this panel will show the current political
   * landscape as a reference, no actions on this panel.
   */
  private static JMenuItem getShowPoliticsPanel(final TripleAFrame frame) {
    return new JMenuItemBuilder(
            "Show Politics Panel", Mnemonic.SHOW_POLITICS_PANEL.getMnemonicCode())
        // On Mac, Cmd-W is the standard "close window" shortcut, which we use for "Leave Game".
        .accelerator(SystemProperties.isMac() ? KeyCode.O : KeyCode.W)
        .actionListener(
            () -> {
              final PoliticalStateOverview ui =
                  new PoliticalStateOverview(
                      frame.getGame().getData(), frame.getUiContext(), false);
              final JScrollPane scroll = new JScrollPane(ui);
              scroll.setBorder(BorderFactory.createEmptyBorder());
              final Dimension screenResolution = Toolkit.getDefaultToolkit().getScreenSize();
              // not only do we have a start bar, but we also have the message dialog to account
              // for
              final int availHeight = screenResolution.height - 120;
              // just the scroll bars plus the window sides
              final int availWidth = screenResolution.width - 40;

              scroll.setPreferredSize(
                  new Dimension(
                      Math.min(scroll.getPreferredSize().width, availWidth),
                      Math.min(scroll.getPreferredSize().height, availHeight)));

              JOptionPane.showMessageDialog(
                  frame, scroll, "Politics Panel", JOptionPane.PLAIN_MESSAGE);
            })
        .build();
  }

  /** The same switches as in the Notifications of the settings; each ticks the setting itself. */
  private static JMenu getNotificationSettings() {
    return new JMenuBuilder("User Notifications", Mnemonic.USER_NOTIFICATIONS.getMnemonicCode())
        .addMenuItem(
            new JMenuItemCheckBoxBuilder("Show End of Turn Report", KeyCode.R)
                .bindSetting(ClientSetting.showEndOfTurnReport)
                .build())
        .addMenuItem(
            new JMenuItemCheckBoxBuilder("Show Triggered Notifications", KeyCode.T)
                .bindSetting(ClientSetting.showTriggeredNotifications)
                .build())
        .addMenuItem(
            new JMenuItemCheckBoxBuilder("Show Trigger/Condition Chance Roll Successful", KeyCode.S)
                .bindSetting(ClientSetting.showTriggerChanceSuccessful)
                .build())
        .addMenuItem(
            new JMenuItemCheckBoxBuilder("Show Trigger/Condition Chance Roll Failure", KeyCode.F)
                .bindSetting(ClientSetting.showTriggerChanceFailure)
                .build())
        .build();
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
