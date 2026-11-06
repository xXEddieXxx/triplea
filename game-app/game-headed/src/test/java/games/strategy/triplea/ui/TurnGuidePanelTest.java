package games.strategy.triplea.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import games.strategy.engine.data.GameData;
import games.strategy.engine.data.GamePlayer;
import games.strategy.triplea.image.FlagIconImageFactory;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
final class TurnGuidePanelTest {
  private static final String PURCHASE_HINT = "Click Buy to spend your income on new units.";

  @Mock private GameData data;
  @Mock private UiContext uiContext;
  @Mock private FlagIconImageFactory flagImageFactory;

  @DisplayName("On the own turn the guide names the phase and explains it")
  @Test
  void ownTurnExplainsThePhase() throws Exception {
    final GamePlayer germans = givenTurnOf("Germans");
    when(uiContext.isCurrentPlayerRemote()).thenReturn(false);

    final List<String> texts = whenStepIsShown(PURCHASE_HINT, "Purchase Units", germans);

    assertThat(texts)
        .as("player and round, phase name, phase hint")
        .containsExactly("Germans  ·  Round 3", "Purchase Units", PURCHASE_HINT);
  }

  @DisplayName("A flag that cannot be loaded leaves out the flag, not the guide")
  @Test
  void missingFlagStillShowsTheGuide() throws Exception {
    when(uiContext.getFlagImageFactory()).thenReturn(flagImageFactory);
    when(flagImageFactory.getFlag(any())).thenThrow(new IllegalStateException("Image Not Found"));
    when(uiContext.isCurrentPlayerRemote()).thenReturn(false);

    final List<String> texts =
        whenStepIsShown(PURCHASE_HINT, "Purchase Units", new GamePlayer("Germans", data));

    assertThat(texts)
        .as("the step update must not fail on a missing image")
        .containsExactly("Germans  ·  Round 3", "Purchase Units", PURCHASE_HINT);
  }

  @DisplayName("On another player's turn the guide says who is playing instead of a hint")
  @Test
  void otherPlayersTurnSaysWhoIsPlaying() throws Exception {
    final GamePlayer germans = givenTurnOf("Germans");
    when(uiContext.isCurrentPlayerRemote()).thenReturn(true);

    final List<String> texts = whenStepIsShown(PURCHASE_HINT, "Purchase Units", germans);

    assertThat(texts)
        .last()
        .as("the hint line says who is playing")
        .isEqualTo("Waiting for Germans to finish their turn.");
  }

  @DisplayName("A step without a player, eg end of round, shows only the round and the step")
  @Test
  void stepWithoutPlayerShowsNoHint() throws Exception {
    final List<String> texts = whenStepIsShown("", "End Round", null);

    assertThat(texts).as("the hint is hidden").containsExactly("Round 3", "End Round");
  }

  @DisplayName("Clicking the player's flag shows that player's capital")
  @Test
  void flagShowsTheCapital() throws Exception {
    final GamePlayer germans = givenTurnOf("Germans");
    when(uiContext.isCurrentPlayerRemote()).thenReturn(false);
    final List<GamePlayer> shownCapitals = new ArrayList<>();
    final TurnGuidePanel turnGuidePanel =
        new TurnGuidePanel(uiContext, new JPanel(), new JPanel(), shownCapitals::add);
    whenStepIsShownIn(turnGuidePanel, PURCHASE_HINT, "Purchase Units", germans);

    final JLabel flag = turnLabelOf(turnGuidePanel);
    Arrays.stream(flag.getMouseListeners())
        .forEach(
            listener ->
                listener.mouseClicked(
                    new MouseEvent(flag, MouseEvent.MOUSE_CLICKED, 0, 0, 1, 1, 1, false)));

    assertThat(shownCapitals).as("the player whose turn shows").containsExactly(germans);
  }

  @DisplayName("The bar shows the window's tools from the start, before the first step")
  @Test
  void toolsShowBeforeTheFirstStep() {
    final JButton menu = new JButton("Menu");
    final JButton history = new JButton("History");

    final TurnGuidePanel turnGuidePanel =
        new TurnGuidePanel(uiContext, history, menu, player -> {});
    turnGuidePanel.setSize(1000, 80);
    layOut(turnGuidePanel);

    assertThat(turnGuidePanel.isVisible())
        .as("the menu must be reachable at once, eg to leave a game that has not started")
        .isTrue();
    assertThat(SwingUtilities.isDescendingFrom(menu, turnGuidePanel))
        .as("the tools sit in the bar")
        .isTrue();
    assertThat(SwingUtilities.convertPoint(history, 0, 0, turnGuidePanel).x)
        .as("the history at the left end, where it opens; the other tools at the right")
        .isLessThan(SwingUtilities.convertPoint(menu, 0, 0, turnGuidePanel).x);
  }

