package games.strategy.engine.framework.startup.ui.posted.game.pbf;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import games.strategy.engine.data.GamePlayer;
import games.strategy.engine.data.GameState;
import games.strategy.engine.data.properties.GameProperties;
import games.strategy.engine.framework.startup.ui.posted.game.HelpTexts;
import games.strategy.engine.framework.startup.ui.posted.game.pbf.test.post.SwingTestPostProgressDisplayFactory;
import games.strategy.engine.framework.startup.ui.posted.game.pbf.test.post.TestPostAction;
import games.strategy.engine.framework.ui.background.BackgroundTaskRunner;
import games.strategy.engine.posted.game.pbf.IForumPoster;
import games.strategy.engine.posted.game.pbf.NodeBbForumPoster;
import games.strategy.engine.posted.game.pbf.NodeBbGameThreads;
import games.strategy.engine.posted.game.pbf.NodeBbGameThreads.Player;
import games.strategy.engine.posted.game.pbf.NodeBbTokenGenerator;
import games.strategy.triplea.UrlConstants;
import games.strategy.triplea.settings.ClientSetting;
import java.nio.CharBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.SwingUtilities;
import lombok.Getter;
import lombok.Setter;
import org.triplea.java.Interruptibles;
import org.triplea.java.Postconditions;
import org.triplea.java.StringUtils;
import org.triplea.java.ViewModelListener;

/**
 * The forum a game's turns go to and the game's thread there. Signed in on the Play by forum page
 * (a browser session), the player starts a new thread or names an existing one; without a session
 * only the A&amp;A forum still takes the old login with username and password.
 */
class ForumPosterEditorViewModel {
  private static final int DUMMY_PASSWORD_LENGTH = 4;

  private static final String SIGN_IN_PAGE = "Multiplayer > Play by forum";

  /** A thread's address, eg "https://forums.triplea-game.org/topic/123/some-title": its number. */
  private static final Pattern THREAD_ADDRESS = Pattern.compile("/topic/(\\d+)(?:[/?#]|$)");

  /** Where the game's turns go. */
  enum ThreadChoice {
    NEW,
    EXISTING
  }

  private final Runnable readyCallback;

  @Setter(onMethod_ = @VisibleForTesting)
  private BiConsumer<String, Integer> testPostAction =
      new TestPostAction(new SwingTestPostProgressDisplayFactory());

  @Setter private ViewModelListener<ForumPosterEditorViewModel> view;

  @Setter(onMethod_ = @VisibleForTesting)
  private BiConsumer<String, Integer> viewForumPostAction =
      (forumName, topicId) -> NodeBbForumPoster.newInstanceByName(forumName, topicId).viewPosted();

  private String forumSelection;

  /** Whether the Play by forum page keeps the player's browser session for the chosen forum. */
  private boolean hasSession;

  /** What the screen tells of the sign-in, eg whose it is or to sign in first. */
  @Getter private String signInText = "";

  private ThreadChoice threadChoice = ThreadChoice.NEW;
  @Getter private String newThreadTitle = "";

  /** The existing thread as entered: its number or its address. */
  @Getter private String topicId = "";

  /** Whether the player chose the thread on this screen: typed, picked, or new or existing. */
  private boolean threadChosenHere;

  @Setter @Getter private boolean attachSaveGameToSummary = true;
  @Setter @Getter private boolean alsoPostAfterCombatMove;
  @Getter private String forumUsername;
  private boolean forumTokenExists;

  /** Whether the chosen forum's login token from before is stored, read once per forum. */
  private boolean keptToken;

  private char[] tempPassword = new char[0];
  @Setter private String otpCode = "";
  @Setter private boolean rememberPassword;

  @Setter(onMethod_ = {@VisibleForTesting})
  private Supplier<NodeBbTokenGenerator> tokenGeneratorSupplier =
      () -> new NodeBbTokenGenerator(getForumUrl());

  ForumPosterEditorViewModel(final Runnable readyCallback) {
    this.readyCallback = readyCallback;
    rememberPassword = ClientSetting.rememberForumPassword.getValue().orElse(false);
    setForumSelection("");
  }

