package games.strategy.engine.posted.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import games.strategy.engine.data.GameData;
import games.strategy.engine.framework.IGame;
import games.strategy.engine.player.PlayerBridge;
import games.strategy.engine.posted.game.pbem.PbemMessagePoster;
import games.strategy.engine.posted.game.pbf.IForumPoster;
import games.strategy.triplea.delegate.remote.IAbstractForumPosterDelegate;
import games.strategy.triplea.ui.panels.map.MapPanel;
import games.strategy.triplea.xml.TestMapGameData;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import javax.swing.AbstractButton;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

final class EndTurnPanelTest {
  private final GameData data = TestMapGameData.REVISED.getGameData();

  @DisplayName("A turn posted when its end began goes on at once: the save posted was saved there")
  @Test
  void postedTurnGoesOn() throws Exception {
    data.getProperties().set(IForumPoster.NAME, "forums.triplea-game.org");
    final EndTurnPanel panel = new EndTurnPanel(data, mock(MapPanel.class));
    final PlayerBridge bridge = givenBridgeOfPostedTurn();

    final CompletableFuture<Void> done =
        CompletableFuture.runAsync(() -> panel.waitForDone(null, bridge));

    assertThat(done.get(10, TimeUnit.SECONDS))
        .as("the next nation's player loads the save and plays on, without Send & done")
        .isNull();
  }

  @DisplayName("Done's key while Send & done posts waits: the turn ends once it is posted")
  @Test
  void doneKeyWaitsForThePost() throws Exception {
    assumeThat(GraphicsEnvironment.isHeadless()).as("the summary opens as a window").isFalse();
    final AtomicBoolean released = new AtomicBoolean();
    final EndTurnPanel panel =
        new EndTurnPanel(data, mock(MapPanel.class)) {
          private static final long serialVersionUID = 1L;

          @Override
          protected void release() {
            released.set(true);
          }
        };
    final CountDownLatch forumAnswers = new CountDownLatch(1);
    final IAbstractForumPosterDelegate delegate = givenDelegateAnsweringOn(forumAnswers);
    final JFrame window = new JFrame();
    try {
      SwingUtilities.invokeAndWait(
          () ->
              panel.forumPosterComponent.layoutComponents(
                  new PbemMessagePoster(data, data.getPlayerList().getPlayers().get(0), 1, "Turn"),
                  delegate,
                  window,
                  mock(IGame.class),
                  true));

      SwingUtilities.invokeAndWait(
          () -> {
            buttonOf(panel.forumPosterComponent, "Send & done").doClick();
            // the turn counts as posted while it posts: the save says so to the next player
            panel.performDone();
          });
      final boolean releasedWhilePosting = released.get();
      forumAnswers.countDown();

      assertThat(releasedWhilePosting).as("the post is not cut short").isFalse();
      await().atMost(Duration.ofSeconds(10)).untilTrue(released);
    } finally {
      SwingUtilities.invokeAndWait(window::dispose);
    }
  }

  private static PlayerBridge givenBridgeOfPostedTurn() {
    final IAbstractForumPosterDelegate delegate = mock(IAbstractForumPosterDelegate.class);
    when(delegate.getHasPostedTurnSummary()).thenReturn(true);
    final PlayerBridge bridge = mock(PlayerBridge.class);
    when(bridge.getRemoteDelegate()).thenReturn(delegate);
    return bridge;
  }

  private static IAbstractForumPosterDelegate givenDelegateAnsweringOn(
      final CountDownLatch answer) {
    final IAbstractForumPosterDelegate delegate = mock(IAbstractForumPosterDelegate.class);
    when(delegate.getHasPostedTurnSummary()).thenReturn(true);
    when(delegate.postTurnSummary(any(), any()))
        .thenAnswer(
            invocation -> {
              answer.await();
              return true;
            });
    return delegate;
  }

  private static AbstractButton buttonOf(final Container container, final String text) {
    return componentsOf(container)
        .filter(AbstractButton.class::isInstance)
        .map(AbstractButton.class::cast)
        .filter(button -> text.equals(button.getText()))
        .findFirst()
        .orElseThrow();
  }

  private static Stream<Component> componentsOf(final Container container) {
    return Arrays.stream(container.getComponents())
        .flatMap(
            component ->
                component instanceof Container inner
                    ? Stream.concat(Stream.of(component), componentsOf(inner))
                    : Stream.of(component));
  }
}
