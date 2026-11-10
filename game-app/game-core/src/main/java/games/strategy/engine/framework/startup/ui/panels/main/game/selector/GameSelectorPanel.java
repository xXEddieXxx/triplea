package games.strategy.engine.framework.startup.ui.panels.main.game.selector;

import com.formdev.flatlaf.FlatClientProperties;
import com.google.common.annotations.VisibleForTesting;
import games.strategy.engine.data.GameData;
import games.strategy.engine.data.properties.IEditableProperty;
import games.strategy.engine.data.properties.PropertiesUi;
import games.strategy.engine.framework.I18nEngineFramework;
import games.strategy.engine.framework.lookandfeel.LookAndFeel;
import games.strategy.engine.framework.map.download.DownloadMapsWindow;
import games.strategy.engine.framework.map.file.system.loader.InstalledMapsListing;
import games.strategy.engine.framework.startup.ui.FileBackedGamePropertiesCache;
import games.strategy.engine.framework.startup.ui.IGamePropertiesCache;
import games.strategy.engine.framework.ui.GameChooser;
import games.strategy.engine.framework.ui.background.BackgroundTaskRunner;
import games.strategy.triplea.settings.ClientSetting;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Observable;
import java.util.Observer;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import lombok.extern.slf4j.Slf4j;
import org.triplea.java.ThreadRunner;
import org.triplea.swing.DocumentListenerBuilder;
import org.triplea.swing.JButtonBuilder;
import org.triplea.swing.JLabelBuilder;
import org.triplea.swing.ScrollableJPanel;
import org.triplea.swing.SwingAction;
import org.triplea.swing.SwingComponents;
import org.triplea.swing.WrappingText;
import org.triplea.swing.jpanel.GridBagConstraintsBuilder;
import org.triplea.swing.jpanel.GridBagConstraintsFill;

/**
 * The selected game of a setup screen, in a column of fixed width on its left: the map's picture,
 * the game's name, round and save, Change map, Load game and Get more maps; below them the Resource
 * Modifiers button, which shows each nation's income fields in its row of players, the rules
 * changed from the game as loaded. The game's options, with the player's rule sets of the game, are
 * a panel of their own ({@link #getOptionsPanel}) that the screen shows on a tab beside the
 * players, so they get the whole width.
 */
@Slf4j
public final class GameSelectorPanel extends JPanel implements Observer {
  private static final long serialVersionUID = -4598107601238030020L;

  /** The width of the map's picture; as wide as 16:9 makes it, as the map browser shows maps. */
  private static final int PICTURE_WIDTH = 288;

  /** The bound property with the number of options the game has, eg for the title of their tab. */
  public static final String OPTION_COUNT = "optionCount";

  // the most height the list of saved rule sets takes before it scrolls
  private static final int RULE_SETS_HEIGHT = 240;

  // characters a file name cannot have, also control characters
  private static final Pattern FORBIDDEN_IN_NAMES = Pattern.compile("[\\\\/:*?\"<>|\\p{Cntrl}]");

  private final GameSelectorModel model;
  private final transient BiConsumer<Component, Consumer<Path>> savedGameChooser;
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

  private final JPanel options = new JPanel(new BorderLayout(0, 12));
  // one button for all layouts of the options, so the screen can hide it where it has no use
  private final JToggleButton resourceModifiers = newResourceModifiersSwitch();
  // below the card: the rules changed from the map's own
  private final JPanel rulesSummary = new JPanel(new GridBagLayout());
  private final JPanel changedRules = new JPanel(new GridBagLayout());
  // keeps the player's rule sets of the game
  private final IGamePropertiesCache gamePropertiesCache;
  // what the list of changed rules shows, so it is laid out again only when that changes; none
  // before the first time, so even "no changes" is shown then
  private @Nullable List<String> shownChanges;
  // shortcut: the options tell no one of a change, so the list looks twice a second while the
  // panel shows; give IEditableProperty change listeners if more than this list must follow them
  private final Timer changesWatch =
      new Timer(
          500,
          e -> {
            // hidden on the main menu and in the lobby
            if (isShowing()) {
              showChangedRules();
            }
          });
  // what the options show, so a new layout comes only with another game, not on each update
  private @Nullable GameData optionsGame;
  private boolean optionsEditable;
  private boolean optionsOfBot;

  /** With Load game asking for the save in a file chooser. */
  public GameSelectorPanel(final GameSelectorModel model) {
    this(model, (parent, load) -> GameFileSelector.selectSavedGame(parent).ifPresent(load));
  }

  /**
   * With Load game asking through the given chooser.
   *
   * @param savedGameChooser Lets the player choose a save for Load game, eg on a page of the main
   *     window, and passes it on; gets this panel as the parent of a dialog.
   */
  public GameSelectorPanel(
      final GameSelectorModel model, final BiConsumer<Component, Consumer<Path>> savedGameChooser) {
    this(model, savedGameChooser, new FileBackedGamePropertiesCache());
  }

