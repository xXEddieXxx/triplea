package games.strategy.engine.framework.startup.ui.panels.main.game.selector;

import com.google.common.annotations.VisibleForTesting;
import games.strategy.engine.data.GameData;
import games.strategy.engine.data.properties.IEditableProperty;
import games.strategy.engine.data.properties.PropertiesUi;
import games.strategy.engine.framework.lookandfeel.LookAndFeel;
import games.strategy.engine.framework.map.download.DownloadMapsWindow;
import games.strategy.engine.framework.map.file.system.loader.InstalledMapsListing;
import games.strategy.engine.framework.startup.ui.FileBackedGamePropertiesCache;
import games.strategy.engine.framework.startup.ui.IGamePropertiesCache;
import games.strategy.engine.framework.ui.GameChooser;
import games.strategy.engine.framework.ui.background.BackgroundTaskRunner;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.Serial;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;
import java.util.Optional;
import javax.annotation.Nullable;
import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import lombok.extern.slf4j.Slf4j;
import org.triplea.java.ThreadRunner;
import org.triplea.swing.JButtonBuilder;
import org.triplea.swing.JLabelBuilder;
import org.triplea.swing.SwingAction;
import org.triplea.swing.jpanel.GridBagConstraintsBuilder;
import org.triplea.swing.jpanel.GridBagConstraintsFill;

/**
 * The selected game of a setup screen, in the column on its right: the map's picture, the game's
 * name, round and save, and Change map, Load game and Get more maps. The game's options are a part
 * of their own ({@link #getOptionsPanel}), which the screen shows below the players, so they are
 * set on the screen itself instead of in a dialog.
 */
@Slf4j
public final class GameSelectorPanel extends JPanel implements Observer {
  private static final long serialVersionUID = -4598107601238030020L;

  /** The width of the map's picture; as wide as 16:9 makes it, as the map browser shows maps. */
  private static final int PICTURE_WIDTH = 288;

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
  private final MapPicture picture = new MapPicture();
  // the map whose picture shows or loads; on the Swing thread only
  private String pictureMap = "";

  private final JButton changeMap =
      new JButtonBuilder()
          .title("Change map...")
          .toolTip("Choose another map or game (autosaves on bot servers will appear here)")
          .build();

  private final JButton loadGame =
      new JButtonBuilder()
          .title("Load game...")
          .toolTip("Open a game saved on your computer and play on")
          .build();

  private final JPanel options = new JPanel(new BorderLayout(0, 8));
  // what the options show, so a new layout comes only with another game, not on each update
  private @Nullable GameData optionsGame;
  private boolean optionsEditable;
  private boolean optionsOfBot;

