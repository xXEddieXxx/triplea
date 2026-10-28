package games.strategy.engine.framework.map.download;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.icons.FlatSearchIcon;
import com.google.common.annotations.VisibleForTesting;
import games.strategy.engine.framework.lookandfeel.LookAndFeel;
import games.strategy.engine.framework.lookandfeel.LookAndFeelSwingFrameListener;
import games.strategy.engine.framework.map.download.MapPreviews.Caches;
import games.strategy.engine.framework.map.download.MapRow.Actions;
import games.strategy.engine.framework.map.file.system.loader.InstalledMapsListing;
import games.strategy.engine.framework.map.listing.MapListingFetcher;
import games.strategy.engine.framework.ui.BackBar;
import games.strategy.engine.framework.ui.MainWindowPages;
import games.strategy.engine.framework.ui.PageHost;
import games.strategy.engine.framework.ui.background.BackgroundTaskRunner;
import games.strategy.triplea.EngineImageLoader;
import games.strategy.triplea.settings.ClientSetting;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.Serial;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.PlainDocument;
import lombok.extern.slf4j.Slf4j;
import org.triplea.http.client.lobby.maps.listing.MapDownloadItem;
import org.triplea.java.Interruptibles;
import org.triplea.swing.JButtonBuilder;
import org.triplea.swing.JLabelBuilder;
import org.triplea.swing.SwingComponents;
import org.triplea.swing.jpanel.JPanelBuilder;
import tools.map.making.ui.MapCreator;

/// Window that allows for map downloads and removal: a `BackBar` with the title, then the tabs
/// "Installed" (with a badge counting the maps that have updates) and "Get more maps". Each tab
/// has the search and the view switch (list, list with pictures, tiles; kept in a setting) above
/// its maps and the details of the selected map on the right. Downloads show their progress on
/// their maps and go on when the window closes.
@Slf4j
public class DownloadMapsWindow extends JFrame {
  @Serial private static final long serialVersionUID = -1542210716764178580L;
  private static final String TITLE = "Maps";
  private static final int WINDOW_WIDTH = 1200;
  private static final int WINDOW_HEIGHT = 760;
  private static final int SEARCH_HEIGHT = 40;
  private static final String INSTALLED_TAB = "Installed";
  private static final String GET_MORE_TAB = "Get more maps";
  private static final SingletonManager SINGLETON_MANAGER = new SingletonManager();

  // map previews, downloaded once per window and shared by its lists
  private final transient Caches previews = Caches.newCaches();
  // the searched text, shared by the search fields of both tabs
  private final Document searchText = new PlainDocument();
  // the view switches of both tabs, which show the same view
  private final List<MapViewSwitch> viewSwitches = new ArrayList<>();

  private final transient DownloadMapsWindowModel downloadMapsWindowModel;
  // the tab of each map, fixed while the window is open
  private final transient TabMembership tabs;
  private final transient MapList installedMaps;
  private final transient MapList mapsToGet;
  private final UpdatesBadge updatesBadge = new UpdatesBadge();
  // the main window shows its own bar above a page, so the page hides this one
  private final JComponent header = new BackBar("Game", TITLE, this::close);
  // shows the progress of a download in the row of its map
  private final transient DownloadListener rowProgress = new RowProgress();

  // what the window shows; while the main window is on screen, it shows this as a page instead
  private final JComponent content;
  private transient @Nullable PageHost pageHost;