  ForumPosterEditorViewModel(final Runnable readyCallback, final GameProperties properties) {
    this.readyCallback = readyCallback;
    rememberPassword = ClientSetting.rememberForumPassword.getValue().orElse(false);
    populateFromGameProperties(properties);
  }

  void setForumPassword(final char[] password) {
    // Check if the password is a dummy. Document model events are only supposed
    // to fire if a user types and enters a valid password, in case an event from UI
    // was thrown from UI for another reason and the password field still has the
    // dummy password, then ignore it.
    if (isDummyPassword(password)) {
      return;
    }
    Arrays.fill(tempPassword, '\0');
    tempPassword = password;
    forumTokenExists = password.length > 0 || keptToken;
    readyCallback.run();
  }

  private NodeBbTokenGenerator.TokenInfo renewToken() {
    revokeToken();

    final var nodeBbTokenGenerator = tokenGeneratorSupplier.get();

    return nodeBbTokenGenerator.generateToken(
        forumUsername, new String(tempPassword), Strings.emptyToNull(otpCode));
  }

  void acquireTokenAndDeletePassword() {
    if (!usesPassword() || CharBuffer.wrap(tempPassword).chars().allMatch(c -> c == 0)) {
      // a password typed for another forum goes as well
      Arrays.fill(tempPassword, '\0');
      return;
    }
    final ClientSetting<char[]> tokenSetting = getTokenSetting();

    final ClientSetting<Integer> uidSetting = getUidSetting();

    // Testing doesn't support EDT on headless devices so we need the check
    final Optional<NodeBbTokenGenerator.TokenInfo> result =
        SwingUtilities.isEventDispatchThread()
            ? Interruptibles.awaitResult(
                    () ->
                        BackgroundTaskRunner.runInBackgroundAndReturn(
                            "Logging in...", this::renewToken))
                .result
            : Optional.of(renewToken());

    result.ifPresent(
        tokenInfo -> {
          tokenSetting.setValueAndFlush(tokenInfo.getToken().toCharArray());
          keptToken = !tokenInfo.getToken().isEmpty();
          uidSetting.setValueAndFlush(tokenInfo.getUserId());
        });
    Arrays.fill(tempPassword, '\0');
  }

  private ClientSetting<char[]> getTokenSetting() {
    return isTripleaForum() ? ClientSetting.tripleaForumToken : ClientSetting.aaForumToken;
  }

  private boolean isTripleaForum() {
    return forumSelection.equals(NodeBbForumPoster.TRIPLEA_FORUM_DISPLAY_NAME);
  }

  private ClientSetting<Integer> getUidSetting() {
    return isTripleaForum() ? ClientSetting.tripleaForumUserId : ClientSetting.aaForumUserId;
  }

  String getForumUrl() {
    // as the session and the token: any forum but the TripleA forum is the A&A forum here
    return isTripleaForum() ? UrlConstants.TRIPLEA_FORUM : UrlConstants.AXIS_AND_ALLIES_FORUM;
  }

  private boolean isDummyPassword(final char[] password) {
    return password.length > 0 && CharBuffer.wrap(password).chars().allMatch(c -> c == '*');
  }

  void setForumUsername(final String username) {
    final ClientSetting<char[]> usernameSetting =
        isTripleaForum() ? ClientSetting.tripleaForumUsername : ClientSetting.aaForumUsername;

    usernameSetting.setValueAndFlush(username.toCharArray());
    forumUsername = username;
    readyCallback.run();
  }

  /**
   * Chooses the forum; a blank name chooses the one the player is signed in to on the Play by forum
   * page, forums.triplea-game.org first, or else that.
   */
  synchronized void setForumSelection(final String forumSelection) {
    final String chosen =
        Optional.ofNullable(forumSelection)
            .filter(name -> !name.isBlank())
            .orElseGet(ForumPosterEditorViewModel::defaultForum);
    Postconditions.assertState(
        chosen != null && !chosen.isBlank(),
        "Forum selection is driven by a drop-down to ensure user can never set it to null, "
            + "if setting to null from a game properties, we default to the first available "
            + "selection entry, forum selection should never be null or empty.");
    if (!chosen.equals(this.forumSelection)) {
      this.forumSelection = chosen;
      // read once per forum: each read decrypts the setting, slowly on purpose
      keptToken = getTokenSetting().getValue().map(token -> token.length > 0).orElse(false);
      hasSession = sessionOf(chosen).isPresent();
      signInText = hasSession ? "Checking your sign-in..." : notSignedInText();
      threadChoice = defaultThreadChoice();
    }
    forumUsername =
        isTripleaForum()
            ? ClientSetting.tripleaForumUsername.getValue().map(String::valueOf).orElse("")
            : ClientSetting.aaForumUsername.getValue().map(String::valueOf).orElse("");
    forumTokenExists = keptToken;
    readyCallback.run();
  }