  public GameSelectorPanel(final GameSelectorModel model) {
    this.model = model;
    final GameData data = model.getGameData();
    if (data != null) {
      setOriginalPropertiesMap(data);
      gamePropertiesCache.loadCachedGamePropertiesInto(data);
    }

    // one column, top to bottom: the map's card, the link to more maps
    setLayout(new GridBagLayout());
    add(LookAndFeel.newSectionLabel("Map"), buildGridRow(0, new Insets(0, 0, 6, 0)));
    add(newGameCard(), buildGridRow(1, new Insets(0, 0, 0, 0)));
    final JButton moreMaps =
        new JButtonBuilder()
            .title("Get more maps...")
            .toolTip("Install additional maps")
            .actionListener(DownloadMapsWindow::showDownloadMapsWindow)
            .build();
    LookAndFeel.setBorderlessButtonStyle(moreMaps);
    moreMaps.setHorizontalAlignment(SwingConstants.LEADING);
    add(moreMaps, buildGridRow(2, new Insets(10, 0, 0, 0)));
    // keeps the card at the top
    add(
        new JPanel(),
        new GridBagConstraintsBuilder(0, 3).weightY(1).fill(GridBagConstraintsFill.BOTH).build());
    options.setOpaque(false);

    changeMap.addActionListener(
        e -> {
          if (canSelectLocalGameData()) {
            selectGameFile();
          } else if (canChangeHostBotGameData()) {
            Optional.ofNullable(model.getClientModelForHostBots())
                .ifPresent(bots -> bots.setMap(this));
          }
        });

    loadGame.addActionListener(
        e -> {
          if (canSelectLocalGameData()) {
            selectSavedGameFile();
          } else if (canChangeHostBotGameData()) {
            Optional.ofNullable(model.getClientModelForHostBots())
                .ifPresent(
                    bots ->
                        bots.executeChangeGameToSaveGameClientAction(
                            JOptionPane.getFrameForComponent(this)));
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

  /** The selected game with what belongs to it: its map's picture, changing it, loading a save. */
  private JPanel newGameCard() {
    final JPanel card = new JPanel(new GridBagLayout());
    card.setBorder(BorderFactory.createEmptyBorder(12, 12, 14, 12));
    LookAndFeel.setCardStyle(card);
    card.add(picture, buildGridRow(0, new Insets(0, 0, 10, 0)));
    card.add(nameText, buildGridRow(1, new Insets(0, 0, 2, 0)));
    card.add(roundText, buildGridRow(2, new Insets(0, 0, 0, 0)));
    card.add(saveGameText, buildGridRow(3, new Insets(0, 0, 0, 0)));
    card.add(changeMap, buildGridRow(4, new Insets(14, 0, 0, 0)));
    card.add(loadGame, buildGridRow(5, new Insets(6, 0, 0, 0)));
    return card;
  }

  /**
   * The options of the selected game, eg Low Luck or technology, to set right on the setup screen;
   * with Reset and Make default where the player may change them, to look at on a joined game.
   */
  public JComponent getOptionsPanel() {
    return options;
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
          loadGame.setEnabled(canSelectGameData || canChangeHostBotGameData);
          changeMap.setEnabled(canSelectGameData || canChangeHostBotGameData);
          showOptions(model.getGameData(), canSelectGameData);
          showPicture(model.getMapName());
        });
  }

  /**
   * The game's options; laid out again only for another game or another kind of screen, eg a bot's
   * game joined, so editing keeps its place.
   */
  private void showOptions(final @Nullable GameData data, final boolean editable) {
    final boolean ofBot = canChangeHostBotGameData();
    if (data == optionsGame
        && editable == optionsEditable
        && ofBot == optionsOfBot
        && options.getComponentCount() > 0) {
      return;
    }
    optionsGame = data;
    optionsEditable = editable;
    optionsOfBot = ofBot;
    options.removeAll();
    final JPanel heading = new JPanel(new BorderLayout());
    heading.setOpaque(false);
    heading.add(LookAndFeel.newSectionLabel("Game options"), BorderLayout.WEST);
    final JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
    actions.setOpaque(false);
    heading.add(actions, BorderLayout.EAST);
    options.add(heading, BorderLayout.NORTH);
    if (ofBot) {
      // the bot keeps the options: they are changed by asking it
      final JButton change =
          new JButtonBuilder()
              .title("Change game options...")
              .actionListener(
                  () ->
                      Optional.ofNullable(model.getClientModelForHostBots())
                          .ifPresent(bots -> bots.changeGameOptions(this)))
              .build();
      actions.add(change);
    }
    if (data == null || data.getProperties().getEditableProperties().isEmpty()) {
      // a joined game has its data at its host only
      options.add(
          new JLabelBuilder(
                  data == null && !editable
                      ? "The host sets the game's options."
                      : "This game has no options.")
              .foregroundColor(LookAndFeel.getSecondaryTextColor())
              .build(),
          BorderLayout.CENTER);
    } else {
      if (editable) {
        actions.add(
            newOptionsLink("Reset", "Back to the options the game was loaded with", this::reset));
        actions.add(
            newOptionsLink(
                "Make default",
                "Start every new game of this map with these options",
                () -> gamePropertiesCache.cacheGameProperties(model.getGameData())));
      }
      options.add(
          PropertiesUi.newScrollableView(new PropertiesUi(data.getProperties(), editable)),
          BorderLayout.CENTER);
    }
    options.revalidate();
    options.repaint();
  }

  private static JButton newOptionsLink(
      final String title, final String tip, final Runnable action) {
    final JButton link =
        new JButtonBuilder().title(title).toolTip(tip).actionListener(action).build();
    LookAndFeel.setBorderlessButtonStyle(link);
    return link;
  }

  /** Reset, and the editors show the values it set. */
  private void reset() {
    if (!originalPropertiesMap.isEmpty()) {
      resetGameOptions();
      refreshOptions();
    }
  }

  /** Lays the options out again, eg after values were set behind the editors. */
  private void refreshOptions() {
    optionsGame = null;
    showOptions(model.getGameData(), canSelectLocalGameData());
  }

  /** The map's picture, read in the background; the box stays quiet until then or without one. */
  private void showPicture(final String mapName) {
    if (mapName.equals(pictureMap)) {
      return;
    }
    pictureMap = mapName;
    picture.setPicture(null);
    if (mapName.isBlank()) {
      return;
    }
    ThreadRunner.runInNewThread(
        () -> {
          final Optional<BufferedImage> read = readPicture(mapName);
          SwingUtilities.invokeLater(
              () -> {
                // the game may have changed meanwhile
                if (mapName.equals(pictureMap)) {
                  picture.setPicture(read.orElse(null));
                }
              });
        });
  }

  /** The installed map's preview, as its folder holds it ("preview.png"); none without one. */
  private static Optional<BufferedImage> readPicture(final String mapName) {
    try {
      final Optional<Path> file =
          InstalledMapsListing.parseMapFiles()
              .findMapFolderByName(mapName)
              .map(folder -> folder.resolve("preview.png"))
              .filter(Files::isRegularFile);
      return file.isEmpty()
          ? Optional.empty()
          : Optional.ofNullable(ImageIO.read(file.get().toFile()));
    } catch (final IOException | RuntimeException e) {
      // eg a map folder that cannot be read: the box stays quiet, the screen works on
      log.info("Could not read the picture of the map {}", mapName, e);
      return Optional.empty();
    }
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
            loadDefaultOptions();
          }
        });
  }

  /** The options saved with Make default for the map, set into the game and shown with it. */
  @VisibleForTesting
  void loadDefaultOptions() {
    gamePropertiesCache.loadCachedGamePropertiesInto(model.getGameData());
    // the editors were laid out with the map's own values when the game loaded
    SwingUtilities.invokeLater(this::refreshOptions);
  }

  /**
   * The map's picture, cut to 16:9 with rounded corners as the map browser shows maps; a quiet box
   * while it loads or for a map without one.
   */
  private static final class MapPicture extends JComponent {
    @Serial private static final long serialVersionUID = 1L;
    private static final int ARC = 10;

    private transient @Nullable BufferedImage image;

    MapPicture() {
      setPreferredSize(new Dimension(PICTURE_WIDTH, PICTURE_WIDTH * 9 / 16));
      setMinimumSize(getPreferredSize());
    }

    void setPicture(final @Nullable BufferedImage picture) {
      image = picture;
      repaint();
    }

    @Override
    protected void paintComponent(final Graphics g) {
      final Graphics2D g2 = (Graphics2D) g.create();
      try {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(
            RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        final int width = getWidth();
        final int height = getHeight();
        g2.clip(new RoundRectangle2D.Float(0, 0, width, height, ARC, ARC));
        final Color ground = UIManager.getColor("Panel.background");
        g2.setColor(ground == null ? Color.DARK_GRAY : ground.darker());
        g2.fillRect(0, 0, width, height);
        if (image != null) {
          // covers the box, cut at the long side, as the map browser does
          final double scale =
              Math.max((double) width / image.getWidth(), (double) height / image.getHeight());
          final int drawnWidth = (int) Math.ceil(image.getWidth() * scale);
          final int drawnHeight = (int) Math.ceil(image.getHeight() * scale);
          g2.drawImage(
              image,
              (width - drawnWidth) / 2,
              (height - drawnHeight) / 2,
              drawnWidth,
              drawnHeight,
              null);
        }
      } finally {
        g2.dispose();
      }
    }
  }
}
