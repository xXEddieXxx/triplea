package games.strategy.engine.framework.startup.ui.posted.game.pbf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.base.Preconditions;
import games.strategy.engine.data.GameData;
import games.strategy.engine.data.properties.GameProperties;
import games.strategy.engine.framework.startup.ui.posted.game.pbf.ForumPosterEditorViewModel.ThreadChoice;
import games.strategy.engine.posted.game.pbf.IForumPoster;
import games.strategy.engine.posted.game.pbf.NodeBbForumPoster;
import games.strategy.engine.posted.game.pbf.NodeBbGameThreads.Player;
import games.strategy.engine.posted.game.pbf.NodeBbTokenGenerator;
import games.strategy.triplea.settings.AbstractClientSettingTestCase;
import games.strategy.triplea.settings.ClientSetting;
import games.strategy.triplea.xml.TestMapGameData;
import java.util.List;
import java.util.function.BiConsumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.triplea.java.ViewModelListener;

@ExtendWith(MockitoExtension.class)
class ForumPosterEditorViewModelTest extends AbstractClientSettingTestCase {

  @Mock private GameData gameData;
  @Mock private ViewModelListener<ForumPosterEditorViewModel> viewModelListener;
  @Mock private BiConsumer<String, Integer> viewForumPostAction;
  @Mock private BiConsumer<String, Integer> testPostAction;
  @Mock private Runnable readyCallback;

  @Test
  @DisplayName("Ensure forum selection is set when loading wtihout game properties")
  void initializationSetsForumSelection() {
    final ForumPosterEditorViewModel viewModel = new ForumPosterEditorViewModel(() -> {});

    assertThat(viewModel.getForumSelection()).isNotNull();
  }

  @Test
  @DisplayName("Ensure we can use GameProperties to set initial values")
  void verifyInitializationWithGameProperties() {
    final GameProperties gameProperties = new GameProperties(gameData);
    gameProperties.set(IForumPoster.NAME, "forumName");
    gameProperties.set(IForumPoster.TOPIC_ID, "topicId");
    gameProperties.set(IForumPoster.POST_AFTER_COMBAT, true);
    gameProperties.set(IForumPoster.INCLUDE_SAVEGAME, false);

    final ForumPosterEditorViewModel viewModel =
        new ForumPosterEditorViewModel(() -> {}, gameProperties);

    assertThat(viewModel.getForumSelection()).isEqualTo("forumName");
    assertThat(viewModel.getTopicId()).isEqualTo("topicId");
    assertThat(viewModel.isTopicIdValid()).as("topic id is not numeric => not valid").isFalse();
    assertThat(viewModel.isAlsoPostAfterCombatMove()).isTrue();
    assertThat(viewModel.isAttachSaveGameToSummary()).isFalse();
  }

  @Test
  @DisplayName("Ensure we can use partial GameProperties to set initial values")
  void verifyInitializeWithPartialGameProperties() {
    final GameProperties gameProperties = new GameProperties(gameData);
    gameProperties.set(IForumPoster.POST_AFTER_COMBAT, true);

    final ForumPosterEditorViewModel viewModel =
        new ForumPosterEditorViewModel(() -> {}, gameProperties);

    assertThat(viewModel.getForumSelection())
        .as("Default forum should be first NodeBB forum when not otherwise specified")
        .isEqualTo(NodeBbForumPoster.availablePosters().iterator().next());
    assertThat(viewModel.getTopicId())
        .as("Expecting default empty topic id string when not specified in game properties")
        .isEmpty();
    assertThat(viewModel.isAlsoPostAfterCombatMove())
        .as("We specified this value in game properties, should be true")
        .isTrue();
    assertThat(viewModel.isAttachSaveGameToSummary())
        .as(
            "Attach save game summary defaults to true, when not specified in game properties, "
                + "we expect the default value")
        .isTrue();
  }

  @DisplayName("Topic ID should only be valid for positive numbers")
  @ParameterizedTest
  @ValueSource(strings = {"", "nAn", "0.0", "0.", "-1", "0", "zero", "1,000"})
  void topicIdNotValidOnNonPositiveOrNonNumbers(final String invalidValue) {
    final ForumPosterEditorViewModel viewModel = new ForumPosterEditorViewModel(() -> {});
    viewModel.setForumSelection(invalidValue);

    assertThat(viewModel.isTopicIdValid()).isFalse();
  }

