package games.strategy.engine.framework.startup.ui;

import com.google.common.annotations.VisibleForTesting;
import games.strategy.engine.framework.I18nEngineFramework;
import games.strategy.engine.framework.I18nResourceBundle;
import games.strategy.engine.framework.lookandfeel.LookAndFeel;
import games.strategy.engine.framework.map.download.DownloadMapsWindow;
import games.strategy.engine.framework.startup.launcher.ILauncher;
import games.strategy.engine.framework.startup.ui.MainMenuFit.Column;
import games.strategy.engine.framework.startup.ui.MainMenuFit.Part;
import games.strategy.engine.framework.startup.ui.panels.main.HeadedServerSetupModel;
import games.strategy.engine.framework.startup.ui.panels.main.game.selector.GameFileSelector;
import games.strategy.engine.framework.ui.BackBar;
import games.strategy.engine.framework.ui.MainFrame;
import games.strategy.triplea.EngineImageLoader;
import games.strategy.triplea.UrlConstants;
import games.strategy.triplea.settings.ClientSetting;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RadialGradientPaint;
import java.io.Serial;
import java.nio.file.Path;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;
import javax.swing.Action;
import javax.swing.Box;
import javax.swing.Box.Filler;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.LayoutFocusTraversalPolicy;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import org.triplea.awt.OpenFileUtility;
import org.triplea.config.product.ProductVersionReader;
import org.triplea.swing.JButtonBuilder;
import org.triplea.swing.JLabelBuilder;
import org.triplea.swing.WrappingText;
import org.triplea.swing.jpanel.GridBagConstraintsAnchor;
import org.triplea.swing.jpanel.GridBagConstraintsBuilder;
import org.triplea.swing.jpanel.GridBagConstraintsFill;

/**
 * The main menu, laid out as strategy games do: on the left the classic TripleA logo and name over
 * a list of large text entries without boxes, Multiplayer in the accent color; at the bottom right
 * a tip and the way into the tutorial. It never scrolls: what does not fit into the window is
 * hidden ({@link MainMenuFit}), the entries always stay. Multiplayer, Load game and How to play
 * open in place of the list, below a {@link BackBar} back to the main menu (also Escape). A quiet
 * gradient lies behind it all. While this panel is shown, the main screen hides its game selection
 * and its footer.
 */
public class MetaSetupPanel extends SetupPanel {

  private static final long serialVersionUID = 3926503672972937677L;

  /** Room left of the menu and the pages, as games keep their menus off the window edge. */
  private static final int LEFT_MARGIN = 96;

  private static final int MENU_WIDTH = 480;
  private static final int SUB_MENU_WIDTH = 640;

  /**
   * The width of the right column in large windows; in smaller ones it narrows down to the least
   * width, which the map of the day's picture and the loading placeholders need, eg 1280 px wide
   * windows still show it.
   */
  private static final int RIGHT_COLUMN_WIDTH = 760;

  private static final int RIGHT_COLUMN_LEAST_WIDTH = 560;

  /** The least room between the menu and the right column. */
  private static final int COLUMN_GAP = 40;

  /** Number of tips, "startup.MetaSetupPanel.Tip.1" to ".8". */
  @VisibleForTesting static final int TIPS = 8;

  private static final String KEY_PREFIX = "startup.SetupPanelModel.btn.";
  private static final String MENU_PREFIX = "startup.MetaSetupPanel.";
  private static final String HELP_PREFIX = "startup.HelpPanel.";
  private static final String MAIN_MENU_KEY = MENU_PREFIX + "MainMenu";

  @VisibleForTesting static final String MAIN = "main";
  @VisibleForTesting static final String MULTIPLAYER = "multiplayer";
  @VisibleForTesting static final String LOAD_GAME = "load";
  @VisibleForTesting static final String HELP = "help";

