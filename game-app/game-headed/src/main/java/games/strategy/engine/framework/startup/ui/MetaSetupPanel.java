package games.strategy.engine.framework.startup.ui;

import com.google.common.annotations.VisibleForTesting;
import games.strategy.engine.framework.I18nEngineFramework;
import games.strategy.engine.framework.I18nResourceBundle;
import games.strategy.engine.framework.lookandfeel.LookAndFeel;
import games.strategy.engine.framework.map.download.DownloadMapsWindow;
import games.strategy.engine.framework.startup.launcher.ILauncher;
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
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.RadialGradientPaint;
import java.awt.Window;
import java.awt.image.BufferedImage;
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
import java.util.function.Function;
import java.util.function.IntPredicate;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import javax.swing.Action;
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
import org.triplea.swing.jpanel.GridBagConstraintsAnchor;
import org.triplea.swing.jpanel.GridBagConstraintsBuilder;
import org.triplea.swing.jpanel.GridBagConstraintsFill;

/**
 * The main menu, laid out as strategy games greet their players: on the left the classic TripleA
 * logo and name over a list of large text entries without boxes, Multiplayer in the accent color
 * with how busy the lobby is below it, a tip and the version at the menu's foot; the rest of the
 * window is the stage of the map of the day, the panels of the forum and the updates lying on its
 * lower part ({@code MainScreen}). It never scrolls: what does not fit into the window is hidden
 * ({@link MainMenuFit}), the entries always stay. Multiplayer, Load game and How to play open in
 * place of the menu, below a {@link BackBar} back to the main menu (also Escape). While this panel
 * is shown, the main screen hides its game selection and its footer.
 */
public class MetaSetupPanel extends SetupPanel {

  private static final long serialVersionUID = 3926503672972937677L;

  /** Room left of the pages, as games keep their menus off the window edge. */
  private static final int LEFT_MARGIN = 96;

  private static final int SUB_MENU_WIDTH = 640;

  /** The menu column takes about a quarter of the window, at least and at most this. */
  private static final double MENU_SHARE = 0.24;

  private static final int MENU_LEAST_WIDTH = 372;
  private static final int MENU_MOST_WIDTH = 600;

  /**
   * The map of the day needs this much height, or the whole stage goes; for its width see there.
   */
  private static final int STAGE_LEAST_HEIGHT = 280;

  /** One area of the news band, the forum or the updates, needs this width. */
  @VisibleForTesting static final int AREA_LEAST_WIDTH = 380;

  /**
   * The height of the logo beside the name; small enough that logo and entries fit together into a
   * window of 720 px, eg a laptop's 1080 px screen at 150 %.
   */
  private static final int LOGO_HEIGHT = 96;

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
  @VisibleForTesting static final String FORUM_GAMES = "forum";

  private final CardLayout menuCards = new CardLayout();
  private final JPanel menus = new JPanel(menuCards);
  private final HeadedServerSetupModel model;
  private final SavedGamesPanel savedGames;
  private final TutorialsPanel tutorials;
  private final ForumGamesPanel forumGames;
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
    // Playing against other people is what TripleA is made for: the one entry in the accent color,
    // with how busy the lobby is below it, so the numbers say what the click leads to
    multiplayer =
        MenuEntry.newHighlighted(
            bundle.getText(MENU_PREFIX + "Multiplayer"),
            bundle.getText(MENU_PREFIX + "Multiplayer.Desc"),
            new LobbyStatusPanel(bundle, sources.lobbyStats()));
    multiplayer.addActionListener(e -> showMenu(MULTIPLAYER));
    tutorials = new TutorialsPanel(bundle, newHelpLinks(bundle));
    forumGames = new ForumGamesPanel(model, ForumGamesPanel.FORUMS, ForumBrowserLogin::signIn);

    menus.add(newMainScreen(bundle, sources), MAIN);
    menus.add(
        newPage(bundle, MENU_PREFIX + "Multiplayer", newMultiplayerMenu(bundle), SUB_MENU_WIDTH),
        MULTIPLAYER);
    menus.add(newPage(bundle, MENU_PREFIX + "LoadGame", savedGames, 0), LOAD_GAME);
    menus.add(newPage(bundle, KEY_PREFIX + "UserGuideHelp.Lbl", tutorials, 0), HELP);
    menus.add(newPage(bundle, KEY_PREFIX + "PlayByForum.Lbl", forumGames, 0), FORUM_GAMES);

