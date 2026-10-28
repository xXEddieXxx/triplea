package games.strategy.engine.framework.startup.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import games.strategy.engine.framework.I18nEngineFramework;
import games.strategy.engine.framework.map.file.system.loader.InstalledMapsListing;
import games.strategy.engine.framework.startup.ui.panels.main.HeadedServerSetupModel;
import games.strategy.engine.framework.startup.ui.panels.main.game.selector.GameSelectorModel;
import games.strategy.triplea.settings.ClientSetting;
import java.awt.Component;
import java.awt.Container;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import javax.swing.JButton;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
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

  @DisplayName("The main menu lists Multiplayer first, then the others and Quit last")
  @Test
  void mainMenuEntries() {
    final List<String> entries = visibleEntries(new MetaSetupPanel(model, OFFLINE));

    assertThat(entries)
        .as("Quit is the way out also in full screen, where the window has no frame to close")
        .containsExactly(
            text("startup.MetaSetupPanel.Multiplayer"),
            text(KEY_PREFIX + "StartLocalGame.Lbl"),
            text("startup.MetaSetupPanel.LoadGame"),
            text("startup.MetaSetupPanel.Maps"),
            text(KEY_PREFIX + "EnginePreferences.Lbl"),
            text(KEY_PREFIX + "UserGuideHelp.Lbl"),
            text("startup.MetaSetupPanel.Quit"));
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
            tuple(false, true),
            tuple(false, true));
    assertThat(MetaSetupPanel.newMultiplayerEntries(I18nEngineFramework.get(), model))
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

  @DisplayName("A small window hides the right column, every entry stays")
  @Test
  void smallWindowKeepsEntries() {
    final MetaSetupPanel mainMenu = givenMenuOfSize(700, 900);

    assertThat(visibleEntries(mainMenu))
        .as("the menu entries always stay, no scroll bar is needed")
        .hasSize(7);
    assertThat(
            Stream.of(CommunityPanel.class, UpdatesPanel.class, MapOfTheDayPanel.class)
                .flatMap(type -> visibleOf(mainMenu, type)))
        .as("forum, updates and the map of the day do not fit next to the menu")
        .isEmpty();
  }

  @DisplayName("A large window shows the right column next to the menu")
  @Test
  void largeWindowShowsRightColumn() {
    final MetaSetupPanel mainMenu = givenMenuOfSize(2400, 1600);

    assertThat(visibleOf(mainMenu, CommunityPanel.class))
        .as("room enough for the forum next to the menu")
        .hasSize(1);
  }

  @DisplayName("A laptop window narrows the right column instead of hiding it")
  @Test
  void laptopWindowNarrowsRightColumn() {
    // a maximized window on a 1280 px wide screen, without its frame
    final MetaSetupPanel mainMenu = givenMenuOfSize(1264, 1000);

    assertThat(visibleOf(mainMenu, CommunityPanel.class))
        .as("the column narrows below its full width, so the forum still fits next to the menu")
        .hasSize(1);
  }

  @DisplayName("The first focus of the main menu is Multiplayer, also after a page")
  @Test
  void firstFocusIsMultiplayer() {
    final MetaSetupPanel mainMenu = givenMenuShown(MetaSetupPanel.MULTIPLAYER);
    mainMenu.showMenu(MetaSetupPanel.MAIN);

    assertThat(mainMenu.getFocusTraversalPolicy().getDefaultComponent(mainMenu))
        .as("never a link of the right column, which would show a focus ring nobody asked for")
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
