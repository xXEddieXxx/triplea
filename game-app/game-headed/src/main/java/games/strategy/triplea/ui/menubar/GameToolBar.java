package games.strategy.triplea.ui.menubar;

import com.formdev.flatlaf.FlatClientProperties;
import com.google.common.annotations.VisibleForTesting;
import games.strategy.engine.framework.system.SystemProperties;
import games.strategy.triplea.odds.calculator.BattleCalculatorDialog;
import games.strategy.triplea.settings.ClientSetting;
import games.strategy.triplea.settings.SettingType;
import games.strategy.triplea.ui.FindTerritoryAction;
import games.strategy.triplea.ui.TripleAFrame;
import games.strategy.triplea.ui.menubar.ToolIcon.Kind;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.Serial;
import javax.swing.AbstractAction;
import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.ButtonModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import javax.swing.ToolTipManager;
import org.triplea.swing.key.binding.KeyCode;

/**
 * The icons of the game window's top bar, in place of a menu bar; every entry of the menus of
 * before is behind one of them. At the left end, {@link #newGameMenus}: the history, the save icon
 * with the files (save, post the turn, leave, exit), the gear with the settings (the In game
 * section, with the map's zoom, unit size and more at its top) and, only in network games, the
 * network icon. At the right end, this bar of tools: the battle calculator, find a territory,
 * politics, edit mode, the statistics icon with the game's statistics, dice and exports and, at the
 * very right, the help icon with the help, the game's options and the AI debug options.
 *
 * <p>The icons are menu bars, so the shortcuts of the menus work as before, eg Ctrl+S, and Alt with
 * a menu's letter opens it, eg Alt+F the files. The shortcuts of the former Game and View menus are
 * kept: Ctrl+B the battle calculator, Ctrl+F find, Ctrl+W (Cmd+O on macOS) politics, Ctrl+E edit
 * mode, Ctrl+G the current game, Ctrl+H (Cmd+Y on macOS) the history and Ctrl+L lock map.
 */
public final class GameToolBar extends JPanel {
  @Serial private static final long serialVersionUID = 8462935127386427311L;
  private static final int ICON_SIZE = 22;
  private static final int QUICK_TOOL_TIP_MILLIS = 150;

  /** The bar for the frame. */
  public GameToolBar(final TripleAFrame frame) {
    super(new FlowLayout(FlowLayout.RIGHT, 0, 0));
    // the bar's own color shows through
    setOpaque(false);
    final JMenuBar icons = newIconBar();
    add(icons);

    icons.add(
        newButton(
            Kind.BATTLE_ODDS,
            "Battle calculator: the odds of a battle"
                + shortcut(GameMenu.Mnemonic.BATTLE_CALCULATOR.getMnemonicCode()),
            () -> BattleCalculatorDialog.show(frame, null, frame.getGame().getData())));
    final FindTerritoryAction findTerritory = new FindTerritoryAction(frame);
    icons.add(
        newButton(
            Kind.FIND,
            "Find a territory" + shortcut(KeyCode.F),
            () -> findTerritory.actionPerformed(null)));
    icons.add(
        newButton(
            Kind.POLITICS,
            "Politics: the relations between the nations" + shortcut(politicsKey()),
            () -> GameMenu.showPoliticsPanel(frame)));
    icons.add(newEditModeButton(frame.getEditModeButtonModel()));
    icons.add(
        asIcon(
            TripleAMenuBar.statisticsMenu(frame),
            Kind.STATISTICS,
            "Statistics, dice and exports (Alt+S)"));
    icons.add(
        asIcon(
            TripleAMenuBar.helpMenu(frame),
            Kind.HELP,
            "Help: game notes, how to move, the units, the game's options (Alt+H)"));

    addShortcuts(frame, findTerritory);
  }

  /**
   * The game's own entries at the left end of the top bar: the history (a book, at the left, where
   * the history opens), the files, the settings and, in network games, the network.
   */
  public static JComponent newGameMenus(final TripleAFrame frame) {
    final JMenuBar icons = newIconBar();
    icons.add(newHistoryButton(frame));
    icons.add(
        asIcon(
            TripleAMenuBar.fileMenu(frame),
            Kind.SAVE,
            "Save the game" + shortcut(KeyCode.S) + ", post the turn, leave (Alt+F)"));
    icons.add(
        newButton(
            Kind.SETTINGS,
            "Settings: the map, units and messages of this game",
            () -> GameMenu.showSettings(frame, SettingType.IN_GAME)));
    TripleAMenuBar.networkMenu(frame)
        .ifPresent(
            network ->
                icons.add(
                    asIcon(
                        network,
                        Kind.NETWORK,
                        "Network game: the players, boot or ban, the lobby (Alt+N)")));
    return icons;
  }

  /** A menu bar for icons, so menu accelerators and Alt with a menu's letter work. */
  @VisibleForTesting
  static JMenuBar newIconBar() {
    final JMenuBar icons = new JMenuBar();
    icons.setBorder(BorderFactory.createEmptyBorder());
    // the top bar's color shows through: FlatLaf paints a menu bar in the menus' color even when it
    // is not opaque, unless its background is one of its own, here a clear one
    icons.setOpaque(false);
    icons.setBackground(new Color(0, 0, 0, 0));
    return icons;
  }

  /**
   * The book where the history opens: the game's own switches, Show history while playing and Show
   * current game while in the history.
   */
  private static JButton newHistoryButton(final TripleAFrame frame) {
    return newButton(
        Kind.HISTORY,
        "History: the moves of the game so far; again to go back to the current game",
        () -> {
          final Action toggle =
              frame.getShowHistoryAction().isEnabled()
                  ? frame.getShowHistoryAction()
                  : frame.getShowGameAction();
          toggle.actionPerformed(null);
        });
  }

