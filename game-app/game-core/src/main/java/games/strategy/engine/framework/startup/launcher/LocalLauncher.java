package games.strategy.engine.framework.startup.launcher;

import com.google.common.annotations.VisibleForTesting;
import games.strategy.engine.data.GameData;
import games.strategy.engine.framework.ServerGame;
import games.strategy.engine.framework.message.PlayerListing;
import games.strategy.engine.framework.startup.launcher.local.PlayerCountrySelection;
import games.strategy.engine.framework.startup.mc.GameSelector;
import games.strategy.engine.framework.startup.ui.PlayerTypes;
import games.strategy.engine.framework.startup.ui.panels.main.game.selector.GameSelectorModel;
import games.strategy.engine.player.Player;
import games.strategy.engine.random.IRandomSource;
import games.strategy.engine.random.PlainRandomSource;
import games.strategy.net.LocalNoOpMessenger;
import games.strategy.net.Messengers;
import games.strategy.net.websocket.ClientNetworkBridge;
import java.awt.Component;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.triplea.java.Interruptibles;
import org.triplea.java.ThreadRunner;
import org.triplea.swing.SwingComponents;

/** Implementation of {@link ILauncher} for a headed local or network client game. */
@Slf4j
public class LocalLauncher implements ILauncher {
  private final GameData gameData;
  private final GameSelector gameSelector;
  private final IRandomSource randomSource;
  private final PlayerListing playerListing;
  private final Component parent;
  private final LaunchAction launchAction;
  private final PlayerTypes playerTypes;

  public LocalLauncher(
      final GameSelector gameSelector,
      final IRandomSource randomSource,
      final PlayerListing playerListing,
      final Component parent,
      final LaunchAction launchAction,
      final PlayerTypes playerTypes) {
    this.randomSource = randomSource;
    this.playerListing = playerListing;
    this.gameSelector = gameSelector;
    this.gameData = gameSelector.getGameData();
    this.parent = parent;
    this.launchAction = launchAction;
    this.playerTypes = playerTypes;
  }

  @Override
  public void launch() {
    final Optional<ServerGame> result = loadGame();
    ThreadRunner.runInNewThread(() -> launchInternal(result.orElse(null)));
  }

  private Optional<ServerGame> loadGame() {
    try {
      playerListing.doPreGameStartDataModifications(gameData);
      final Messengers messengers = new Messengers(new LocalNoOpMessenger());
      final Set<Player> gamePlayers =
          gameData.getGameLoader().newPlayers(playerListing.getLocalPlayerTypeMap(playerTypes));
      final ServerGame game =
          new ServerGame(
              gameData,
              gamePlayers,
              new HashMap<>(),
              messengers,
              ClientNetworkBridge.NO_OP_SENDER,
              launchAction);
      game.setRandomSource(randomSource);
      gameData.getGameLoader().startGame(game, gamePlayers, launchAction, null);
      return Optional.of(game);
    } catch (final MapNotFoundException e) {
      // The throwing method of MapNotFoundException notifies and prompts user to download the map.
      return Optional.empty();
    } catch (final Exception ex) {
      log.error("Failed to start game", ex);
      return Optional.empty();
    }
  }

  private void launchInternal(@Nullable final ServerGame game) {
    try {
      if (game != null) {
        game.startGame();
      }
    } finally {
      showSetupAgain(gameSelector, parent);
    }
  }

  /** After a game, played or watched: the selected game back to the default, its window shown. */
  private static void showSetupAgain(final GameSelector gameSelector, final Component parent) {
    // not on the swing thread, and it notifies setupPanel observers; the pause works around the
    // zip stream being closed while the default game is parsed, maybe as map resources unload
    Interruptibles.sleep(100);
    gameSelector.onGameEnded();
    SwingComponents.setFrameFromComponentVisible(parent);
  }

  /**
   * A launcher that opens the selected game to look at only: its map, units and history, with no
   * player and no step running, so nothing is played, saved or posted on its own. With no player of
   * its own the game window shows the history, as to a player waiting for another's turn. Once the
   * window is left, the parent shows again.
   */
  public static ILauncher newViewer(
      final GameSelector gameSelector, final Component parent, final LaunchAction launchAction) {
    // the game selected now: a download of its map before the launch takes a while, and the
    // selection may change meanwhile, eg to the default game after another game ended
    final GameData selected = gameSelector.getGameData();
    return () -> {
      final Optional<ServerGame> viewed = openToView(selected, launchAction);
      ThreadRunner.runInNewThread(
          () -> {
            try {
              viewed.ifPresent(ServerGame::awaitGameOver);
            } finally {
              showSetupAgain(gameSelector, parent);
            }
          });
    };
  }

  @VisibleForTesting
  static Optional<ServerGame> openToView(final GameData gameData, final LaunchAction launchAction) {
    try {
      return showToView(
          new ServerGame(
              gameData,
              Set.of(),
              new HashMap<>(),
              new Messengers(new LocalNoOpMessenger()),
              ClientNetworkBridge.NO_OP_SENDER,
              launchAction),
          launchAction);
    } catch (final RuntimeException | LinkageError e) {
      // eg a class of the game window missing: the launcher shows the setup again
      log.error("Failed to open the game", e);
      return Optional.empty();
    }
  }

  /** Opens the game's window; if that fails, a window half made goes with the game. */
  private static Optional<ServerGame> showToView(
      final ServerGame game, final LaunchAction launchAction) {
    try {
      game.getData().getGameLoader().startGame(game, Set.of(), launchAction, null);
      return Optional.of(game);
    } catch (final MapNotFoundException e) {
      // The throwing method of MapNotFoundException notifies and prompts user to download the map.
      game.stopGame();
      return Optional.empty();
    } catch (final RuntimeException | LinkageError e) {
      log.error("Failed to open the game", e);
      game.stopGame();
      return Optional.empty();
    }
  }

  /** Creates a launcher for a single player local (no network) game. */
  public static LocalLauncher create(
      final GameSelectorModel gameSelectorModel,
      final Collection<? extends PlayerCountrySelection> playerRows,
      final Component parent,
      final LaunchAction launchAction) {

    final Map<String, PlayerTypes.Type> playerTypes =
        playerRows.stream()
            .collect(
                Collectors.toMap(
                    PlayerCountrySelection::getPlayerName, PlayerCountrySelection::getPlayerType));

    final Map<String, Boolean> playersEnabled =
        playerRows.stream()
            .collect(
                Collectors.toMap(
                    PlayerCountrySelection::getPlayerName,
                    PlayerCountrySelection::isPlayerEnabled));

    final PlayerListing playerListing =
        new PlayerListing(
            playersEnabled,
            playerTypes,
            gameSelectorModel.getGameName(),
            gameSelectorModel.getGameRound());
    return new LocalLauncher(
        gameSelectorModel,
        new PlainRandomSource(),
        playerListing,
        parent,
        launchAction,
        new PlayerTypes(launchAction.getPlayerTypes()));
  }
}