  private final CardLayout menuCards = new CardLayout();
  private final JPanel menus = new JPanel(menuCards);
  private final HeadedServerSetupModel model;
  private final SavedGamesPanel savedGames;
  private final TutorialsPanel tutorials;
  private final MenuEntry multiplayer;
  private final AtomicBoolean loadingMap = new AtomicBoolean();
  private String shownMenu = MAIN;

  public MetaSetupPanel(final HeadedServerSetupModel model) {
    this(model, MainMenuSources.live());
  }

  @VisibleForTesting
  MetaSetupPanel(final HeadedServerSetupModel model, final MainMenuSources sources) {
    this.model = model;
    final I18nResourceBundle bundle = I18nEngineFramework.get();
    savedGames =
        new SavedGamesPanel(
            bundle,
            model::continueSavedGame,
            () -> GameFileSelector.selectSavedGame(this).ifPresent(model::continueSavedGame));
    // Playing against other people is what TripleA is made for: the one entry in the accent color.
    multiplayer =
        MenuEntry.newHighlighted(
            bundle.getText(MENU_PREFIX + "Multiplayer"),
            bundle.getText(MENU_PREFIX + "Multiplayer.Desc"));
    multiplayer.addActionListener(e -> showMenu(MULTIPLAYER));
    tutorials = new TutorialsPanel(bundle, newHelpLinks(bundle));

    menus.add(newMainScreen(bundle, sources), MAIN);
    menus.add(
        newPage(bundle, MENU_PREFIX + "Multiplayer", newMultiplayerMenu(bundle), SUB_MENU_WIDTH),
        MULTIPLAYER);
    menus.add(newPage(bundle, MENU_PREFIX + "LoadGame", savedGames, 0), LOAD_GAME);
    menus.add(newPage(bundle, KEY_PREFIX + "UserGuideHelp.Lbl", tutorials, 0), HELP);

    setLayout(new BorderLayout());
    add(menus, BorderLayout.CENTER);
    // the window's first focus goes to Multiplayer, never to a link of the right column
    setFocusTraversalPolicyProvider(true);
    setFocusTraversalPolicy(new MainMenuFocus());
    // the gradient behind shows through every panel of the menu
    makeTransparent(menus);
  }

  /** The links to more help below the tutorial, each opening a page in the browser. */
  private JComponent newHelpLinks(final I18nResourceBundle bundle) {
    final List<Entry<String, String>> helps =
        List.of(
            Map.entry("UserGuide", UrlConstants.USER_GUIDE),
            Map.entry("Forum", UrlConstants.TRIPLEA_FORUM),
            Map.entry("ReleaseNotes", UrlConstants.RELEASE_NOTES),
            Map.entry("MapMaking", UrlConstants.MAP_MAKER_HELP),
            Map.entry("ReportBug", UrlConstants.GITHUB_ISSUES));
    final JPanel links = new JPanel(new GridBagLayout());
    IntStream.range(0, helps.size())
        .forEach(
            row -> links.add(newHelpLink(bundle, helps.get(row)), newRowConstraints(row, 0, 0)));
    return links;
  }

  private JButton newHelpLink(final I18nResourceBundle bundle, final Entry<String, String> help) {
    final JButton link =
        new JButtonBuilder(bundle.getText(HELP_PREFIX + help.getKey()) + "  ›")
            .toolTip(bundle.getText(HELP_PREFIX + help.getKey() + ".Desc"))
            .actionListener(() -> OpenFileUtility.openUrl(this, help.getValue()))
            .build();
    LookAndFeel.setBorderlessButtonStyle(link);
    link.setHorizontalAlignment(SwingConstants.LEADING);
    return link;
  }

