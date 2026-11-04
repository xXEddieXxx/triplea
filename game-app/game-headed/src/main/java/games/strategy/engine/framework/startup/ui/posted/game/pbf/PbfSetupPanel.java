package games.strategy.engine.framework.startup.ui.posted.game.pbf;

import com.google.common.base.Preconditions;
import games.strategy.engine.data.GameState;
import games.strategy.engine.data.properties.GameProperties;
import games.strategy.engine.framework.I18nEngineFramework;
import games.strategy.engine.framework.message.PlayerListing;
import games.strategy.engine.framework.startup.launcher.ILauncher;
import games.strategy.engine.framework.startup.launcher.LocalLauncher;
import games.strategy.engine.framework.startup.mc.HeadedLaunchAction;
import games.strategy.engine.framework.startup.mc.HeadedPlayerTypes;
import games.strategy.engine.framework.startup.ui.PlayerSelectorRow;
import games.strategy.engine.framework.startup.ui.PlayerTypes;
import games.strategy.engine.framework.startup.ui.SetupPanel;
import games.strategy.engine.framework.startup.ui.panels.main.game.selector.GameSelectorModel;
import games.strategy.engine.framework.startup.ui.posted.game.DiceServerEditor;
import games.strategy.engine.framework.ui.background.BackgroundTaskRunner;
import games.strategy.engine.posted.game.pbf.IForumPoster;
import games.strategy.engine.posted.game.pbf.NodeBbGameThreads;
import games.strategy.engine.posted.game.pbf.NodeBbGameThreads.GameThread;
import games.strategy.engine.posted.game.pbf.NodeBbGameThreads.RefusedException;
import games.strategy.engine.random.PbemDiceRoller;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.GridBagLayout;
import java.awt.Window;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;
import java.util.Optional;
import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import org.triplea.swing.SwingAction;
import org.triplea.swing.jpanel.GridBagConstraintsAnchor;
import org.triplea.swing.jpanel.GridBagConstraintsBuilder;
import org.triplea.swing.jpanel.GridBagConstraintsFill;
import org.triplea.util.ExitStatus;

/**
 * A panel for setting up Play by Email/Forum. This panel listens to the GameSelectionModel so it
 * can refresh when a new game is selected or save game loaded The MainPanel also listens to this
 * panel, and we notify it through the notifyObservers()
 */
public class PbfSetupPanel extends SetupPanel implements Observer {
  private static final long serialVersionUID = 9006941131918034674L;
  private final GameSelectorModel gameSelectorModel;
  private final DiceServerEditor diceServerEditor;
  private final ForumPosterEditor forumPosterEditor;
  private final ForumPosterEditorViewModel forumPosterEditorViewModel;
  private final List<PlayerSelectorRow> playerTypes = new ArrayList<>();
  private final JPanel localPlayerPanel = new JPanel();
  private final JButton localPlayerSelection =
      new JButton(I18nEngineFramework.get().getText("startup.SetupPanel.btn.PlayerSelection.Lbl"));

  /**
   * Creates a new instance.
   *
   * @param model the GameSelectionModel, through which changes are obtained when new games are
   *     chosen, or save games loaded
   */
  public PbfSetupPanel(final GameSelectorModel model) {
    gameSelectorModel = model;
    diceServerEditor = new DiceServerEditor(this::fireListener);

    // create a view model with game properties if they are defined, otherwise create
    // one without game properties.
    forumPosterEditorViewModel =
        Optional.ofNullable(model.getGameData())
            .map(GameState::getProperties)
            .map(
                gameProperties ->
                    new ForumPosterEditorViewModel(this::fireListener, gameProperties))
            .orElseGet(() -> new ForumPosterEditorViewModel(this::fireListener));

    forumPosterEditor = new ForumPosterEditor(forumPosterEditorViewModel);

    createComponents();
    layoutComponents();
    setupListeners();
    loadAll();
  }

  private void createComponents() {
    final JScrollPane scrollPane = new JScrollPane(localPlayerPanel);
    localPlayerPanel.addHierarchyListener(
        e -> {
          final Window window = SwingUtilities.getWindowAncestor(localPlayerPanel);
          if (window instanceof Dialog) {
            final Dialog dialog = (Dialog) window;
            if (!dialog.isResizable()) {
              dialog.setResizable(true);
              dialog.setMinimumSize(new Dimension(700, 700));
            }
          }
        });
    localPlayerSelection.addActionListener(
        e ->
            JOptionPane.showMessageDialog(
                PbfSetupPanel.this,
                scrollPane,
                I18nEngineFramework.get()
                    .getText("startup.SetupPanel.PlayerSelection.Dialog.Title"),
                JOptionPane.PLAIN_MESSAGE));
  }

