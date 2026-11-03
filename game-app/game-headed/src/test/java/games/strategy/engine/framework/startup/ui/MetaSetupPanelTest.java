package games.strategy.engine.framework.startup.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assumptions.assumeThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import games.strategy.engine.framework.I18nEngineFramework;
import games.strategy.engine.framework.map.file.system.loader.InstalledMapsListing;
import games.strategy.engine.framework.startup.ui.panels.main.HeadedServerSetupModel;
import games.strategy.engine.framework.startup.ui.panels.main.game.selector.GameSelectorModel;
import games.strategy.triplea.settings.ClientSetting;
import java.awt.Component;
import java.awt.Container;
import java.awt.Frame;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.sonatype.goodies.prefs.memory.MemoryPreferences;

final class MetaSetupPanelTest {
  private static final String KEY_PREFIX = "startup.SetupPanelModel.btn.";

  /** No maps, no forum, no lobby server: tests stay off the disk and the network. */
  private static final MainMenuSources OFFLINE =
      new MainMenuSources(
          () -> InstalledMapsListing.builder().build(),
          List::of,
          List::of,
          List::of,
          Optional::empty,
          Optional::empty);

  @TempDir Path savedGames;

  private final HeadedServerSetupModel model = new HeadedServerSetupModel(new GameSelectorModel());

  @BeforeEach
  void givenSavedGamesFolder() {
    ClientSetting.setPreferences(new MemoryPreferences());
    ClientSetting.saveGamesFolderPath.setValue(savedGames);
  }

  @AfterEach
  void resetClientSettingPreferences() {
    ClientSetting.resetPreferences();
  }

  @DisplayName("The main menu lists Multiplayer first, then the other ways in, no Quit")
  @Test
  void mainMenuEntries() {
    final List<String> entries = visibleEntries(new MetaSetupPanel(model, OFFLINE));

    assertThat(entries)
        .as("the window's frame is the way out, the menu offers only ways in")
        .containsExactly(
            text("startup.MetaSetupPanel.Multiplayer"),
            text(KEY_PREFIX + "StartLocalGame.Lbl"),
            text("startup.MetaSetupPanel.LoadGame"),
            text("startup.MetaSetupPanel.Maps"),
            text(KEY_PREFIX + "EnginePreferences.Lbl"),
            text(KEY_PREFIX + "UserGuideHelp.Lbl"));
  }

  @DisplayName("Quit ends the entries only in a window without a frame to close, as in full screen")
  @Test
  void onlyFramelessWindowOffersQuit() {
    assumeThat(GraphicsEnvironment.isHeadless()).as("Quit asks the window").isFalse();
    final JFrame framed = new JFrame();
    final JFrame frameless = new JFrame();
    frameless.setUndecorated(true);

    try {
      assertThat(List.of(lastEntryShownIn(framed), lastEntryShownIn(frameless)))
          .as("the frame's close button is the way out; without a frame Quit is")
          .containsExactly(
              text(KEY_PREFIX + "UserGuideHelp.Lbl"), text("startup.MetaSetupPanel.Quit"));
    } finally {
      framed.dispose();
      frameless.dispose();
    }
  }

  @DisplayName("Only a window without a frame offers Quit, also without a display")
  @Test
  void offersQuitOnlyWithoutFrame() {
    final Frame framed = mock(Frame.class);
    final Frame frameless = mock(Frame.class);
    when(frameless.isUndecorated()).thenReturn(true);

    assertThat(
            List.of(
                MetaSetupPanel.offersQuit(framed),
                MetaSetupPanel.offersQuit(frameless),
                MetaSetupPanel.offersQuit(null)))
        .as("a frame's close button is the way out; a menu in no window offers none")
        .containsExactly(false, true, false);
  }

  /** The last entry of a main menu in the window, made displayable but never shown. */
  private String lastEntryShownIn(final JFrame window) {
    final MetaSetupPanel mainMenu = new MetaSetupPanel(model, OFFLINE);
    window.add(mainMenu);
    window.addNotify();
    return visibleEntries(mainMenu).getLast();
  }

  @DisplayName("Only the two ways to play have a line below their name, no entry has a tool tip")
  @Test
  void onlyWaysToPlayAreDescribed() {
    final List<MenuEntry> entries = entriesOf(new MetaSetupPanel(model, OFFLINE)).toList();

    assertThat(entries)
        .as("Multiplayer and Play locally invite to play; the others say enough by their name")
        .extracting(
            card -> !card.getAccessibleContext().getAccessibleDescription().isEmpty(),
            card -> card.getToolTipText() == null)
        .containsExactly(
            tuple(true, true),
            tuple(true, true),
            tuple(false, true),
            tuple(false, true),
            tuple(false, true),
            tuple(false, true));
    assertThat(MetaSetupPanel.newMultiplayerEntries(I18nEngineFramework.get(), model, () -> {}))
        .as("the ways to play with others have no tool tips either")
        .allMatch(card -> card.getToolTipText() == null);
  }