  /**
   * How busy the lobby is at the top left, the menu below it, vertically centered, a tip and the
   * version at the bottom; on the right what happens around TripleA. What does not fit into the
   * window is hidden, see {@link MainMenuFit}.
   */
  private JComponent newMainScreen(final I18nResourceBundle bundle, final MainMenuSources sources) {
    final Map<Part, JComponent> parts = new EnumMap<>(Part.class);
    parts.put(Part.LOBBY_STATUS, new LobbyStatusPanel(bundle, sources.lobbyStats()));
    parts.put(Part.BRAND, newBrand(bundle));
    parts.put(Part.TIP, newTip(bundle));
    parts.put(Part.COMMUNITY, new CommunityPanel(bundle, sources));
    parts.put(Part.UPDATES, new UpdatesPanel(bundle, sources));
    parts.put(
        Part.MAP_OF_THE_DAY,
        new MapOfTheDayPanel(
            bundle, sources.installedMaps(), sources.mapsToDownload(), this::playLocally));
    final JComponent entries = newMenuEntries(bundle);
    final JLabel version =
        new JLabelBuilder(
                bundle.getText(MENU_PREFIX + "Version", ProductVersionReader.getCurrentVersion()))
            .foregroundColor(LookAndFeel.getSecondaryTextColor())
            .build();
    return new MainScreen(parts, entries, version);
  }

  /**
   * Opens the local game setup on the game XML, read in the background; a second click while it is
   * read does nothing.
   */
  private void playLocally(final Path gameXml) {
    if (!loadingMap.compareAndSet(false, true)) {
      return;
    }
    new Thread(
            () -> {
              try {
                if (model.getGameSelectorModel().loadMap(gameXml)) {
                  SwingUtilities.invokeLater(model::showLocal);
                }
              } finally {
                loadingMap.set(false);
              }
            },
            "Load map of the day")
        .start();
  }

  /** The entries of the main menu, below the logo and name; they always stay on screen. */
  private JComponent newMenuEntries(final I18nResourceBundle bundle) {
    // the two ways to play invite with a line each; the other entries need only their name
    final MenuEntry playLocally =
        MenuEntry.newEntry(
            bundle.getText(KEY_PREFIX + "StartLocalGame.Lbl"),
            bundle.getText(KEY_PREFIX + "StartLocalGame.Desc"));
    playLocally.addActionListener(e -> model.showLocal());
    final MenuEntry loadGame = MenuEntry.newEntry(bundle.getText(MENU_PREFIX + "LoadGame"), "");
    loadGame.addActionListener(e -> showMenu(LOAD_GAME));
    final MenuEntry maps = MenuEntry.newEntry(bundle.getText(MENU_PREFIX + "Maps"), "");
    maps.addActionListener(e -> DownloadMapsWindow.showDownloadMapsWindow());
    final MenuEntry settings =
        MenuEntry.newEntry(bundle.getText(KEY_PREFIX + "EnginePreferences.Lbl"), "");
    settings.addActionListener(
        e -> ClientSetting.showSettingsWindow(JOptionPane.getFrameForComponent(this)));
    final MenuEntry help = MenuEntry.newEntry(bundle.getText(KEY_PREFIX + "UserGuideHelp.Lbl"), "");
    help.addActionListener(e -> showMenu(HELP));
    // the way out, as in every game menu; in full screen there is no window frame to close
    final MenuEntry quit = MenuEntry.newEntry(bundle.getText(MENU_PREFIX + "Quit"), "");
    // as the Quit button of the setup screens: the quit actions run first, eg leaving the lobby
    quit.addActionListener(e -> MainFrame.quit());

    final List<MenuEntry> entries =
        List.of(multiplayer, playLocally, loadGame, maps, settings, help, quit);
    final JPanel column = new JPanel(new GridBagLayout());
    IntStream.range(0, entries.size())
        .forEach(
            index ->
                column.add(entries.get(index), newRowConstraints(index, index == 0 ? 40 : 2, 0)));
    column.add(Box.createHorizontalStrut(MENU_WIDTH), newRowConstraints(entries.size(), 0, 0));
    return column;
  }

  /** The classic TripleA soldier and name, as on the original start screen, and a tagline. */
  private static JComponent newBrand(final I18nResourceBundle bundle) {
    final JPanel brand = new JPanel(new GridBagLayout());
    brand.add(newLogo(), newRowConstraints(0, 0, 0));
    brand.add(newGameName(), newRowConstraints(1, 8, 0));
    brand.add(newTagline(bundle), newRowConstraints(2, 0, 0));
    return brand;
  }

