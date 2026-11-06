package games.strategy.engine.framework.ui;

import com.formdev.flatlaf.FlatClientProperties;
import com.google.common.annotations.VisibleForTesting;
import games.strategy.engine.framework.lookandfeel.LookAndFeel;
import games.strategy.engine.framework.map.download.MapBrowserView;
import games.strategy.engine.framework.map.download.MapPreviewBox;
import games.strategy.engine.framework.map.download.MapPreviews;
import games.strategy.engine.framework.map.download.MapPreviews.Size;
import games.strategy.engine.framework.map.download.MapViewSwitch;
import games.strategy.engine.framework.map.download.PreviewSlider;
import games.strategy.engine.framework.map.file.system.loader.InstalledMapsListing;
import games.strategy.triplea.settings.ClientSetting;
import games.strategy.ui.Util;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.Serial;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import javax.annotation.Nullable;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import lombok.AllArgsConstructor;
import lombok.experimental.UtilityClass;
import org.triplea.swing.DocumentListenerBuilder;
import org.triplea.swing.JButtonBuilder;
import org.triplea.swing.JLabelBuilder;
import org.triplea.swing.SwingComponents;
import org.triplea.swing.jpanel.JPanelBuilder;
import org.triplea.swing.key.binding.KeyCode;
import org.triplea.swing.key.binding.SwingKeyBinding;

/**
 * Use to display a modal dialog that prompts the user to select a game (map) from the list of
 * installed games (maps).
 */
@UtilityClass
public class GameChooser {