  @DisplayName("Multiplayer opens the ways to play with others")
  @Test
  void multiplayerOpensItsMenu() {
    final MetaSetupPanel mainMenu = new MetaSetupPanel(model, OFFLINE);

    findEntry(mainMenu, text("startup.MetaSetupPanel.Multiplayer")).doClick();

    assertThat(visibleEntries(mainMenu))
        .as("the lobby first, then the local network, email and forum")
        .containsExactly(
            text(KEY_PREFIX + "PlayOnline.Lbl"),
            text(KEY_PREFIX + "HostNetworkGame.Lbl"),
            text(KEY_PREFIX + "ConnectToNetworkedGame.Lbl"),
            text(KEY_PREFIX + "PlayByEmail.Lbl"),
            text(KEY_PREFIX + "PlayByForum.Lbl"));
  }

  @DisplayName("Play by forum opens the player's forum games, signed out with the sign-in")
  @Test
  void playByForumOpensForumGames() {
    final MetaSetupPanel mainMenu = givenMenuShown(MetaSetupPanel.MULTIPLAYER);

    findEntry(mainMenu, text(KEY_PREFIX + "PlayByForum.Lbl")).doClick();

    assertThat(mainMenu.getShownMenu())
        .as("a page of the main menu, no longer the setup of a new game")
        .isEqualTo(MetaSetupPanel.FORUM_GAMES);
    assertThat(buttonsOf(mainMenu).map(JButton::getText))
        .as("without a kept forum session the page asks to sign in, nothing else")
        .contains("Sign in with the forum")
        .doesNotContain("+  New game");
  }

  @DisplayName("The link back of a page names the main menu and returns to it")
  @Test
  void backReturnsToMainMenu() {
    final MetaSetupPanel mainMenu = givenMenuShown(MetaSetupPanel.MULTIPLAYER);

    findBackLinkIn(mainMenu).doClick();

    assertThat(mainMenu.getShownMenu())
        .as("the page of Multiplayer is left")
        .isEqualTo(MetaSetupPanel.MAIN);
  }

  @DisplayName("Every number chooses one of the tips, so none is missing in the bundle")
  @Test
  void everyTipExists() {
    assertThat(
            LongStream.range(-1, MetaSetupPanel.TIPS + 1)
                .mapToObj(chooser -> MetaSetupPanel.tip(I18nEngineFramework.get(), chooser)))
        .as("a missing key would show the key itself instead of a tip")
        .noneMatch(tip -> tip.startsWith("startup."))
        .hasSize(MetaSetupPanel.TIPS + 2);
  }

  @DisplayName("The logo loses the empty room around it, so it lines up with the texts")
  @Test
  void logoLosesClearEdges() {
    final BufferedImage logo = new BufferedImage(10, 8, BufferedImage.TYPE_INT_ARGB);
    logo.setRGB(2, 3, 0xFF000000);
    logo.setRGB(5, 4, 0x80FFFFFF);

    final BufferedImage trimmed = MetaSetupPanel.withoutClearEdges(logo);

    assertThat(List.of(trimmed.getWidth(), trimmed.getHeight()))
        .as("columns 2 to 5 and rows 3 to 4 have visible pixels, also half transparent ones")
        .containsExactly(4, 2);
  }

  @DisplayName("A narrow window hides the stage, every entry and the menu's footer stay")
  @Test
  void narrowWindowKeepsEntries() {
    final MetaSetupPanel mainMenu = givenMenuOfSize(600, 900);

    assertThat(visibleEntries(mainMenu))
        .as("the menu entries always stay, no scroll bar is needed")
        .hasSize(6);
    assertThat(
            Stream.of(CommunityPanel.class, UpdatesPanel.class, MapOfTheDayPanel.class)
                .flatMap(type -> visibleOf(mainMenu, type)))
        .as("the map of the day and the news band do not fit next to the menu")
        .isEmpty();
    assertThat(visibleOf(mainMenu, MenuFooter.class))
        .as("tip and version stay at the foot of the menu")
        .hasSize(1);
  }