  /** The classic logo in the size of its file, as the original start screen showed it. */
  private static JLabel newLogo() {
    return new JLabel(
        new ImageIcon(EngineImageLoader.loadImage("launch_screens", "triplea-logo.png")),
        SwingConstants.LEADING);
  }

  /** The name below the logo, written as the game writes it: "TripleA". */
  private static JLabel newGameName() {
    final JLabel name = new JLabel("TripleA");
    name.setFont(name.getFont().deriveFont(Font.PLAIN, name.getFont().getSize2D() + 26));
    return name;
  }

  /** A short line about the game below its name. */
  private static JLabel newTagline(final I18nResourceBundle bundle) {
    final JLabel tagline = new JLabel(bundle.getText(MENU_PREFIX + "Tagline"));
    tagline.setForeground(LookAndFeel.getSecondaryTextColor());
    tagline.setFont(tagline.getFont().deriveFont(tagline.getFont().getSize2D() + 3));
    return tagline;
  }

  /** A full-width row of a column, with the given space above it and indent on the left. */
  private static GridBagConstraints newRowConstraints(
      final int row, final int spaceAbove, final int indent) {
    return new GridBagConstraintsBuilder(0, row)
        .weightX(1)
        .fill(GridBagConstraintsFill.HORIZONTAL)
        .insets(spaceAbove, indent, 0, 0)
        .build();
  }

  /**
   * "Tip: ..." in one quiet line; a different tip now and then, so returning players learn
   * something too.
   */
  private static JComponent newTip(final I18nResourceBundle bundle) {
    final WrappingText tip =
        new WrappingText(
            bundle.getText(MENU_PREFIX + "Tip")
                + ": "
                + tip(bundle, Instant.now().getEpochSecond()));
    tip.setForeground(LookAndFeel.getSecondaryTextColor());
    return tip;
  }

  /** One of the {@link #TIPS} tips, chosen by any number, eg the seconds of the clock. */
  @VisibleForTesting
  static String tip(final I18nResourceBundle bundle, final long chooser) {
    return bundle.getText(MENU_PREFIX + "Tip." + (1 + Math.floorMod(chooser, TIPS)));
  }

  /** The ways to play with others: the lobby, the local network, by email and by forum. */
  private JComponent newMultiplayerMenu(final I18nResourceBundle bundle) {
    final List<MenuEntry> entries = newMultiplayerEntries(bundle, model);
    final JPanel column = new JPanel(new GridBagLayout());
    IntStream.range(0, entries.size())
        .forEach(row -> column.add(entries.get(row), newRowConstraints(row, row == 0 ? 0 : 4, 0)));
    column.add(
        new JPanel(),
        new GridBagConstraintsBuilder(0, entries.size())
            .weightY(1)
            .fill(GridBagConstraintsFill.BOTH)
            .build());
    return column;
  }

  @VisibleForTesting
  static List<MenuEntry> newMultiplayerEntries(
      final I18nResourceBundle bundle, final HeadedServerSetupModel model) {
    final MenuEntry lobby =
        MenuEntry.newHighlighted(
            bundle.getText(KEY_PREFIX + "PlayOnline.Lbl"),
            bundle.getText(KEY_PREFIX + "PlayOnline.Desc"));
    lobby.addActionListener(e -> model.login());
    final MenuEntry host = newWayToPlay(bundle, "HostNetworkGame");
    host.addActionListener(e -> new Thread(model::showServer).start());
    final MenuEntry join = newWayToPlay(bundle, "ConnectToNetworkedGame");
    join.addActionListener(e -> new Thread(model::showClient).start());
    final MenuEntry email = newWayToPlay(bundle, "PlayByEmail");
    email.addActionListener(e -> model.showPbem());
    final MenuEntry forum = newWayToPlay(bundle, "PlayByForum");
    forum.addActionListener(e -> model.showPbf());
    return List.of(lobby, host, join, email, forum);
  }