  @Test
  void viewForumPostCallsPostActionWhenDataIsValid() {
    final ForumPosterEditorViewModel viewModel = new ForumPosterEditorViewModel(() -> {});
    viewModel.setForumSelection("forumSelection");
    viewModel.setTopicId("100");
    Preconditions.checkState(
        viewModel.isTopicIdValid(), "Test makes sense only if topic id is valid");
    viewModel.setViewForumPostAction(viewForumPostAction);

    viewModel.viewForumButtonClicked();

    verify(viewForumPostAction).accept("forumSelection", 100);
  }

  @DisplayName("View Forum button click is no-op if topic id or forum selection are not valid")
  @ParameterizedTest
  @ValueSource(strings = {"0.0", "-1", "2311111111111", "not a number", "NaN"})
  void viewForumPostNoOpOnInvalidTopicId(final String topicId) {
    final ForumPosterEditorViewModel viewModel = new ForumPosterEditorViewModel(() -> {});
    viewModel.setTopicId(topicId);
    viewModel.setViewForumPostAction(viewForumPostAction);

    viewModel.viewForumButtonClicked();

    verify(viewForumPostAction, never()).accept(any(), any());
  }

  private ForumPosterEditorViewModel givenViewModelWithInvalidFieldSettings(
      final String topicId, final String username, final char[] password) {
    final ForumPosterEditorViewModel viewModel = new ForumPosterEditorViewModel(() -> {});
    // the forum that still takes the login with username and password
    viewModel.setForumSelection(NodeBbForumPoster.AXIS_AND_ALLIES_ORG_DISPLAY_NAME);
    viewModel.setTopicId(topicId);
    viewModel.setForumUsername(username);
    viewModel.setForumPassword(password);
    return viewModel;
  }

  @SuppressWarnings("unused")
  private static List<Arguments> invalidForumSettings() {
    return List.of(
        // invalid topic IDs
        Arguments.of("not a number", "user", new char[] {'a'}),
        Arguments.of("", "user", new char[] {'a'}),
        Arguments.of("0.0", "user", new char[] {'a'}),
        Arguments.of("0", "user", new char[] {'a'}),
        Arguments.of("-1", "user", new char[] {'a'}),
        // missing user
        Arguments.of("1", "", new char[] {'a'}),
        // missing password
        Arguments.of("1", "", new char[] {}));
  }

  @DisplayName("Ensure fields are not valid if forum selection or topic id are not valid")
  @ParameterizedTest
  @MethodSource("invalidForumSettings")
  void verifyInvalidForumSettings(
      final String topicId, final String username, final char[] password) {
    final ForumPosterEditorViewModel viewModel =
        givenViewModelWithInvalidFieldSettings(topicId, username, password);

    assertThat(viewModel.areFieldsValid()).isFalse();
  }

  @DisplayName("Ensure test post button is no-op if fields are not valid")
  @ParameterizedTest
  @MethodSource("invalidForumSettings")
  void forumPostTestButtonIsNoOpOnInvalidData(
      final String topicId, final String username, final char[] password) {
    final ForumPosterEditorViewModel viewModel =
        givenViewModelWithInvalidFieldSettings(topicId, username, password);
    viewModel.setTestPostAction(testPostAction);

    viewModel.testPostButtonClicked();
    verify(testPostAction, never()).accept(any(), any());
  }

  @Test
  void forumPostButtonIsActiveWithValidData() {
    final ForumPosterEditorViewModel viewModel = new ForumPosterEditorViewModel(() -> {});
    viewModel.setTestPostAction(testPostAction);
    viewModel.setForumSelection("forumSelection");
    viewModel.setTopicId("20");
    Preconditions.checkState(
        viewModel.isTopicIdValid(), "Test makes sense only if topic id is valid");
    viewModel.setForumUsername("username");
    viewModel.setForumPassword(new char[] {'a', 'b'});
    final NodeBbTokenGenerator tokenGenerator = mock(NodeBbTokenGenerator.class);
    viewModel.setTokenGeneratorSupplier(() -> tokenGenerator);
    final NodeBbTokenGenerator.TokenInfo tokenInfo = mock(NodeBbTokenGenerator.TokenInfo.class);
    when(tokenGenerator.generateToken(any(), any(), any())).thenReturn(tokenInfo);
    when(tokenInfo.getToken()).thenReturn("");

    viewModel.testPostButtonClicked();

    verify(testPostAction).accept("forumSelection", 20);
    // No Token to revoke
    verify(tokenGenerator, times(0)).revokeToken(any(), anyInt());
  }