  @VisibleForTesting
  DownloadMapsWindow(
      final Collection<String> pendingDownloadMapNames,
      final DownloadMapsWindowModel downloadMapsWindowModel) {
    super(TITLE);
    content = (JComponent) getContentPane();
    this.downloadMapsWindowModel = downloadMapsWindowModel;
    getMapStore().addMapStatusListener((oldStatus, newStatus) -> refreshMaps());

    setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    setSize(WINDOW_WIDTH, WINDOW_HEIGHT);
    setLocationRelativeTo(null);
    setMinimumSize(new Dimension(200, 200));

    setIconImage(EngineImageLoader.loadFrameIcon());

    tabs = new TabMembership(getMapStore().getAll(), this::isInstalled);
    final Actions actions = new Actions(this::install, this::cancel, this::remove);
    final MapBrowserView view = ClientSetting.mapBrowserView.getValueOrThrow();
    installedMaps = new MapList(this::getInstalledMaps, previews, actions, newToolbar(view), view);
    mapsToGet = new MapList(this::getMapsToGet, previews, actions, newToolbar(view), view);
    listenToSearch();
    DownloadCoordinator.instance.addDownloadListener(rowProgress);
    // downloads of an earlier maps window go on; their maps show that they download
    getMapStore()
        .updateStatus(
            getMapStore().getMapsByName(DownloadMapsWindow::getRunningDownloadNames),
            ManagedMapStatus.DOWNLOADING);

    final List<ManagedMap> pendingDownloads = new ArrayList<>();
    final Collection<String> unknownMapNames = new ArrayList<>();
    for (final String mapName : pendingDownloadMapNames) {
      findMapByName(mapName)
          .ifPresentOrElse(pendingDownloads::add, () -> unknownMapNames.add(mapName));
    }
    if (!pendingDownloads.isEmpty()) {
      install(pendingDownloads);
    }
    if (!unknownMapNames.isEmpty()) {
      SwingComponents.newMessageDialog(this, formatIgnoredPendingMapsMessage(unknownMapNames));
    }
    updateBadge();

    add(header, BorderLayout.NORTH);
    add(newTabs(), BorderLayout.CENTER);
    add(newFooter(), BorderLayout.SOUTH);
  }

  private ManagedMapStore getMapStore() {
    return downloadMapsWindowModel.getMapStore();
  }

  /** Shows the maps again, eg after one was installed, and the number of updates. */
  private void refreshMaps() {
    installedMaps.refresh();
    mapsToGet.refresh();
    updateBadge();
  }

  /** The maps that download, not those whose download failed, was cancelled or is done. */
  private static Collection<String> getRunningDownloadNames() {
    return DownloadCoordinator.instance.getDownloads().stream()
        .filter(DownloadFile::isRunning)
        .map(download -> download.getDownload().getMapName())
        .toList();
  }

  private void updateBadge() {
    updatesBadge.showUpdates(getMapStore().getCountByStatus(ManagedMapStatus.UPDATE_AVAILABLE));
  }

  private Optional<ManagedMap> findMapByName(final String mapName) {
    final Optional<ManagedMap> mapByName = getMapStore().getMapByName(mapName);
    if (mapByName.isPresent()) {
      return mapByName;
    }
    final String normalizedName = InstalledMapsListing.normalizeName(mapName);
    return getMapStore().getMapByName(normalizedName);
  }

  private static String formatIgnoredPendingMapsMessage(final Collection<String> unknownMapNames) {
    final StringBuilder sb = new StringBuilder();
    sb.append("<html>");
    sb.append("Some maps were not downloaded.<br>");

    if (!unknownMapNames.isEmpty()) {
      sb.append("<br>");
      sb.append("Could not find the following map(s):<br>");
      sb.append("<ul>");
      for (final String mapName : unknownMapNames) {
        sb.append("<li>").append(mapName).append("</li>");
      }
      sb.append("</ul>");
    }

    sb.append("</html>");
    return sb.toString();
  }

  /**
   * Downloads the maps, eg a new one or the newest version of an installed one, in the background.
   */
  private void install(final Collection<ManagedMap> maps) {
    getMapStore().updateStatus(List.copyOf(maps), ManagedMapStatus.DOWNLOADING);
    maps.stream().map(ManagedMap::getMapDownloadItem).forEach(DownloadCoordinator.instance::accept);
  }

  /** Stops the map's download; the map is again to get, or installed in its old version. */
  private void cancel(final ManagedMap map) {
    DownloadCoordinator.instance.cancel(map.getMapDownloadItem());
    getMapStore().updateStatus(List.of(map), statusWithoutDownload(map));
  }

  private ManagedMapStatus statusWithoutDownload(final ManagedMap map) {
    if (downloadMapsWindowModel.getInstallLocation(map).isEmpty()) {
      return ManagedMapStatus.AVAILABLE;
    }
    return Optional.ofNullable(map.getInstalledMap())
            .filter(installed -> installed.isOutOfDate(map.getMapDownloadItem()))
            .isPresent()
        ? ManagedMapStatus.UPDATE_AVAILABLE
        : ManagedMapStatus.INSTALLED;
  }

