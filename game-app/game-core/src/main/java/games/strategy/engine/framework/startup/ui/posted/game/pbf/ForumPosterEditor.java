package games.strategy.engine.framework.startup.ui.posted.game.pbf;

import games.strategy.engine.framework.lookandfeel.LookAndFeel;
import games.strategy.engine.framework.startup.ui.posted.game.HelpTexts;
import games.strategy.engine.framework.startup.ui.posted.game.pbf.ForumPosterEditorViewModel.ThreadChoice;
import games.strategy.engine.posted.game.pbf.NodeBbGameThreads;
import games.strategy.engine.posted.game.pbf.NodeBbGameThreads.Player;
import games.strategy.engine.posted.game.pbf.OldForumGames;
import games.strategy.triplea.settings.ClientSetting;
import java.awt.GridBagLayout;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import org.triplea.java.ThreadRunner;
import org.triplea.java.ViewModelListener;
import org.triplea.swing.CollapsiblePanel;
import org.triplea.swing.DocumentListenerBuilder;
import org.triplea.swing.JButtonBuilder;
import org.triplea.swing.JCheckBoxBuilder;
import org.triplea.swing.SwingComponents;
import org.triplea.swing.jpanel.GridBagConstraintsAnchor;
import org.triplea.swing.jpanel.GridBagConstraintsBuilder;
import org.triplea.swing.jpanel.GridBagConstraintsFill;
import org.triplea.swing.jpanel.JPanelBuilder;

/**
 * Where the game's turns are posted: the forum, whether the player is signed in there, the game's
 * thread and, under "More options", what a post includes. The login with username and password
 * shows only for the A&amp;A forum without a session.
 */
class ForumPosterEditor extends JPanel implements ViewModelListener<ForumPosterEditorViewModel> {
  private static final long serialVersionUID = -6069315084412575053L;

  private final ForumPosterEditorViewModel viewModel;
  private final JButton viewPosts = new JButton("View thread");
  private final JButton testForum = new JButton("Test Post");
  private final JLabel helpMessage = new JLabel();
  private final JButton helpButton =
      new JButtonBuilder("Help")
          .actionListener(
              () ->
                  JOptionPane.showMessageDialog(
                      this, helpMessage, "Play By Forum Help", JOptionPane.INFORMATION_MESSAGE))
          .toolTip("Click this button to show help text")
          .build();
  private final JComboBox<String> forums = new JComboBox<>();
  private final JLabel signInStatus = new JLabel();
  private final JLabel threadLabel = new JLabel("Game thread");
  private final JRadioButton newThread = new JRadioButton("Start a new thread");
  private final JRadioButton existingThread = new JRadioButton("Use an existing thread");
  private final JPanel threadChoice =
      new JPanelBuilder()
          .boxLayoutHorizontal()
          .add(newThread)
          .addHorizontalStrut(12)
          .add(existingThread)
          .build();
  private final JLabel titleLabel = new JLabel("Thread title");
  private final JTextField titleField = new JTextField();
  private final JLabel topicIdLabel = new JLabel("Thread address or number");
  private final JTextField topicIdField = new JTextField();
  private final JButton chooseGameButton = new JButton("Choose game...");
  private final JPanel threadActions =
      new JPanelBuilder()
          .boxLayoutHorizontal()
          .add(chooseGameButton)
          .addHorizontalStrut(4)
          .add(viewPosts)
          .build();
  private final JLabel usernameLabel = new JLabel("Forum Username");
  private final JTextField usernameField = new JTextField();
  private final JLabel passwordLabel = new JLabel("Forum Password");
  private final JPasswordField passwordField = new JPasswordField();
  private final JLabel otpLabel = new JLabel("2FA OTP-Code (optional)");
  private final JTextField otpField = new JTextField();
  private final JCheckBox rememberPassword =
      new JCheckBoxBuilder("Remember Password").bind(ClientSetting.rememberForumPassword).build();
  private final JButton rememberPasswordHelpButton =
      new JButtonBuilder("Help")
          .actionListener(
              button ->
                  JOptionPane.showMessageDialog(
                      button,
                      HelpTexts.rememberPlayByForumPassword(),
                      "Remember Password",
                      JOptionPane.INFORMATION_MESSAGE))
          .build();
  private final JPanel rememberPasswordRow =
      new JPanelBuilder()
          .boxLayoutHorizontal()
          .add(rememberPassword)
          .add(rememberPasswordHelpButton)
          .build();
  private final List<JComponent> login =
      List.of(
          usernameLabel,
          usernameField,
          passwordLabel,
          passwordField,
          otpLabel,
          otpField,
          rememberPasswordRow,
          testForum);
  private final JCheckBox attachSaveGameToSummary = new JCheckBox("Attach save game to summary");
  private final JCheckBox alsoPostAfterCombatMove = new JCheckBox("Also post after combat move");
  private final CollapsiblePanel moreOptions =
      new CollapsiblePanel(
          new JPanelBuilder()
              .boxLayoutVertical()
              .add(attachSaveGameToSummary)
              .add(alsoPostAfterCombatMove)
              .build(),
          "More options",
          collapsed -> {});

