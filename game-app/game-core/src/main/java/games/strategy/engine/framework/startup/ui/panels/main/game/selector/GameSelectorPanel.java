package games.strategy.engine.framework.startup.ui.panels.main.game.selector;

import com.google.common.annotations.VisibleForTesting;
import games.strategy.engine.data.GameData;
import games.strategy.engine.data.properties.IEditableProperty;
import games.strategy.engine.data.properties.PropertiesUi;
import games.strategy.engine.framework.lookandfeel.LookAndFeel;
import games.strategy.engine.framework.map.download.DownloadMapsWindow;
import games.strategy.engine.framework.map.file.system.loader.InstalledMapsListing;
import games.strategy.engine.framework.startup.mc.ClientModel;
import games.strategy.engine.framework.startup.ui.FileBackedGamePropertiesCache;
import games.strategy.engine.framework.startup.ui.IGamePropertiesCache;
import games.strategy.engine.framework.system.SystemProperties;
import games.strategy.engine.framework.ui.GameChooser;
import games.strategy.engine.framework.ui.background.BackgroundTaskRunner;
import games.strategy.triplea.EngineImageLoader;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;
import javax.annotation.Nullable;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import org.triplea.config.product.ProductVersionReader;
import org.triplea.swing.JButtonBuilder;
import org.triplea.swing.JLabelBuilder;
import org.triplea.swing.SwingAction;
import org.triplea.swing.jpanel.GridBagConstraintsAnchor;
import org.triplea.swing.jpanel.GridBagConstraintsBuilder;
import org.triplea.swing.jpanel.GridBagConstraintsFill;

/**
 * Left hand side panel of the launcher screen that has various info, like selected game and engine
 * version.
 */
public final class GameSelectorPanel extends JPanel implements Observer {
  private static final long serialVersionUID = -4598107601238030020L;

  private final GameSelectorModel model;
  private final IGamePropertiesCache gamePropertiesCache = new FileBackedGamePropertiesCache();
  // the options of the game as it was loaded, before any change; replaced, never changed, as
  // games load on other threads while the Swing thread reads them
  private volatile Map<String, Object> originalPropertiesMap = new HashMap<>();
  private volatile @Nullable GameData originalPropertiesGame;
  private final JLabel nameText = new JLabelBuilder().bold().adjustFontSize(4).build();
  private final JLabel saveGameText =
      new JLabelBuilder().foregroundColor(LookAndFeel.getSecondaryTextColor()).build();
  private final JLabel roundText =
      new JLabelBuilder().foregroundColor(LookAndFeel.getSecondaryTextColor()).build();

  private final JButton loadNewGame =
      new JButtonBuilder()
          .title("Change Game...")
          .toolTip("Start a new game (autosaves on bot servers will appear here)")
          .build();

  private final JButton loadSavedGame =
      new JButtonBuilder()
          .title("Open Saved Game...")
          .toolTip("Open a game saved on your computer.")
          .build();

  private final JButton mapOptions =
      new JButtonBuilder()
          .title("Game Options...")
          .toolTip(
              "<html>Set options for the currently selected game, <br>such as enabling/disabling "
                  + "Low Luck, or Technology</html>")
          .build();