  /**
   * Displays the Game Chooser dialog and invokes the gameChosenCallback if a game is selected.
   * Blocking, blocks until the dialog is closed.
   *
   * @param gameChosenCallback The callback invoked when a game is chosen.
   */
  public static void chooseGame(
      final Frame owner,
      final InstalledMapsListing installedMapsListing,
      final String gameName,
      final Consumer<Path> gameChosenCallback) {

    final JDialog dialog = new JDialog(owner, "Select a Game", true);
    dialog.setLayout(new BorderLayout());

    final List<DefaultGameChooserEntry> allGames =
        List.copyOf(installedMapsListing.createGameChooserEntries());
    final DefaultListModel<DefaultGameChooserEntry> gameChooserModel = new DefaultListModel<>();
    gameChooserModel.addAll(allGames);

    final JList<DefaultGameChooserEntry> gameList = new JList<>(gameChooserModel);
    // the views of the maps window, with the installed maps' pictures; one choice for both
    final MapPreviews rowPictures = new MapPreviews(Size.ROW);
    final MapPreviews tilePictures = new MapPreviews(Size.TILE);
    final Consumer<MapBrowserView> showView =
        view -> showView(gameList, view, view == MapBrowserView.TILES ? tilePictures : rowPictures);
    final MapBrowserView chosenView = ClientSetting.mapBrowserView.getValueOrThrow();
    showView.accept(chosenView);
    final MapViewSwitch viewSwitch =
        new MapViewSwitch(
            chosenView,
            view -> {
              ClientSetting.mapBrowserView.setValueAndFlush(view);
              showView.accept(view);
            });
    if (gameName == null || gameName.equals("-")) {
      gameList.setSelectedIndex(0);
    } else {
      IntStream.range(0, gameChooserModel.size())
          .mapToObj(gameChooserModel::get)
          .filter(entry -> entry.getGameName().equals(gameName))
          .findAny()
          .ifPresent(entry -> gameList.setSelectedValue(entry, true));
    }

    final JSplitPane mainSplit = new JSplitPane();
    mainSplit.setResizeWeight(0.0);
    dialog.add(mainSplit, BorderLayout.CENTER);
    final JPanel leftPanel = new JPanel();
    leftPanel.setLayout(new GridBagLayout());
    leftPanel.add(
        new JLabelBuilder("Games").adjustFontSize(2).bold().build(),
        new GridBagConstraints(
            0,
            0,
            3,
            1,
            0,
            0,
            GridBagConstraints.WEST,
            GridBagConstraints.NONE,
            new Insets(10, 10, 10, 10),
            0,
            0));

    // a search by game or map and an order above the games, as in the map browser
    final JTextField searchField = new JTextField();
    searchField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Search games or maps");
    final JComboBox<Order> orderBox = new JComboBox<>(Order.values());
    final Runnable showMatching =
        () -> {
          final DefaultGameChooserEntry selected = gameList.getSelectedValue();
          gameChooserModel.clear();
          gameChooserModel.addAll(
              matching(allGames, searchField.getText(), (Order) orderBox.getSelectedItem()));
          if (selected != null && gameChooserModel.contains(selected)) {
            gameList.setSelectedValue(selected, true);
          } else if (!gameChooserModel.isEmpty()) {
            gameList.setSelectedIndex(0);
          }
        };
    // the builder calls back on a timer thread
    new DocumentListenerBuilder(() -> SwingUtilities.invokeLater(showMatching))
        .attachTo(searchField);
    orderBox.addActionListener(e -> showMatching.run());
    leftPanel.add(
        searchField,
        new GridBagConstraints(
            0,
            1,
            1,
            1,
            1.0,
            0,
            GridBagConstraints.WEST,
            GridBagConstraints.HORIZONTAL,
            new Insets(0, 10, 8, 0),
            0,
            0));
    leftPanel.add(
        orderBox,
        new GridBagConstraints(
            1,
            1,
            1,
            1,
            0,
            0,
            GridBagConstraints.EAST,
            GridBagConstraints.NONE,
            new Insets(0, 6, 8, 0),
            0,
            0));
    leftPanel.add(
        viewSwitch,
        new GridBagConstraints(
            2,
            1,
            1,
            1,
            0,
            0,
            GridBagConstraints.EAST,
            GridBagConstraints.NONE,
            new Insets(0, 0, 8, 0),
            0,
            0));

    final JScrollPane listScroll = SwingComponents.newJScrollPane(gameList);
    listScroll.setMinimumSize(new Dimension(200, 0));
    leftPanel.add(
        listScroll,
        new GridBagConstraints(
            0,
            2,
            3,
            1,
            1.0,
            1.0,
            GridBagConstraints.EAST,
            GridBagConstraints.BOTH,
            new Insets(0, 10, 0, 0),
            0,
            0));
    mainSplit.setLeftComponent(leftPanel);

    // the chosen game on the right: its map's picture, its name and map, then its notes
    final GameNotesView notesPanel = new GameNotesView();
    final GameHeader header = new GameHeader();
    final Consumer<DefaultGameChooserEntry> showGame =
        game -> {
          header.showGame(game);
          notesPanel.setText(game.readGameNotes());
        };
    Optional.ofNullable(gameList.getSelectedValue()).ifPresent(showGame);

    // a column like the details of the maps window: the picture across its width, then the notes
    final JPanel infoPanel = new JPanel(new BorderLayout(0, 16));
    infoPanel.setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24));
    infoPanel.add(header, BorderLayout.NORTH);
    infoPanel.add(SwingComponents.newJScrollPane(notesPanel), BorderLayout.CENTER);
    infoPanel.addComponentListener(
        new ComponentAdapter() {
          @Override
          public void componentResized(final ComponentEvent e) {
            header.fitPicture(
                infoPanel.getWidth() - infoPanel.getInsets().left - infoPanel.getInsets().right);
          }
        });

    mainSplit.setRightComponent(infoPanel);
    mainSplit.setBorder(null);

    final Runnable selectAndReturn =
        () -> {
          dialog.setVisible(false);
          Optional.ofNullable(gameList.getSelectedValue())
              .flatMap(DefaultGameChooserEntry::getGameXmlFilePath)
              .ifPresent(gameChosenCallback);
        };

    final JButton cancelButton =
        new JButtonBuilder("Cancel").actionListener(dialog::dispose).build();
    SwingKeyBinding.addKeyBinding(cancelButton, KeyCode.ESCAPE, dialog::dispose);

    final JPanel buttonsPanel =
        new JPanelBuilder()
            .boxLayoutHorizontal()
            .addHorizontalStrut(30)
            .add(Box.createGlue())
            .add(new JButtonBuilder("OK").actionListener(selectAndReturn).build())
            .add(cancelButton)
            .add(Box.createGlue())
            .build();
    dialog.add(buttonsPanel, BorderLayout.SOUTH);

    gameList.addListSelectionListener(
        e -> {
          if (!e.getValueIsAdjusting()) {
            Optional.ofNullable(gameList.getSelectedValue()).ifPresent(showGame);
          }
        });
    gameList.addMouseListener(
        new MouseAdapter() {
          @Override
          public void mouseClicked(final MouseEvent event) {
            if (event.getClickCount() == 2) {
              selectAndReturn.run();
            }
          }
        });

    final Dimension screenSize = Util.getScreenSize(dialog);
    final int width = Math.clamp((int) (screenSize.width * 0.6), 800, 1180);
    final int height = Math.max(600, (int) (screenSize.height * 0.66));
    dialog.setSize(new Dimension(width, height));
    // the list is wide enough for game and map names next to the search and the order
    mainSplit.setDividerLocation(360);
    dialog.setLocationRelativeTo(null);
    dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
    // typing searches at once
    SwingUtilities.invokeLater(searchField::requestFocusInWindow);
    dialog.setVisible(true); // Blocking and waits for user action
  }

  /**
   * The games whose name or map name contains the search, ignoring case, in the order; all games
   * for an empty search.
   */
  @VisibleForTesting
  static List<DefaultGameChooserEntry> matching(
      final Collection<DefaultGameChooserEntry> games, final String search, final Order order) {
    final String searched = search.strip().toLowerCase(Locale.ROOT);
    return games.stream()
        .filter(
            game ->
                game.getGameName().toLowerCase(Locale.ROOT).contains(searched)
                    || game.getInstalledMap()
                        .getMapName()
                        .toLowerCase(Locale.ROOT)
                        .contains(searched))
        .sorted(order.comparator)
        .toList();
  }

  /** The orders of the games to choose from. */
  @AllArgsConstructor
  enum Order {
    NAME("By name", Comparator.naturalOrder()),
    MAP(
        "By map",
        Comparator.comparing(
                (DefaultGameChooserEntry game) -> game.getInstalledMap().getMapName(),
                String.CASE_INSENSITIVE_ORDER)
            .thenComparing(Comparator.naturalOrder())),
    // maps without a date last
    NEWEST(
        "Recently updated",
        Comparator.comparing(
                (DefaultGameChooserEntry game) ->
                    game.getInstalledMap().getLastModifiedDate().orElse(Instant.MIN),
                Comparator.reverseOrder())
            .thenComparing(Comparator.naturalOrder()));

    private final String title;
    private final Comparator<DefaultGameChooserEntry> comparator;

    @Override
    public String toString() {
      return title;
    }
  }

  /**
   * Shows the games in the view: lines, rows with pictures, or tiles in rows that wrap at the width
   * of the list.
   */
  private static void showView(
      final JList<DefaultGameChooserEntry> list,
      final MapBrowserView view,
      final MapPreviews pictures) {
    list.setCellRenderer(new GameCellRenderer(view, pictures, list::repaint));
    list.setLayoutOrientation(
        view == MapBrowserView.TILES ? JList.HORIZONTAL_WRAP : JList.VERTICAL);
    list.setVisibleRowCount(view == MapBrowserView.TILES ? -1 : 8);
    // the cells of a view with pictures all have one size: the list need not measure every game
    list.setPrototypeCellValue(
        view == MapBrowserView.LIST || list.getModel().getSize() == 0
            ? null
            : list.getModel().getElementAt(0));
    list.setFixedCellWidth(
        view == MapBrowserView.TILES ? Size.TILE.getWidth() + 2 * GameCellRenderer.PADDING : -1);
    if (view == MapBrowserView.LIST) {
      list.setFixedCellHeight(-1);
    }
    list.ensureIndexIsVisible(Math.max(0, list.getSelectedIndex()));
  }

  /**
   * The chosen game above its notes, as the maps window shows a map: its map's picture across the
   * width of the column, which a click on its lower border slides up out of the way so the notes
   * get the room, then the game's name and its map.
   */
  private static final class GameHeader extends JPanel {
    @Serial private static final long serialVersionUID = 1L;

    private final transient MapPreviews pictures = new MapPreviews(Size.DETAILS);
    private final PreviewSlider slider = new PreviewSlider();
    private final JLabel gameName = new JLabelBuilder().bold().adjustFontSize(4).build();
    private final JLabel mapName =
        new JLabelBuilder().foregroundColor(LookAndFeel.getSecondaryTextColor()).build();
    private @Nullable MapPreviewBox picture;

    GameHeader() {
      setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
      setOpaque(false);
      for (final JComponent part :
          List.of(slider, (JComponent) Box.createVerticalStrut(16), gameName, mapName)) {
        part.setAlignmentX(LEFT_ALIGNMENT);
        add(part);
      }
    }

    void showGame(final DefaultGameChooserEntry game) {
      gameName.setText(game.getGameName());
      mapName.setText("Map: " + game.getInstalledMap().getMapName());
      // a map without a picture shows the quiet box, as the maps window does
      picture = new MapPreviewBox(pictures, game.findPreviewUrl());
      fitPicture(getWidth());
      slider.showPreview(picture);
      revalidate();
      repaint();
    }

    /** The picture fills the width of the column, eg after the dialog was resized. */
    void fitPicture(final int width) {
      if (picture != null && width > 0 && width != picture.getPreferredSize().width) {
        picture.resize(width);
      }
    }
  }
}