  @DisplayName("A password typed for one forum is wiped when the game starts on another")
  @Test
  void passwordTypedForOtherForumIsWiped() {
    givenTripleaForumSession();
    final ForumPosterEditorViewModel viewModel = new ForumPosterEditorViewModel(readyCallback);
    final NodeBbTokenGenerator tokenGenerator = mock(NodeBbTokenGenerator.class);
    viewModel.setTokenGeneratorSupplier(() -> tokenGenerator);
    viewModel.setForumSelection(NodeBbForumPoster.AXIS_AND_ALLIES_ORG_DISPLAY_NAME);
    final char[] password = {'a', 'b'};
    viewModel.setForumPassword(password);
    viewModel.setForumSelection(NodeBbForumPoster.TRIPLEA_FORUM_DISPLAY_NAME);

    // signed in there: no login, and the password goes
    viewModel.acquireTokenAndDeletePassword();
    viewModel.setForumSelection(NodeBbForumPoster.AXIS_AND_ALLIES_ORG_DISPLAY_NAME);
    viewModel.acquireTokenAndDeletePassword();

    assertThat(password).as("wiped when the game starts on the other forum").containsOnly('\0');
    verify(tokenGenerator, never()).generateToken(any(), any(), any());
  }

  @DisplayName("Ensure fields are valid if forum selection, topic id and credentials are set")
  @Test
  void validForumSettings() {
    final ForumPosterEditorViewModel viewModel = new ForumPosterEditorViewModel(() -> {});
    viewModel.setForumSelection("forumSelection");
    viewModel.setTopicId("20");
    viewModel.setForumUsername("username");
    viewModel.setForumPassword(new char[] {'a', 'b'});
    Preconditions.checkState(
        viewModel.isTopicIdValid(), "Test makes sense only if topic id is valid");

    assertThat(viewModel.areFieldsValid()).isTrue();
  }

  @Test
  void topicId() {
    final ForumPosterEditorViewModel viewModel = new ForumPosterEditorViewModel(readyCallback);
    viewModel.setTopicId("");

    verify(readyCallback).run();
  }

  @Test
  void readyCallbackIsInvokedOnConstructionWhenForumIsSetToDefaultValue() {
    new ForumPosterEditorViewModel(readyCallback);

    verify(readyCallback).run();
  }

  @Test
  void updatingUsernameInvokesReadyCallback() {
    final ForumPosterEditorViewModel viewModel = new ForumPosterEditorViewModel(readyCallback);
    viewModel.setForumUsername("");

    verify(readyCallback, times(2)).run();
  }

  @Test
  void updatingPasswordInvokesReadyCallback() {
    final ForumPosterEditorViewModel viewModel = new ForumPosterEditorViewModel(readyCallback);
    viewModel.setForumPassword(new char[] {'a'});

    verify(readyCallback, times(2)).run();
  }

  @Test
  void forumSelection() {
    final ForumPosterEditorViewModel viewModel = new ForumPosterEditorViewModel(readyCallback);
    viewModel.setForumSelection("");

    // called once in construction, called again on 'setForumSelection'
    verify(readyCallback, times(2)).run();
  }

  @Test
  void viewCallbackIsInvokedWhenViewModelDataIsChanged() {
    final ForumPosterEditorViewModel viewModel = new ForumPosterEditorViewModel(readyCallback);
    viewModel.setView(viewModelListener);
    viewModel.populateFromGameProperties(new GameProperties(gameData));

    verify(viewModelListener).viewModelChanged(viewModel);
  }

  @Test
  void initiallyUsernameIsInvalidDueToBeingBlank() {
    final ForumPosterEditorViewModel viewModel = new ForumPosterEditorViewModel(readyCallback);
    assertThat(viewModel.isForumUsernameValid()).isFalse();
  }

  @Test
  void nonBlankUsernameIsValid() {
    final ForumPosterEditorViewModel viewModel = new ForumPosterEditorViewModel(readyCallback);
    viewModel.setForumUsername("name");
    assertThat(viewModel.isForumUsernameValid()).isTrue();
  }

