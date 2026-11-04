package games.strategy.engine.posted.game.pbem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import games.strategy.engine.data.GameData;
import games.strategy.engine.framework.IGame;
import games.strategy.triplea.xml.TestMapGameData;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

final class PbemMessagePosterTest {
  private final GameData data = TestMapGameData.REVISED.getGameData();
  private final PbemMessagePoster poster =
      new PbemMessagePoster(data, data.getPlayerList().getPlayers().get(0), 1, "Turn Summary");

  @DisplayName("A post that sends no email counts as posted: no email is no failure")
  @Test
  void noEmailIsNoFailure() {
    assertThat(poster.post(null, "Turn Summary"))
        .as("a game by forum only posted its turn; Send & done then ends the turn")
        .isTrue();
  }

  @DisplayName("A save that fails tells the screen that sent it, which would wait on otherwise")
  @Test
  void failedSaveIsTold() throws Exception {
    final IGame game = givenGameThatCannotBeSaved();
    final CompletableFuture<String> told = new CompletableFuture<>();

    poster.postInBackground(
        "Turn Summary",
        "",
        "summary",
        null,
        game,
        answer -> told.complete("posted"),
        told::complete);

    assertThat(told.get(10, TimeUnit.SECONDS))
        .as("the end of the turn shows why and lets the player try again")
        .isEqualTo("Posting failed: disk full");
  }

  @DisplayName("An error without a message is told by its kind, so the screen hears of it")
  @Test
  void failureWithoutMessageIsTold() throws Exception {
    final IGame game = mock(IGame.class);
    doThrow(new IllegalStateException()).when(game).saveGame(any(Path.class));
    final CompletableFuture<String> told = new CompletableFuture<>();

    poster.postInBackground(
        "Turn Summary",
        "",
        "summary",
        null,
        game,
        answer -> told.complete("posted"),
        told::complete);

    assertThat(told.get(10, TimeUnit.SECONDS))
        .as("no message must not leave the end of the turn at Sending...")
        .isEqualTo("Posting failed: IllegalStateException");
  }

  @DisplayName("The email shows the player's message as written, also a '<' or an '&'")
  @Test
  void emailShowsMessageAsWritten() {
    assertThat(PbemMessagePoster.convertToHtml("a < b & c\nd"))
        .as("free text, not markup; lines break where written")
        .isEqualTo("<pre><br/>a &lt; b &amp; c<br/>d<br/></pre>");
  }

  @DisplayName("The player's message stands between the game and the summary; none leaves no gap")
  @Test
  void messageAboveSummary() {
    assertThat(
            List.of(
                PbemMessagePoster.postText("Game", "  Moscow holds. ", "Summary"),
                PbemMessagePoster.postText("Game", " ", "Summary")))
        .as("the others read the message first, then what happened")
        .containsExactly("Game\n\nMoscow holds.\n\nSummary", "Game\n\nSummary");
  }

  private static IGame givenGameThatCannotBeSaved() {
    final IGame game = mock(IGame.class);
    doThrow(new IllegalStateException("disk full")).when(game).saveGame(any(Path.class));
    return game;
  }
}