  public GameSelectorPanel(final GameSelectorModel model) {
    this.model = model;
    final GameData data = model.getGameData();
    if (data != null) {
      setOriginalPropertiesMap(data);
      gamePropertiesCache.loadCachedGamePropertiesInto(data);
    }

    // One column, top to bottom: logo, the card of the selected game with what belongs to it,
    // links to other games, version info.
    setLayout(new GridBagLayout());

    final JLabel logoLabel =
        new JLabel(
            new ImageIcon(EngineImageLoader.loadImage("launch_screens", "triplea-logo.png")));
    add(
        logoLabel,
        new GridBagConstraintsBuilder(0, 0)
            .anchor(GridBagConstraintsAnchor.CENTER)
            .insets(0, 0, 16, 0)
            .build());

    add(LookAndFeel.newSectionLabel("Selected game"), buildGridRow(1, new Insets(0, 0, 6, 0)));
    add(newGameCard(), buildGridRow(2, new Insets(0, 0, 0, 0)));

    final JButton downloadMapButton =
        new JButtonBuilder()
            .title("Download Maps...")
            .toolTip("Click this button to install additional maps")
            .actionListener(DownloadMapsWindow::showDownloadMapsWindow)
            .build();
    for (final JButton link : List.of(loadSavedGame, downloadMapButton)) {
      LookAndFeel.setBorderlessButtonStyle(link);
      link.setHorizontalAlignment(SwingConstants.LEADING);
    }
    add(loadSavedGame, buildGridRow(3, new Insets(14, 0, 0, 0)));
    add(downloadMapButton, buildGridRow(4, new Insets(2, 0, 0, 0)));

    // spacer, pushes the version info to the bottom
    add(
        new JPanel(),
        new GridBagConstraintsBuilder(0, 5).weightY(1).fill(GridBagConstraintsFill.BOTH).build());

    final JLabel versionLabel =
        new JLabelBuilder(
                String.format(
                    "TripleA %s  ·  Java %s",
                    ProductVersionReader.getCurrentVersion(), SystemProperties.getJavaVersion()))
            .foregroundColor(LookAndFeel.getSecondaryTextColor())
            .adjustFontSize(-1)
            .build();
    add(versionLabel, buildGridRow(6, new Insets(12, 0, 0, 0)));

    loadNewGame.addActionListener(
        e -> {
          if (canSelectLocalGameData()) {
            selectGameFile();
          } else if (canChangeHostBotGameData()) {
            final ClientModel clientModelForHostBots = model.getClientModelForHostBots();
            if (clientModelForHostBots != null) {
              clientModelForHostBots.setMap(this);
            }
          }
        });

    loadSavedGame.addActionListener(
        e -> {
          if (canSelectLocalGameData()) {
            selectSavedGameFile();
          } else if (canChangeHostBotGameData()) {
            final ClientModel clientModelForHostBots = model.getClientModelForHostBots();
            if (clientModelForHostBots != null) {
              clientModelForHostBots.executeChangeGameToSaveGameClientAction(
                  JOptionPane.getFrameForComponent(this));
            }
          }
        });

    mapOptions.addActionListener(
        e -> {
          if (canSelectLocalGameData()) {
            selectGameOptions();
          } else if (canChangeHostBotGameData()) {
            final ClientModel clientModelForHostBots = model.getClientModelForHostBots();
            if (clientModelForHostBots != null) {
              clientModelForHostBots.changeGameOptions(this);
            }
          }
        });

    updateGameData();
  }

  /** A full-width row; buttons stretch to the width of the panel. */
  private static GridBagConstraints buildGridRow(final int y, final Insets insets) {
    return new GridBagConstraintsBuilder(0, y)
        .weightX(1)
        .fill(GridBagConstraintsFill.HORIZONTAL)
        .insets(insets)
        .build();
  }

  /** The selected game with what belongs to it: changing it and its options. */
  private JPanel newGameCard() {
    final JPanel card = new JPanel(new GridBagLayout());
    card.setBorder(BorderFactory.createEmptyBorder(12, 14, 14, 14));
    LookAndFeel.setCardStyle(card);
    card.add(nameText, buildGridRow(0, new Insets(0, 0, 2, 0)));
    card.add(roundText, buildGridRow(1, new Insets(0, 0, 0, 0)));
    card.add(saveGameText, buildGridRow(2, new Insets(0, 0, 0, 0)));
    card.add(loadNewGame, buildGridRow(3, new Insets(14, 0, 0, 0)));
    card.add(mapOptions, buildGridRow(4, new Insets(6, 0, 0, 0)));
    return card;
  }

  private void setOriginalPropertiesMap(@Nullable final GameData data) {
    final Map<String, Object> properties = new HashMap<>();
    if (data != null) {
      for (final IEditableProperty<?> property : data.getProperties().getEditableProperties()) {
        properties.put(property.getName(), property.getValue());
      }
    }
    originalPropertiesMap = properties;
    originalPropertiesGame = data;
  }