  @Test
  void initiallyPasswordIsInvalidDueToBeingBlank() {
    final ForumPosterEditorViewModel viewModel = new ForumPosterEditorViewModel(readyCallback);
    assertThat(viewModel.isForumPasswordValid()).isFalse();
  }

  @Test
  void nonBlankPasswordIsValid() {
    final ForumPosterEditorViewModel viewModel = new ForumPosterEditorViewModel(readyCallback);
    viewModel.setForumPassword(new char[] {'a'});
    assertThat(viewModel.isForumPasswordValid()).isTrue();
  }

  @Test
  @DisplayName("If password form UI is the dummy password, then it should not be set")
  void doNotSetADummyPassword() {
    ClientSetting.aaForumUsername.setValueAndFlush(new char[] {'a'});
    final ForumPosterEditorViewModel viewModel = new ForumPosterEditorViewModel(readyCallback);
    viewModel.setForumSelection(NodeBbForumPoster.AXIS_AND_ALLIES_ORG_DISPLAY_NAME);
    viewModel.setForumPassword(new char[] {'*', '*'});

    assertThat(viewModel.getForumPassword())
        .as("No password shoudl be set on the model, the dummy password is rejected")
        .isEmpty();
    assertThat(ClientSetting.aaForumUsername.getValue()).contains(new char[] {'a'});
  }

  @Test
  void changingForumSelectionToAxisAndAlliesOrgTogglesUsernameAndPassword() {
    ClientSetting.aaForumUsername.setValueAndFlush(new char[] {'a'});
    ClientSetting.aaForumToken.setValueAndFlush(new char[] {'b'});

    final ForumPosterEditorViewModel viewModel = new ForumPosterEditorViewModel(readyCallback);
    viewModel.setForumSelection(NodeBbForumPoster.AXIS_AND_ALLIES_ORG_DISPLAY_NAME);

    assertThat(viewModel.getForumUsername()).isEqualTo("a");
    assertThat(viewModel.getForumPassword().length()).isEqualTo(4);
    assertThat(String.valueOf(viewModel.getForumPassword()))
        .as(
            "we do not store the actual password, we'll set a dummy password in the text field "
                + "to represent it being set, only a token is stored in ClientSettings.")
        .isNotEqualTo("b");
  }

  @Test
  void changingForumSelectionToTripleATogglesUsernameAndPassword() {
    ClientSetting.tripleaForumUsername.setValueAndFlush(new char[] {'c'});

    final ForumPosterEditorViewModel viewModel = new ForumPosterEditorViewModel(readyCallback);
    viewModel.setForumSelection(NodeBbForumPoster.TRIPLEA_FORUM_DISPLAY_NAME);

    assertThat(viewModel.getForumUsername()).isEqualTo("c");
    assertThat(viewModel.getForumPassword().length()).isEqualTo(0);
  }

  @DisplayName("Signed in, a new game with a title for its new thread can start")
  @Test
  void signedInNewThreadWithTitleCanStart() {
    givenTripleaForumSession();
    final ForumPosterEditorViewModel viewModel = new ForumPosterEditorViewModel(readyCallback);

    viewModel.setNewThreadTitle("Global 1940: Axis vs Allies");

    assertThat(viewModel.isNewThread()).as("a new game starts its own thread").isTrue();
    assertThat(viewModel.areFieldsValid())
        .as("no username, password or thread number needed")
        .isTrue();
    assertThat(viewModel.usesPassword()).as("no login fields").isFalse();
  }

  @DisplayName("A new thread needs a title")
  @Test
  void newThreadNeedsTitle() {
    givenTripleaForumSession();
    final ForumPosterEditorViewModel viewModel = new ForumPosterEditorViewModel(readyCallback);

    viewModel.setNewThreadTitle("  ");

    assertThat(viewModel.areFieldsValid())
        .as("the forum takes no thread without a title")
        .isFalse();
  }

  @DisplayName("Signed in, an existing thread without a number cannot start")
  @Test
  void existingThreadNeedsTopicId() {
    final ForumPosterEditorViewModel viewModel = givenSignedInExistingThread();

    viewModel.setTopicId("my game");

    assertThat(viewModel.areFieldsValid()).as("no thread to post to").isFalse();
  }

