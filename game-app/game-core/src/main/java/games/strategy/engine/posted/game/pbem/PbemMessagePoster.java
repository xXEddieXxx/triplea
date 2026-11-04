package games.strategy.engine.posted.game.pbem;

import com.google.common.annotations.VisibleForTesting;
import games.strategy.engine.data.GameData;
import games.strategy.engine.data.GamePlayer;
import games.strategy.engine.data.GameState;
import games.strategy.engine.data.properties.GameProperties;
import games.strategy.engine.framework.GameDataFileUtils;
import games.strategy.engine.framework.IGame;
import games.strategy.engine.history.IDelegateHistoryWriter;
import games.strategy.engine.posted.game.pbf.IForumPoster;
import games.strategy.engine.posted.game.pbf.NodeBbForumPoster;
import games.strategy.engine.posted.game.pbf.NodeBbForumPoster.SaveGameParameter;
import games.strategy.triplea.delegate.remote.IAbstractForumPosterDelegate;
import games.strategy.triplea.ui.history.HistoryLog;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.StringEscapeUtils;
import org.triplea.java.ThreadRunner;
import org.triplea.swing.ProgressWindow;

/**
 * This class is responsible for posting turn summary and email at the end of each round in a PBEM
 * game. A new instance is created at end of turn, based on the Email and a forum poster stored in
 * the game data. This class does only implement {@link Serializable} because otherwise the delegate
 * would reject it, even though this class is for local use only.
 */
@Slf4j
public class PbemMessagePoster implements Serializable {
  private static final long serialVersionUID = -1L;
  private final GameProperties gameProperties;
  private Path saveGameFile = null;
  private String turnSummary = null;
  private String turnSummaryRef = null;
  private String emailSendStatus;

  /** What the player wrote to the other players, above the summary; empty when nothing. */
  private String playerMessage = "";

  /** Why the last post failed, for the screen that posted to tell. */
  private String failure = "";

  private final GamePlayer currentPlayer;
  private final int roundNumber;
  private final String gameNameAndInfo;

  public PbemMessagePoster(
      final GameData gameData,
      final GamePlayer currentPlayer,
      final int roundNumber,
      final String title) {
    this.currentPlayer = currentPlayer;
    this.roundNumber = roundNumber;
    gameProperties = gameData.getProperties();
    gameNameAndInfo = "TripleA " + title + " for game: " + gameData.getGameName();
  }

  public boolean hasMessengers() {
    return gameProperties.get(IForumPoster.NAME) != null
        || gameProperties.get(IEmailSender.SUBJECT) != null;
  }

  public static boolean gameDataHasPlayByEmailOrForumMessengers(final GameState gameData) {
    return gameData != null
        && (gameData.getProperties().get(IForumPoster.NAME) != null
            || gameData.getProperties().get(IEmailSender.SUBJECT) != null);
  }

  public void setSaveGame(final Path saveGameFile) {
    this.saveGameFile = saveGameFile;
  }

  /**
   * Post summary to form and/or email, and writes the action performed to the history writer.
   *
   * @param historyWriter the history writer (which has no effect since save game has already be
   *     generated)
   * @return true if all posts were successful
   */
  public boolean post(final IDelegateHistoryWriter historyWriter, final String title) {
    final Optional<NodeBbForumPoster> forumPoster = newForumPoster();
    final StringBuilder saveGameSb = new StringBuilder().append("triplea_");

    if (forumPoster.isPresent()) {
      saveGameSb.append(gameProperties.get(IForumPoster.TOPIC_ID)).append("_");
    }
    saveGameSb
        .append(roundNumber)
        .append(currentPlayer.getName(), 0, Math.min(3, currentPlayer.getName().length() - 1));
    final String saveGameName = GameDataFileUtils.addExtension(saveGameSb.toString());
    final String text = postText(gameNameAndInfo, playerMessage, turnSummary);
    boolean forumSuccess = true;
    if (forumPoster.isPresent()) {
      try {
        turnSummaryRef =
            forumPoster
                .get()
                .postTurnSummary(
                    text,
                    "TripleA " + title + ": " + currentPlayer.getName() + " round " + roundNumber,
                    SaveGameParameter.builder()
                        .path(saveGameFile)
                        .displayName(saveGameName)
                        .build())
                .get();
        if (turnSummaryRef != null && historyWriter != null) {
          historyWriter.startEvent("Turn Summary: " + turnSummaryRef);
        }
      } catch (final ExecutionException | RuntimeException e) {
        // also an error before the forum was asked: the email still goes out
        forumSuccess = false;
        failure =
            reasonOf(
                e instanceof ExecutionException ? Optional.ofNullable(e.getCause()).orElse(e) : e);
        // the screen that posted tells why: a log at warn or error would add a window of its own
        log.info("Failed to post game to forum", e);
      } catch (final InterruptedException e) {
        Thread.currentThread().interrupt();
        forumSuccess = false;
        failure = "Posting was interrupted.";
      }
    }
    final Optional<IEmailSender> emailSender = newEmailSender();
    final boolean emailSuccess =
        emailSender
            .map(
                sender -> {
                  try {
                    sender.sendEmail(
                        currentPlayer.getName() + " - round " + roundNumber,
                        convertToHtml(text),
                        saveGameFile,
                        saveGameName);
                    emailSendStatus =
                        "Success, sent to " + gameProperties.get(IEmailSender.RECIPIENTS);
                    return true;
                  } catch (final IOException e) {
                    emailSendStatus = "Failed! Error " + e.getMessage();
                    // after the forum's reason, if it failed too: the player learns of both
                    failure =
                        (failure.isBlank() ? "" : failure + " ")
                            + "The email was not sent: "
                            + reasonOf(e);
                    log.info("Failed to send game via email", e);
                    return false;
                  }
                })
            // a game played by forum only sends no email: nothing failed
            .orElse(true);
    if (historyWriter != null) {
      final StringBuilder sb = new StringBuilder("Post Turn Summary");
      if (forumPoster.isPresent()) {
        sb.append(" to ")
            .append(gameProperties.get(IForumPoster.NAME))
            .append(" success = ")
            .append(forumSuccess);
      }
      if (emailSender.isPresent()) {
        sb.append(forumPoster.isPresent() ? " and to " : " to ");
        sb.append(gameProperties.get(IEmailSender.RECIPIENTS))
            .append(" success = ")
            .append(emailSuccess);
      }
      historyWriter.startEvent(sb.toString());
    }
    return forumSuccess && emailSuccess;
  }

