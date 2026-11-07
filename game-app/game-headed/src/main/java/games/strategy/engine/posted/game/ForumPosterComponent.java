package games.strategy.engine.posted.game;

import games.strategy.engine.data.GameData;
import games.strategy.engine.data.GamePlayer;
import games.strategy.engine.framework.IGame;
import games.strategy.engine.framework.lookandfeel.LookAndFeel;
import games.strategy.engine.framework.startup.ui.ForumMessageEditor;
import games.strategy.engine.posted.game.pbem.PbemMessagePoster;
import games.strategy.engine.posted.game.pbf.IForumPoster;
import games.strategy.engine.posted.game.pbf.NodeBbForumPoster;
import games.strategy.engine.posted.game.pbf.NodeBbGameThreads;
import games.strategy.engine.posted.game.pbf.NodeBbGameThreads.GameThread;
import games.strategy.engine.posted.game.pbf.NodeBbGameThreads.Player;
import games.strategy.engine.random.IRandomStats;
import games.strategy.triplea.delegate.GameStepPropertiesHelper;
import games.strategy.triplea.delegate.remote.IAbstractForumPosterDelegate;
import games.strategy.triplea.ui.ActionButtonsPanel;
import games.strategy.triplea.ui.history.HistoryLog;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import lombok.extern.slf4j.Slf4j;
import org.triplea.java.ThreadRunner;
import org.triplea.swing.CollapsiblePanel;
import org.triplea.swing.JButtonBuilder;
import org.triplea.swing.SwingAction;
import org.triplea.swing.WrappingText;
import org.triplea.swing.jpanel.JPanelBuilder;

/**
 * The end of a turn, or of a step whose summary is posted, in a game by forum or email: a message
 * to the other players, written as in the forum, and Send &amp; done, which posts it with the
 * turn's summary and the save game and then goes on, eg ends the turn. Sending tells how it went
 * right here, so no window opens. What the summary holds is under "Summary options"; Done without
 * sending goes on without a post.
 */
@Slf4j
public final class ForumPosterComponent extends JPanel {
  private static final long serialVersionUID = 4754052934098190357L;
  private static final String DONE_WITHOUT_SENDING = "Done without sending";

  private final GameData data;
  private PbemMessagePoster poster;
  private IGame game;
  private HistoryLog historyLog;
  private final ForumMessageEditor editor;

  /** The game's players who posted in its thread lately, to mention. */
  private transient List<String> mentionable = List.of();

  private final JButton sendAndDoneButton = new JButton("Send & done");
  private final JButton doneWithoutSending;
  private final WrappingText status = new WrappingText();
  private final JCheckBox includeTerritoryCheckBox;
  private final JCheckBox includeProductionCheckBox;
  private final JCheckBox showDetailsCheckBox;
  private final JCheckBox showDiceStatisticsCheckBox;
  private final Runnable doneAction;
  private final String title;
  private boolean sending;
  private IAbstractForumPosterDelegate forumPosterDelegate;

  public ForumPosterComponent(final GameData data, final Runnable doneAction, final String title) {
    setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
    setBorder(new EmptyBorder(5, 5, 0, 0));

    this.data = data;
    this.doneAction = doneAction;
    this.title = title;
    editor =
        new ForumMessageEditor(
            "A message to the other players, optional; Preview shows it as the forum will",
            this::forumAddress,
            // a game by forum: pictures go up with the session of the Play by forum page
            NodeBbForumPoster.availablePosters().contains(forumName()) ? this::attachPicture : null,
            () -> this.mentionable);

    includeTerritoryCheckBox =
        new JCheckBox(SwingAction.of("Include territory summary", e -> updateHistoryLog()));
    includeProductionCheckBox =
        new JCheckBox(SwingAction.of("Include Production Summary", e -> updateHistoryLog()));
    showDetailsCheckBox =
        new JCheckBox(SwingAction.of("Show dice/battle details", e -> updateHistoryLog()));
    showDiceStatisticsCheckBox =
        new JCheckBox(SwingAction.of("Include Overall Dice Statistics", e -> updateHistoryLog()));
    LookAndFeel.setPrimaryButtonStyle(sendAndDoneButton);
    sendAndDoneButton.setToolTipText(
        "Posts your message, the "
            + title.toLowerCase(Locale.ROOT)
            + " and the save game, then goes on as Done does");
    sendAndDoneButton.addActionListener(e -> sendAndDone());
    doneWithoutSending =
        new JButtonBuilder()
            .title(DONE_WITHOUT_SENDING)
            .actionListener(doneAction)
            .toolTip(ActionButtonsPanel.DONE_BUTTON_TOOLTIP)
            .build();
    LookAndFeel.setBorderlessButtonStyle(doneWithoutSending);
  }