  @DisplayName("Signed in, an existing thread is named by its address")
  @Test
  void existingThreadByAddress() {
    final ForumPosterEditorViewModel viewModel = givenSignedInExistingThread();

    viewModel.setTopicId("https://forums.triplea-game.org/topic/123/some-title");

    assertThat(viewModel.areFieldsValid()).as("the address names the thread").isTrue();
    assertThat(viewModel.getThreadTopicId()).as("its number is stored").contains(123);
  }

  @DisplayName("Without a session, a login token kept from before still continues a game")
  @Test
  void keptTokenContinuesGame() {
    ClientSetting.tripleaForumToken.setValueAndFlush("token".toCharArray());
    final ForumPosterEditorViewModel viewModel = new ForumPosterEditorViewModel(readyCallback);
    viewModel.setForumSelection(NodeBbForumPoster.TRIPLEA_FORUM_DISPLAY_NAME);
    viewModel.setThreadChoice(ThreadChoice.EXISTING);

    viewModel.setTopicId("20");

    assertThat(viewModel.areFieldsValid())
        .as("the turns' posts still use the token on this forum")
        .isTrue();
  }

  private ForumPosterEditorViewModel givenSignedInExistingThread() {
    givenTripleaForumSession();
    final ForumPosterEditorViewModel viewModel = new ForumPosterEditorViewModel(readyCallback);
    viewModel.setThreadChoice(ThreadChoice.EXISTING);
    return viewModel;
  }

  @DisplayName("A revoked login token is forgotten: it would post nothing")
  @Test
  void revokedTokenIsForgotten() {
    ClientSetting.tripleaForumToken.setValueAndFlush("token".toCharArray());
    ClientSetting.tripleaForumUserId.setValueAndFlush(7);
    final ForumPosterEditorViewModel viewModel = new ForumPosterEditorViewModel(readyCallback);
    final NodeBbTokenGenerator tokenGenerator = mock(NodeBbTokenGenerator.class);
    viewModel.setTokenGeneratorSupplier(() -> tokenGenerator);
    viewModel.setForumSelection(NodeBbForumPoster.TRIPLEA_FORUM_DISPLAY_NAME);

    viewModel.revokeToken();

    verify(tokenGenerator).revokeToken("token", 7);
    assertThat(ClientSetting.tripleaForumToken.getValue()).as("no longer stored").isEmpty();
    assertThat(viewModel.hasKeptToken()).as("so it counts as kept no more").isFalse();
  }

  @DisplayName("On the A&A forum a kept token's hint offers no new threads: TripleA starts none")
  @Test
  void keptTokenOnAaForumOffersNoNewThread() {
    ClientSetting.aaForumToken.setValueAndFlush("token".toCharArray());
    final ForumPosterEditorViewModel viewModel = new ForumPosterEditorViewModel(readyCallback);

    viewModel.setForumSelection(NodeBbForumPoster.AXIS_AND_ALLIES_ORG_DISPLAY_NAME);

    assertThat(viewModel.getSignInText())
        .as("signing in there would not let TripleA start one")
        .contains("login token kept from before")
        .doesNotContain("new threads");
  }

  @DisplayName("On forums.triplea-game.org a kept token's hint offers signing in for new threads")
  @Test
  void keptTokenOnTripleaForumOffersNewThread() {
    ClientSetting.tripleaForumToken.setValueAndFlush("token".toCharArray());

    final ForumPosterEditorViewModel viewModel = new ForumPosterEditorViewModel(readyCallback);

    assertThat(viewModel.getSignInText())
        .as("signed in, TripleA starts the game's thread there")
        .contains("to start new threads");
  }

  @DisplayName("A token the forum could not revoke stays: it still posts")
  @Test
  void unrevokedTokenStays() {
    ClientSetting.tripleaForumToken.setValueAndFlush("token".toCharArray());
    ClientSetting.tripleaForumUserId.setValueAndFlush(7);
    final ForumPosterEditorViewModel viewModel = new ForumPosterEditorViewModel(readyCallback);
    final NodeBbTokenGenerator tokenGenerator = mock(NodeBbTokenGenerator.class);
    viewModel.setTokenGeneratorSupplier(() -> tokenGenerator);
    doThrow(new IllegalStateException("offline")).when(tokenGenerator).revokeToken("token", 7);

    assertThatThrownBy(viewModel::revokeToken).hasMessage("offline");

    assertThat(ClientSetting.tripleaForumToken.getValue())
        .as("still valid on the forum")
        .isPresent();
    assertThat(viewModel.hasKeptToken()).as("so still kept").isTrue();
  }