  private void layoutComponents() {
    removeAll();
    setLayout(new GridBagLayout());
    // Empty border works as margin
    setBorder(new EmptyBorder(10, 10, 10, 10));
    int row = 0;
    // the forum first: where the game is played
    final JTabbedPane tabbedPane = new JTabbedPane();
    add(
        tabbedPane,
        new GridBagConstraintsBuilder(0, row++)
            .gridWidth(1)
            .gridHeight(1)
            .weightX(1.0)
            .weightY(0.0)
            .anchor(GridBagConstraintsAnchor.NORTHWEST)
            .fill(GridBagConstraintsFill.HORIZONTAL)
            .insets(10, 0, 20, 0)
            .build());
    tabbedPane.addTab(
        I18nEngineFramework.get().getText("startup.PbfSetupPanel.tab.ForumPosterEditor.Lbl"),
        forumPosterEditor);

    add(
        diceServerEditor,
        new GridBagConstraintsBuilder(0, row++)
            .gridWidth(1)
            .gridHeight(1)
            .weightX(1.0)
            .weightY(0.0)
            .anchor(GridBagConstraintsAnchor.NORTHWEST)
            .fill(GridBagConstraintsFill.HORIZONTAL)
            .insets(10, 0, 20, 0)
            .build());

    // add selection of local players
    add(
        localPlayerSelection,
        new GridBagConstraintsBuilder(0, row)
            .gridWidth(1)
            .gridHeight(1)
            .weightX(1.0)
            .weightY(0.0)
            .anchor(GridBagConstraintsAnchor.NORTHEAST)
            .fill(GridBagConstraintsFill.NONE)
            .insets(10, 0, 10, 0)
            .build());
    layoutPlayerComponents(localPlayerPanel, playerTypes, gameSelectorModel.getGameData());
  }

  @Override
  public List<Action> getUserActions() {
    return List.of();
  }

  @Override
  public boolean isCancelButtonVisible() {
    return true;
  }

  @Override
  public String getScreenTitle() {
    return getWayToPlayTitle("PlayByForum");
  }

  private void setupListeners() {
    // register, so we get notified when the game model (GameData) changes
    // (e.g if the user load a save game or selects another game)
    gameSelectorModel.addObserver(this);
  }

  private void loadAll() {
    Optional.ofNullable(gameSelectorModel.getGameData())
        .ifPresent(
            data -> {
              diceServerEditor.populateFromGameProperties(data.getProperties());
              // before the properties, whose view update shows it
              forumPosterEditorViewModel.setNewThreadTitle(
                  ForumPosterEditorViewModel.newThreadTitleOf(data));
              forumPosterEditorViewModel.populateFromGameProperties(data.getProperties());
            });
  }

  /** Called when the current game changes. */
  @Override
  public void cancel() {
    gameSelectorModel.deleteObserver(this);
  }

  /** Called when the observers detect change, to see if the game is in a start-able state. */
  @Override
  public boolean canGameStart() {
    final boolean diceServerValid = diceServerEditor.areFieldsValid();
    final boolean forumValid = forumPosterEditorViewModel.areFieldsValid();
    final boolean ready = diceServerValid && forumValid && gameSelectorModel.getGameData() != null;
    // make sure at least 1 player is enabled
    return ready && playerTypes.stream().anyMatch(PlayerSelectorRow::isPlayerEnabled);
  }

  @Override
  public void postStartGame() {
    final GameState data = gameSelectorModel.getGameData();

    Preconditions.checkNotNull(
        data,
        "Game Data must not be null when starting a game, "
            + "this error indicates a programming bug that allowed for the start game button to be "
            + "enabled without first valid game data being loaded. ");
    if (diceServerEditor.areFieldsValid()) {
      diceServerEditor.applyToGameProperties(data.getProperties());
    }
    // none when the new thread was not started: the game did not start either
    final Optional<Integer> topicId = forumPosterEditorViewModel.getThreadTopicId();
    if (forumPosterEditorViewModel.areFieldsValid() && topicId.isPresent()) {
      final GameProperties properties = data.getProperties();
      properties.set(
          IForumPoster.NAME, //
          forumPosterEditorViewModel.getForumSelection());
      properties.set(IForumPoster.TOPIC_ID, topicId.get());
      properties.set(
          IForumPoster.POST_AFTER_COMBAT, forumPosterEditorViewModel.isAlsoPostAfterCombatMove());
      properties.set(
          IForumPoster.INCLUDE_SAVEGAME, forumPosterEditorViewModel.isAttachSaveGameToSummary());
      // the thread is the started game's now: the next new game here starts or takes its own
      forumPosterEditorViewModel.useExistingThread(String.valueOf(topicId.get()));
    }
  }

  /**
   * Is called in response to the GameSelectionModel being updated. It means the we have to reload
   * the form
   *
   * @param o always null
   * @param arg always null
   */
  @Override
  public void update(final Observable o, final Object arg) {
    SwingAction.invokeNowOrLater(
        () -> {
          loadAll();
          layoutComponents();
        });
  }