  /** The post: the game, the player's message if there is one, then the summary. */
  @VisibleForTesting
  static String postText(final String game, final String message, final String summary) {
    return game + "\n\n" + (message.isBlank() ? "" : message.strip() + "\n\n") + summary;
  }

  /** Why it failed, as the player reads it; the kind of error where it gives no message. */
  private static String reasonOf(final Throwable e) {
    return Objects.requireNonNullElse(e.getMessage(), e.getClass().getSimpleName());
  }

  private Optional<NodeBbForumPoster> newForumPoster() {
    final String name = gameProperties.get(IForumPoster.NAME, "");
    if (name.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(
        NodeBbForumPoster.newInstanceByName(name, gameProperties.get(IForumPoster.TOPIC_ID, 0)));
  }

  private Optional<IEmailSender> newEmailSender() {
    final String subject = gameProperties.get(IEmailSender.SUBJECT, "");
    if (subject.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(
        IEmailSender.newInstance(subject, gameProperties.get(IEmailSender.RECIPIENTS, "")));
  }

  /**
   * Converts text to html, by transforming \n to &lt;br/&gt;.
   *
   * @param string the string to transform
   * @return the transformed string
   */
  @VisibleForTesting
  static String convertToHtml(final String string) {
    // the player's message is free text: a "<" or "&" in it shows as written
    return "<pre><br/>"
        + StringEscapeUtils.escapeHtml4(string).replaceAll("\n", "<br/>")
        + "<br/></pre>";
  }

  public boolean alsoPostMoveSummary() {
    return gameProperties.get(
        IForumPoster.POST_AFTER_COMBAT, gameProperties.get(IEmailSender.POST_AFTER_COMBAT, false));
  }

  /**
   * Posts the player's message, the turn summary and the save game in the background, without
   * asking first and without a window of its own: the screen that sent it tells how it went, eg the
   * end of a turn, which goes on once posted.
   *
   * @param message what the player writes to the other players, above the summary; may be empty
   * @param posted on the EDT, once the forum and the email took it, with the forum's answer, eg
   *     that a moderator approves the post first ({@link NodeBbForumPoster#isHeldBack})
   * @param failed on the EDT, with why not
   */
  public void postInBackground(
      final String title,
      final String message,
      final String summary,
      final @Nullable IAbstractForumPosterDelegate postingDelegate,
      final IGame game,
      final Consumer<String> posted,
      final Consumer<String> failed) {
    playerMessage = message;
    turnSummary = summary;
    ThreadRunner.runInNewThread(
        () -> {
          boolean postOk = false;
          try {
            postOk = saveAndPost(title, postingDelegate, game);
          } finally {
            // also after an error the screen hears of it, so it never waits on
            final boolean ok = postOk;
            SwingUtilities.invokeLater(
                () -> {
                  if (ok) {
                    posted.accept(Optional.ofNullable(turnSummaryRef).orElse(""));
                  } else {
                    failed.accept(failure.isBlank() ? "The forum did not take the post." : failure);
                  }
                });
          }
        });
  }

  /**
   * Saves the game and posts it with the summary set before.
   *
   * @return whether the forum and the email took it
   */
  private boolean saveAndPost(
      final String title,
      final @Nullable IAbstractForumPosterDelegate postingDelegate,
      final IGame game) {
    failure = "";
    boolean postOk;
    Path file = null;
    try {
      // saved as posted: whoever loads the save, eg the next nation's player, goes on at once
      if (postingDelegate != null) {
        postingDelegate.setHasPostedTurnSummary(true);
      }
      file = Files.createTempFile("triplea", GameDataFileUtils.getExtension());
      game.saveGame(file);
      setSaveGame(file);
      // the delegate posts, so its history tells of the post
      postOk =
          postingDelegate == null
              ? post(null, title)
              : postingDelegate.postTurnSummary(this, title);
    } catch (final IOException e) {
      postOk = false;
      failure = "The save game was not written: " + reasonOf(e);
      log.info("Failed to create save game", e);
    } catch (final RuntimeException e) {
      // eg the game could not be saved: the screen that posted must hear of it, not wait on
      postOk = false;
      failure = "Posting failed: " + reasonOf(e);
      log.info("Failed to post the turn", e);
    } finally {
      deleteSaveGame(file);
    }
    if (postingDelegate != null) {
      try {
        postingDelegate.setHasPostedTurnSummary(postOk);
      } catch (final RuntimeException e) {
        // eg the game ended meanwhile: the post went or failed all the same
        log.info("Could not note whether the turn was posted", e);
      }
    }
    return postOk;
  }

  private static void deleteSaveGame(final @Nullable Path file) {
    if (file != null) {
      try {
        Files.delete(file);
      } catch (final IOException e) {
        log.warn("Could not delete file " + file.toAbsolutePath(), e);
      }
    }
  }

  /**
   * Posts a game turn summary and the save game to the email (if set) and the forum (if set), from
   * the menu: the player is asked first, and a window tells how it went.
   */
  public void postTurn(
      final String title,
      final HistoryLog historyLog,
      final boolean includeSaveGame,
      final @Nullable IAbstractForumPosterDelegate postingDelegate,
      final JFrame frame,
      final IGame game,
      final @Nullable JComponent postButton) {
    String message = "";
    final String displayName = gameProperties.get(IForumPoster.NAME, "");
    final StringBuilder sb = new StringBuilder();
    if (!displayName.isEmpty()) {
      sb.append(message).append("Post ").append(title).append(" ");
      if (includeSaveGame) {
        sb.append("and save game ");
      }
      sb.append("to ").append(displayName).append("?\n");
    }
    final String opponent = gameProperties.get(IEmailSender.RECIPIENTS, "");
    if (!opponent.isEmpty()) {
      sb.append("Send email to ").append(opponent).append("?\n");
    }
    message = sb.toString();
    final int choice =
        JOptionPane.showConfirmDialog(
            frame,
            message,
            "Post " + title + "?",
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.PLAIN_MESSAGE,
            null);
    if (choice == 0) {
      if (postButton != null) {
        postButton.setEnabled(false);
      }
      final ProgressWindow progressWindow = new ProgressWindow(frame, "Posting " + title + "...");
      progressWindow.setVisible(true);
      turnSummary = historyLog.toString();
      // start a new thread for posting the summary.
      ThreadRunner.runInNewThread(
          () -> {
            final boolean postOk = saveAndPost(title, postingDelegate, game);
            final StringBuilder sb1 = new StringBuilder();
            if (gameProperties.get(IForumPoster.NAME) != null && this.turnSummaryRef != null) {
              sb1.append("\nSummary Text: ").append(this.turnSummaryRef);
            }
            if (gameProperties.get(IEmailSender.SUBJECT) != null) {
              sb1.append("\nEmails: ").append(emailSendStatus);
            }
            if (!postOk) {
              sb1.append('\n').append(failure);
            }
            progressWindow.setVisible(false);
            progressWindow.removeAll();
            progressWindow.dispose();
            final String finalMessage = sb1.toString();
            SwingUtilities.invokeLater(
                () -> {
                  historyLog.append(finalMessage);
                  historyLog.append("\n");
                  if (postButton != null) {
                    postButton.setEnabled(!postOk);
                  }
                  JOptionPane.showMessageDialog(
                      frame,
                      finalMessage,
                      title + " Posted",
                      postOk ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.ERROR_MESSAGE);
                });
          });
    }
  }

  @SuppressWarnings("static-method")
  private void readObject(@SuppressWarnings("unused") final ObjectInputStream stream) {
    throw new UnsupportedOperationException("This class shouldn't get de-serialized!");
  }

  @SuppressWarnings("static-method")
  private void writeObject(@SuppressWarnings("unused") final ObjectOutputStream stream) {
    throw new UnsupportedOperationException("This class shouldn't get serialized!");
  }
}