  /** Posts and, once the forum or email took it, goes on as Done does, eg ends the turn. */
  private void sendAndDone() {
    // first: a summary that fails leaves the turn as it was, the ways forward open
    updateHistoryLog();
    sending = true;
    sendAndDoneButton.setEnabled(false);
    doneWithoutSending.setEnabled(false);
    editor.setEnabled(false);
    status.setText("Sending...");
    poster.postInBackground(
        title,
        editor.getTextArea().getText(),
        historyLog.toString(),
        forumPosterDelegate,
        game,
        answer -> {
          sending = false;
          if (NodeBbForumPoster.isHeldBack(answer)) {
            // the others do not see it yet: the player should know before going on
            status.setText(answer + " Done goes on.");
            doneWithoutSending.setText("Done");
            doneWithoutSending.setEnabled(true);
          } else {
            status.setText("Sent.");
            doneAction.run();
          }
        },
        why -> {
          sending = false;
          sendAndDoneButton.setEnabled(true);
          doneWithoutSending.setEnabled(true);
          editor.setEnabled(true);
          status.setText("Not sent (" + why + "). Try again, or finish without sending.");
        });
  }

  /** Whether Send &amp; done is posting: the turn ends once it is posted, not before. */
  boolean isSending() {
    return sending;
  }

  /**
   * Invoked by the parent container to layout the components of this panel based on the specified
   * state.
   *
   * @param frame the game's window, which the summary opens over
   * @param game the game, which is saved and posted
   * @return A reference to this panel; must be added to the parent container's layout.
   */
  ForumPosterComponent layoutComponents(
      final PbemMessagePoster poster,
      final IAbstractForumPosterDelegate forumPosterDelegate,
      final JFrame frame,
      final IGame game,
      final boolean includeDetailsAndSummary) {
    this.forumPosterDelegate = forumPosterDelegate;
    this.game = game;
    this.poster = poster;
    historyLog = new HistoryLog(frame);
    if (includeDetailsAndSummary) {
      showDetailsCheckBox.setSelected(true);
    }
    updateHistoryLog();
    removeAll();
    // the panel stays for the game: the next nation's turn starts with an empty message
    editor.clear();
    readPlayersToMention();

    editor.setAlignmentX(LEFT_ALIGNMENT);
    editor.setPreferredSize(new Dimension(320, 170));
    editor.setMaximumSize(new Dimension(Integer.MAX_VALUE, 170));
    add(editor);
    add(Box.createVerticalStrut(6));
    final WrappingText what =
        new WrappingText(
            "Send & done posts your message, the "
                + title.toLowerCase(Locale.ROOT)
                + " and the save game, then goes on.");
    what.setForeground(LookAndFeel.getSecondaryTextColor());
    addLeftAligned(what);
    add(Box.createVerticalStrut(6));
    if (includeDetailsAndSummary) {
      final CollapsiblePanel options =
          new CollapsiblePanel(
              new JPanelBuilder()
                  .boxLayoutVertical()
                  .add(includeTerritoryCheckBox)
                  .add(includeProductionCheckBox)
                  .add(showDetailsCheckBox)
                  .add(showDiceStatisticsCheckBox)
                  .build(),
              "Summary options",
              collapsed -> {});
      options.setCollapsed(true);
      addLeftAligned(options);
    }
    final JButton preview =
        new JButtonBuilder()
            .title("Show the " + title.toLowerCase(Locale.ROOT))
            .actionListener(this::viewHistoryLog)
            .build();
    LookAndFeel.setBorderlessButtonStyle(preview);
    addLeftAligned(preview);
    add(Box.createVerticalStrut(6));
    final JPanel finish = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    finish.add(sendAndDoneButton);
    finish.add(Box.createHorizontalStrut(8));
    finish.add(doneWithoutSending);
    addLeftAligned(finish);
    sendAndDoneButton.setEnabled(true);
    doneWithoutSending.setText(DONE_WITHOUT_SENDING);
    doneWithoutSending.setEnabled(true);
    editor.setEnabled(true);
    status.setText("");
    addLeftAligned(status);
    validate();
    return this;
  }