  /** The forum last asked whose its session is, so it is asked once per choice. */
  private String askedForum = "";

  private int row;

  ForumPosterEditor(final ForumPosterEditorViewModel viewModel) {
    super(new GridBagLayout());
    this.viewModel = viewModel;
    viewModel.setView(this);
    // If password is already stored we already remember
    if (viewModel.isForumPasswordValid()) {
      rememberPassword.setSelected(true);
    }
    rememberPassword.addActionListener(
        e -> viewModel.setRememberPassword(rememberPassword.isSelected()));

    viewModel.getForumSelectionOptions().forEach(forums::addItem);
    forums.setSelectedItem(viewModel.getForumSelection());
    // also fired when the view model's forum is shown: only the player's choice counts
    forums.addActionListener(
        e -> {
          if (!viewModel.getForumSelection().equals(forums.getSelectedItem())) {
            viewModel.setForumSelection((String) forums.getSelectedItem());
            usernameField.setText(viewModel.getForumUsername());
            passwordField.setText(viewModel.getForumPassword());
            refresh();
          }
        });
    addRow(new JLabel("Forum"), forums, helpButton);
    signInStatus.setForeground(LookAndFeel.getSecondaryTextColor());
    addRow(null, signInStatus, null);

    final ButtonGroup threadButtons = new ButtonGroup();
    threadButtons.add(newThread);
    threadButtons.add(existingThread);
    newThread.addActionListener(e -> chooseThread(ThreadChoice.NEW));
    existingThread.addActionListener(e -> chooseThread(ThreadChoice.EXISTING));
    addRow(threadLabel, threadChoice, null);
    new DocumentListenerBuilder(
            () -> afterTyping(() -> viewModel.setNewThreadTitle(titleField.getText())))
        .attachTo(titleField);
    addRow(titleLabel, titleField, null);
    new DocumentListenerBuilder(
            () -> afterTyping(() -> viewModel.setTopicId(topicIdField.getText())))
        .attachTo(topicIdField);
    topicIdField.setToolTipText(
        "Paste the thread's address from your browser, eg"
            + " https://forums.triplea-game.org/topic/123/my-game, or type its number");
    viewPosts.addActionListener(e -> viewModel.viewForumButtonClicked());
    chooseGameButton.addActionListener(e -> chooseGame());
    addRow(topicIdLabel, topicIdField, threadActions);

    new DocumentListenerBuilder(
            () -> afterTyping(() -> viewModel.setForumUsername(usernameField.getText().trim())))
        .attachTo(usernameField);
    addRow(usernameLabel, usernameField, null);
    new DocumentListenerBuilder(
            () -> afterTyping(() -> viewModel.setForumPassword(passwordField.getPassword())))
        .attachTo(passwordField);
    addRow(passwordLabel, passwordField, null);
    new DocumentListenerBuilder(() -> viewModel.setOtpCode(otpField.getText())).attachTo(otpField);
    addRow(otpLabel, otpField, null);
    testForum.addActionListener(e -> viewModel.testPostButtonClicked());
    addRow(null, rememberPasswordRow, testForum);

    attachSaveGameToSummary.addActionListener(
        e -> viewModel.setAttachSaveGameToSummary(attachSaveGameToSummary.isSelected()));
    alsoPostAfterCombatMove.addActionListener(
        e -> viewModel.setAlsoPostAfterCombatMove(alsoPostAfterCombatMove.isSelected()));
    moreOptions.setCollapsed(true);
    add(
        moreOptions,
        new GridBagConstraintsBuilder(1, row)
            .gridWidth(2)
            .anchor(GridBagConstraintsAnchor.WEST)
            .fill(GridBagConstraintsFill.HORIZONTAL)
            .insets(6, 0, 0, 0)
            .build());
    viewModelChanged(viewModel);
  }