  private static String defaultForum() {
    return NodeBbForumPoster.availablePosters().stream()
        .filter(forum -> sessionOf(forum).isPresent())
        .findFirst()
        .orElse(NodeBbForumPoster.TRIPLEA_FORUM_DISPLAY_NAME);
  }

  /** The browser session the Play by forum page keeps for the forum; empty when signed out. */
  private static Optional<String> sessionOf(final String forum) {
    return NodeBbGameThreads.sessionOf(forum);
  }

  private String notSignedInText() {
    if (hasKeptToken()) {
      return "Not signed in: the turns go out with the login token kept from before."
          + (getPlayByForumCategory() > 0
              ? " Sign in on " + SIGN_IN_PAGE + " to start new threads."
              : "");
    }
    return isTripleaForum()
        ? "Sign in first on "
            + SIGN_IN_PAGE
            + ". This forum no longer takes the username and password of older versions."
        : "Not signed in: sign in on " + SIGN_IN_PAGE + ", or enter your forum login below.";
  }

  /**
   * Whether a login token from before is kept for the chosen forum, the stored one itself: a
   * password typed does not count, as the forum's login may refuse it.
   */
  boolean hasKeptToken() {
    return keptToken;
  }

  /** A new game gets a new thread where TripleA can start one, a game with a thread keeps it. */
  private ThreadChoice defaultThreadChoice() {
    return canStartThread() && topicId.isBlank() ? ThreadChoice.NEW : ThreadChoice.EXISTING;
  }

  /**
   * Returns a dummy password value that has the value of {@link #DUMMY_PASSWORD_LENGTH}. This
   * should only be used to set the UI text value and never used as the actual users password.
   */
  String getForumPassword() {
    return forumTokenExists ? "*".repeat(DUMMY_PASSWORD_LENGTH) : "";
  }

  public String getForumSelection() {
    Preconditions.checkNotNull(forumSelection, "Forum selection should never be null");
    return forumSelection;
  }

  synchronized void setTopicId(final String topicId) {
    if (this.topicId.equals(topicId)) {
      return;
    }

    this.topicId = topicId;
    threadChosenHere = true;
    readyCallback.run();
  }

  synchronized void setNewThreadTitle(final String title) {
    if (newThreadTitle.equals(title)) {
      return;
    }
    newThreadTitle = title;
    readyCallback.run();
  }

  synchronized void setThreadChoice(final ThreadChoice choice) {
    // a click on the choice shown already chooses nothing
    if (threadChoice != choice) {
      threadChosenHere = true;
    }
    threadChoice = choice;
    readyCallback.run();
  }

  Iterable<String> getForumSelectionOptions() {
    final Iterable<String> selections = NodeBbForumPoster.availablePosters();
    Postconditions.assertState(selections.iterator().hasNext());
    return selections;
  }

  /**
   * Whether the game can start: signed in with a title for the new thread or an existing thread;
   * else on the A&amp;A forum with an existing thread and the login; else with an existing thread
   * and a login token kept from before, which the turns' posts still use.
   */
  synchronized boolean areFieldsValid() {
    if (usesPassword()) {
      return isTopicIdValid() && isForumUsernameValid() && isForumPasswordValid();
    }
    if (!isSignedIn()) {
      return hasKeptToken() && !isNewThread() && isTopicIdValid();
    }
    return isNewThread() ? isNewThreadTitleValid() : isTopicIdValid();
  }

  /** Whether the player is signed in to the chosen forum on the Play by forum page. */
  boolean isSignedIn() {
    return hasSession;
  }