  @DisplayName("Without a session forums.triplea-game.org cannot start: its old login fails")
  @Test
  void tripleaForumWithoutSessionCannotStart() {
    final ForumPosterEditorViewModel viewModel = new ForumPosterEditorViewModel(readyCallback);
    viewModel.setForumSelection(NodeBbForumPoster.TRIPLEA_FORUM_DISPLAY_NAME);
    viewModel.setTopicId("20");
    viewModel.setForumUsername("username");
    viewModel.setForumPassword(new char[] {'a', 'b'});

    assertThat(viewModel.areFieldsValid())
        .as("the forum answers the password login with a CAPTCHA")
        .isFalse();
    assertThat(viewModel.usesPassword()).as("so it shows no login fields").isFalse();
    assertThat(viewModel.getSignInText())
        .as("it tells where to sign in")
        .contains("Multiplayer > Play by forum");
  }

  @DisplayName("The forum the player is signed in to is preselected")
  @Test
  void signedInForumIsPreselected() {
    ClientSetting.aaForumSession.setValueAndFlush("session".toCharArray());

    assertThat(new ForumPosterEditorViewModel(readyCallback).getForumSelection())
        .as("signed in to the A&A forum only")
        .isEqualTo(NodeBbForumPoster.AXIS_AND_ALLIES_ORG_DISPLAY_NAME);
  }

  @DisplayName("Signed in to both forums, forums.triplea-game.org is preselected")
  @Test
  void tripleaForumIsPreselectedFirst() {
    ClientSetting.aaForumSession.setValueAndFlush("session".toCharArray());
    givenTripleaForumSession();

    assertThat(new ForumPosterEditorViewModel(readyCallback).getForumSelection())
        .as("the TripleA forum's Play by Forum category is the one known")
        .isEqualTo(NodeBbForumPoster.TRIPLEA_FORUM_DISPLAY_NAME);
  }

  @DisplayName("A save with its thread keeps posting there")
  @Test
  void saveWithThreadUsesExistingThread() {
    givenTripleaForumSession();
    final GameProperties gameProperties = new GameProperties(gameData);
    gameProperties.set(IForumPoster.NAME, NodeBbForumPoster.TRIPLEA_FORUM_DISPLAY_NAME);
    gameProperties.set(IForumPoster.TOPIC_ID, 123);

    final ForumPosterEditorViewModel viewModel =
        new ForumPosterEditorViewModel(readyCallback, gameProperties);

    assertThat(viewModel.isNewThread()).as("no second thread for the game").isFalse();
    assertThat(viewModel.getThreadTopicId()).as("the save's thread").contains(123);
  }

  @DisplayName("The thread started at the game's start is the game's thread from then on")
  @Test
  void startedThreadIsTheGames() {
    givenTripleaForumSession();
    final ForumPosterEditorViewModel viewModel = new ForumPosterEditorViewModel(readyCallback);

    viewModel.useExistingThread("120");

    assertThat(viewModel.isNewThread()).as("starting again starts no second thread").isFalse();
    assertThat(viewModel.getThreadTopicId()).as("the thread the forum started").contains(120);
  }

  @DisplayName("A session the forum no longer signs in with cannot start the game")
  @Test
  void ranOutSessionCannotStart() {
    givenTripleaForumSession();
    final ForumPosterEditorViewModel viewModel = new ForumPosterEditorViewModel(readyCallback);
    viewModel.setNewThreadTitle("Global 1940 game");

    viewModel.sessionRanOut();

    assertThat(viewModel.areFieldsValid()).as("nothing could be posted").isFalse();
    assertThat(viewModel.getSignInText()).as("it says what to do").contains("Sign in again");
    assertThat(ClientSetting.tripleaForumSession.getValue())
        .as("forgotten: else the turns' posts went out with it")
        .isEmpty();
  }