  /** Called when the user hits play. */
  @Override
  public Optional<ILauncher> getLauncher() {
    Preconditions.checkNotNull(
        gameSelectorModel.getGameData(),
        "Game Data must not be null when launching a game, "
            + "this error indicates a programming bug that allowed for the start game button to be "
            + "enabled without first valid game data being loaded. ");

    if (forumPosterEditorViewModel.isNewThread() && !startThread()) {
      // stays on this screen, which tells what to do
      return Optional.empty();
    }
    forumPosterEditor.requestToken();
    if (forumPosterEditor.shouldRevokeTokenOnShutdown()) {
      ExitStatus.addExitAction(forumPosterEditor::revokeToken);
    }

    final PbemDiceRoller randomSource = new PbemDiceRoller(diceServerEditor.newDiceServer());
    final Map<String, PlayerTypes.Type> playerTypes = new HashMap<>();
    final Map<String, Boolean> playersEnabled = new HashMap<>();
    for (final PlayerSelectorRow player : this.playerTypes) {
      playerTypes.put(player.getPlayerName(), player.getPlayerType());
      playersEnabled.put(player.getPlayerName(), player.isPlayerEnabled());
    }
    // we don't need the playerToNode list, the
    // disable-able players, or the alliances
    // list, for a local game
    final PlayerListing pl =
        new PlayerListing(
            null,
            playersEnabled,
            playerTypes,
            gameSelectorModel.getGameName(),
            gameSelectorModel.getGameRound(),
            null,
            null);
    return Optional.of(
        new LocalLauncher(
            gameSelectorModel,
            randomSource,
            pl,
            this,
            new HeadedLaunchAction(this),
            new PlayerTypes(HeadedPlayerTypes.getPlayerTypes())));
  }

  /**
   * Starts the game's thread in the forum, a wait window meanwhile; its number is the game's from
   * now on. Tells the player why there is none, eg the forum holds it back for a moderator.
   *
   * @return whether the game has its thread now
   */
  private boolean startThread() {
    final Optional<NodeBbGameThreads> session = forumPosterEditorViewModel.newSessionClient();
    if (session.isEmpty()) {
      // eg the Play by forum page found meanwhile that the sign-in ran out
      JOptionPane.showMessageDialog(
          this,
          "You are no longer signed in. Sign in again on Multiplayer > Play by forum.",
          "No thread started",
          JOptionPane.WARNING_MESSAGE);
      return false;
    }
    final NodeBbGameThreads forum = session.get();
    final String title = forumPosterEditorViewModel.getNewThreadTitle().strip();
    final String firstPost =
        ForumPosterEditorViewModel.firstPostOf(gameSelectorModel.getGameData());
    try {
      final Optional<GameThread> thread =
          BackgroundTaskRunner.runInBackgroundAndReturn(
              "Starting the game's thread in the forum...",
              () -> forum.startThread(title, firstPost),
              null,
              IOException.class);
      if (thread.isEmpty()) {
        // the thread has no number until it shows: the game would post nowhere
        forumPosterEditorViewModel.useExistingThread("");
        JOptionPane.showMessageDialog(
            this,
            "<html><p style='width: 400px;'>The forum holds the new thread back until a moderator"
                + " approved it, as it does for new members. The game has not started.<br/><br/>"
                + "Once the thread shows in the forum, paste its address into \"Thread address or"
                + " number\" and start the game again.</p></html>",
            "The thread waits for a moderator",
            JOptionPane.INFORMATION_MESSAGE);
        return false;
      }
      forumPosterEditorViewModel.useExistingThread(String.valueOf(thread.get().topicId()));
      return true;
    } catch (final RefusedException e) {
      // the forum said no: no thread, and starting again starts one when the cause is gone
      JOptionPane.showMessageDialog(
          this,
          "The forum did not start the game's thread: "
              + e.getMessage()
              + "\nIf your sign-in ran out, sign in again on Multiplayer > Play by forum.",
          "No thread started",
          JOptionPane.ERROR_MESSAGE);
      return false;
    } catch (final IOException e) {
      maybeStarted(
          "The forum did not answer as expected: "
              + e.getMessage()
              + "\nIf your sign-in ran out, sign in again on Multiplayer > Play by forum.");
      return false;
    } catch (final InterruptedException expected) {
      // the wait window's Cancel, on the EDT: its interrupt is no news for the EDT
      maybeStarted("Starting the thread was cancelled.");
      return false;
    }
  }

  /**
   * The forum may have started the thread all the same, eg its answer came too late: so no second
   * one, the player looks first and then takes it or starts a new one on purpose.
   */
  private void maybeStarted(final String what) {
    forumPosterEditorViewModel.useExistingThread("");
    JOptionPane.showMessageDialog(
        this,
        what
            + "\n\nThe forum may have started the thread anyway. Look for it under My games or in"
            + " the forum: if it is there, paste its address into \"Thread address or number\";"
            + " if not, choose \"Start a new thread\" and start the game again.",
        "No thread started",
        JOptionPane.WARNING_MESSAGE);
  }
}