  private void remove(final ManagedMap map) {
    // deleting the map marks it available; it stays in its tab, offering Install, until reopen
    FileSystemAccessStrategy.remove(
        content, downloadMapsWindowModel::delete, List.of(map), removed -> {});
  }

  private List<ManagedMap> getInstalledMaps() {
    return tabs.installed(getMapStore().getAll());
  }

  private List<ManagedMap> getMapsToGet() {
    return tabs.toGet(getMapStore().getAll());
  }

  private boolean isInstalled(final ManagedMap map) {
    return switch (map.getMapStatus()) {
      case INSTALLED, UPDATE_AVAILABLE, REMOVING -> true;
      // an update replaces a map that is installed; a new map is not installed until it is done
      case DOWNLOADING -> downloadMapsWindowModel.getInstallLocation(map).isPresent();
      case AVAILABLE, ERROR -> false;
      default -> throw new IllegalStateException("Unknown map status: " + map.getMapStatus());
    };
  }

  /** The search filling the width above the maps of a tab, the view switch on its right. */
  private JPanel newToolbar(final MapBrowserView view) {
    final MapViewSwitch viewSwitch = new MapViewSwitch(view, this::showView);
    viewSwitches.add(viewSwitch);
    return new JPanelBuilder()
        .border(BorderFactory.createEmptyBorder(12, 20, 8, 20))
        .borderLayout()
        .addCenter(newSearchField())
        .addEast(viewSwitch)
        .build();
  }

  private JTextField newSearchField() {
    final JTextField searchField = new JTextField(searchText, null, 0);
    searchField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Search maps");
    searchField.putClientProperty(FlatClientProperties.TEXT_FIELD_SHOW_CLEAR_BUTTON, true);
    searchField.putClientProperty(
        FlatClientProperties.TEXT_FIELD_LEADING_ICON, new FlatSearchIcon());
    searchField.setToolTipText("Search maps by name or category");
    searchField.setPreferredSize(new Dimension(0, SEARCH_HEIGHT));
    SwingUtilities.invokeLater(searchField::requestFocusInWindow);
    return searchField;
  }

  /** Shows the maps in the view in both tabs and keeps it for the next time. */
  private void showView(final MapBrowserView view) {
    ClientSetting.mapBrowserView.setValueAndFlush(view);
    viewSwitches.forEach(viewSwitch -> viewSwitch.showChosen(view));
    installedMaps.showView(view);
    mapsToGet.showView(view);
  }

  /** Searching the maps is quick, so it follows every keystroke in either tab. */
  private void listenToSearch() {
    searchText.addDocumentListener(
        new DocumentListener() {
          @Override
          public void insertUpdate(final DocumentEvent e) {
            search();
          }

          @Override
          public void removeUpdate(final DocumentEvent e) {
            search();
          }

          @Override
          public void changedUpdate(final DocumentEvent e) {
            search();
          }
        });
  }

  private void search() {
    try {
      final String text = searchText.getText(0, searchText.getLength());
      installedMaps.search(text);
      mapsToGet.search(text);
    } catch (final BadLocationException e) {
      throw new IllegalStateException("The whole text is always there", e);
    }
  }

  /**
   * The installed maps come first if some of them have updates, so the player sees them; else the
   * maps to get.
   */
  private JTabbedPane newTabs() {
    final JTabbedPane tabs = new JTabbedPane();
    tabs.putClientProperty(
        FlatClientProperties.TABBED_PANE_TAB_TYPE,
        FlatClientProperties.TABBED_PANE_TAB_TYPE_UNDERLINED);
    tabs.putClientProperty(FlatClientProperties.TABBED_PANE_TAB_HEIGHT, 44);
    tabs.putClientProperty(FlatClientProperties.STYLE, "tabAreaInsets: 0,20,0,20");
    if (getMapStore().hasAnyMap(ManagedMapStatus.UPDATE_AVAILABLE)) {
      addInstalledTab(tabs);
      tabs.addTab(GET_MORE_TAB, mapsToGet.getSwingComponent());
    } else {
      tabs.addTab(GET_MORE_TAB, mapsToGet.getSwingComponent());
      addInstalledTab(tabs);
    }
    return tabs;
  }