    setLayout(new BorderLayout());
    add(menus, BorderLayout.CENTER);
    // the window's first focus goes to Multiplayer, never to a link of the stage or news band
    setFocusTraversalPolicyProvider(true);
    setFocusTraversalPolicy(new MainMenuFocus());
    // the background behind shows through every panel of the menu
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
   * The menu on the left with a tip and the version at its foot; the map of the day as the stage,
   * the forum and the updates lying on its lower part. What does not fit into the window is hidden,
   * see {@link MainMenuFit}.
   */
  private JComponent newMainScreen(final I18nResourceBundle bundle, final MainMenuSources sources) {
    final MenuFooter footer =
        new MenuFooter(
            bundle,
            tipText(bundle),
            bundle.getText(MENU_PREFIX + "Version", ProductVersionReader.getCurrentVersion()),
            sources.latestVersion());
    return new MainScreen(
        new Brand(bundle),
        new MapOfTheDayPanel(
            bundle, sources.installedMaps(), sources.mapsToDownload(), this::playLocally),
        new CommunityPanel(bundle, sources),
        new UpdatesPanel(bundle, sources),
        newMenuEntries(bundle),
        footer);
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

  /**
   * The entries of the main menu, below the logo and name; they always stay on screen. Two groups:
   * the two ways to play, large and with a line each, and the further entries a step smaller with
   * only their names. The window's frame is the way out; Quit shows only in a window without a
   * frame, see {@code MainScreen#addNotify}.
   */
  private MenuEntries newMenuEntries(final I18nResourceBundle bundle) {
    final MenuEntry playLocally =
        MenuEntry.newEntry(
            bundle.getText(KEY_PREFIX + "StartLocalGame.Lbl"),
            bundle.getText(KEY_PREFIX + "StartLocalGame.Desc"));
    playLocally.addActionListener(e -> model.showLocal());
    final MenuEntry loadGame = MenuEntry.newSecondary(bundle.getText(MENU_PREFIX + "LoadGame"));
    loadGame.addActionListener(e -> showMenu(LOAD_GAME));
    final MenuEntry maps = MenuEntry.newSecondary(bundle.getText(MENU_PREFIX + "Maps"));
    maps.addActionListener(e -> DownloadMapsWindow.showDownloadMapsWindow());
    final MenuEntry settings =
        MenuEntry.newSecondary(bundle.getText(KEY_PREFIX + "EnginePreferences.Lbl"));
    settings.addActionListener(
        e -> ClientSetting.showSettingsWindow(JOptionPane.getFrameForComponent(this)));
    final MenuEntry help = MenuEntry.newSecondary(bundle.getText(KEY_PREFIX + "UserGuideHelp.Lbl"));
    help.addActionListener(e -> showMenu(HELP));
    return new MenuEntries(
        List.of(multiplayer, playLocally),
        List.of(loadGame, maps, settings, help),
        newQuit(bundle));
  }

  /**
   * The way out, hidden until the menu is in a window without a frame to close, as in full screen.
   * As the Quit button of the setup screens, the quit actions run first, eg leaving the lobby.
   */
  private static MenuEntry newQuit(final I18nResourceBundle bundle) {
    final MenuEntry quit = MenuEntry.newSecondary(bundle.getText(MENU_PREFIX + "Quit"));
    quit.addActionListener(e -> MainFrame.quit());
    quit.setVisible(false);
    return quit;
  }

  /**
   * Whether the window has no frame to close, as in full screen, so that the main menu offers Quit.
   */
  @VisibleForTesting
  static boolean offersQuit(final @Nullable Window window) {
    return window instanceof Frame frame && frame.isUndecorated();
  }

  /** The entries of the main menu: the ways to play, the further entries and Quit below them. */
  private record MenuEntries(List<MenuEntry> waysToPlay, List<MenuEntry> further, MenuEntry quit) {
    /** Every entry, from the top down, shown or not. */
    Stream<MenuEntry> all() {
      return Stream.of(waysToPlay.stream(), further.stream(), Stream.of(quit))
          .flatMap(Function.identity());
    }
  }

  /**
   * The classic TripleA soldier beside the name and a tagline, as a game shows its logo and name
   * side by side. The logo without the empty room around it in its file, so that it lines up with
   * the texts.
   */
  private static final class Brand extends JPanel {
    @Serial private static final long serialVersionUID = -3395412617781442193L;

    private final transient BufferedImage logoImage =
        withoutClearEdges(EngineImageLoader.loadImage("launch_screens", "triplea-logo.png"));
    private final JLabel logo = new JLabel("", SwingConstants.LEADING);
    private final JLabel name = new JLabel("TripleA");
    private final JLabel tagline;
    private final float nameSize;
    private final float taglineSize;

    Brand(final I18nResourceBundle bundle) {
      super(new BorderLayout(MenuSpacing.M, 0));
      setOpaque(false);
      nameSize = name.getFont().getSize2D() + 26;
      tagline = new JLabel(bundle.getText(MENU_PREFIX + "Tagline"));
      tagline.setForeground(LookAndFeel.getSecondaryTextColor());
      taglineSize = tagline.getFont().getSize2D() + 3;
      final JPanel names = new JPanel(new GridBagLayout());
      names.setOpaque(false);
      names.add(name, newRowConstraints(0, 0, 0));
      names.add(tagline, newRowConstraints(1, 0, 0));
      add(logo, BorderLayout.WEST);
      add(names, BorderLayout.CENTER);
      setTypeScale(1f);
    }

    /** Logo and texts the factor larger than {@link #LOGO_HEIGHT} and their usual size. */
    void setTypeScale(final float scale) {
      final int height = Math.round(LOGO_HEIGHT * scale);
      final int width = logoImage.getWidth() * height / logoImage.getHeight();
      logo.setIcon(new ImageIcon(logoImage.getScaledInstance(width, height, Image.SCALE_SMOOTH)));
      name.setFont(name.getFont().deriveFont(Font.PLAIN, nameSize * scale));
      tagline.setFont(tagline.getFont().deriveFont(taglineSize * scale));
    }
  }

  /** The image without its fully transparent rows and columns at the edges. */
  @VisibleForTesting
  static BufferedImage withoutClearEdges(final BufferedImage image) {
    final IntPredicate column =
        x -> IntStream.range(0, image.getHeight()).anyMatch(y -> hasVisiblePixel(image, x, y));
    final IntPredicate row =
        y -> IntStream.range(0, image.getWidth()).anyMatch(x -> hasVisiblePixel(image, x, y));
    final int left = IntStream.range(0, image.getWidth()).filter(column).findFirst().orElse(0);
    final int right =
        IntStream.range(0, image.getWidth())
            .map(x -> image.getWidth() - 1 - x)
            .filter(column)
            .findFirst()
            .orElse(image.getWidth() - 1);
    final int top = IntStream.range(0, image.getHeight()).filter(row).findFirst().orElse(0);
    final int bottom =
        IntStream.range(0, image.getHeight())
            .map(y -> image.getHeight() - 1 - y)
            .filter(row)
            .findFirst()
            .orElse(image.getHeight() - 1);
    return image.getSubimage(left, top, right - left + 1, bottom - top + 1);
  }

  private static boolean hasVisiblePixel(final BufferedImage image, final int x, final int y) {
    return (image.getRGB(x, y) >>> 24) != 0;
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
   * "Tip: ..." for the menu's foot; a different tip now and then, so returning players learn
   * something too.
   */
  private static String tipText(final I18nResourceBundle bundle) {
    return bundle.getText(MENU_PREFIX + "Tip") + ": " + tip(bundle, Instant.now().getEpochSecond());
  }

  /** One of the {@link #TIPS} tips, chosen by any number, eg the seconds of the clock. */
  @VisibleForTesting
  static String tip(final I18nResourceBundle bundle, final long chooser) {
    return bundle.getText(MENU_PREFIX + "Tip." + (1 + Math.floorMod(chooser, TIPS)));
  }

  /** The ways to play with others: the lobby, the local network, by email and by forum. */
  private JComponent newMultiplayerMenu(final I18nResourceBundle bundle) {
    final List<MenuEntry> entries =
        newMultiplayerEntries(bundle, model, () -> showMenu(FORUM_GAMES));
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

  /**
   * The ways to play with others; play by forum opens the player's forum games ({@link
   * ForumGamesPanel}).
   */
  @VisibleForTesting
  static List<MenuEntry> newMultiplayerEntries(
      final I18nResourceBundle bundle,
      final HeadedServerSetupModel model,
      final Runnable showForumGames) {
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
    forum.addActionListener(e -> showForumGames.run());
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
        || component instanceof TutorialsPanel;
  }

  /**
   * The window's color, and a faint glow of the accent in the middle of the menu column that fades
   * out before its edge, so the map of the day's shade meets the plain color there.
   */
  @Override
  protected void paintComponent(final Graphics g) {
    final Graphics2D g2 = (Graphics2D) g.create();
    g2.setColor(MenuColors.windowColor());
    g2.fillRect(0, 0, getWidth(), getHeight());
    final Color accent = UIManager.getColor("TripleA.highlightBackground");
    if (accent != null) {
      final int menuWidth = menuWidth(getWidth());
      g2.setPaint(
          new RadialGradientPaint(
              menuWidth * 0.42f,
              getHeight() * 0.46f,
              Math.max(1f, menuWidth * 0.42f),
              new float[] {0f, 1f},
              new Color[] {MenuColors.withAlpha(accent, 12), MenuColors.withAlpha(accent, 0)}));
      g2.fillRect(0, 0, menuWidth, getHeight());
    }
    g2.dispose();
  }

  /** The width of the menu column in a window of the width: about a quarter, within bounds. */
  private static int menuWidth(final int windowWidth) {
    return Math.clamp(Math.round(windowWidth * MENU_SHARE), MENU_LEAST_WIDTH, MENU_MOST_WIDTH);
  }

  /**
   * Shows one of the menus: {@link #MAIN}, {@link #MULTIPLAYER}, {@link #LOAD_GAME}, {@link #HELP},
   * {@link #FORUM_GAMES}.
   */
  @VisibleForTesting
  void showMenu(final String menu) {
    shownMenu = menu;
    menuCards.show(menus, menu);
    if (LOAD_GAME.equals(menu)) {
      savedGames.refresh();
    } else if (FORUM_GAMES.equals(menu)) {
      forumGames.refresh();
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
   * The main menu without scroll bars. The menu column on the left, about a quarter of the window:
   * logo and name at the top, a tip and the version at the bottom, the entries between them a
   * little above the middle, their gaps growing with the window's height. The rest is the stage:
   * the map of the day to the window's edges, and lying on its lower part the forum and the
   * updates, each a panel of its own. One gutter, growing with the window's width ({@link
   * MenuSpacing#gutter}), is the inset of menu, stage and panels. Before each layout it hides the
   * parts that do not fit ({@link MainMenuFit}).
   */
  private static final class MainScreen extends JPanel {
    @Serial private static final long serialVersionUID = 1637260914312478856L;

    /** The least room between logo, entries and the footer. */
    private static final int LEAST_GAP = MenuSpacing.M;

    /** From this size on the menu's type is {@link #LARGE_TYPE_SCALE} times larger. */
    private static final Dimension LARGE_WINDOW = new Dimension(2200, 1200);

    private static final float LARGE_TYPE_SCALE = 1.2f;

    private final Brand brand;
    private final MapOfTheDayPanel mapOfTheDay;
    private final NewsArea forum;
    private final NewsArea updates;
    private final transient Map<Part, JComponent> parts;
    private final transient MenuEntries entries;
    private final MenuFooter footer;
    private int gutter;
    private float typeScale = 1f;

    MainScreen(
        final Brand brand,
        final MapOfTheDayPanel mapOfTheDay,
        final NewsArea forum,
        final NewsArea updates,
        final MenuEntries entries,
        final MenuFooter footer) {
      super(null);
      this.brand = brand;
      this.mapOfTheDay = mapOfTheDay;
      this.forum = forum;
      this.updates = updates;
      parts =
          new EnumMap<>(
              Map.of(
                  Part.BRAND, brand,
                  Part.MAP_OF_THE_DAY, mapOfTheDay,
                  Part.COMMUNITY, forum,
                  Part.UPDATES, updates));
      this.entries = entries;
      this.footer = footer;
      add(brand);
      entries.all().forEach(this::add);
      add(footer);
      // the panels lie on the stage: added before it, they are painted after it, on top
      add(forum);
      add(updates);
      add(mapOfTheDay);
    }

    /** The panels of the news band overlap the stage, so Swing must not paint children apart. */
    @Override
    public boolean isOptimizedDrawingEnabled() {
      return false;
    }

    /**
     * Shows Quit in a window without a frame, as in full screen; decided by the window itself, as
     * the setting for full screen takes effect only at the next start.
     */
    @Override
    public void addNotify() {
      super.addNotify();
      entries.quit().setVisible(offersQuit(SwingUtilities.getWindowAncestor(this)));
    }

    /** Hides what does not fit, then places menu, stage and the panels on it. */
    @Override
    public void doLayout() {
      applyGutter(MenuSpacing.gutter(getWidth()));
      applyTypeScale(
          getWidth() >= LARGE_WINDOW.width && getHeight() >= LARGE_WINDOW.height
              ? LARGE_TYPE_SCALE
              : 1f);
      final int menuWidth =
          Math.max(menuWidth(getWidth()), entriesWidth() + gutter + gutter / 2 - MenuEntry.INDENT);
      final Set<Part> fitting =
          MainMenuFit.fittingParts(
              new Dimension(getWidth() - menuWidth, getHeight()),
              menuHeightAlways(),
              newLeastSizes());
      parts.forEach((part, component) -> component.setVisible(fitting.contains(part)));
      layOutMenu(menuWidth);
      mapOfTheDay.setBounds(menuWidth, 0, getWidth() - menuWidth, getHeight());
      layOutNews(menuWidth);
    }

    private void applyGutter(final int newGutter) {
      if (gutter != newGutter) {
        gutter = newGutter;
        mapOfTheDay.setGutter(newGutter);
      }
    }

    /** In large windows logo, name and entries a step larger, where their usual size looks lost. */
    private void applyTypeScale(final float newScale) {
      if (typeScale != newScale) {
        typeScale = newScale;
        brand.setTypeScale(newScale);
        entries.all().forEach(entry -> entry.setTypeScale(newScale));
      }
    }

    private int entriesWidth() {
      return entries.all().mapToInt(entry -> entry.getPreferredSize().width).max().orElse(0);
    }

    /**
     * The entries with their gaps as laid out and the footer, the least gaps around them, and the
     * gutters above and below.
     */
    private int menuHeightAlways() {
      return 2 * gutter
          + 2 * LEAST_GAP
          + entriesHeight(betweenWays(), beforeFurther())
          + footer.getPreferredSize().height;
    }

    /**
     * The ways to play and the further entries with the gaps between them; the further entries
     * touch, the room around their texts keeps them apart.
     */
    private int entriesHeight(final int betweenWays, final int beforeFurther) {
      return entries
              .all()
              .filter(Component::isVisible)
              .mapToInt(entry -> entry.getPreferredSize().height)
              .sum()
          + betweenWays * (entries.waysToPlay().size() - 1)
          + beforeFurther;
    }

    /** The gap between the ways to play, growing with the height: tight in 720 px, airy in 1440. */
    private int betweenWays() {
      return scaledGap(getHeight(), 0.022, 10, 34);
    }

    /** The gap before the further entries, growing with the height. */
    private int beforeFurther() {
      return scaledGap(getHeight(), 0.04, 18, 60);
    }

    /**
     * A share of the height within bounds, without the room the entries keep around their texts.
     */
    private static int scaledGap(
        final int height, final double share, final int least, final int most) {
      return Math.max(0, Math.clamp(Math.round(height * share), least, most) - 2 * MenuSpacing.S);
    }

    /**
     * The least sizes; forum and updates with the room around them on the stage: the gutter below
     * and beside them, and the gutter between them and the map's info above.
     */
    private Map<Part, Dimension> newLeastSizes() {
      return Map.of(
          Part.BRAND, new Dimension(0, brand.getPreferredSize().height),
          Part.MAP_OF_THE_DAY,
              new Dimension(
                  MapOfTheDayPanel.LEAST_WIDTH,
                  // the area's name at the top and the map's info at the bottom, both whole
                  Math.max(STAGE_LEAST_HEIGHT, mapOfTheDay.getPreferredSize().height + 2 * gutter)),
          Part.COMMUNITY,
              new Dimension(
                  AREA_LEAST_WIDTH + 2 * gutter, forum.getPreferredSize().height + 2 * gutter),
          Part.UPDATES,
              new Dimension(
                  AREA_LEAST_WIDTH + gutter / 2, updates.getPreferredSize().height + 2 * gutter));
    }

    /**
     * Logo and name at the top, the tip and the version at the bottom, the ways to play and the
     * further entries between them, a little above the middle, Quit below them when shown.
     */
    private void layOutMenu(final int menuWidth) {
      final int brandHeight = brand.isVisible() ? brand.getPreferredSize().height : 0;
      brand.setBounds(gutter, gutter, menuWidth - gutter, brandHeight);
      final int entryX = gutter - MenuEntry.INDENT;
      final int entryWidth = menuWidth - entryX - gutter / 2;
      final int footerHeight = footer.getPreferredSize().height;
      final int bottomFooterY = getHeight() - gutter - footerHeight;

      final int betweenWays = betweenWays();
      final int beforeFurther = beforeFurther();
      final int top = gutter + brandHeight;
      final int free = bottomFooterY - top - entriesHeight(betweenWays, beforeFurther);
      // the group sits on glue weighted 1 : 1.35 above and below, its optical middle
      int y = top + Math.max(LEAST_GAP, (int) (free / 2.35));
      for (final MenuEntry way : entries.waysToPlay()) {
        y = place(way, entryX, y, entryWidth) + betweenWays;
      }
      y += beforeFurther - betweenWays;
      for (final MenuEntry entry : entries.further()) {
        y = place(entry, entryX, y, entryWidth);
      }
      y = entries.quit().isVisible() ? place(entries.quit(), entryX, y, entryWidth) : y;
      // at the bottom, or below the entries in a window too low for both
      footer.setBounds(
          gutter,
          Math.max(bottomFooterY, y + LEAST_GAP),
          menuWidth - gutter - gutter / 2,
          footerHeight);
    }

    /** Places the entry at the height and returns the height below it. */
    private static int place(final MenuEntry entry, final int x, final int y, final int width) {
      final int entryHeight = entry.getPreferredSize().height;
      entry.setBounds(x, y, width, entryHeight);
      return y + entryHeight;
    }

    /**
     * Forum and updates side by side on the lower part of the stage, a gutter from its edges and
     * half a gutter apart, as high as the higher of them; the forum alone when the updates do not
     * fit. The stage keeps its map's info above them.
     */
    private void layOutNews(final int menuWidth) {
      if (!forum.isVisible()) {
        mapOfTheDay.setBandHeight(0);
        return;
      }
      final int bandHeight =
          Math.max(
              forum.getPreferredSize().height,
              updates.isVisible() ? updates.getPreferredSize().height : 0);
      final int y = getHeight() - gutter - bandHeight;
      final int left = menuWidth + gutter;
      final int width = getWidth() - menuWidth - 2 * gutter;
      final int forumWidth = updates.isVisible() ? (width - gutter / 2) / 2 : width;
      forum.setBounds(left, y, forumWidth, bandHeight);
      updates.setBounds(
          left + forumWidth + gutter / 2, y, width - forumWidth - gutter / 2, bandHeight);
      mapOfTheDay.setBandHeight(bandHeight + 2 * gutter);
    }
  }
}
