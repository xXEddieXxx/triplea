package games.strategy.engine.posted.game.pbem;

import com.google.common.annotations.VisibleForTesting;
import games.strategy.engine.data.GameData;
import games.strategy.engine.data.GamePlayer;
import games.strategy.engine.framework.IGame;
import games.strategy.engine.posted.game.pbf.IForumPoster;
import games.strategy.engine.posted.game.pbf.NodeBbForumPoster;
import games.strategy.engine.posted.game.pbf.NodeBbGameThreads;
import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import org.triplea.java.ThreadRunner;

/**
 * A question to the opponent of a game played by forum or email, as its players ask for a decision
 * of the other side during a turn, eg whether to scramble or which units to lose: posted to the
 * game's thread, or sent by email, with the save game, which the opponent can look at. The post is
 * short, the game, the round and the question, so the opponent can answer from the forum's page; in
 * a game by forum a picture of what the player sees, eg the map or the battle, goes with it where
 * the player is signed in on the Play by forum page. The decision's dialog stays open for the
 * answer; the button tells how the asking went, so no window opens over the dialog.
 */
@Slf4j
public final class OpponentQuestion {
  private static final String TITLE = "Decision Request";

  /** The game, which is saved for the opponent. */
  private final IGame game;

  /** What the player sees, eg the map or the battle, shown to the opponent; null for nothing. */
  private final Supplier<Component> shown;

  public OpponentQuestion(final IGame game) {
    this(game, () -> null);
  }

  /**
   * @param shown what the player sees when asking, eg the map, sent along as a picture
   */
  public OpponentQuestion(final IGame game, final Supplier<Component> shown) {
    this.game = game;
    this.shown = shown;
  }

  /**
   * Whether the decision is the opponent's: the game goes by forum or email, and the deciding
   * player is not the one whose turn it is, eg the defender asked to scramble.
   */
  public boolean isOpponentsDecision(final GamePlayer decider) {
    final GameData data = game.getData();
    final GamePlayer moving = data.getSequence().getStep().getPlayerId();
    return PbemMessagePoster.gameDataHasPlayByEmailOrForumMessengers(data)
        && moving != null
        && !moving.equals(decider);
  }

  /**
   * "Ask in the forum", or "Ask by email" where the game goes by email: posts the question to the
   * deciding player; none where the decision is not the opponent's.
   */
  public Optional<JButton> newAskButton(final GamePlayer decider, final Supplier<String> question) {
    if (!isOpponentsDecision(decider)) {
      return Optional.empty();
    }
    final String label =
        game.getData().getProperties().get(IForumPoster.NAME) == null
            ? "Ask by email"
            : "Ask in the forum";
    final JButton ask = new JButton(label);
    ask.setToolTipText(
        "Posts the question with the save game for "
            + decider.getName()
            + "; enter the answer here once it came");
    ask.addActionListener(e -> ask(ask, label, decider, question.get()));
    return Optional.of(ask);
  }

  private void ask(
      final JButton button, final String label, final GamePlayer decider, final String question) {
    final GameData data = game.getData();
    button.setEnabled(false);
    button.setText("Asking...");
    // taken now, on the EDT, as the player sees it
    final Optional<Path> picture = screenshotOf(shown.get());
    final String forumName = data.getProperties().get(IForumPoster.NAME, "");
    ThreadRunner.runInNewThread(
        () -> {
          final String message =
              picture
                  .flatMap(file -> uploaded(file, forumName))
                  .map(url -> "![](" + url + ")")
                  .orElse("");
          SwingUtilities.invokeLater(
              () ->
                  new PbemMessagePoster(data, decider, data.getSequence().getRound(), TITLE)
                      .postInBackground(
                          TITLE,
                          message,
                          decider.getName() + ", your decision: " + question,
                          null,
                          game,
                          answer -> {
                            final boolean heldBack = NodeBbForumPoster.isHeldBack(answer);
                            button.setText(heldBack ? "Asked, awaits a moderator" : "Asked");
                            button.setToolTipText(
                                (heldBack ? answer : "Posted.")
                                    + " Enter the answer here once it came");
                          },
                          why -> {
                            button.setEnabled(true);
                            button.setText(label + " again");
                            button.setToolTipText("Not posted: " + why);
                          }));
        });
  }

  /** The component as a PNG file, as it shows; empty when there is nothing to show. */
  @VisibleForTesting
  static Optional<Path> screenshotOf(final @Nullable Component component) {
    if (component == null || component.getWidth() <= 0 || component.getHeight() <= 0) {
      return Optional.empty();
    }
    final BufferedImage image =
        new BufferedImage(component.getWidth(), component.getHeight(), BufferedImage.TYPE_INT_RGB);
    final Graphics2D graphics = image.createGraphics();
    try {
      component.printAll(graphics);
    } finally {
      graphics.dispose();
    }
    try {
      final Path file = Files.createTempFile("triplea-question-", ".png");
      ImageIO.write(image, "png", file.toFile());
      return Optional.of(file);
    } catch (final IOException e) {
      log.info("No picture for the question: {}", e.getMessage());
      return Optional.empty();
    }
  }

  /**
   * The picture's address on the game's forum, uploaded with the player's session; empty in a game
   * by email, while signed out or when the forum did not take it: the question goes without.
   */
  private static Optional<String> uploaded(final Path picture, final String forumName) {
    try {
      return NodeBbGameThreads.withSessionOf(forumName).map(client -> upload(client, picture));
    } finally {
      try {
        Files.deleteIfExists(picture);
      } catch (final IOException e) {
        log.info("Picture of the question not deleted: {}", picture, e);
      }
    }
  }

  private static @Nullable String upload(final NodeBbGameThreads client, final Path picture) {
    try {
      return client.uploadPicture(picture);
    } catch (final IOException e) {
      log.info("Picture of the question not uploaded: {}", e.getMessage());
      return null;
    }
  }
}
