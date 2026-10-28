package games.strategy.engine.framework.startup.ui;

import games.strategy.engine.data.AllianceTracker;
import games.strategy.engine.data.GamePlayer;
import games.strategy.engine.data.GameState;
import games.strategy.engine.framework.I18nEngineFramework;
import games.strategy.engine.framework.I18nResourceBundle;
import games.strategy.engine.framework.lookandfeel.LookAndFeel;
import games.strategy.engine.framework.startup.mc.HeadedPlayerTypes;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import org.triplea.game.chat.ChatModel;
import org.triplea.game.startup.SetupModel;
import org.triplea.swing.JButtonBuilder;
import org.triplea.swing.JLabelBuilder;
import org.triplea.swing.SwingAction;
import org.triplea.swing.SwingComponents;

/**
 * Headed Implementation of SetupConfiguration. This is the base-class for any panel that configures
 * a game.
 */
public abstract class SetupPanel extends JPanel implements SetupModel {
  private static final long serialVersionUID = 4001323470187210773L;
  private static final String SET_ALL_DEFAULT_LABEL =
      I18nEngineFramework.get().getText("startup.SetupPanel.SET_ALL_DEFAULT_LABEL");

  // a cell right of and below the player table, taking the room a large window has left
  private static final int FILLER_COLUMN = 50;
  private static final int FILLER_ROW = 10_000;

  @Nullable private transient Consumer<SetupPanel> listener;

  public void setPanelChangedListener(final Consumer<SetupPanel> listener) {
    this.listener = listener;
  }

  public void fireListener() {
    if (listener != null) {
      listener.accept(this);
    }
  }

  @Nullable
  @Override
  public ChatModel getChatModel() {
    return null;
  }

  public abstract List<Action> getUserActions();

  /**
   * The title of the screen, eg "Play locally": the bar above a setup screen shows it, and the link
   * back of a page opened on the screen, eg Maps, names it.
   */
  public abstract String getScreenTitle();

  /** The name of a way to play, "startup.SetupPanelModel.btn.{key}.Lbl", eg as a screen title. */
  protected static String getWayToPlayTitle(final String key) {
    return I18nEngineFramework.get().getText("startup.SetupPanelModel.btn." + key + ".Lbl");
  }

