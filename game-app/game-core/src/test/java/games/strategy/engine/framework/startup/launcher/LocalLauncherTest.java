package games.strategy.engine.framework.startup.launcher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import games.strategy.engine.data.GameData;
import games.strategy.engine.data.GameStep;
import games.strategy.engine.framework.ServerGame;
import games.strategy.triplea.xml.TestMapGameData;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

final class LocalLauncherTest {
  private final GameData gameData = TestMapGameData.REVISED.getGameData();
  private final LaunchAction launchAction = mock(LaunchAction.class);

  @DisplayName("A game opened to view has no player and runs no step until its window is left")
  @Test
  @Timeout(10)
  void gameToViewRunsNoStep() throws Exception {
    final GameStep step = gameData.getSequence().getStep();

    final ServerGame game = LocalLauncher.openToView(gameData, launchAction).orElseThrow();

    verify(launchAction).startGame(any(), eq(game), eq(Set.of()), isNull());
    assertThat(gameData.getSequence().getStep())
        .as("nobody plays: the game stays at the step it was saved at")
        .isSameAs(step);
    final CompletableFuture<Void> left = CompletableFuture.runAsync(game::awaitGameOver);
    assertThat(left)
        .as("the launcher waits while the window is open")
        .failsWithin(Duration.ofMillis(200))
        .withThrowableOfType(TimeoutException.class);
    // left: the launcher waiting for the end goes on, else the test times out
    game.stopGame();
    left.get();
  }

  @DisplayName("A game whose window fails to open is not viewed, and the menu comes back")
  @Test
  void gameWhoseWindowFailsIsNotViewed() {
    doThrow(new IllegalStateException("no window"))
        .when(launchAction)
        .startGame(any(), any(), any(), any());

    assertThat(LocalLauncher.openToView(gameData, launchAction))
        .as("the launcher then shows the setup again instead of waiting for a window")
        .isEmpty();
  }

  @DisplayName("A game whose window class is missing is not viewed, and the menu comes back")
  @Test
  void gameWhoseWindowClassIsMissingIsNotViewed() {
    doThrow(new NoClassDefFoundError("no window class"))
        .when(launchAction)
        .startGame(any(), any(), any(), any());

    assertThat(LocalLauncher.openToView(gameData, launchAction))
        .as("an error, not an exception, yet the launcher shows the setup again")
        .isEmpty();
  }

  @DisplayName("A game that cannot be made is not viewed, and the menu comes back")
  @Test
  void gameThatCannotBeMadeIsNotViewed() {
    assertThat(LocalLauncher.openToView(null, launchAction))
        .as("eg no game data: no exception reaches the launcher, which shows the setup again")
        .isEmpty();
  }
}
