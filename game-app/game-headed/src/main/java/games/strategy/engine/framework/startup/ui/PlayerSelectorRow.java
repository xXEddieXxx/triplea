package games.strategy.engine.framework.startup.ui;

import games.strategy.engine.data.GamePlayer;
import games.strategy.engine.framework.I18nEngineFramework;
import games.strategy.engine.framework.startup.launcher.local.PlayerCountrySelection;
import games.strategy.engine.framework.startup.mc.HeadedPlayerTypes;
import java.awt.Container;
import java.awt.GridBagConstraints;
import java.awt.Insets;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
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
  }

  void setPlayerType(final String playerType) {
    if (enabled && !player.isHidden()) {
      playerTypes.setSelectedItem(playerType);
    }
  }

  /**
   * Sets the type for a quick setup of all players at once. Players that do nothing by default, eg
   * neutrals, keep doing nothing: a quick setup must not make them take turns.
   */
  void setQuickSetupType(final String playerType) {
    if (!enabled || player.isHidden()) {
      // like the other buttons that set all players, it leaves disabled and hidden players alone
      return;
    }
    // the map gives these nations to the computer or to nobody, eg neutrals
    if (player.isDefaultTypeDoesNothing() || player.isDefaultTypeAi()) {
      setDefaultPlayerType();
    } else {
      setPlayerType(playerType);
    }
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
    parent.fireListener();
  }
}