  public void layoutPlayerComponents(
      final JPanel panel, final List<PlayerSelectorRow> playerRows, final GameState data) {
    final I18nResourceBundle bundle = I18nEngineFramework.get();
    panel.removeAll();
    playerRows.clear();
    panel.setLayout(new GridBagLayout());
    if (data == null) {
      panel.add(new JLabel(bundle.getText("startup.SetupPanel.noGameSelected.Lbl")));
      return;
    }

    final Collection<String> playersThatMayBeDisabled =
        data.getPlayerList().getPlayersThatMayBeDisabled();
    final Map<String, Boolean> playersEnablementListing =
        data.getPlayerList().getPlayersEnabledListing();
    final Map<String, String> reloadSelections = GamePlayer.currentPlayers(data);
    final List<GamePlayer> players = data.getPlayerList().getPlayers();

    // row 0 stays free for the quick setup of games on this computer
    int gridX = 0;
    int gridY = 2;
    if (!playersThatMayBeDisabled.isEmpty()
        || playersEnablementListing.containsValue(Boolean.FALSE)) {
      final JLabel enableLabel = new JLabel(bundle.getText("startup.SetupPanel.enable.Lbl"));
      panel.add(
          enableLabel,
          new GridBagConstraints(
              gridX++,
              gridY,
              1,
              1,
              0,
              0,
              GridBagConstraints.WEST,
              GridBagConstraints.NONE,
              new Insets(0, 5, 5, 0),
              0,
              0));
    }
    final JLabel setAllTypesLabel = new JLabel(bundle.getText("startup.SetupPanel.setAllTo.Lbl"));
    panel.add(
        setAllTypesLabel,
        new GridBagConstraints(
            gridX,
            gridY - 1,
            1,
            1,
            0,
            0,
            GridBagConstraints.EAST,
            GridBagConstraints.NONE,
            new Insets(5, 5, 15, 0),
            0,
            0));
    final JLabel nameLabel = new JLabel(bundle.getText("startup.SetupPanel.name.Lbl"));
    panel.add(
        nameLabel,
        new GridBagConstraints(
            gridX++,
            gridY,
            1,
            1,
            0,
            0,
            GridBagConstraints.WEST,
            GridBagConstraints.NONE,
            new Insets(0, 5, 5, 0),
            0,
            0));
    final PlayerTypes playerTypes = new PlayerTypes(HeadedPlayerTypes.getPlayerTypes());
    final JComboBox<String> setAllTypes = new JComboBox<>(playerTypes.getAvailablePlayerLabels());
    setAllTypes.insertItemAt(SET_ALL_DEFAULT_LABEL, 0);
    setAllTypes.setSelectedIndex(-1);
    panel.add(
        setAllTypes,
        new GridBagConstraints(
            gridX,
            gridY - 1,
            1,
            1,
            0,
            0,
            GridBagConstraints.WEST,
            GridBagConstraints.NONE,
            new Insets(5, 5, 15, 0),
            0,
            0));
    final JLabel typeLabel = new JLabel(bundle.getText("startup.SetupPanel.type.Lbl"));
    panel.add(
        typeLabel,
        new GridBagConstraints(
            gridX++,
            gridY,
            1,
            1,
            0,
            0,
            GridBagConstraints.WEST,
            GridBagConstraints.NONE,
            new Insets(0, 5, 5, 0),
            0,
            0));
    final JLabel allianceLabel = new JLabel(bundle.getText("startup.SetupPanel.alliance.Lbl"));
    panel.add(
        allianceLabel,
        new GridBagConstraints(
            gridX++,
            gridY,
            1,
            1,
            0,
            0,
            GridBagConstraints.WEST,
            GridBagConstraints.NONE,
            new Insets(0, 7, 5, 5),
            0,
            0));
    final JButton resourceModifiers = new JButton();
    panel.add(
        resourceModifiers,
        new GridBagConstraints(
            gridX,
            gridY - 1,
            3,
            1,
            0,
            0,
            GridBagConstraints.WEST,
            GridBagConstraints.NONE,
            new Insets(5, 5, 15, 0),
            0,
            0));
    final JLabel incomeLabel = new JLabel(bundle.getText("startup.SetupPanel.income.Lbl"));
    panel.add(
        incomeLabel,
        new GridBagConstraints(
            gridX++,
            gridY,
            1,
            1,
            0,
            0,
            GridBagConstraints.WEST,
            GridBagConstraints.NONE,
            new Insets(0, 20, 5, 0),
            0,
            0));
    incomeLabel.setVisible(false);
    gridX++;
    final JLabel puIncomeBonusLabel =
        new JLabel(bundle.getText("startup.SetupPanel.bonusIncome.Lbl"));
    panel.add(
        puIncomeBonusLabel,
        new GridBagConstraints(
            gridX,
            gridY,
            2,
            1,
            0,
            0,
            GridBagConstraints.WEST,
            GridBagConstraints.NONE,
            new Insets(0, 20, 5, 0),
            0,
            0));
    puIncomeBonusLabel.setVisible(false);

    // Add players in the order they were defined in the XML
    for (final GamePlayer player : players) {
      final PlayerSelectorRow selector =
          new PlayerSelectorRow(
              playerRows,
              player,
              reloadSelections,
              playersThatMayBeDisabled,
              playersEnablementListing,
              data.getAllianceTracker().getAlliancesPlayerIsIn(player),
              this,
              data.getProperties(),
              new PlayerTypes(HeadedPlayerTypes.getPlayerTypes()));
      playerRows.add(selector);
      if (!player.isHidden()) {
        selector.layout(++gridY, panel);
        selector.setResourceModifiersVisible(false);
      }
    }

    resourceModifiers.setAction(
        SwingAction.of(
            I18nEngineFramework.get().getText("startup.SetupPanel.resourceModifiers.Lbl"),
            e -> {
              final boolean isVisible = incomeLabel.isVisible();
              incomeLabel.setVisible(!isVisible);
              puIncomeBonusLabel.setVisible(!isVisible);
              playerRows.forEach(row -> row.setResourceModifiersVisible(!isVisible));
            }));

    setAllTypes.addActionListener(
        e -> {
          final String selectedType = (String) setAllTypes.getSelectedItem();
          if (selectedType != null) {
            if (SET_ALL_DEFAULT_LABEL.equals(selectedType)) {
              playerRows.forEach(PlayerSelectorRow::setDefaultPlayerType);
            } else {
              playerRows.forEach(row -> row.setPlayerType(selectedType));
            }
          }
        });
    SwingComponents.redraw(panel);
  }

  /**
   * Lays out the player table like {@link #layoutPlayerComponents}, with quick setup buttons above
   * it, for games played on this computer.
   */
  void layoutPlayerComponentsWithQuickSetup(
      final JPanel panel,
      final List<PlayerSelectorRow> playerRows,
      @Nullable final GameState data) {
    layoutPlayerComponents(panel, playerRows, data);
    if (data == null) {
      return;
    }
    panel.setBorder(BorderFactory.createEmptyBorder(24, 32, 24, 32));
    panel.add(
        newQuickSetupPanel(playerRows, data.getAllianceTracker()),
        new GridBagConstraints(
            0,
            0,
            GridBagConstraints.REMAINDER,
            1,
            0,
            0,
            GridBagConstraints.WEST,
            GridBagConstraints.NONE,
            new Insets(0, 0, 22, 0),
            0,
            0));
    // the table stays at the top left, a large window does not leave it floating in the middle
    panel.add(
        new JPanel(),
        new GridBagConstraints(
            FILLER_COLUMN,
            FILLER_ROW,
            1,
            1,
            1,
            1,
            GridBagConstraints.CENTER,
            GridBagConstraints.BOTH,
            new Insets(0, 0, 0, 0),
            0,
            0));
    SwingComponents.redraw(panel);
  }

