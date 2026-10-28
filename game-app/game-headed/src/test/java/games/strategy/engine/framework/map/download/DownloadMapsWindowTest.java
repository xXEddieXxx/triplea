package games.strategy.engine.framework.map.download;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

import games.strategy.engine.framework.ui.PageHost;
import games.strategy.triplea.settings.ClientSetting;
import java.awt.GraphicsEnvironment;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonatype.goodies.prefs.memory.MemoryPreferences;

/**
 * In game-headed although the window is in game-core: the window shows the icon from its assets.
 * The window needs a display, so without one, eg on a build server, the tests are skipped.
 */
final class DownloadMapsWindowTest {
  private final FakeMainWindow mainWindow = new FakeMainWindow();

  @BeforeEach
  void givenNoInstalledMaps(@TempDir final Path mapsFolder) {
    assumeThat(GraphicsEnvironment.isHeadless()).as("the maps are a window").isFalse();
    ClientSetting.setPreferences(new MemoryPreferences());
    ClientSetting.mapFolderOverride.setValue(mapsFolder);
  }

  @AfterEach
  void resetClientSettingPreferences() {
    ClientSetting.resetPreferences();
  }

  @DisplayName("Asking for the maps while their page is open keeps the page, and Done closes it")
  @Test
  void reopenedPageStays() throws InterruptedException, InvocationTargetException {
    SwingUtilities.invokeAndWait(
        () -> {
          final DownloadMapsWindow maps =
              new DownloadMapsWindow(List.of(), new DownloadMapsWindowModel(List.of()));
          maps.showAsPage(mainWindow);

          // eg a map download is asked for while the page is open
          maps.showAsPage(mainWindow);
          maps.close();
        });

    assertThat(mainWindow.shownPages).as("the open page is not shown a second time").isEqualTo(1);
    assertThat(mainWindow.pageClosed).as("Done goes back from the page").isTrue();
  }

  /** Like the main window, showing a page leaves the page shown before. */
  private static final class FakeMainWindow implements PageHost {
    private Runnable whenPageLeft = () -> {};
    private int shownPages;
    private boolean pageClosed;

    @Override
    public boolean isShowing() {
      return true;
    }

    @Override
    public void showPage(
        final String title,
        final JComponent page,
        final JComponent actions,
        final Runnable back,
        final Runnable whenLeft) {
      final Runnable leftPage = whenPageLeft;
      whenPageLeft = whenLeft;
      leftPage.run();
      shownPages++;
    }

    @Override
    public void closePage() {
      whenPageLeft = () -> {};
      pageClosed = true;
    }
  }
}