  /**
   * A row of the form: a label or none, the field, which takes the room left, and a button or none.
   * A hidden row takes no room: nothing in it stays visible.
   */
  private void addRow(
      @Nullable final JComponent label, final JComponent field, @Nullable final JComponent extra) {
    if (label != null) {
      add(
          label,
          new GridBagConstraintsBuilder(0, row)
              .anchor(GridBagConstraintsAnchor.WEST)
              .insets(0, 0, 4, 8)
              .build());
    }
    add(
        field,
        new GridBagConstraintsBuilder(1, row)
            .weightX(1.0)
            .anchor(GridBagConstraintsAnchor.WEST)
            .fill(GridBagConstraintsFill.HORIZONTAL)
            .insets(0, 0, 4, 0)
            .build());
    if (extra != null) {
      add(
          extra,
          new GridBagConstraintsBuilder(2, row)
              .anchor(GridBagConstraintsAnchor.EAST)
              .insets(0, 4, 4, 0)
              .build());
    }
    row++;
  }

  private void chooseThread(final ThreadChoice choice) {
    viewModel.setThreadChoice(choice);
    refresh();
  }

  /** Hands what the player typed on to the view model, off the EDT, then shows its state. */
  private void afterTyping(final Runnable update) {
    update.run();
    SwingUtilities.invokeLater(this::refresh);
  }

  /** Lists the forum's current games; the one the player picks becomes the game's thread. */
  private void chooseGame() {
    final Set<String> old = OldForumGames.keys();
    final String forumUrl = viewModel.getForumUrl();
    viewModel
        .newSessionClient()
        .flatMap(
            forum ->
                GameThreadChooser.choose(
                    this,
                    forum,
                    // as on the Play by forum page: finished games the player moved away stay away
                    thread -> old.contains(OldForumGames.keyOf(forumUrl, thread.topicId())),
                    // as when the screen asked: signed out, so no turn goes out with the session
                    () -> {
                      viewModel.sessionRanOut();
                      refresh();
                    }))
        .ifPresent(thread -> viewModel.chooseExistingThread(String.valueOf(thread.topicId())));
  }

  @Override
  public void viewModelChanged(final ForumPosterEditorViewModel forumPosterEditorViewModel) {
    forums.setSelectedItem(forumPosterEditorViewModel.getForumSelection());
    titleField.setText(forumPosterEditorViewModel.getNewThreadTitle());
    topicIdField.setText(forumPosterEditorViewModel.getTopicId());
    usernameField.setText(forumPosterEditorViewModel.getForumUsername());
    passwordField.setText(String.valueOf(forumPosterEditorViewModel.getForumPassword()));
    alsoPostAfterCombatMove.setSelected(forumPosterEditorViewModel.isAlsoPostAfterCombatMove());
    attachSaveGameToSummary.setSelected(forumPosterEditorViewModel.isAttachSaveGameToSummary());
    refresh();
  }