  @DisplayName("A step shown late, after a slow flag image, does not replace the step after it")
  @Test
  @Timeout(10)
  void lateStepDoesNotWin() throws Exception {
    final CountDownLatch flagRequested = new CountDownLatch(1);
    final CountDownLatch flagLoaded = new CountDownLatch(1);
    givenSlowFlag(flagRequested, flagLoaded);
    when(uiContext.isCurrentPlayerRemote()).thenReturn(false);
    final TurnGuidePanel turnGuidePanel = newPanel();
    final Thread purchase =
        new Thread(
            () ->
                turnGuidePanel.showStep(
                    3, PURCHASE_HINT, "Purchase Units", new GamePlayer("Germans", data)));
    purchase.start();
    flagRequested.await();

    turnGuidePanel.showStep(3, "", "End Round", null);
    flagLoaded.countDown();
    purchase.join();
    SwingUtilities.invokeAndWait(() -> {});

    assertThat(visibleTexts(turnGuidePanel))
        .as("the game is at the end of the round, the purchase step is over")
        .containsExactly("Round 3", "End Round");
  }

  private void givenSlowFlag(final CountDownLatch flagRequested, final CountDownLatch flagLoaded) {
    when(uiContext.getFlagImageFactory()).thenReturn(flagImageFactory);
    when(flagImageFactory.getFlag(any()))
        .thenAnswer(
            invocation -> {
              flagRequested.countDown();
              flagLoaded.await();
              return new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
            });
  }

  private GamePlayer givenTurnOf(final String playerName) {
    when(uiContext.getFlagImageFactory()).thenReturn(flagImageFactory);
    when(flagImageFactory.getFlag(any()))
        .thenReturn(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB));
    return new GamePlayer(playerName, data);
  }

  /** Shows the step and returns the texts of the visible lines of the guide, top to bottom. */
  private List<String> whenStepIsShown(
      final String phaseHint, final String stepDisplayName, @Nullable final GamePlayer player)
      throws InterruptedException, InvocationTargetException {
    return visibleTexts(whenStepIsShownIn(newPanel(), phaseHint, stepDisplayName, player));
  }

  private TurnGuidePanel newPanel() {
    return new TurnGuidePanel(uiContext, new JPanel(), new JPanel(), player -> {});
  }

  private static TurnGuidePanel whenStepIsShownIn(
      final TurnGuidePanel turnGuidePanel,
      final String phaseHint,
      final String stepDisplayName,
      @Nullable final GamePlayer player)
      throws InterruptedException, InvocationTargetException {
    turnGuidePanel.showStep(3, phaseHint, stepDisplayName, player);
    SwingUtilities.invokeAndWait(() -> {}); // the guide updates itself on the EDT
    return turnGuidePanel;
  }

  /** The label of the player and round, which carries the player's flag. */
  private static JLabel turnLabelOf(final Container container) {
    return allComponents(container)
        .filter(JLabel.class::isInstance)
        .map(JLabel.class::cast)
        .filter(label -> label.getText() != null && label.getText().contains("Round"))
        .findFirst()
        .orElseThrow();
  }

  private static Stream<Component> allComponents(final Container container) {
    return Arrays.stream(container.getComponents())
        .flatMap(
            component ->
                component instanceof Container child
                    ? Stream.concat(Stream.of(component), allComponents(child))
                    : Stream.of(component));
  }

  private static void layOut(final Container container) {
    container.doLayout();
    Arrays.stream(container.getComponents())
        .filter(Container.class::isInstance)
        .map(Container.class::cast)
        .forEach(TurnGuidePanelTest::layOut);
  }

  /** The texts of the visible labels and texts of the guide, in reading order. */
  private static List<String> visibleTexts(final Container container) {
    return Arrays.stream(container.getComponents())
        .filter(Component::isVisible)
        .flatMap(TurnGuidePanelTest::textsOf)
        .toList();
  }

  private static Stream<String> textsOf(final Component component) {
    if (component instanceof JLabel label) {
      return Stream.of(label.getText());
    }
    if (component instanceof JTextComponent text) {
      return Stream.of(text.getText());
    }
    return component instanceof Container container
        ? visibleTexts(container).stream()
        : Stream.empty();
  }
}