  private void selectGameOptions() {
    // backup current game properties before showing dialog
    final Map<String, Object> currentPropertiesMap = new HashMap<>();
    for (final IEditableProperty<?> property :
        model.getGameData().getProperties().getEditableProperties()) {
      currentPropertiesMap.put(property.getName(), property.getValue());
    }
    final PropertiesUi panel = new PropertiesUi(model.getGameData().getProperties(), true);
    final JOptionPane pane =
        new JOptionPane(PropertiesUi.newScrollableView(panel), JOptionPane.PLAIN_MESSAGE);
    final String ok = "OK";
    final String cancel = "Cancel";
    final String makeDefault = "Make Default";
    final String reset = "Reset";
    pane.setOptions(new Object[] {ok, makeDefault, reset, cancel});
    final JDialog window =
        pane.createDialog(JOptionPane.getFrameForComponent(this), "Game Options");
    window.setVisible(true);
    final Object buttonPressed = pane.getValue();
    if (buttonPressed == null || buttonPressed.equals(cancel)) {
      // restore properties, if cancel was pressed, or window was closed
      for (final IEditableProperty<?> property :
          model.getGameData().getProperties().getEditableProperties()) {
        property.validateAndSet(currentPropertiesMap.get(property.getName()));
      }
    } else if (buttonPressed.equals(reset)) {
      if (!originalPropertiesMap.isEmpty()) {
        resetGameOptions();
        selectGameOptions();
      }
    } else if (buttonPressed.equals(makeDefault)) {
      gamePropertiesCache.cacheGameProperties(model.getGameData());
    }
  }

  /** Sets the options back to those the game had when it was loaded. */
  @VisibleForTesting
  void resetGameOptions() {
    final Map<String, Object> original = originalPropertiesMap;
    for (final IEditableProperty<?> property :
        model.getGameData().getProperties().getEditableProperties()) {
      property.validateAndSet(original.get(property.getName()));
    }
  }

  private void updateGameData() {
    SwingAction.invokeNowOrLater(
        () -> {
          nameText.setText(model.getGameName());
          roundText.setText("Round " + model.getGameRound());
          roundText.setVisible(model.isGameRoundKnown());
          model
              .getSaveGameFileName()
              .ifPresentOrElse(
                  fileName -> {
                    saveGameText.setText("Saved game: " + fileName);
                    saveGameText.setVisible(true);
                  },
                  () -> saveGameText.setVisible(false));

          final boolean canSelectGameData = canSelectLocalGameData();
          final boolean canChangeHostBotGameData = canChangeHostBotGameData();
          loadSavedGame.setEnabled(canSelectGameData || canChangeHostBotGameData);
          loadNewGame.setEnabled(canSelectGameData || canChangeHostBotGameData);
          // Disable game options if there are none.
          mapOptions.setEnabled(
              canChangeHostBotGameData
                  || (canSelectGameData
                      && model.getGameData() != null
                      && !model.getGameData().getProperties().getEditableProperties().isEmpty()));
        });
  }

  private boolean canSelectLocalGameData() {
    return model != null && model.isCanSelect();
  }

  private boolean canChangeHostBotGameData() {
    return model != null && model.isHostIsHeadlessBot();
  }

  @Override
  public void update(final Observable o, final Object arg) {
    // however a game got loaded, eg from the main menu, Reset in its options goes back to these
    if (model.getGameData() != originalPropertiesGame) {
      setOriginalPropertiesMap(model.getGameData());
    }
    updateGameData();
  }

  private void selectSavedGameFile() {
    GameFileSelector.selectSavedGame(this).ifPresent(this::loadSaveFile);
  }

  private void loadSaveFile(final Path file) {
    SavedGameLoader.load(model, file);
  }

  private void selectGameFile() {
    try {
      final InstalledMapsListing installedMapsListing =
          BackgroundTaskRunner.runInBackgroundAndReturn(
              "Loading all available games...", InstalledMapsListing::parseMapFiles);

      GameChooser.chooseGame(
          JOptionPane.getFrameForComponent(this),
          installedMapsListing,
          model.getGameName(),
          this::gameSelected);
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  public void gameSelected(final Path gameFile) {
    BackgroundTaskRunner.runInBackground(
        "Loading map...",
        () -> {
          if (model.loadMap(gameFile)) {
            // only for new games, not saved games, we set the default options, and set them only
            // once (the first time it is loaded)
            gamePropertiesCache.loadCachedGamePropertiesInto(model.getGameData());
          }
        });
  }
}
