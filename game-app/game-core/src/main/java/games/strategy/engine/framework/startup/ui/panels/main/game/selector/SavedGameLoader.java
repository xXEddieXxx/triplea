package games.strategy.engine.framework.startup.ui.panels.main.game.selector;

import static org.triplea.swing.SwingComponents.DialogWithLinksParams;
import static org.triplea.swing.SwingComponents.DialogWithLinksTypes;

import games.strategy.engine.framework.ui.background.TaskRunner;
import games.strategy.triplea.UrlConstants;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.experimental.UtilityClass;
import org.triplea.swing.SwingComponents;

/** Loads a saved game into the game selector, behind a wait dialog, and explains a failure. */
@UtilityClass
public final class SavedGameLoader {
  /** Returns whether the save was loaded; if loading failed with an error, a dialog shows it. */
  public static boolean load(final GameSelectorModel model, final Path save) {
    final AtomicBoolean loaded = new AtomicBoolean();
    TaskRunner.builder()
        .waitDialogTitle("Loading Save Game")
        .exceptionHandler(
            e ->
                SwingComponents.showDialogWithLinks(
                    DialogWithLinksParams.builder()
                        .title("Failed To Load Save Game")
                        .dialogType(DialogWithLinksTypes.ERROR)
                        .dialogText(
                            String.format(
                                "<html>Error: %s<br/><br/>"
                                    + "If this is not expected, please "
                                    + "file a <a href=%s>bug report</a><br/>"
                                    + "and attach the error message above and the "
                                    + "save game you are trying to load.",
                                e.getMessage(), UrlConstants.GITHUB_ISSUES))
                        .build()))
        .build()
        .run(() -> loaded.set(model.loadSave(save)));
    return loaded.get();
  }
}