  /** Uploads the picture to the game's forum and links it at the caret, as the forum's editor. */
  private void attachPicture(final Path picture) {
    final Optional<NodeBbGameThreads> client = NodeBbGameThreads.withSessionOf(forumName());
    if (client.isEmpty()) {
      status.setText("Sign in on the Play by forum page to add pictures.");
      return;
    }
    status.setText("Uploading " + picture.getFileName() + "...");
    inBackground(
        () -> client.get().uploadPicture(picture),
        address -> {
          final JTextArea text = editor.getTextArea();
          text.insert("![](" + address + ")", text.getCaretPosition());
          status.setText("Picture added: Preview shows it as the post will.");
        },
        error -> status.setText("The picture was not uploaded: " + error));
  }

  private String forumName() {
    return data.getProperties().get(IForumPoster.NAME, "");
  }

  /** The players who posted in the game's thread lately, but the player, read in the background. */
  private void readPlayersToMention() {
    final int topicId = data.getProperties().get(IForumPoster.TOPIC_ID, 0);
    if (topicId <= 0 || !NodeBbForumPoster.availablePosters().contains(forumName())) {
      return;
    }
    final String forum = forumName();
    inBackground(
        () ->
            NodeBbGameThreads.withSessionOf(forum)
                .map(client -> playersToMention(client, topicId))
                .orElse(List.of()),
        players -> mentionable = players,
        error -> log.info("The game's players to mention were not read: {}", error));
  }

  private static List<String> playersToMention(final NodeBbGameThreads client, final int topicId) {
    try {
      return client
          .newestPage(GameThread.of(topicId, "", ""))
          .playersBut(client.signedInPlayer().map(Player::userslug).orElse(""));
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static <T> void inBackground(
      final Callable<T> task, final Consumer<T> onDone, final Consumer<String> onError) {
    ThreadRunner.runInNewThread(
        () -> {
          try {
            final T result = task.call();
            SwingUtilities.invokeLater(() -> onDone.accept(result));
          } catch (final Exception e) {
            SwingUtilities.invokeLater(() -> onError.accept(String.valueOf(e.getMessage())));
          }
        });
  }

  /** The game's forum, which links and pictures of the message's preview are relative to. */
  private URI forumAddress() {
    final String url =
        NodeBbForumPoster.forumUrlOf(data.getProperties().get(IForumPoster.NAME, ""));
    // a folder, eg the A&A forum's "/forums": relative links lie in it
    return URI.create(url.endsWith("/") ? url : url + "/");
  }

  private void addLeftAligned(final JComponent component) {
    component.setAlignmentX(Component.LEFT_ALIGNMENT);
    add(component);
  }

  private void viewHistoryLog() {
    updateHistoryLog();
    historyLog.setVisible(true);
  }

  private void updateHistoryLog() {
    final Collection<GamePlayer> allowedIDs = GameStepPropertiesHelper.getTurnSummaryPlayers(data);
    // clear first, then update
    historyLog.clear();
    historyLog.printFullTurn(data, showDetailsCheckBox.isSelected(), allowedIDs);
    if (includeTerritoryCheckBox.isSelected()) {
      historyLog.printTerritorySummary(data, allowedIDs);
    }
    if (includeProductionCheckBox.isSelected()) {
      historyLog.printProductionSummary(data);
    }
    if (showDiceStatisticsCheckBox.isSelected()) {
      historyLog.printDiceStatistics(
          data,
          (IRandomStats) game.getMessengers().getRemote(IRandomStats.RANDOM_STATS_REMOTE_NAME));
    }
  }

  public boolean canPostTurnSummary() {
    return forumPosterDelegate != null;
  }

  public boolean getHasPostedTurnSummary() {
    return forumPosterDelegate.getHasPostedTurnSummary();
  }
}