  /**
   * Whether the player signs in with username and password here: without a session on the A&amp;A
   * forum, which still takes them.
   */
  boolean usesPassword() {
    return !isSignedIn() && !isTripleaForum();
  }

  /** Whether TripleA can start the game's thread: signed in to a forum with a known category. */
  boolean canStartThread() {
    return isSignedIn() && getPlayByForumCategory() > 0;
  }

  private int getPlayByForumCategory() {
    return isTripleaForum() ? NodeBbGameThreads.TRIPLEA_PLAY_BY_FORUM_CATEGORY : 0;
  }

  /** Whether the game starts a new thread, when it starts. */
  boolean isNewThread() {
    return threadChoice == ThreadChoice.NEW && canStartThread();
  }

  boolean isNewThreadTitleValid() {
    return !newThreadTitle.isBlank();
  }

  String getForumProviderHelpText() {
    return isTripleaForum() ? HelpTexts.TRIPLEA_FORUM : HelpTexts.AXIS_AND_ALLIES_DOT_ORG_FORUM;
  }

  boolean isForumPasswordValid() {
    return forumTokenExists;
  }

  boolean isForumUsernameValid() {
    return forumUsername != null && !forumUsername.isBlank();
  }

  boolean isViewForumPostButtonEnabled() {
    return isTopicIdValid();
  }

  boolean isTestForumPostButtonEnabled() {
    return usesPassword() && areFieldsValid();
  }

  boolean isTopicIdValid() {
    return topicIdOf(topicId).isPresent();
  }

  /** The number of the game's existing thread; empty for a new thread or none valid. */
  Optional<Integer> getThreadTopicId() {
    return isNewThread() ? Optional.empty() : topicIdOf(topicId);
  }

  private Optional<Integer> topicIdOf(final String numberOrAddress) {
    return topicIdOf(numberOrAddress, getForumUrl());
  }

  /**
   * The thread's number from what the player entered: the number, eg "123", or the thread's address
   * on the forum, eg "https://forums.triplea-game.org/topic/123/some-title"; none for an address of
   * another forum, whose thread numbers are others.
   */
  @VisibleForTesting
  static Optional<Integer> topicIdOf(final String numberOrAddress, final String forumUrl) {
    final String entered = numberOrAddress.trim();
    // an address, with or without "https://" and "www.": the chosen forum's only, up to a "/"
    final String forum = bareAddress(forumUrl).replaceFirst("/?$", "/");
    if (entered.contains("/") && !bareAddress(entered).startsWith(forum)) {
      return Optional.empty();
    }
    final Matcher address = THREAD_ADDRESS.matcher(entered);
    final String number = address.find() ? address.group(1) : entered;
    return StringUtils.isPositiveInt(number)
        ? Optional.of(Integer.parseInt(number))
        : Optional.empty();
  }

  private static String bareAddress(final String address) {
    return address
        .toLowerCase(Locale.ROOT)
        .replaceFirst("^[a-z]+://", "")
        .replaceFirst("^www\\.", "");
  }

  /**
   * Shows the game's forum settings, eg when the screen opens or another game is chosen. A save
   * that has its thread keeps posting there; a game without one keeps the thread the player chose
   * here, eg before choosing the map, instead of starting a new one.
   */
  public synchronized void populateFromGameProperties(final GameProperties properties) {
    final String gameThread = properties.get(IForumPoster.TOPIC_ID, "");
    if (!gameThread.isBlank() || !threadChosenHere) {
      // the thread chosen here keeps its forum too: a number means another thread elsewhere
      setForumSelection((String) properties.get(IForumPoster.NAME));
      topicId = gameThread;
      threadChoice = defaultThreadChoice();
      threadChosenHere = false;
    }
    this.alsoPostAfterCombatMove = properties.get(IForumPoster.POST_AFTER_COMBAT, false);
    this.attachSaveGameToSummary = properties.get(IForumPoster.INCLUDE_SAVEGAME, true);
    Optional.ofNullable(view).ifPresent(v -> v.viewModelChanged(this));
    readyCallback.run();
  }

  /** The forum said whose the session is. */
  synchronized void signedInAs(final Player player) {
    signInText = "Signed in as " + player.username();
    readyCallback.run();
  }

