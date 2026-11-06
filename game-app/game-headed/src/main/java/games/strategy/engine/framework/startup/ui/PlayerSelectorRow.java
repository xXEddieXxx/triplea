package games.strategy.engine.framework.startup.ui;

import games.strategy.engine.data.GamePlayer;
import games.strategy.engine.data.properties.GameProperties;
import games.strategy.engine.framework.I18nEngineFramework;
import games.strategy.engine.framework.startup.launcher.local.PlayerCountrySelection;
import games.strategy.engine.framework.startup.mc.HeadedPlayerTypes;
import games.strategy.triplea.Constants;
import java.awt.Container;
import java.awt.GridBagConstraints;
import java.awt.Insets;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;

/**
 * Represents a player selection row worth of data, during initial setup this is a row where a
 * player can choose to play a country, set it to AI, etc.
 */
public class PlayerSelectorRow implements PlayerCountrySelection {

  private final JCheckBox enabledCheckBox;
  private final String playerName;
  private final GamePlayer player;
  private final JComboBox<String> playerTypes;
  private final JComponent incomePercentage;
  private final JLabel incomePercentageLabel;
  private final JComponent puIncomeBonus;
  private final JLabel puIncomeBonusLabel;
  private boolean enabled = true;
  private final JLabel name;
  private JButton alliances;
  private final Collection<String> playersThatMayBeDisabled;
  private final SetupPanel parent;
  private final PlayerTypes playerTypesProvider;

  PlayerSelectorRow(
      final List<PlayerSelectorRow> playerRows,
      final GamePlayer player,
      final Map<String, String> reloadSelections,
      final Collection<String> playersThatMayBeDisabled,
      final Map<String, Boolean> playersEnablementListing,
      final Collection<String> playerAlliances,
      final SetupPanel parent,
      final GameProperties gameProperties,
      final PlayerTypes playerTypes) {
    this.playersThatMayBeDisabled = playersThatMayBeDisabled;
    this.parent = parent;
    playerName = player.getName();
    this.player = player;
    name = new JLabel(playerName + ":");
    this.playerTypesProvider = playerTypes;

    enabledCheckBox = new JCheckBox();
    enabledCheckBox.addActionListener( // disablePlayerActionListener
        e -> {
          if (enabledCheckBox.isSelected()) {
            enabled = true;
            // the 1st in the list should be human
            PlayerSelectorRow.this.playerTypes.setSelectedItem(HeadedPlayerTypes.HUMAN_PLAYER);
          } else {
            enabled = false;
            // the 2nd in the list should be Weak AI
            PlayerSelectorRow.this.playerTypes.setSelectedItem(PlayerTypes.WEAK_AI);
          }
          PlayerSelectorRow.this.setWidgetActivation();
        });
    enabledCheckBox.setSelected(playersEnablementListing.get(playerName));
    enabledCheckBox.setEnabled(playersThatMayBeDisabled.contains(playerName));

    this.playerTypes = new JComboBox<>(this.playerTypesProvider.getAvailablePlayerLabels());
    String previousSelection = reloadSelections.get(playerName);
    if (previousSelection.equalsIgnoreCase(PlayerTypes.PLAYER_TYPE_DEFAULT_LABEL)) {
      previousSelection = HeadedPlayerTypes.HUMAN_PLAYER.getLabel();
    }
    if (List.of(this.playerTypesProvider.getAvailablePlayerLabels()).contains(previousSelection)) {
      this.playerTypes.setSelectedItem(previousSelection);
    } else {
      setDefaultPlayerType();
    }

    alliances = null;
    if (!playerAlliances.contains(playerName)) {
      final String alliancesLabelText = playerAlliances.toString();
      alliances = new JButton(alliancesLabelText);
      alliances.setToolTipText(
          I18nEngineFramework.get()
              .getText("startup.PlayerSelectorRow.btn.Alliances.Tltp", alliancesLabelText));
      alliances.addActionListener(
          e ->
              playerRows.stream()
                  .filter(
                      row ->
                          row.alliances != null
                              && row.alliances.getText().equals(alliancesLabelText))
                  .forEach(row -> row.setPlayerType(HeadedPlayerTypes.HUMAN_PLAYER.getLabel())));
    }

    incomePercentage =
        gameProperties
            .getPlayerPropertyOrThrow(Constants.getPropertyNameIncomePercentageFor(player))
            .getEditorComponent();
    incomePercentageLabel = new JLabel("%");

    // also renames a bonus an old save has under its former name
    puIncomeBonus = gameProperties.ensurePropertyPuIncomeBonusFor(player).getEditorComponent();
    puIncomeBonusLabel = new JLabel("PUs");

    setWidgetActivation();
  }