  private void addInstalledTab(final JTabbedPane tabs) {
    tabs.addTab(INSTALLED_TAB, installedMaps.getSwingComponent());
    final JPanel tabTitle = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
    tabTitle.setOpaque(false);
    tabTitle.add(new JLabel(INSTALLED_TAB));
    tabTitle.add(updatesBadge);
    tabs.setTabComponentAt(tabs.indexOfComponent(installedMaps.getSwingComponent()), tabTitle);
  }

  private JPanel newFooter() {
    // the map creator lives here, with the maps, instead of in the main menu
    final JButton mapCreator =
        new JButtonBuilder()
            .title("Open the map creator")
            .actionListener(MapCreator::openMapCreatorWindow)
            .build();
    LookAndFeel.setBorderlessButtonStyle(mapCreator);
    mapCreator.setForeground(LookAndFeel.getAccentColor());
    final JPanel makeYourOwn = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    makeYourOwn.add(
        new JLabelBuilder("Want to make your own?")
            .foregroundColor(LookAndFeel.getSecondaryTextColor())
            .build());
    makeYourOwn.add(mapCreator);
    return new JPanelBuilder()
        .border(LookAndFeel.newFooterBorder())
        .borderLayout()
        .addWest(makeYourOwn)
        .addEast(
            new JButtonBuilder()
                .title("Done")
                .toolTip("Closes the maps. Downloads that already started go on.")
                .actionListener(this::close)
                .build())
        .build();
  }

  @VisibleForTesting
  void close() {
    setVisible(false);
    dispose();
    if (pageHost != null) {
      pageHost.closePage();
      forgetPage();
    }
  }

  @Override
  public void dispose() {
    DownloadCoordinator.instance.removeDownloadListener(rowProgress);
    super.dispose();
  }

  /** Shows the maps as a page of the main window, until Done; the window stays hidden. */
  @VisibleForTesting
  void showAsPage(final PageHost mainWindow) {
    if (pageHost != null) {
      // the page is shown already, eg a map download was asked for while it is open
      return;
    }
    pageHost = mainWindow;
    header.setVisible(false);
    setContentPane(new JPanel());
    mainWindow.showPage(
        TITLE,
        content,
        this::close,
        () -> {
          dispose();
          forgetPage();
        });
  }

  /** A window that was only a page was never on screen and sends no closed event; forget it. */
  private void forgetPage() {
    pageHost = null;
    SINGLETON_MANAGER.forget(this);
  }

  /// Shows the Download Maps window.
  /// @throws IllegalStateException If this method is not called from the EDT.
  public static void showDownloadMapsWindow() {
    checkState(SwingUtilities.isEventDispatchThread());

    showDownloadMapsWindowAndDownload(List.of());
  }

  /// Shows the Download Maps window and immediately begins downloading the specified map in
  /// background.
  /// <p>The user will be notified if the specified map is unknown.
  /// @param mapName The name of the map to download.
  /// @throws IllegalStateException If this method is not called from the EDT.
  public static void showDownloadMapsWindowAndDownload(final String mapName) {
    checkState(SwingUtilities.isEventDispatchThread());
    checkNotNull(mapName);

    showDownloadMapsWindowAndDownload(List.of(mapName));
  }

  /// Shows the Download Maps window and immediately begins downloading the specified maps in
  /// background.
  /// <p>The user will be notified if any of the specified maps are unknown.
  /// @param mapNamesToDownload The collection containing the names of the maps to download
  /// @throws IllegalStateException If this method is not called from the EDT.
  public static void showDownloadMapsWindowAndDownload(
      final Collection<String> mapNamesToDownload) {
    if (!SwingUtilities.isEventDispatchThread()) {
      SwingUtilities.invokeLater(() -> showDownloadMapsWindowAndDownload(mapNamesToDownload));
      return;
    }
    checkNotNull(mapNamesToDownload);
    SINGLETON_MANAGER.showAndDownload(mapNamesToDownload);
  }