  /** Creates an entry from the i18n texts "startup.SetupPanelModel.btn.{key}.Lbl" and ".Desc". */
  private static MenuEntry newWayToPlay(final I18nResourceBundle bundle, final String key) {
    return MenuEntry.newEntry(
        bundle.getText(KEY_PREFIX + key + ".Lbl"), bundle.getText(KEY_PREFIX + key + ".Desc"));
  }

  /**
   * A page in place of the main menu: the bar with the link back to the main menu and the title,
   * below it the content at the left edge of the menu, which fills the rest of the window, or keeps
   * the given width (0: the full width).
   */
  private JComponent newPage(
      final I18nResourceBundle bundle,
      final String titleKey,
      final JComponent content,
      final int width) {
    final JPanel page = new JPanel(new GridBagLayout());
    page.add(
        new BackBar(getMainMenuTitle(), bundle.getText(titleKey), () -> showMenu(MAIN)),
        new GridBagConstraintsBuilder(0, 0)
            .weightX(1)
            .fill(GridBagConstraintsFill.HORIZONTAL)
            .insets(0, 0, 28, 0)
            .build());
    final GridBagConstraintsBuilder contentConstraints =
        new GridBagConstraintsBuilder(0, 1)
            .weightX(1)
            .weightY(1)
            .anchor(GridBagConstraintsAnchor.NORTHWEST)
            // entries have a bar left of their titles: the titles line up with the page title
            .insets(0, LEFT_MARGIN - 20, 32, LEFT_MARGIN);
    if (width == 0) {
      page.add(content, contentConstraints.fill(GridBagConstraintsFill.BOTH).build());
    } else {
      content.setPreferredSize(new Dimension(width, content.getPreferredSize().height));
      page.add(content, contentConstraints.fill(GridBagConstraintsFill.VERTICAL).build());
    }
    return page;
  }

  /** Lets the background through the plain panels; lists, fields and scroll areas keep theirs. */
  private static void makeTransparent(final Container container) {
    for (final Component component : container.getComponents()) {
      if (component instanceof Container child && !(component instanceof JScrollPane)) {
        makeTransparent(child);
      }
    }
    if (isPlainPanel(container)) {
      ((JPanel) container).setOpaque(false);
    }
  }

  private static boolean isPlainPanel(final Component component) {
    return component.getClass() == JPanel.class
        || component instanceof MainScreen
        || component instanceof SavedGamesPanel
        || component instanceof TutorialsPanel
        || component instanceof MapOfTheDayPanel;
  }

  /** A quiet gradient, lighter at the top left where the menu is, darker to the edges. */
  @Override
  protected void paintComponent(final Graphics g) {
    final Graphics2D g2 = (Graphics2D) g.create();
    final Color background = UIManager.getColor("Panel.background");
    final Color base = background == null ? Color.DARK_GRAY : background;
    final boolean dark = LookAndFeel.isColorDark(base);
    final Color lighter = dark ? mix(base, Color.WHITE, 0.06) : mix(base, Color.WHITE, 0.5);
    final Color darker = dark ? mix(base, Color.BLACK, 0.35) : mix(base, Color.BLACK, 0.06);
    g2.setPaint(new GradientPaint(0, 0, lighter, getWidth(), getHeight(), darker));
    g2.fillRect(0, 0, getWidth(), getHeight());
    final float radius = Math.max(getWidth(), getHeight()) * 0.6f;
    g2.setPaint(
        new RadialGradientPaint(
            getWidth() * 0.2f,
            getHeight() * 0.35f,
            radius,
            new float[] {0f, 1f},
            new Color[] {
              MenuEntry.withAlpha(lighter, dark ? 120 : 60), MenuEntry.withAlpha(lighter, 0)
            }));
    g2.fillRect(0, 0, getWidth(), getHeight());
    g2.dispose();
  }

  private static Color mix(final Color color, final Color other, final double share) {
    return new Color(
        (int) (color.getRed() * (1 - share) + other.getRed() * share),
        (int) (color.getGreen() * (1 - share) + other.getGreen() * share),
        (int) (color.getBlue() * (1 - share) + other.getBlue() * share));
  }