  /** A menu of the bar that shows as an icon, its name only in its tool tip. */
  private static JMenu asIcon(final JMenu menu, final Kind icon, final String toolTip) {
    menu.setText("");
    menu.setIcon(new ToolIcon(icon, ICON_SIZE));
    menu.setToolTipText(toolTip);
    menu.getAccessibleContext().setAccessibleName(toolTip);
    explainAtOnce(menu);
    return menu;
  }

  /** On while the game is edited; usable only when the player may edit, as the game decides. */
  private static JToggleButton newEditModeButton(final ButtonModel editMode) {
    final JToggleButton button = new JToggleButton(new ToolIcon(Kind.EDIT, ICON_SIZE));
    button.setModel(editMode);
    style(
        button,
        "Edit mode: change units, owners and money of the game"
            + shortcut(GameMenu.Mnemonic.ENABLE_EDIT_MODE.getMnemonicCode()));
    return button;
  }

  private static JButton newButton(final Kind icon, final String toolTip, final Runnable action) {
    final JButton button = new JButton(new ToolIcon(icon, ICON_SIZE));
    style(button, toolTip);
    button.addActionListener(e -> action.run());
    return button;
  }

  private static void style(final AbstractButton button, final String toolTip) {
    button.putClientProperty(
        FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
    button.setToolTipText(toolTip);
    button.getAccessibleContext().setAccessibleName(toolTip);
    // a click leaves the keyboard on the map
    button.setFocusable(false);
    explainAtOnce(button);
  }

  /**
   * The tool tip says what an icon does as soon as the mouse rests on it, not after the usual
   * delay, which is long for a row of icons without text.
   */
  private static void explainAtOnce(final JComponent icon) {
    icon.addMouseListener(
        new MouseAdapter() {
          private int usualDelay = ToolTipManager.sharedInstance().getInitialDelay();

          @Override
          public void mouseEntered(final MouseEvent e) {
            usualDelay = ToolTipManager.sharedInstance().getInitialDelay();
            ToolTipManager.sharedInstance().setInitialDelay(QUICK_TOOL_TIP_MILLIS);
          }

          @Override
          public void mouseExited(final MouseEvent e) {
            ToolTipManager.sharedInstance().setInitialDelay(usualDelay);
          }
        });
  }

  /** The shortcuts of the former Game and View menus, which have no menu entry any more. */
  private static void addShortcuts(
      final TripleAFrame frame, final FindTerritoryAction findTerritory) {
    final JRootPane rootPane = frame.getRootPane();
    addShortcut(
        rootPane,
        GameMenu.Mnemonic.BATTLE_CALCULATOR.getMnemonicCode(),
        "battle calculator",
        () -> BattleCalculatorDialog.show(frame, null, frame.getGame().getData()));
    addShortcut(rootPane, KeyCode.F, "find territory", () -> findTerritory.actionPerformed(null));
    addShortcut(rootPane, politicsKey(), "politics", () -> GameMenu.showPoliticsPanel(frame));
    final ButtonModel editMode = frame.getEditModeButtonModel();
    addShortcut(
        rootPane,
        GameMenu.Mnemonic.ENABLE_EDIT_MODE.getMnemonicCode(),
        "edit mode",
        () -> {
          if (editMode.isEnabled()) {
            editMode.setSelected(!editMode.isSelected());
          }
        });
    addShortcut(
        rootPane,
        GameMenu.Mnemonic.SHOW_GAME.getMnemonicCode(),
        "show game",
        () -> runIfEnabled(frame.getShowGameAction()));
    addShortcut(
        rootPane,
        (SystemProperties.isMac()
                ? GameMenu.Mnemonic.SHOW_HISTORY_MAC
                : GameMenu.Mnemonic.SHOW_HISTORY)
            .getMnemonicCode(),
        "show history",
        () -> runIfEnabled(frame.getShowHistoryAction()));
    addLockMapShortcut(rootPane);
  }

  /**
   * Runs the action only while it is enabled, as its menu entry did: eg Show current game does
   * nothing while the current game shows.
   */
  private static void runIfEnabled(final Action action) {
    if (action.isEnabled()) {
      action.actionPerformed(null);
    }
  }

  /** On macOS Cmd+W closes a window, so politics is Cmd+O there, as before. */
  private static KeyCode politicsKey() {
    return SystemProperties.isMac() ? KeyCode.O : KeyCode.W;
  }

  /**
   * Ctrl+L (Cmd+L on macOS) locks or unlocks the map, as the View menu's Lock Map did before the
   * switch moved to the settings.
   */
  @VisibleForTesting
  static void addLockMapShortcut(final JRootPane rootPane) {
    addShortcut(
        rootPane,
        KeyCode.L,
        "lock map",
        () -> ClientSetting.lockMap.setValueAndFlush(!ClientSetting.lockMap.getValueOrThrow()));
  }

  /** The key with the platform's menu shortcut modifier, eg Ctrl+F, runs the action. */
  private static void addShortcut(
      final JRootPane rootPane, final KeyCode key, final String name, final Runnable action) {
    rootPane
        .getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
        .put(
            KeyStroke.getKeyStroke(
                key.getInputEventCode(), Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()),
            name);
    rootPane
        .getActionMap()
        .put(
            name,
            new AbstractAction() {
              @Serial private static final long serialVersionUID = 1L;

              @Override
              public void actionPerformed(final ActionEvent e) {
                action.run();
              }
            });
  }

  /** " (Ctrl+S)" for the key with the platform's menu shortcut modifier, eg Cmd on macOS. */
  private static String shortcut(final KeyCode key) {
    return " ("
        + InputEvent.getModifiersExText(Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx())
        + "+"
        + KeyEvent.getKeyText(key.getInputEventCode())
        + ")";
  }
}