  /** Passes the progress of downloads to the rows of their maps. */
  private final class RowProgress implements DownloadListener {
    @Override
    public void downloadUpdated(final MapDownloadItem download, final long bytesReceived) {
      SwingUtilities.invokeLater(
          () -> {
            installedMaps.showProgress(download, bytesReceived);
            mapsToGet.showProgress(download, bytesReceived);
          });
    }

    @Override
    public void downloadComplete(final MapDownloadItem download) {
      // the map store marks the map installed, which shows the rows again
    }

    @Override
    public void downloadFailed(final MapDownloadItem download) {
      // the map store marks the map failed, which shows the rows again
    }
  }

  /// Ensures the Download Maps windows is instantiated only once
  private static final class SingletonManager {
    private enum State {
      UNINITIALIZED,
      INITIALIZING,
      INITIALIZED
    }

    private State state;
    private DownloadMapsWindow window;

    SingletonManager() {
      uninitialize();
    }

    private void uninitialize() {
      assert SwingUtilities.isEventDispatchThread();

      state = State.UNINITIALIZED;
      window = null;
    }

    /** Forgets the window once it closed, unless a newer one has taken its place already. */
    private void forget(final DownloadMapsWindow closedWindow) {
      if (window == closedWindow) {
        uninitialize();
      }
    }

    private static void logMapDownloadRequestIgnored(final Collection<String> mapNames) {
      if (!mapNames.isEmpty()) {
        log.info(
            "ignoring request to download maps because window initialization has already started");
      }
    }

    void showAndDownload(final Collection<String> mapNamesToDownload) {
      assert SwingUtilities.isEventDispatchThread();

      switch (state) {
        case UNINITIALIZED:
          initialize(mapNamesToDownload);
          break;

        case INITIALIZING:
          logMapDownloadRequestIgnored(mapNamesToDownload);
          // do nothing; window will be shown when initialization is complete
          break;

        case INITIALIZED:
          logMapDownloadRequestIgnored(mapNamesToDownload);
          show();
          break;

        default:
          throw new AssertionError("unknown state");
      }
    }

    private void initialize(final Collection<String> mapNamesToDownload) {
      assert SwingUtilities.isEventDispatchThread();
      assert state == State.UNINITIALIZED;

      Interruptibles.awaitResult(SingletonManager::getMapDownloadListInBackground)
          .result
          .ifPresent(
              downloadMapsWindowModel -> {
                state = State.INITIALIZING;
                createAndShow(mapNamesToDownload, downloadMapsWindowModel);
              });
    }

    private static DownloadMapsWindowModel getMapDownloadListInBackground()
        throws InterruptedException {
      return BackgroundTaskRunner.runInBackgroundAndReturn(
          "Downloading list of available maps...",
          () -> new DownloadMapsWindowModel(MapListingFetcher.getMapDownloadList()));
    }

    private void createAndShow(
        final Collection<String> mapNamesToDownload,
        final DownloadMapsWindowModel downloadMapsWindowModel) {
      assert SwingUtilities.isEventDispatchThread();
      assert state == State.INITIALIZING;
      assert window == null;

      final DownloadMapsWindow newWindow =
          new DownloadMapsWindow(mapNamesToDownload, downloadMapsWindowModel);
      window = newWindow;
      SwingComponents.addWindowClosedListener(newWindow, () -> forget(newWindow));
      // as a page, the main window follows theme changes; the listener only goes on a closed event
      newWindow.addWindowListener(
          new WindowAdapter() {
            @Override
            public void windowOpened(final WindowEvent e) {
              LookAndFeelSwingFrameListener.register(newWindow);
            }
          });
      state = State.INITIALIZED;

      show();
    }

    /**
     * Shows the maps in the main window while it is on screen, so the game stays one window;
     * otherwise, eg during a game, or if the maps window is open already, in their window.
     */
    private void show() {
      assert SwingUtilities.isEventDispatchThread();
      assert state == State.INITIALIZED;
      assert window != null;

      final Optional<PageHost> mainWindow =
          window.isVisible() ? Optional.empty() : MainWindowPages.getVisibleMainWindow();
      if (mainWindow.isPresent()) {
        window.showAsPage(mainWindow.get());
      } else {
        window.setVisible(true);
        window.requestFocus();
        window.toFront();
      }
    }
  }
}