  /**
   * Shows one of the menus: {@link #MAIN}, {@link #MULTIPLAYER}, {@link #LOAD_GAME}, {@link #HELP}.
   */
  @VisibleForTesting
  void showMenu(final String menu) {
    shownMenu = menu;
    menuCards.show(menus, menu);
    if (LOAD_GAME.equals(menu)) {
      savedGames.refresh();
    } else if (MAIN.equals(menu)) {
      // after the focus left the hidden page, so it does not land on a link of the main menu
      SwingUtilities.invokeLater(multiplayer::requestFocusInWindow);
    }
  }

  @VisibleForTesting
  String getShownMenu() {
    return shownMenu;
  }

  /**
   * Tab goes through the screen in layout order; the first focus of the main menu is Multiplayer.
   */
  private final class MainMenuFocus extends LayoutFocusTraversalPolicy {
    @Serial private static final long serialVersionUID = -6083623468510873937L;

    @Override
    public Component getDefaultComponent(final Container root) {
      return MAIN.equals(shownMenu) ? multiplayer : super.getDefaultComponent(root);
    }
  }

  @Override
  public void addNotify() {
    super.addNotify();
    // the keyboard starts on the highlighted entry
    SwingUtilities.invokeLater(multiplayer::requestFocusInWindow);
  }

  @Override
  public boolean isMainMenu() {
    return true;
  }

  @Override
  public boolean canGameStart() {
    return false;
  }

  @Override
  public List<Action> getUserActions() {
    return List.of();
  }

  @Override
  public boolean isCancelButtonVisible() {
    return false;
  }

  /** "Main menu", where the link back of a page opened here, eg Settings, leads. */
  @Override
  public String getScreenTitle() {
    return getMainMenuTitle();
  }

  /** "Main menu", eg for the links back to it. */
  public static String getMainMenuTitle() {
    return I18nEngineFramework.get().getText(MAIN_MENU_KEY);
  }

  @Override
  public void cancel() {
    // nothing to do
  }

  @Override
  public Optional<ILauncher> getLauncher() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void postStartGame() {
    // nothing to do
  }

  /**
   * The main menu without scroll bars: on the left the lobby status, the logo and name, the
   * entries, the tip and the version, on the right the forum, the updates and the map of the day.
   * Before each layout it hides the parts that do not fit into its size ({@link MainMenuFit}).
   */
  private static final class MainScreen extends JPanel {
    @Serial private static final long serialVersionUID = 1637260914312478856L;

    private final transient Map<Part, JComponent> parts;
    private final JComponent entries;
    private final JComponent version;
    private final JComponent menu = new JPanel(new GridBagLayout());
    private final JComponent filler = new JPanel();
    private final JComponent rightColumn = new JPanel(new GridBagLayout());
    private final Filler columnWidth =
        new Filler(
            new Dimension(RIGHT_COLUMN_WIDTH, 0),
            new Dimension(RIGHT_COLUMN_WIDTH, 0),
            new Dimension(RIGHT_COLUMN_WIDTH, 0));

