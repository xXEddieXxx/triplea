package games.strategy.triplea.settings;

import com.google.common.base.Preconditions;
import games.strategy.engine.framework.ui.BackBar;
import games.strategy.engine.framework.ui.MainWindowPages;
import games.strategy.engine.framework.ui.PageHost;
import games.strategy.triplea.settings.SaveFunction.SaveResult;
import java.awt.BorderLayout;
import java.awt.Dialog.ModalityType;
import java.awt.Dimension;
import java.awt.Frame;
import java.util.Optional;
import javax.annotation.Nullable;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import org.triplea.swing.SwingComponents;

/**
 * UI window with controls to update game settings and preferences, laid out by {@link
 * SettingsPage}: one section per {@code SettingType}, chosen on the left or found by the search.
 * All data needed to render the settings UI is pulled from the {@code ClientSetting} enum. Changes
 * are saved when the settings are left, by the link back in their {@link BackBar}, Escape, the back
 * button of the mouse or the close button of the dialog; if a value is invalid, it says which and
 * asks whether to leave anyway, keeping the old value.
 *
 * @see ClientSetting
 */
@SuppressWarnings("ImmutableEnumChecker") // Enum singleton pattern
public enum SettingsWindow {
  INSTANCE;

  private static final String TITLE = "Settings";

  private @Nullable JDialog dialog;
  // the main window, while the settings are one of its pages
  private @Nullable PageHost pageHost;
  private @Nullable SettingsPage page;

  public static void updateLookAndFeel() {
    Optional.ofNullable(INSTANCE.dialog).ifPresent(SwingUtilities::updateComponentTreeUI);
    Optional.ofNullable(INSTANCE.page)
        .map(SettingsPage::getComponent)
        .ifPresent(SwingUtilities::updateComponentTreeUI);
  }

  /** Disposes window or closes the page, and nulls out references. */
  public void close() {
    if (dialog != null) {
      dialog.dispose();
      dialog = null;
    }
    if (pageHost != null) {
      pageHost.closePage();
      pageHost = null;
    }
    page = null;
  }

  /**
   * Opens the settings: as a page of the main window while it is showing, so the game stays one
   * window, otherwise, eg during a game, as a dialog.
   */
  public void open(final Frame owner) {
    Preconditions.checkState(SwingUtilities.isEventDispatchThread());
    if (dialog != null && !dialog.isDisplayable()) {
      // closing its owner, eg the game window, disposed the dialog without a closing event
      close();
    }
    if (dialog != null) {
      // window is already visible, bring it to the front
      dialog.toFront();
    } else if (pageHost == null) {
      MainWindowPages.getVisibleMainWindow()
          .ifPresentOrElse(this::openAsPage, () -> openAsDialog(owner));
    }
  }

  /** The main window shows the bar above the page; its link back saves and closes. */
  private void openAsPage(final PageHost mainWindow) {
    pageHost = mainWindow;
    page = new SettingsPage();
    mainWindow.showPage(
        TITLE, page.getComponent(), newSavedNote(), this::saveAndClose, this::pageLeft);
  }

  /** Where the settings are saved, at the right end of their bar. */
  private static JComponent newSavedNote() {
    return BackBar.newNote("Changes are saved when you leave Settings");
  }

  /** Another page or a game replaced the settings: keeps what is valid, as on leaving them. */
  private void pageLeft() {
    Optional.ofNullable(page)
        .ifPresent(
            settings ->
                SaveFunction.saveSettings(settings.getSelectionComponents(), ClientSetting::flush));
    pageHost = null;
    close();
  }

  private void openAsDialog(final Frame owner) {
    dialog = new JDialog(owner, TITLE);
    // modal: the game's menus change some of these settings too, and leaving must not undo that
    dialog.setModalityType(ModalityType.APPLICATION_MODAL);
    page = new SettingsPage();
    // the same bar as on the page of the main window; its link, Escape and the mouse go back
    final JPanel content = new JPanel(new BorderLayout());
    content.add(new BackBar("Game", TITLE, this::saveAndClose, newSavedNote()), BorderLayout.NORTH);
    content.add(page.getComponent(), BorderLayout.CENTER);
    content.setPreferredSize(new Dimension(1080, 700));
    dialog.setContentPane(content);
    dialog.pack();
    dialog.setLocationRelativeTo(dialog.getOwner());
    // closing saves first, and asks before closing if a value is invalid
    dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
    SwingComponents.addWindowClosingListener(dialog, this::saveAndClose);
    dialog.setVisible(true);
  }

  /** Saves, and closes unless a value is invalid and the player wants to correct it. */
  private void saveAndClose() {
    if (page == null) {
      return;
    }
    final SaveResult saveResult =
        SaveFunction.saveSettings(page.getSelectionComponents(), ClientSetting::flush);
    if (!saveResult.hasFailures()
        || JOptionPane.showConfirmDialog(
                page.getComponent(),
                saveResult.message
                    + "\nLeave Settings anyway? Those settings keep their old value.",
                "Not all settings were saved",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE)
            == JOptionPane.YES_OPTION) {
      close();
    }
  }
}
