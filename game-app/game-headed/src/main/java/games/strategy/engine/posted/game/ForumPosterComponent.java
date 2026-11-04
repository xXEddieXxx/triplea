package games.strategy.engine.posted.game;

import games.strategy.engine.data.GameData;
import games.strategy.engine.data.GamePlayer;
import games.strategy.engine.framework.IGame;
import games.strategy.engine.framework.lookandfeel.LookAndFeel;
import games.strategy.engine.framework.startup.ui.ForumMessageEditor;
import games.strategy.engine.posted.game.pbem.PbemMessagePoster;
import games.strategy.engine.posted.game.pbf.IForumPoster;
import games.strategy.engine.posted.game.pbf.NodeBbForumPoster;
import games.strategy.engine.random.IRandomStats;
import games.strategy.triplea.delegate.GameStepPropertiesHelper;
import games.strategy.triplea.delegate.remote.IAbstractForumPosterDelegate;
import games.strategy.triplea.ui.ActionButtonsPanel;
import games.strategy.triplea.ui.history.HistoryLog;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.net.URI;
import java.util.Collection;
import java.util.Locale;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
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
public final class ForumPosterComponent extends JPanel {
  private static final long serialVersionUID = 4754052934098190357L;
  private static final String DONE_WITHOUT_SENDING = "Done without sending";

  private final GameData data;
  private PbemMessagePoster poster;
  private IGame game;
  private HistoryLog historyLog;
  private final ForumMessageEditor editor =
      new ForumMessageEditor(
          "A message to the other players, optional; Preview shows it as the forum will",
          this::forumAddress);
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