  /**
   * Shows what fits the forum and the sign-in: signed in, the game's thread and the options; on the
   * A&amp;A forum without a session also the login; on forums.triplea-game.org without one only the
   * hint to sign in first. Asks the forum whose the session is once per forum.
   */
  private void refresh() {
    final boolean password = viewModel.usesPassword();
    // with a token kept from before the turns go out too: to the thread the screen shows
    final boolean thread = viewModel.isSignedIn() || password || viewModel.hasKeptToken();
    final boolean newOne = viewModel.isNewThread();
    signInStatus.setText(viewModel.getSignInText());
    signInStatus.setToolTipText(viewModel.getSignInText());
    helpMessage.setText(viewModel.getForumProviderHelpText());
    threadLabel.setVisible(thread);
    threadChoice.setVisible(thread);
    newThread.setEnabled(viewModel.canStartThread());
    newThread.setToolTipText(
        viewModel.canStartThread()
            ? "TripleA starts the thread in the forum's Play by Forum category when the game starts"
            : "TripleA starts threads on forums.triplea-game.org only, once you are signed in");
    newThread.setSelected(newOne);
    existingThread.setSelected(!newOne);
    titleLabel.setVisible(thread && newOne);
    titleField.setVisible(thread && newOne);
    topicIdLabel.setVisible(thread && !newOne);
    topicIdField.setVisible(thread && !newOne);
    threadActions.setVisible(thread && !newOne);
    // the forum shows its games only to a signed-in player; the state is known, so no slow read
    chooseGameButton.setEnabled(viewModel.isSignedIn());
    chooseGameButton.setToolTipText(
        viewModel.isSignedIn()
            ? "Your games and the forum's newest games: take one instead of pasting its address"
            : "Sign in on Multiplayer > Play by forum to choose from your games");
    login.forEach(component -> component.setVisible(password));
    moreOptions.setVisible(thread);
    SwingComponents.highlightLabelIfNotValid(viewModel.isNewThreadTitleValid(), titleLabel);
    SwingComponents.highlightLabelIfNotValid(viewModel.isTopicIdValid(), topicIdLabel);
    SwingComponents.highlightLabelIfNotValid(viewModel.isForumUsernameValid(), usernameLabel);
    SwingComponents.highlightLabelIfNotValid(viewModel.isForumPasswordValid(), passwordLabel);
    viewPosts.setEnabled(viewModel.isViewForumPostButtonEnabled());
    testForum.setEnabled(viewModel.isTestForumPostButtonEnabled());
    if (!askedForum.equals(viewModel.getForumSelection())) {
      askedForum = viewModel.getForumSelection();
      viewModel.newSessionClient().ifPresent(this::askWhoIsSignedIn);
    }
    revalidate();
    repaint();
  }

  /** Asks the forum in the background whose the session is; the answer shows if still current. */
  private void askWhoIsSignedIn(final NodeBbGameThreads forum) {
    final String asked = askedForum;
    ThreadRunner.runInNewThread(
        () -> {
          final Runnable answer = answerOf(forum);
          SwingUtilities.invokeLater(
              () -> {
                if (asked.equals(viewModel.getForumSelection())) {
                  answer.run();
                  refresh();
                }
              });
        });
  }

  private Runnable answerOf(final NodeBbGameThreads forum) {
    try {
      final Optional<Player> player = forum.signedInPlayer();
      return () -> player.ifPresentOrElse(viewModel::signedInAs, viewModel::sessionRanOut);
    } catch (final IOException e) {
      return () -> viewModel.signInCheckFailed(e.getMessage());
    }
  }

  boolean shouldRevokeTokenOnShutdown() {
    return viewModel.shouldRevokeTokenOnShutdown();
  }

  void revokeToken() {
    viewModel.revokeToken();
  }

  void requestToken() {
    viewModel.acquireTokenAndDeletePassword();
  }
}