    MainScreen(
        final Map<Part, JComponent> parts, final JComponent entries, final JComponent version) {
      super(new GridBagLayout());
      this.parts = new EnumMap<>(parts);
      this.entries = entries;
      this.version = version;
      menu.add(parts.get(Part.BRAND), newRowConstraints(0, 0, 20));
      menu.add(entries, newRowConstraints(1, 0, 0));
      // what is new in the forum on top, the latest versions and map updates below it, then the
      // map of the day large; clear room between them, so they do not read as one block
      rightColumn.add(parts.get(Part.COMMUNITY), newRowConstraints(0, 0, 0));
      rightColumn.add(parts.get(Part.UPDATES), newRowConstraints(1, 40, 0));
      rightColumn.add(parts.get(Part.MAP_OF_THE_DAY), newRowConstraints(2, 40, 0));
      rightColumn.add(columnWidth, newRowConstraints(3, 0, 0));
      filler.setPreferredSize(new Dimension(COLUMN_GAP, 0));

      add(
          parts.get(Part.LOBBY_STATUS),
          new GridBagConstraintsBuilder(0, 0)
              .anchor(GridBagConstraintsAnchor.WEST)
              .insets(24, LEFT_MARGIN, 0, 0)
              .build());
      // the menu takes the free height, so it stays vertically centered
      add(
          menu,
          new GridBagConstraintsBuilder(0, 1)
              .weightY(1)
              .anchor(GridBagConstraintsAnchor.WEST)
              .insets(0, LEFT_MARGIN - 20, 0, 0)
              .build());
      add(
          filler,
          new GridBagConstraintsBuilder(1, 0).weightX(1).fill(GridBagConstraintsFill.BOTH).build());
      add(
          rightColumn,
          new GridBagConstraintsBuilder(2, 0)
              .anchor(GridBagConstraintsAnchor.EAST)
              .gridHeight(4)
              .insets(16, 0, 16, LEFT_MARGIN)
              .build());
      add(
          parts.get(Part.TIP),
          new GridBagConstraintsBuilder(0, 2)
              .fill(GridBagConstraintsFill.HORIZONTAL)
              .insets(0, LEFT_MARGIN, 10, 0)
              .build());
      add(
          version,
          new GridBagConstraintsBuilder(0, 3)
              .anchor(GridBagConstraintsAnchor.WEST)
              .insets(0, LEFT_MARGIN, 20, 0)
              .build());
    }

    /**
     * Narrows the right column to the room beside the menu, then hides what does not fit and lays
     * out the rest; a part that grows, eg the forum, too.
     */
    @Override
    public void doLayout() {
      final Insets columnRoom = ((GridBagLayout) getLayout()).getConstraints(rightColumn).insets;
      final int width =
          Math.clamp(
              getWidth() - newAlwaysShownSize().width - columnRoom.left - columnRoom.right,
              RIGHT_COLUMN_LEAST_WIDTH,
              RIGHT_COLUMN_WIDTH);
      if (columnWidth.getPreferredSize().width != width) {
        final Dimension size = new Dimension(width, 0);
        columnWidth.changeShape(size, size, size);
      }
      final Set<Part> fitting =
          MainMenuFit.fittingParts(getSize(), newAlwaysShownSize(), newPartSizes());
      parts.forEach((part, component) -> component.setVisible(fitting.contains(part)));
      rightColumn.setVisible(fitting.stream().anyMatch(part -> part.getColumn() == Column.RIGHT));
      super.doLayout();
    }

    /** The entries and the version one above the other, the menu and the room right of it. */
    private Dimension newAlwaysShownSize() {
      return new Dimension(
          outerSize(menu).width + filler.getPreferredSize().width,
          outerSize(entries).height + outerSize(version).height);
    }

    /** The parts with their room; the room around the right column counts to the forum. */
    private Map<Part, Dimension> newPartSizes() {
      final Map<Part, Dimension> sizes = new EnumMap<>(Part.class);
      final int columnWidth = outerSize(rightColumn).width;
      final int columnRoom = outerSize(rightColumn).height - rightColumn.getPreferredSize().height;
      parts.forEach(
          (part, component) -> {
            final Dimension size = outerSize(component);
            sizes.put(
                part,
                part.getColumn() == Column.LEFT
                    ? size
                    : new Dimension(
                        columnWidth, size.height + (part == Part.COMMUNITY ? columnRoom : 0)));
          });
      return sizes;
    }

    /** The preferred size with the room the layout of its parent leaves around it. */
    private static Dimension outerSize(final Component component) {
      final Dimension size = component.getPreferredSize();
      final Insets room =
          ((GridBagLayout) component.getParent().getLayout()).getConstraints(component).insets;
      return new Dimension(
          size.width + room.left + room.right, size.height + room.top + room.bottom);
    }
  }
}