  void layout(final int row, final Container container) {
    int gridx = 0;
    if (!playersThatMayBeDisabled.isEmpty()) {
      container.add(
          enabledCheckBox,
          new GridBagConstraints(
              gridx++,
              row,
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
    container.add(
        name,
        new GridBagConstraints(
            gridx++,
            row,
            1,
            1,
            0,
            0,
            GridBagConstraints.WEST,
            GridBagConstraints.NONE,
            new Insets(0, 5, 5, 0),
            0,
            0));
    container.add(
        playerTypes,
        new GridBagConstraints(
            gridx++,
            row,
            1,
            1,
            0,
            0,
            GridBagConstraints.WEST,
            GridBagConstraints.NONE,
            new Insets(0, 5, 5, 0),
            0,
            0));
    if (alliances != null) {
      container.add(
          alliances,
          new GridBagConstraints(
              gridx,
              row,
              1,
              1,
              0,
              0,
              GridBagConstraints.WEST,
              GridBagConstraints.NONE,
              new Insets(0, 7, 5, 5),
              0,
              0));
    }
    gridx++;
    container.add(incomePercentage, newIncomeCell(gridx++, row, 20));
    container.add(incomePercentageLabel, newIncomeCell(gridx++, row, 5));
    container.add(puIncomeBonus, newIncomeCell(gridx++, row, 20));
    container.add(puIncomeBonusLabel, newIncomeCell(gridx, row, 5));
  }

  private static GridBagConstraints newIncomeCell(final int x, final int y, final int spaceLeft) {
    return new GridBagConstraints(
        x,
        y,
        1,
        1,
        0,
        0,
        GridBagConstraints.WEST,
        GridBagConstraints.NONE,
        new Insets(0, spaceLeft, 5, 5),
        0,
        0);
  }

  /** Shows or hides the nation's income fields, which the game options switch on. */
  void setResourceModifiersVisible(final boolean isVisible) {
    incomePercentage.setVisible(isVisible);
    incomePercentageLabel.setVisible(isVisible);
    puIncomeBonus.setVisible(isVisible);
    puIncomeBonusLabel.setVisible(isVisible);
  }

  void setPlayerType(final String playerType) {
    if (enabled && !player.isHidden()) {
      playerTypes.setSelectedItem(playerType);
    }
  }

  /**
   * Sets the type for a quick setup of all players at once. Players that do nothing by default, eg
   * neutrals, keep doing nothing: a quick setup must not make them take turns. Nations the map
   * gives to the computer get the computer player chosen, eg Fast AI, instead of a human. A nation
   * the computer is to play and plays already keeps its AI, so AIs picked for single nations stay.
   *
   * @param computerType The computer player the player picked in the table, eg by "Set all to".
   */
  void setQuickSetupType(final String playerType, final String computerType) {
    if (!enabled || player.isHidden()) {
      // like the other buttons that set all players, it leaves disabled and hidden players alone
      return;
    }
    if (player.isDefaultTypeDoesNothing()) {
      setDefaultPlayerType();
      return;
    }
    final String wanted = player.isDefaultTypeAi() ? computerType : playerType;
    // a nation the computer plays already keeps its AI, eg Fast AI picked for it alone
    if (!wanted.equals(computerType) || !isPlayedByComputer()) {
      setPlayerType(wanted);
    }
  }

  /** Whether the computer plays the nation, eg Fast AI, and not a person nor nobody. */
  boolean isPlayedByComputer() {
    final String label = getPlayerType().getLabel();
    return !label.equals(HeadedPlayerTypes.HUMAN_PLAYER.getLabel())
        && !label.equals(PlayerTypes.DOES_NOTHING_PLAYER_LABEL)
        && !label.equals(PlayerTypes.PLAYER_TYPE_DEFAULT_LABEL);
  }

  /** Whether the map gives the nation to the computer, as its default. */
  boolean isComputerByDefault() {
    return player.isDefaultTypeAi();
  }

  void setDefaultPlayerType() {
    if (player.isDefaultTypeAi()) {
      playerTypes.setSelectedItem(PlayerTypes.PRO_AI.getLabel());
    } else if (player.isDefaultTypeDoesNothing()) {
      playerTypes.setSelectedItem(PlayerTypes.DOES_NOTHING_PLAYER_LABEL);
    } else {
      playerTypes.setSelectedItem(HeadedPlayerTypes.HUMAN_PLAYER.getLabel());
    }
  }

  @Override
  public String getPlayerName() {
    return playerName;
  }

  @Override
  public PlayerTypes.Type getPlayerType() {
    return this.playerTypesProvider.fromLabel(String.valueOf(this.playerTypes.getSelectedItem()));
  }

  @Override
  public boolean isPlayerEnabled() {
    return enabledCheckBox.isSelected();
  }

  private void setWidgetActivation() {
    name.setEnabled(enabled);
    if (alliances != null) {
      alliances.setEnabled(enabled);
    }
    enabledCheckBox.setEnabled(playersThatMayBeDisabled.contains(playerName));
    incomePercentage.setEnabled(enabled);
    puIncomeBonus.setEnabled(enabled);
    parent.fireListener();
  }
}