  @DisplayName("Multiplayer shows how busy the lobby is, also in a small window")
  @Test
  void multiplayerShowsLobbyStatus() {
    final MetaSetupPanel mainMenu = givenMenuOfSize(700, 600);

    assertThat(visibleOf(mainMenu, LobbyStatusPanel.class).toList())
        .as("the numbers say what the click on Multiplayer leads to, so they sit inside it")
        .singleElement()
        .satisfies(
            status ->
                assertThat(
                        ((MenuEntry) SwingUtilities.getAncestorOfClass(MenuEntry.class, status))
                            .getAccessibleContext()
                            .getAccessibleName())
                    .isEqualTo(text("startup.MetaSetupPanel.Multiplayer")));
  }

  @DisplayName("A large window shows the map of the day and both areas of the news band")
  @Test
  void largeWindowShowsStageAndBand() {
    final MetaSetupPanel mainMenu = givenMenuOfSize(2400, 1600);

    assertThat(
            Stream.of(MapOfTheDayPanel.class, CommunityPanel.class, UpdatesPanel.class)
                .flatMap(type -> visibleOf(mainMenu, type)))
        .as("room enough for the stage and the forum and updates side by side below it")
        .hasSize(3);
  }

  @DisplayName("The entries run from the top down without overlap, above the menu's footer")
  @ParameterizedTest(name = "in a window {0} px high")
  @ValueSource(ints = {900, 520})
  void entriesRunTopDownAboveFooter(final int height) {
    final MetaSetupPanel mainMenu = givenMenuOfSize(1264, height);
    final List<MenuEntry> entries = entriesOf(mainMenu).toList();
    final MenuFooter footer = visibleOf(mainMenu, MenuFooter.class).findFirst().orElseThrow();

    assertThat(IntStream.range(1, entries.size()))
        .as("every entry starts below the one before it")
        .allMatch(
            index ->
                entries.get(index).getY()
                    >= entries.get(index - 1).getY() + entries.get(index - 1).getHeight());
    assertThat(entries.getLast().getY() + entries.getLast().getHeight())
        .as("the tip and the version stay below the entries")
        .isLessThanOrEqualTo(footer.getY());
    assertThat(footer.getY() + footer.getHeight())
        .as("the footer stays inside the window")
        .isLessThanOrEqualTo(mainMenu.getHeight());
  }

  @DisplayName("Forum and updates lie on the stage as two panels, a gutter from its edges")
  @Test
  void newsPanelsLieOnStage() {
    final MetaSetupPanel mainMenu = givenMenuOfSize(1920, 1080);
    final MapOfTheDayPanel stage =
        visibleOf(mainMenu, MapOfTheDayPanel.class).findFirst().orElseThrow();
    final CommunityPanel forum =
        visibleOf(mainMenu, CommunityPanel.class).findFirst().orElseThrow();
    final UpdatesPanel updates = visibleOf(mainMenu, UpdatesPanel.class).findFirst().orElseThrow();
    final int gutter = MenuSpacing.gutter(1920);

    assertThat(stage.getHeight())
        .as("without a status bar the stage reaches the window's bottom")
        .isEqualTo(mainMenu.getHeight());
    assertThat(
            List.of(
                forum.getX() - stage.getX(),
                stage.getX() + stage.getWidth() - updates.getX() - updates.getWidth(),
                stage.getHeight() - forum.getY() - forum.getHeight(),
                updates.getX() - forum.getX() - forum.getWidth()))
        .as("a gutter left, right and below, half a gutter between the panels")
        .containsExactly(gutter, gutter, gutter, gutter / 2);
  }

  @DisplayName("In large windows the menu's type is a step larger, in smaller ones it is not")
  @Test
  void largeWindowEnlargesMenuType() {
    final List<Integer> multiplayerHeights =
        Stream.of(
                givenMenuOfSize(1600, 900),
                givenMenuOfSize(2199, 1600),
                givenMenuOfSize(2400, 1600))
            .map(mainMenu -> findEntry(mainMenu, text("startup.MetaSetupPanel.Multiplayer")))
            .map(entry -> entry.getPreferredSize().height)
            .toList();

    assertThat(multiplayerHeights.get(1))
        .as("2199 px wide is below the step, the type keeps its size")
        .isEqualTo(multiplayerHeights.get(0));
    assertThat(multiplayerHeights.get(2))
        .as("from 2200 x 1200 on the entry's type and so its height grow")
        .isGreaterThan(multiplayerHeights.get(0));
  }

  @DisplayName("A laptop window keeps the map of the day and the whole news band")
  @Test
  void laptopWindowKeepsStageAndBand() {
    // a maximized window on a 1280 px wide screen, without its frame
    final MetaSetupPanel mainMenu = givenMenuOfSize(1264, 700);

    assertThat(
            Stream.of(MapOfTheDayPanel.class, CommunityPanel.class, UpdatesPanel.class)
                .flatMap(type -> visibleOf(mainMenu, type)))
        .as("the stage shrinks before anything goes")
        .hasSize(3);
  }