  /**
   * A heading and buttons that set up all players at once: one per alliance to play that alliance
   * against the computer, and one for everyone taking turns at this computer. The table below stays
   * for setting each nation on its own.
   */
  private static JPanel newQuickSetupPanel(
      final List<PlayerSelectorRow> playerRows, final AllianceTracker allianceTracker) {
    final I18nResourceBundle bundle = I18nEngineFramework.get();
    final JPanel panel = new JPanel(new BorderLayout(0, 6));
    final JPanel heading = new JPanel(new BorderLayout(0, 4));
    heading.add(
        new JLabelBuilder(bundle.getText("startup.SetupPanel.quickSetup.Title"))
            .bold()
            .adjustFontSize(4)
            .build(),
        BorderLayout.NORTH);
    heading.add(
        new JLabelBuilder(bundle.getText("startup.SetupPanel.quickSetup.Hint"))
            .foregroundColor(LookAndFeel.getSecondaryTextColor())
            .build(),
        BorderLayout.CENTER);
    heading.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
    panel.add(heading, BorderLayout.NORTH);
    // ordinary buttons in a row: a shortcut for the table below, not the main thing of the screen
    final JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEADING, 8, 0));
    // the first button lines up with the heading; the layout puts its gap before it too
    buttons.setBorder(BorderFactory.createEmptyBorder(0, -8, 0, 0));
    panel.add(buttons, BorderLayout.CENTER);
    // the buttons read the rows when clicked: a new layout refills the list
    for (final String alliance : getPlayableAlliances(allianceTracker)) {
      final Set<String> members =
          allianceTracker.getPlayersInAlliance(alliance).stream()
              .map(GamePlayer::getName)
              .collect(Collectors.toSet());
      buttons.add(
          newQuickSetupButton(
              bundle.getText("startup.SetupPanel.quickSetup.PlayAs.Lbl", alliance),
              () ->
                  playerRows.forEach(
                      row ->
                          row.setQuickSetupType(
                              members.contains(row.getPlayerName())
                                  ? HeadedPlayerTypes.HUMAN_PLAYER.getLabel()
                                  : PlayerTypes.PRO_AI.getLabel()))));
    }
    buttons.add(
        newQuickSetupButton(
            bundle.getText("startup.SetupPanel.quickSetup.HotSeat.Lbl"),
            () ->
                playerRows.forEach(
                    row -> row.setQuickSetupType(HeadedPlayerTypes.HUMAN_PLAYER.getLabel()))));
    return panel;
  }

  /**
   * The alliances worth a quick setup button, by name: those with a nation a person can play and of
   * several nations, like "Axis" and "Allies". Maps also put single nations, eg neutrals, in
   * alliances of their own; these get a button only if no alliance has several nations.
   */
  private static Collection<String> getPlayableAlliances(final AllianceTracker allianceTracker) {
    final Set<String> alliances =
        allianceTracker.getAlliances().stream()
            .filter(
                alliance ->
                    allianceTracker.getPlayersInAlliance(alliance).stream()
                        .anyMatch(SetupPanel::isForPeople))
            .collect(Collectors.toCollection(TreeSet::new));
    final Set<String> sides =
        alliances.stream()
            .filter(alliance -> allianceTracker.getPlayersInAlliance(alliance).size() >= 2)
            .collect(Collectors.toCollection(TreeSet::new));
    return sides.isEmpty() ? alliances : sides;
  }

  /** Whether the map lets a person play the nation; it gives some to the computer or to nobody. */
  private static boolean isForPeople(final GamePlayer player) {
    return !player.isDefaultTypeAi() && !player.isDefaultTypeDoesNothing();
  }

  /** An ordinary button with the name of the setup. */
  private static JButton newQuickSetupButton(final String title, final Runnable action) {
    return new JButtonBuilder(title).actionListener(action).build();
  }

  public abstract boolean isCancelButtonVisible();

  /** Whether this is the main menu; a save opened from outside the client is continued there. */
  public boolean isMainMenu() {
    return false;
  }

  /**
   * Whether this screen fills the window without game selection and footer, like the main menu and
   * the lobby.
   */
  public boolean isFullWindow() {
    return isMainMenu();
  }
}
