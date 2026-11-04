package games.strategy.engine.posted.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import games.strategy.engine.data.GameData;
import games.strategy.engine.framework.IGame;
import games.strategy.engine.posted.game.pbem.PbemMessagePoster;
import games.strategy.triplea.delegate.remote.IAbstractForumPosterDelegate;
import games.strategy.triplea.xml.TestMapGameData;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.AbstractButton;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

final class ForumPosterComponentTest {
  private static final String TITLE = "Turn Summary";

  private final GameData data = TestMapGameData.REVISED.getGameData();
  private final IGame game = mock(IGame.class);
  private final IAbstractForumPosterDelegate delegate = mock(IAbstractForumPosterDelegate.class);
  private final AtomicBoolean done = new AtomicBoolean();
  private ForumPosterComponent component;

  private JFrame window;

  @BeforeEach
  void needsScreen() {
    assumeThat(GraphicsEnvironment.isHeadless()).as("the summary opens as a window").isFalse();
    window = new JFrame();
  }

  @AfterEach
  void closeWindows() {
    // the summary's windows belong to the game's: they go with it
    Optional.ofNullable(window).ifPresent(JFrame::dispose);
  }

  @DisplayName("Send & done ends the step once the forum took the turn")
  @Test
  void sentTurnGoesOn() throws Exception {
    when(delegate.postTurnSummary(any(), any())).thenReturn(true);
    final ForumPosterComponent end = givenEndOfTurn();

    SwingUtilities.invokeAndWait(() -> buttonOf(end, "Send & done").doClick());

    await().atMost(Duration.ofSeconds(10)).untilTrue(done);
  }

  @DisplayName("A turn not sent stays: it says why, and the player tries again or goes on")
  @Test
  void turnNotSentStays() throws Exception {
    doThrow(new IllegalStateException("disk full")).when(game).saveGame(any(Path.class));
    final ForumPosterComponent end = givenEndOfTurn();

    SwingUtilities.invokeAndWait(() -> buttonOf(end, "Send & done").doClick());

    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () ->
                assertThat(textsOf(end))
                    .as("the reason, right where the player sent it")
                    .anyMatch(text -> text.contains("Posting failed: disk full")));
    assertThat(done).as("the turn goes on only once sent or by the player").isFalse();
    assertThat(List.of(buttonOf(end, "Send & done"), buttonOf(end, "Done without sending")))
        .as("both ways forward are open again")
        .allMatch(Component::isEnabled);
  }

  @DisplayName("The next turn starts with an empty message, as the panel stays for the game")
  @Test
  void nextTurnStartsEmpty() throws Exception {
    final ForumPosterComponent end = givenEndOfTurn();
    SwingUtilities.invokeAndWait(
        () -> messageOf(end).setText("Moscow holds. On to Berlin next round."));

    SwingUtilities.invokeAndWait(this::layOutNextTurn);

    assertThat(messageOf(end).getText()).as("eg the player's next nation").isEmpty();
  }

  private ForumPosterComponent givenEndOfTurn() throws Exception {
    component = new ForumPosterComponent(data, () -> done.set(true), TITLE);
    SwingUtilities.invokeAndWait(this::layOutNextTurn);
    return component;
  }

  private void layOutNextTurn() {
    component.layoutComponents(
        new PbemMessagePoster(data, data.getPlayerList().getPlayers().get(0), 1, TITLE),
        delegate,
        window,
        game,
        true);
  }

  private static AbstractButton buttonOf(final Container container, final String text) {
    return componentsOf(container).stream()
        .filter(AbstractButton.class::isInstance)
        .map(AbstractButton.class::cast)
        .filter(button -> text.equals(button.getText()))
        .findFirst()
        .orElseThrow();
  }

  private static JTextComponent messageOf(final Container container) {
    return componentsOf(container).stream()
        .filter(JTextComponent.class::isInstance)
        .map(JTextComponent.class::cast)
        .filter(JTextComponent::isEditable)
        .findFirst()
        .orElseThrow();
  }

  private static List<String> textsOf(final Container container) {
    return componentsOf(container).stream()
        .filter(JTextComponent.class::isInstance)
        .map(component -> ((JTextComponent) component).getText())
        .toList();
  }

  private static List<Component> componentsOf(final Container container) {
    final List<Component> all = new ArrayList<>();
    for (final Component component : container.getComponents()) {
      all.add(component);
      if (component instanceof Container inner) {
        all.addAll(componentsOf(inner));
      }
    }
    return all;
  }
}