  @DisplayName("The first focus of the main menu is Multiplayer, also after a page")
  @Test
  void firstFocusIsMultiplayer() {
    final MetaSetupPanel mainMenu = givenMenuShown(MetaSetupPanel.MULTIPLAYER);
    mainMenu.showMenu(MetaSetupPanel.MAIN);

    assertThat(mainMenu.getFocusTraversalPolicy().getDefaultComponent(mainMenu))
        .as("never a link of the stage or the news band, which would show an unwanted focus ring")
        .isInstanceOfSatisfying(
            MenuEntry.class,
            entry ->
                assertThat(entry.getAccessibleContext().getAccessibleName())
                    .isEqualTo(text("startup.MetaSetupPanel.Multiplayer")));
  }

  /** The main menu laid out in the size, as the window would; it has no window in tests. */
  private MetaSetupPanel givenMenuOfSize(final int width, final int height) {
    final MetaSetupPanel mainMenu = new MetaSetupPanel(model, OFFLINE);
    mainMenu.setSize(width, height);
    layOut(mainMenu);
    return mainMenu;
  }

  private static void layOut(final Container container) {
    container.doLayout();
    Arrays.stream(container.getComponents())
        .filter(Container.class::isInstance)
        .forEach(child -> layOut((Container) child));
  }

  /** The components of the type the player sees: not those in hidden panels. */
  private static <T> Stream<T> visibleOf(final Component component, final Class<T> type) {
    if (!component.isVisible()) {
      return Stream.empty();
    }
    if (type.isInstance(component)) {
      return Stream.of(type.cast(component));
    }
    return component instanceof Container container
        ? Arrays.stream(container.getComponents()).flatMap(child -> visibleOf(child, type))
        : Stream.empty();
  }

  private MetaSetupPanel givenMenuShown(final String menu) {
    final MetaSetupPanel mainMenu = new MetaSetupPanel(model, OFFLINE);
    mainMenu.showMenu(menu);
    return mainMenu;
  }

  /** The link back of the page shown; the links of hidden pages are not visible. */
  private static JButton findBackLinkIn(final Container container) {
    final String back = "Back to Main menu";
    return buttonsOf(container)
        .filter(button -> back.equals(button.getAccessibleContext().getAccessibleName()))
        .findFirst()
        .orElseThrow();
  }

  private static Stream<JButton> buttonsOf(final Component component) {
    if (!component.isVisible()) {
      return Stream.empty();
    }
    if (component instanceof JButton button) {
      return Stream.of(button);
    }
    return component instanceof Container container
        ? Arrays.stream(container.getComponents()).flatMap(MetaSetupPanelTest::buttonsOf)
        : Stream.empty();
  }

  @DisplayName("Load game and How to play open their pages in place of the main menu")
  @Test
  void loadGameAndHowToPlayOpenPages() {
    final MetaSetupPanel mainMenu = new MetaSetupPanel(model, OFFLINE);

    findEntry(mainMenu, text("startup.MetaSetupPanel.LoadGame")).doClick();
    final String afterLoadGame = mainMenu.getShownMenu();
    mainMenu.showMenu(MetaSetupPanel.MAIN);
    findEntry(mainMenu, text(KEY_PREFIX + "UserGuideHelp.Lbl")).doClick();

    assertThat(List.of(afterLoadGame, mainMenu.getShownMenu()))
        .as("pages of the main window, not dialogs or the browser")
        .containsExactly(MetaSetupPanel.LOAD_GAME, MetaSetupPanel.HELP);
  }

  private static String text(final String key) {
    return I18nEngineFramework.get().getText(key);
  }

  private static MenuEntry findEntry(final Container container, final String title) {
    return entriesOf(container)
        .filter(card -> card.getAccessibleContext().getAccessibleName().equals(title))
        .findFirst()
        .orElseThrow();
  }

  private static List<String> visibleEntries(final Container container) {
    return entriesOf(container)
        .map(card -> card.getAccessibleContext().getAccessibleName())
        .toList();
  }

  /** The entries the player sees: the cards of hidden menus are not visible. */
  private static Stream<MenuEntry> entriesOf(final Component component) {
    if (!component.isVisible()) {
      return Stream.empty();
    }
    if (component instanceof MenuEntry card) {
      return Stream.of(card);
    }
    return component instanceof Container container
        ? Arrays.stream(container.getComponents()).flatMap(MetaSetupPanelTest::entriesOf)
        : Stream.empty();
  }
}