  @DisplayName("Signed in, the screen names the player the forum told")
  @Test
  void signedInShowsPlayer() {
    givenTripleaForumSession();
    final ForumPosterEditorViewModel viewModel = new ForumPosterEditorViewModel(readyCallback);

    viewModel.signedInAs(new Player("Ann Lee", "ann-lee"));

    assertThat(viewModel.getSignInText()).isEqualTo("Signed in as Ann Lee");
  }

  @DisplayName("Signed in, no login token is revoked at the end")
  @Test
  void signedInHandlesNoToken() {
    givenTripleaForumSession();
    final ForumPosterEditorViewModel viewModel = new ForumPosterEditorViewModel(readyCallback);
    viewModel.setRememberPassword(false);

    assertThat(viewModel.shouldRevokeTokenOnShutdown())
        .as("the session is no token TripleA made")
        .isFalse();
  }

  @DisplayName("A thread's number is read from the number or the thread's address")
  @ParameterizedTest
  @CsvSource({
    "123, https://forums.triplea-game.org/, 123",
    "' 7 ', https://forums.triplea-game.org/, 7",
    "https://forums.triplea-game.org/topic/123/some-title, https://forums.triplea-game.org/, 123",
    "https://forums.triplea-game.org/topic/123, https://forums.triplea-game.org/, 123",
    "forums.triplea-game.org/topic/45/x?page=2, https://forums.triplea-game.org/, 45",
    "https://www.axisandallies.org/forums/topic/40000/x/12, https://www.axisandallies.org/forums, 40000",
    "http://forums.triplea-game.org/topic/5, https://forums.triplea-game.org/, 5",
    "https://axisandallies.org/forums/topic/6, https://www.axisandallies.org/forums, 6"
  })
  void topicIdFromNumberOrAddress(final String entered, final String forum, final int topicId) {
    assertThat(ForumPosterEditorViewModel.topicIdOf(entered, forum))
        .as("the thread on the chosen forum")
        .contains(topicId);
  }

  @DisplayName("Text that names no thread gives no number")
  @ParameterizedTest
  @ValueSource(
      strings = {
        "",
        "my game",
        "0",
        "-1",
        "2311111111111",
        "https://forums.triplea-game.org/category/45/play-by-forum",
        "https://forums.triplea-game.org/topic/abc",
        "https://forums.triplea-game.org.example.com/topic/5",
        "https://www.axisandallies.org/forums/topic/40000/global-game",
        "www.axisandallies.org/forums/topic/123/game"
      })
  void noTopicIdWithoutThread(final String entered) {
    assertThat(ForumPosterEditorViewModel.topicIdOf(entered, "https://forums.triplea-game.org/"))
        .as("no thread of the chosen forum, forums.triplea-game.org")
        .isEmpty();
  }

  @DisplayName("An address beside the A&A forum's path names none of its threads")
  @Test
  void noTopicIdBesideForumPath() {
    assertThat(
            ForumPosterEditorViewModel.topicIdOf(
                "https://www.axisandallies.org/forums-old/topic/5",
                "https://www.axisandallies.org/forums"))
        .as("\"/forums-old\" is another application on the forum's host")
        .isEmpty();
  }

  @DisplayName("The new thread's title names the game and its two sides, else the game")
  @Test
  void newThreadTitleNamesGameAndSides() {
    assertThat(ForumPosterEditorViewModel.newThreadTitleOf(TestMapGameData.REVISED.getGameData()))
        .as("the alliances in the order of their first nations")
        .isEqualTo("World War II Revised Test: Allies vs Axis");
    assertThat(ForumPosterEditorViewModel.newThreadTitleOf(TestMapGameData.TEST.getGameData()))
        .as("a nation without an alliance is a side of its own")
        .isEqualTo("test: natp vs bush");
    assertThat(ForumPosterEditorViewModel.newThreadTitleOf(TestMapGameData.TWW.getGameData()))
        .as("many sides: the game alone")
        .isEqualTo("Total World War: December 1941 game");
  }

  @DisplayName("The new thread's first post names the game and its nations")
  @Test
  void firstPostNamesGameAndNations() {
    assertThat(ForumPosterEditorViewModel.firstPostOf(TestMapGameData.REVISED.getGameData()))
        .contains("World War II Revised Test")
        .contains("Russians, Germans, British, Japanese, Americans");
  }

  private static void givenTripleaForumSession() {
    ClientSetting.tripleaForumSession.setValueAndFlush("session".toCharArray());
  }
}