  /**
   * The forum said the session signs nobody in any more: the player signs in again. It is
   * forgotten, as the Play by forum page forgets it: else the turns' posts went out with it.
   */
  synchronized void sessionRanOut() {
    hasSession = false;
    (isTripleaForum() ? ClientSetting.tripleaForumSession : ClientSetting.aaForumSession)
        .setValueAndFlush(null);
    signInText = "Your sign-in ran out. Sign in again on " + SIGN_IN_PAGE + ".";
    readyCallback.run();
  }

  /** The forum could not be asked whose the session is, eg offline; it counts as signed in. */
  synchronized void signInCheckFailed(final String error) {
    signInText = "Signed in, but the forum could not be asked who you are: " + error;
    readyCallback.run();
  }

  /** The chosen forum with the player's session, to ask and post as the player; empty without. */
  Optional<NodeBbGameThreads> newSessionClient() {
    return sessionOf(forumSelection)
        .map(
            session ->
                NodeBbGameThreads.withSession(getForumUrl(), getPlayByForumCategory(), session));
  }

  /**
   * The game's turns go to an existing thread from now on, eg the one just started, so a second
   * start starts no second thread; an empty number for the player to enter.
   */
  synchronized void useExistingThread(final String topicId) {
    threadChoice = ThreadChoice.EXISTING;
    this.topicId = topicId;
    // eg the thread a start began: the game's, not the player's choice for the next map
    threadChosenHere = false;
    Optional.ofNullable(view).ifPresent(v -> v.viewModelChanged(this));
    readyCallback.run();
  }

  /**
   * The player chose the game's thread, eg by Choose game: it stays when the player then chooses
   * the map, unlike the thread a start began, which belongs to the game that started.
   */
  synchronized void chooseExistingThread(final String topicId) {
    useExistingThread(topicId);
    threadChosenHere = true;
  }

  synchronized void viewForumButtonClicked() {
    topicIdOf(topicId).ifPresent(id -> viewForumPostAction.accept(forumSelection, id));
  }

  synchronized void testPostButtonClicked() {
    if (isTestForumPostButtonEnabled()) {
      acquireTokenAndDeletePassword();
      testPostAction.accept(forumSelection, topicIdOf(topicId).orElseThrow());
    }
  }

  boolean shouldRevokeTokenOnShutdown() {
    return usesPassword() && !rememberPassword;
  }

  void revokeToken() {
    final ClientSetting<char[]> tokenSetting = getTokenSetting();
    final ClientSetting<Integer> uidSetting = getUidSetting();

    tokenSetting
        .getValue()
        .ifPresent(
            token ->
                uidSetting
                    .getValue()
                    .ifPresent(
                        uid -> {
                          tokenGeneratorSupplier.get().revokeToken(new String(token), uid);
                          // revoked, it posts nothing: so it counts as kept no more
                          tokenSetting.resetValue();
                          keptToken = false;
                        }));
  }

  /**
   * The suggested title of the game's new thread: the game and its two sides, eg "Global 1940: Axis
   * vs Allies", the sides in the order of the game's nations; else "Global 1940 game".
   */
  static String newThreadTitleOf(final GameState data) {
    final List<GamePlayer> nations = visibleNations(data);
    final List<String> alliances =
        nations.stream()
            .flatMap(nation -> data.getAllianceTracker().getAlliancesPlayerIsIn(nation).stream())
            .distinct()
            .toList();
    final List<String> sides =
        alliances.size() == 2 ? alliances : nations.stream().map(GamePlayer::getName).toList();
    return sides.size() == 2
        ? data.getGameName() + ": " + sides.getFirst() + " vs " + sides.getLast()
        : data.getGameName() + " game";
  }

  private static List<GamePlayer> visibleNations(final GameState data) {
    return data.getPlayerList().getPlayers().stream().filter(nation -> !nation.isHidden()).toList();
  }

  /** The first post of the game's new thread: the game and its nations. */
  static String firstPostOf(final GameState data) {
    return "A TripleA game of "
        + data.getGameName()
        + " with "
        + String.join(", ", visibleNations(data).stream().map(GamePlayer::getName).toList())
        + ". The turns follow in this thread.";
  }
}