  /** With the rule sets kept where the given cache keeps them, eg in memory for a test. */
  @VisibleForTesting
  GameSelectorPanel(
      final GameSelectorModel model,
      final BiConsumer<Component, Consumer<Path>> savedGameChooser,
      final IGamePropertiesCache gamePropertiesCache) {
    this.model = model;
    this.savedGameChooser = savedGameChooser;
    this.gamePropertiesCache = gamePropertiesCache;
    final GameData data = model.getGameData();
    if (data != null) {
      setOriginalPropertiesMap(data);
    }

    // one column, top to bottom: the map's card, the link to more maps, the income switch, the
    // rules changed from the map's own
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
    // in the column, as it changes the players' table, whichever tab shows
    add(resourceModifiers, buildGridRow(3, new Insets(12, 0, 0, 0)));
    rulesSummary.add(
        LookAndFeel.newSectionLabel("Changed rules"), buildGridRow(0, new Insets(0, 0, 6, 0)));
    rulesSummary.add(changedRules, buildGridRow(1, new Insets(0, 0, 0, 0)));
    add(rulesSummary, buildGridRow(4, new Insets(22, 0, 0, 0)));
    // takes the room below, so the card stays at the top
    add(new JPanel(), buildFillingRow(5, new Insets(0, 0, 0, 0)));
    options.setBorder(BorderFactory.createEmptyBorder(16, 16, 12, 16));

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

  @Override
  public void addNotify() {
    super.addNotify();
    changesWatch.start();
  }

  @Override
  public void removeNotify() {
    changesWatch.stop();
    super.removeNotify();
  }

  /** A full-width row; buttons stretch to the width of the panel. */
  private static GridBagConstraints buildGridRow(final int y, final Insets insets) {
    return new GridBagConstraintsBuilder(0, y)
        .weightX(1)
        .fill(GridBagConstraintsFill.HORIZONTAL)
        .insets(insets)
        .build();
  }

  /** A row taking the height the others leave. */
  private static GridBagConstraints buildFillingRow(final int y, final Insets insets) {
    return new GridBagConstraintsBuilder(0, y)
        .weightX(1)
        .weightY(1)
        .fill(GridBagConstraintsFill.BOTH)
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
   * with Reset and Rule sets where the player may change them, to look at on a joined game. The
   * screen shows them on a tab of their own; {@link #OPTION_COUNT} tells how many there are.
   */
  public JComponent getOptionsPanel() {
    return options;
  }

  /** How many options the selected game has; none without its data, eg a game joined. */
  public int getOptionCount() {
    return Optional.ofNullable(model.getGameData())
        .map(data -> data.getProperties().getEditableProperties().size())
        .orElse(0);
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
    // the tab names the options; this row has what can be done with them
    final JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEADING, 12, 0));
    // the first one lines up with the options; the layout puts its gap before it too
    actions.setBorder(BorderFactory.createEmptyBorder(0, -12, 0, 0));
    actions.setOpaque(false);
    options.add(actions, BorderLayout.NORTH);
    // tells the tab how many options there are; -1 as the old value, so it is always told
    firePropertyChange(OPTION_COUNT, -1, getOptionCount());
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
    if (data == null) {
      // a joined game has its data at its host only
      options.add(
          new JLabelBuilder(
                  editable ? "This game has no options." : "The host sets the game's options.")
              .foregroundColor(LookAndFeel.getSecondaryTextColor())
              .build(),
          BorderLayout.CENTER);
    } else {
      if (editable && !data.getProperties().getEditableProperties().isEmpty()) {
        actions.add(
            newOptionsLink("Reset", "Back to the options the game was loaded with", this::reset));
        final JButton ruleSetsButton =
            new JButtonBuilder()
                .title("Rule sets")
                .toolTip("Keep these options under a name, or load options kept before")
                .build();
        ruleSetsButton.addActionListener(e -> showRuleSets(ruleSetsButton));
        actions.add(ruleSetsButton);
      }
      final PropertiesUi rules = PropertiesUi.newGrouped(data.getProperties(), editable);
      // as wide as the tab: the cards fill columns instead of scrolling sideways
      final ScrollableJPanel content = new ScrollableJPanel();
      content.setLayout(new BorderLayout());
      content.add(rules, BorderLayout.NORTH);
      final JScrollPane scroll = SwingComponents.newJScrollPane(content);
      scroll.setBorder(BorderFactory.createEmptyBorder());
      scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
      // the tab's size decides, not the options
      scroll.setPreferredSize(new Dimension(0, 0));
      options.add(PropertiesUi.withFilter(rules, scroll), BorderLayout.CENTER);
    }
    // a joined game has its options at the host, a bot's game asks the bot
    rulesSummary.setVisible(data != null && editable);
    showChangedRules();
    options.revalidate();
    options.repaint();
  }

  /**
   * The options that differ from those the game was loaded with, eg "Low Luck: on", below the card;
   * laid out again only when they changed.
   */
  private void showChangedRules() {
    final List<String> changes = describeChanges();
    if (Objects.equals(changes, shownChanges)) {
      return;
    }
    shownChanges = changes;
    changedRules.removeAll();
    if (changes.isEmpty()) {
      changedRules.add(
          newHint("None: the map's own rules."), buildGridRow(0, new Insets(0, 0, 0, 0)));
    }
    for (int i = 0; i < changes.size(); i++) {
      // wraps a long option's name instead of widening the column
      changedRules.add(
          new WrappingText(changes.get(i)), buildGridRow(i, new Insets(i == 0 ? 0 : 4, 0, 0, 0)));
    }
    changedRules.revalidate();
    changedRules.repaint();
  }

  /** "Name: value" of each option that differs from the game as it was loaded. */
  @VisibleForTesting
  List<String> describeChanges() {
    final GameData data = model.getGameData();
    if (data == null) {
      return List.of();
    }
    final Map<String, Object> original = originalPropertiesMap;
    return data.getProperties().getEditableProperties().stream()
        .filter(property -> !Objects.equals(original.get(property.getName()), property.getValue()))
        .map(property -> property.getName() + ": " + describe(property.getValue()))
        .toList();
  }

  private static String describe(final Object value) {
    if (value instanceof Boolean on) {
      return on ? "on" : "off";
    }
    return String.valueOf(value);
  }

  private static JComponent newHint(final String text) {
    final WrappingText hint = new WrappingText(text);
    hint.setForeground(LookAndFeel.getSecondaryTextColor());
    return hint;
  }

  /**
   * The player's rule sets of the game below the Rule sets button: a name and ✓ keep the options as
   * one more set, a click on a set loads it, the trash can deletes it.
   */
  private void showRuleSets(final JComponent anchor) {
    final JPopupMenu popup = new JPopupMenu();
    final JTextField name = new JTextField(16);
    name.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Name for these rules");
    final JButton save = new JButtonBuilder().title("✓").build();
    final JPanel saved = new JPanel(new GridBagLayout());
    saved.setOpaque(false);
    final Runnable updateSave =
        () -> {
          final Optional<String> reason = whyNotSaved(name.getText());
          save.setEnabled(reason.isEmpty());
          save.setToolTipText(reason.orElse("Save"));
        };
    final Runnable saveAndList =
        () -> {
          if (whyNotSaved(name.getText()).isEmpty()) {
            saveRuleSet(name.getText().strip());
            name.setText("");
            listRuleSets(saved, popup);
          }
        };
    new DocumentListenerBuilder(updateSave).attachTo(name);
    name.addActionListener(e -> saveAndList.run());
    save.addActionListener(e -> saveAndList.run());
    updateSave.run();
    final JPanel entry = new JPanel(new BorderLayout(6, 0));
    entry.setOpaque(false);
    entry.add(name, BorderLayout.CENTER);
    entry.add(save, BorderLayout.EAST);
    final JPanel content = new JPanel(new BorderLayout(0, 10));
    content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
    content.add(entry, BorderLayout.NORTH);
    final JScrollPane savedScroll = SwingComponents.newJScrollPane(saved);
    savedScroll.setBorder(BorderFactory.createEmptyBorder());
    savedScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    content.add(savedScroll, BorderLayout.CENTER);
    popup.add(content);
    listRuleSets(saved, popup);
    popup.show(anchor, 0, anchor.getHeight());
    name.requestFocusInWindow();
  }

  /** Lists the saved rule sets in the popup, newest state from the disk. */
  @VisibleForTesting
  void listRuleSets(final JPanel saved, final JPopupMenu popup) {
    saved.removeAll();
    final List<String> names =
        Optional.ofNullable(model.getGameData())
            .map(gamePropertiesCache::getRuleSetNames)
            .orElse(List.of());
    if (names.isEmpty()) {
      saved.add(
          newHint("No rule sets yet. Change options, then name them above."),
          buildGridRow(0, new Insets(0, 0, 0, 0)));
    }
    for (int i = 0; i < names.size(); i++) {
      final String ruleSet = names.get(i);
      final JButton use =
          newOptionsLink(
              ruleSet,
              "Load",
              () -> {
                popup.setVisible(false);
                useRuleSet(ruleSet);
              });
      use.setHorizontalAlignment(SwingConstants.LEADING);
      // an icon only: the builder wants a title
      final JButton delete = new JButton(new TrashIcon());
      LookAndFeel.setBorderlessButtonStyle(delete);
      delete.setToolTipText("Delete");
      delete.getAccessibleContext().setAccessibleName("Delete " + ruleSet);
      delete.addActionListener(
          e -> {
            deleteRuleSet(ruleSet);
            listRuleSets(saved, popup);
          });
      final JPanel row = new JPanel(new BorderLayout());
      row.setOpaque(false);
      row.add(use, BorderLayout.CENTER);
      row.add(delete, BorderLayout.EAST);
      saved.add(row, buildGridRow(i, new Insets(0, 0, 0, 0)));
    }
    // many sets scroll in the popup instead of making it taller than the screen
    Optional.ofNullable(SwingUtilities.getAncestorOfClass(JScrollPane.class, saved))
        .ifPresent(
            scroll ->
                scroll.setPreferredSize(
                    new Dimension(
                        saved.getPreferredSize().width
                            + ((JScrollPane) scroll)
                                .getVerticalScrollBar()
                                .getPreferredSize()
                                .width,
                        Math.min(saved.getPreferredSize().height, RULE_SETS_HEIGHT))));
    saved.revalidate();
    popup.pack();
  }

  /**
   * Why the options cannot be kept under the name now, as the ✓'s hint: a set needs a name of its
   * own and options that differ from the map's.
   */
  @VisibleForTesting
  Optional<String> whyNotSaved(final String name) {
    final GameData data = model.getGameData();
    if (data == null) {
      return Optional.of("No options");
    }
    if (name.isBlank()) {
      return Optional.of("Name it first");
    }
    // the name is the file's: one of these would be dropped and could meet another set's name
    if (FORBIDDEN_IN_NAMES.matcher(name).find()) {
      return Optional.of("No \\ / : * ? \" < > |");
    }
    if (describeChanges().isEmpty()) {
      return Optional.of("Change an option first");
    }
    // the file system may not tell "Classic" from "classic"
    if (gamePropertiesCache.getRuleSetNames(data).stream()
        .anyMatch(name.strip()::equalsIgnoreCase)) {
      return Optional.of("Name taken");
    }
    return Optional.empty();
  }

  @VisibleForTesting
  void saveRuleSet(final String name) {
    Optional.ofNullable(model.getGameData())
        .ifPresent(data -> gamePropertiesCache.saveRuleSet(data, name));
  }

  private void useRuleSet(final String name) {
    Optional.ofNullable(model.getGameData())
        .ifPresent(data -> gamePropertiesCache.loadRuleSetInto(data, name));
    refreshOptions();
  }

  private void deleteRuleSet(final String name) {
    Optional.ofNullable(model.getGameData())
        .ifPresent(data -> gamePropertiesCache.deleteRuleSet(data, name));
  }

  /**
   * Switches each nation's income fields on and off, in its row of the players: the share of its
   * production it collects and a bonus on top. Remembered for the next games.
   */
  /**
   * The Resource Modifiers button of the options; the screen hides it where its player table has no
   * income fields, eg when hosting a network game.
   */
  public JComponent getResourceModifiersSwitch() {
    return resourceModifiers;
  }

  private static JToggleButton newResourceModifiersSwitch() {
    final JToggleButton toggle =
        new JToggleButton(
            I18nEngineFramework.get().getText("startup.SetupPanel.resourceModifiers.Lbl"),
            ClientSetting.showResourceModifiers.getValueOrThrow());
    toggle.setToolTipText("Each nation's income, shown next to it in the list of players");
    toggle.addActionListener(
        e -> ClientSetting.showResourceModifiers.setValueAndFlush(toggle.isSelected()));
    return toggle;
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
    savedGameChooser.accept(this, this::loadSaveFile);
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
    BackgroundTaskRunner.runInBackground("Loading map...", () -> model.loadMap(gameFile));
  }

  /** A small trash can in the button's text color, for deleting a rule set. */
  private static final class TrashIcon implements Icon {
    private static final int SIZE = 14;

    @Override
    public void paintIcon(final Component component, final Graphics g, final int x, final int y) {
      final Graphics2D g2 = (Graphics2D) g.create();
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2.setColor(component.getForeground());
      g2.setStroke(new BasicStroke(1.3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
      // the lid with its handle, then the can with two grooves
      g2.drawLine(x + 2, y + 3, x + 12, y + 3);
      g2.drawLine(x + 5, y + 3, x + 6, y + 1);
      g2.drawLine(x + 6, y + 1, x + 8, y + 1);
      g2.drawLine(x + 8, y + 1, x + 9, y + 3);
      g2.drawRoundRect(x + 3, y + 4, 8, 9, 2, 2);
      g2.drawLine(x + 6, y + 6, x + 6, y + 11);
      g2.drawLine(x + 8, y + 6, x + 8, y + 11);
      g2.dispose();
    }

    @Override
    public int getIconWidth() {
      return SIZE;
    }

    @Override
    public int getIconHeight() {
      return SIZE;
    }
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
