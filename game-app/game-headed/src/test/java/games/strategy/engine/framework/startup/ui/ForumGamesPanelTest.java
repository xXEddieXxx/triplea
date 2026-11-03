package games.strategy.engine.framework.startup.ui;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.awaitility.Awaitility.await;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import games.strategy.engine.framework.startup.ui.ForumBrowserLogin.SignedIn;
import games.strategy.engine.framework.startup.ui.ForumGamesPanel.Forum;
import games.strategy.engine.framework.startup.ui.ForumGamesPanel.SignInPage;
import games.strategy.engine.framework.startup.ui.panels.main.HeadedServerSetupModel;
import games.strategy.engine.framework.startup.ui.panels.main.game.selector.GameSelectorModel;
import games.strategy.engine.posted.game.pbf.NodeBbGameThreads;
import games.strategy.engine.posted.game.pbf.NodeBbGameThreads.GameThread;
import games.strategy.engine.posted.game.pbf.NodeBbGameThreads.Player;
import games.strategy.triplea.settings.ClientSetting;
import java.awt.Component;
import java.awt.Container;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.swing.AbstractButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonatype.goodies.prefs.memory.MemoryPreferences;

final class ForumGamesPanelTest {
  private static final String SESSION = "s%3Asession";

  private static final String TOPICS =
      """
      {"topics": [{"tid": 101, "slug": "101/global", "title": "<html><b>Global 1940</b>", "cid": 45,
        "lastposttime": 1700000000000, "postcount": 4}]}
      """;

  private static final String CATEGORY =
      """
      {"topics": [
        {"tid": 7, "title": "Rules", "pinned": 1},
        {"tid": 4441, "slug": "4441/qualifier", "title": "Qualifier Game 2",
         "lastposttime": 1793000000000}]}
      """;

  /** The login page as a stand-in: Ann signs in at once with the session. */
  private static final SignInPage ANN_SIGNS_IN =
      (parent, forumUrl, onSignedIn, onError) ->
          onSignedIn.accept(new SignedIn(SESSION, new Player("Ann", "ann")));

  private final WireMockServer forum = new WireMockServer(0);
  private final HeadedServerSetupModel model = new HeadedServerSetupModel(new GameSelectorModel());

  @BeforeEach
  void givenForum() {
    ClientSetting.setPreferences(new MemoryPreferences());
    forum.start();
    forum.stubFor(
        get(urlEqualTo("/api/config"))
            .withHeader("Cookie", equalTo("express.sid=" + SESSION))
            .willReturn(aResponse().withBody("{\"uid\": 7}")));
    forum.stubFor(
        get(urlEqualTo("/api/user/uid/7"))
            .willReturn(aResponse().withBody("{\"username\": \"Ann\", \"userslug\": \"ann\"}")));
    forum.stubFor(get(urlEqualTo("/api/user/ann/topics")).willReturn(aResponse().withBody(TOPICS)));
    forum.stubFor(
        get(urlPathEqualTo("/api/user/ann/posts"))
            .willReturn(aResponse().withBody("{\"posts\": []}")));
    // the newest games whatever the player's own setting orders the category by
    forum.stubFor(
        get(urlEqualTo("/api/category/45?sort=recently_replied"))
            .willReturn(aResponse().withBody(CATEGORY)));
  }

  @AfterEach
  void stopForumAndResetSettings() {
    forum.stop();
    ClientSetting.resetPreferences();
  }

  @DisplayName("Signing in on the forum's page keeps the session and shows the player's games")
  @Test
  void signInKeepsSessionAndShowsGames() throws Exception {
    final ForumGamesPanel page = givenPage(ANN_SIGNS_IN);
    SwingUtilities.invokeAndWait(page::refresh);

    SwingUtilities.invokeAndWait(() -> buttonOf(page, "Sign in with the forum").doClick());

    assertThat(ClientSetting.tripleaForumSession.getValue().map(String::valueOf))
        .as("the session is kept, encrypted in the settings")
        .contains(SESSION);
    awaitGames(page, "<html><b>Global 1940</b>");
    assertThat(forum.findAll(anyRequestedFor(anyUrl())))
        .as("signing in and showing the games only reads the forum, with the session")
        .allSatisfy(
            request -> {
              assertThat(request.getMethod().getName()).isEqualTo("GET");
              assertThat(request.getHeader("Cookie")).isEqualTo("express.sid=" + SESSION);
            });
  }

  @DisplayName("Send posts the message to the chosen game's thread and empties the composer")
  @Test
  void sendPostsToTheThread() throws Exception {
    ClientSetting.tripleaForumSession.setValueAndFlush(SESSION.toCharArray());
    forum.stubFor(
        get(urlEqualTo("/api/config"))
            .withHeader("Cookie", equalTo("express.sid=" + SESSION))
            .willReturn(aResponse().withBody("{\"uid\": 7, \"csrf_token\": \"token-1\"}")));
    forum.stubFor(
        post(urlEqualTo("/api/v3/topics/101"))
            .withHeader("x-csrf-token", equalTo("token-1"))
            .willReturn(aResponse().withBody("{\"response\": {\"pid\": 9}}")));
    final ForumGamesPanel page = givenPage(ANN_SIGNS_IN);
    SwingUtilities.invokeAndWait(page::refresh);
    awaitGames(page, "<html><b>Global 1940</b>");
    SwingUtilities.invokeAndWait(() -> page.getThreads().setSelectedIndex(0));
    final JTextArea message = composerOf(page);

    SwingUtilities.invokeAndWait(
        () -> {
          message.setText("  Your turn, Bob!  ");
          buttonOf(page, "Send").doClick();
        });

    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> assertThat(message.getText()).as("sent, so emptied").isEmpty());
    forum.verify(
        postRequestedFor(urlEqualTo("/api/v3/topics/101"))
            .withHeader("Cookie", equalTo("express.sid=" + SESSION))
            .withRequestBody(equalToJson("{\"content\": \"Your turn, Bob!\"}")));
  }

  @DisplayName(
      "While a post is sent Send is off; the answer is told in another game, its draft kept")
  @Test
  void answerOfSendIsToldInAnotherGame() throws Exception {
    ClientSetting.tripleaForumSession.setValueAndFlush(SESSION.toCharArray());
    forum.stubFor(
        get(urlEqualTo("/api/user/ann/topics"))
            .willReturn(
                aResponse()
                    .withBody(
                        """
                        {"topics": [
                          {"tid": 101, "slug": "101/global", "title": "Global 1940", "cid": 45,
                           "lastposttime": 1700000000000, "postcount": 4},
                          {"tid": 102, "slug": "102/pacific", "title": "Pacific", "cid": 45,
                           "lastposttime": 1690000000000, "postcount": 2}]}
                        """)));
    forum.stubFor(
        get(urlEqualTo("/api/config"))
            .withHeader("Cookie", equalTo("express.sid=" + SESSION))
            .willReturn(aResponse().withBody("{\"uid\": 7, \"csrf_token\": \"token-1\"}")));
    forum.stubFor(
        post(urlEqualTo("/api/v3/topics/101"))
            .willReturn(aResponse().withFixedDelay(1500).withBody("{\"response\": {\"pid\": 9}}")));
    final ForumGamesPanel page = givenPage(ANN_SIGNS_IN);
    SwingUtilities.invokeAndWait(page::refresh);
    await().atMost(Duration.ofSeconds(10)).until(() -> page.getThreads().getModel().getSize() == 2);
    SwingUtilities.invokeAndWait(() -> page.getThreads().setSelectedIndex(0));
    final JTextArea message = composerOf(page);
    SwingUtilities.invokeAndWait(
        () -> {
          message.setText("Your turn, Bob!");
          buttonOf(page, "Send").doClick();
        });

    SwingUtilities.invokeAndWait(
        () -> {
          page.getThreads().setSelectedIndex(1);
          message.setText("Draft for the Pacific");
        });

    assertThat(buttonOf(page, "Send").isEnabled())
        .as("one post at a time: a second Send would post twice")
        .isFalse();
    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () ->
                assertThat(componentsOf(page))
                    .as("the answer is told in the other game, with the game it was for")
                    .anyMatch(
                        component ->
                            component instanceof JTextArea text
                                && "Sent to \"Global 1940\".".equals(text.getText())));
    assertThat(message.getText())
        .as("what was written for the other game meanwhile stays")
        .isEqualTo("Draft for the Pacific");
    assertThat(buttonOf(page, "Send").isEnabled()).as("the next post may be sent").isTrue();
  }

  @DisplayName("A post not sent comes back to its game, also when the game's list was read again")
  @Test
  void postNotSentComesBackToItsGame() throws Exception {
    ClientSetting.tripleaForumSession.setValueAndFlush(SESSION.toCharArray());
    forum.stubFor(
        get(urlEqualTo("/api/user/ann/topics"))
            .inScenario("read again")
            .whenScenarioStateIs(Scenario.STARTED)
            .willReturn(aResponse().withBody(TOPICS))
            .willSetStateTo("answered"));
    // read again, the game has a post more: another record of the same game
    forum.stubFor(
        get(urlEqualTo("/api/user/ann/topics"))
            .inScenario("read again")
            .whenScenarioStateIs("answered")
            .willReturn(
                aResponse().withBody(TOPICS.replace("\"postcount\": 4", "\"postcount\": 5"))));
    forum.stubFor(
        get(urlEqualTo("/api/config"))
            .withHeader("Cookie", equalTo("express.sid=" + SESSION))
            .willReturn(aResponse().withBody("{\"uid\": 7, \"csrf_token\": \"token-1\"}")));
    forum.stubFor(
        post(urlEqualTo("/api/v3/topics/101"))
            .willReturn(aResponse().withStatus(500).withFixedDelay(2500)));
    final ForumGamesPanel page = givenPage(ANN_SIGNS_IN);
    SwingUtilities.invokeAndWait(page::refresh);
    awaitGames(page, "<html><b>Global 1940</b>");
    SwingUtilities.invokeAndWait(() -> page.getThreads().setSelectedIndex(0));
    final JTextArea message = composerOf(page);
    SwingUtilities.invokeAndWait(
        () -> {
          message.setText("Your turn, Bob!");
          buttonOf(page, "Send").doClick();
        });

    SwingUtilities.invokeAndWait(page::showBrowse);
    awaitGames(page, "Qualifier Game 2");
    SwingUtilities.invokeAndWait(() -> buttonOf(page, "My games").doClick());
    await()
        .atMost(Duration.ofSeconds(10))
        .until(() -> threadsOf(page).stream().anyMatch(game -> game.posts() == 5));
    SwingUtilities.invokeAndWait(() -> page.getThreads().setSelectedIndex(0));

    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () ->
                assertThat(message.getText())
                    .as("the composer made anew for the game gets back what was not sent")
                    .isEqualTo("Your turn, Bob!"));
    assertThat(componentsOf(page))
        .as("said for the game shown, not as of another game")
        .filteredOn(JTextArea.class::isInstance)
        .extracting(component -> ((JTextArea) component).getText())
        .anyMatch(text -> text.startsWith("Not sent: "));
  }

  @DisplayName("My games: those waiting for the player first, and filters for whose move it is")
  @Test
  void myGamesByWhoseMoveItIs() throws Exception {
    ClientSetting.tripleaForumSession.setValueAndFlush(SESSION.toCharArray());
    forum.stubFor(
        get(urlEqualTo("/api/user/ann/topics"))
            .willReturn(
                aResponse()
                    .withBody(
                        """
                        {"topics": [
                          {"tid": 102, "slug": "102/pacific", "title": "Pacific", "cid": 45,
                           "lastposttime": 1700000900000, "teaser": {"user": {"userslug": "ann"}}},
                          {"tid": 101, "slug": "101/global", "title": "Global 1940", "cid": 45,
                           "lastposttime": 1700000000000, "teaser": {"user": {"userslug": "bob"}}}]}
                        """)));
    final ForumGamesPanel page = givenPage(ANN_SIGNS_IN);
    SwingUtilities.invokeAndWait(page::refresh);
    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () ->
                assertThat(threadsOf(page))
                    .as(
                        "Bob posted last in Global: Ann's move comes first, though answered earlier")
                    .extracting(GameThread::title)
                    .containsExactly("Global 1940", "Pacific"));

    SwingUtilities.invokeAndWait(() -> buttonOf(page, "Your turn").doClick());
    assertThat(threadsOf(page))
        .as("Bob posted last in Global: it is Ann's move")
        .extracting(GameThread::title)
        .containsExactly("Global 1940");
    SwingUtilities.invokeAndWait(() -> buttonOf(page, "Waiting").doClick());
    assertThat(threadsOf(page))
        .as("Ann posted last in Pacific: the others are to move")
        .extracting(GameThread::title)
        .containsExactly("Pacific");
  }

  @DisplayName("The search narrows the games to those whose title holds it")
  @Test
  void searchNarrowsGames() throws Exception {
    ClientSetting.tripleaForumSession.setValueAndFlush(SESSION.toCharArray());
    final ForumGamesPanel page = givenPage(ANN_SIGNS_IN);
    SwingUtilities.invokeAndWait(page::refresh);
    awaitGames(page, "<html><b>Global 1940</b>");

    SwingUtilities.invokeAndWait(() -> searchOf(page).setText("pacific"));

    // the search waits a moment after the last key, as while typing
    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(() -> assertThat(threadsOf(page)).as("no title holds it").isEmpty());
    SwingUtilities.invokeAndWait(() -> searchOf(page).setText("GLOBAL"));
    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () ->
                assertThat(threadsOf(page))
                    .as("case aside")
                    .extracting(GameThread::title)
                    .containsExactly("<html><b>Global 1940</b>"));
  }

  @DisplayName("Any game has the answer box, with a bar that writes the forum's formatting")
  @Test
  void answerBoxWithFormattingInGamesOfOthers() throws Exception {
    ClientSetting.tripleaForumSession.setValueAndFlush(SESSION.toCharArray());
    final ForumGamesPanel page = givenPage(ANN_SIGNS_IN);
    SwingUtilities.invokeAndWait(page::refresh);
    awaitGames(page, "<html><b>Global 1940</b>");
    SwingUtilities.invokeAndWait(page::showBrowse);
    awaitGames(page, "Qualifier Game 2");

    SwingUtilities.invokeAndWait(() -> page.getThreads().setSelectedIndex(0));
    final JTextArea message = composerOf(page);
    SwingUtilities.invokeAndWait(
        () -> {
          message.setText("well played\nsee you");
          message.select(0, 4);
          buttonNamed(page, "Bold").doClick();
          message.setCaretPosition(message.getText().length());
          buttonNamed(page, "Quote").doClick();
        });

    assertThat(message.getText())
        .as("a game of others: bold around the word chosen, the caret's line quoted")
        .isEqualTo("**well** played\n> see you");
    SwingUtilities.invokeAndWait(
        () -> {
          message.setText("\nabc");
          message.setCaretPosition(0);
          buttonNamed(page, "Quote").doClick();
        });
    assertThat(message.getText())
        .as("the caret on the empty first line: that line is quoted, nothing breaks")
        .isEqualTo("> \nabc");
    SwingUtilities.invokeAndWait(
        () -> {
          message.setText("ab\ncd");
          message.select(0, 3);
          buttonNamed(page, "Quote").doClick();
        });
    assertThat(message.getText())
        .as("a selection ending with the line break quotes its own line, not the next")
        .isEqualTo("> ab\ncd");
  }

  /** A button by the name a screen reader tells, as the bar's icons have no text. */
  private static AbstractButton buttonNamed(final Container container, final String name) {
    return componentsOf(container)
        .filter(AbstractButton.class::isInstance)
        .map(AbstractButton.class::cast)
        .filter(button -> name.equals(button.getAccessibleContext().getAccessibleName()))
        .findFirst()
        .orElseThrow();
  }

  @DisplayName(
      "A picture attached goes up to the forum and is linked at the caret, as the forum does")
  @Test
  void attachedPictureIsUploadedAndLinked(@TempDir final Path folder) throws Exception {
    ClientSetting.tripleaForumSession.setValueAndFlush(SESSION.toCharArray());
    forum.stubFor(
        post(urlEqualTo("/api/post/upload"))
            .willReturn(
                aResponse()
                    .withBody(
                        """
                        {"response": {"images": [{"url": "/assets/uploads/files/1-map.png"}]}}
                        """)));
    final Path picture = Files.write(folder.resolve("map.png"), new byte[] {1, 2, 3});
    final ForumGamesPanel page = givenPage(ANN_SIGNS_IN);
    SwingUtilities.invokeAndWait(page::refresh);
    awaitGames(page, "<html><b>Global 1940</b>");
    SwingUtilities.invokeAndWait(() -> page.getThreads().setSelectedIndex(0));
    final JTextArea message = composerOf(page);
    SwingUtilities.invokeAndWait(
        () -> {
          message.setText("Look: ");
          message.setCaretPosition(message.getText().length());
        });

    SwingUtilities.invokeAndWait(() -> page.attachPicture(picture));

    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () ->
                assertThat(message.getText())
                    .as("the picture's link where the caret was; the post itself not sent yet")
                    .isEqualTo("Look: ![map](/assets/uploads/files/1-map.png)"));
    assertThat(forum.findAll(postRequestedFor(urlEqualTo("/api/v3/topics/101"))))
        .as("the answer goes with Send only")
        .isEmpty();
  }

  @DisplayName("A search while the games are read keeps saying so, and then finds among them")
  @Test
  void searchWhileReadingKeepsMessage() throws Exception {
    ClientSetting.tripleaForumSession.setValueAndFlush(SESSION.toCharArray());
    forum.stubFor(
        get(urlEqualTo("/api/user/ann/topics"))
            .willReturn(aResponse().withFixedDelay(1500).withBody(TOPICS)));
    final ForumGamesPanel page = givenPage(ANN_SIGNS_IN);
    SwingUtilities.invokeAndWait(page::refresh);
    await().atMost(Duration.ofSeconds(5)).until(() -> showsLabel(page, "Reading the games..."));

    SwingUtilities.invokeAndWait(() -> searchOf(page).setText("glob"));

    // past the search's wait after the last key
    await().pollDelay(Duration.ofMillis(400)).atMost(Duration.ofSeconds(1)).until(() -> true);
    assertThat(showsLabel(page, "Reading the games..."))
        .as("no \"No games yet\" while the forum still answers")
        .isTrue();
    awaitGames(page, "<html><b>Global 1940</b>");
  }

  private static boolean showsLabel(final ForumGamesPanel page, final String text) {
    return componentsOf(page)
        .anyMatch(component -> component instanceof JLabel label && text.equals(label.getText()));
  }

  @DisplayName("Show all opens a long post in full, and goes")
  @Test
  void showAllOpensLongPost() throws Exception {
    ClientSetting.tripleaForumSession.setValueAndFlush(SESSION.toCharArray());
    forum.stubFor(
        get(urlEqualTo("/api/topic/101/global?page=1&sort=oldest_to_newest"))
            .willReturn(
                aResponse()
                    .withBody(
                        """
                        {"posts": [{"timestamp": 1700000000000, "user": {"username": "bob"},
                          "content": "<p>1<br>2<br>3<br>4<br>5<br>6<br>7<br>8<br>9<br>10</p>"}],
                         "pagination": {"currentPage": 1, "pageCount": 1}}
                        """)));
    final ForumGamesPanel page = givenPage(ANN_SIGNS_IN);
    SwingUtilities.invokeAndWait(page::refresh);
    awaitGames(page, "<html><b>Global 1940</b>");
    SwingUtilities.invokeAndWait(() -> page.getThreads().setSelectedIndex(0));
    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () ->
                assertThat(componentsOf(page))
                    .as("the post shows its first lines")
                    .anyMatch(component -> isButton(component, "Show all")));
    final ForumPost post = postOf(page);
    assertThat(post.getParent().getPreferredSize().height)
        .as("folded: the post's first lines only")
        .isLessThan(post.getPreferredSize().height);

    SwingUtilities.invokeAndWait(() -> buttonOf(page, "Show all").doClick());

    assertThat(componentsOf(page))
        .as("the button that opened it is gone")
        .noneMatch(component -> isButton(component, "Show all"));
    assertThat(post.getParent().getPreferredSize().height)
        .as("the whole post shows")
        .isEqualTo(post.getPreferredSize().height);
  }

  private static ForumPost postOf(final ForumGamesPanel page) {
    return componentsOf(page)
        .filter(ForumPost.class::isInstance)
        .map(ForumPost.class::cast)
        .findFirst()
        .orElseThrow();
  }

  @DisplayName("Watch follows a game in the forum; a watched game shows under My games to view")
  @Test
  void watchFollowsGame() throws Exception {
    ClientSetting.tripleaForumSession.setValueAndFlush(SESSION.toCharArray());
    forum.stubFor(
        get(urlEqualTo("/api/topic/4441/qualifier?page=1&sort=oldest_to_newest"))
            .willReturn(
                aResponse()
                    .withBody(
                        """
                        {"posts": [], "pagination": {"currentPage": 1, "pageCount": 1},
                         "isFollowing": false}
                        """)));
    forum.stubFor(
        put(urlEqualTo("/api/v3/topics/4441/follow"))
            .willReturn(aResponse().withBody("{\"status\": {\"code\": \"ok\"}}")));
    final ForumGamesPanel page = givenPage(ANN_SIGNS_IN);
    SwingUtilities.invokeAndWait(page::refresh);
    awaitGames(page, "<html><b>Global 1940</b>");
    SwingUtilities.invokeAndWait(page::showBrowse);
    awaitGames(page, "Qualifier Game 2");
    SwingUtilities.invokeAndWait(() -> page.getThreads().setSelectedIndex(0));
    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () ->
                assertThat(componentsOf(page))
                    .as("on once the forum told that Ann does not follow the game yet")
                    .anyMatch(component -> isButton(component, "Watch") && component.isEnabled()));

    SwingUtilities.invokeAndWait(() -> buttonOf(page, "Watch").doClick());

    awaitButtonStartingWith(page, "✓  Watching");
    forum.verify(
        putRequestedFor(urlEqualTo("/api/v3/topics/4441/follow"))
            .withHeader("Cookie", equalTo("express.sid=" + SESSION)));
  }

  @DisplayName("Watch the forum did not take switches back and tells why; the next works as ever")
  @Test
  void watchRefusedSwitchesBack() throws Exception {
    ClientSetting.tripleaForumSession.setValueAndFlush(SESSION.toCharArray());
    forum.stubFor(
        get(urlEqualTo("/api/topic/4441/qualifier?page=1&sort=oldest_to_newest"))
            .willReturn(
                aResponse()
                    .withBody(
                        "{\"posts\": [], \"pagination\": {\"currentPage\": 1, \"pageCount\": 1}}")));
    forum.stubFor(
        put(urlEqualTo("/api/v3/topics/4441/follow"))
            .inScenario("follow")
            .whenScenarioStateIs(Scenario.STARTED)
            .willReturn(aResponse().withStatus(500))
            .willSetStateTo("up again"));
    forum.stubFor(
        put(urlEqualTo("/api/v3/topics/4441/follow"))
            .inScenario("follow")
            .whenScenarioStateIs("up again")
            .willReturn(aResponse().withBody("{\"status\": {\"code\": \"ok\"}}")));
    final ForumGamesPanel page = givenPage(ANN_SIGNS_IN);
    SwingUtilities.invokeAndWait(page::refresh);
    awaitGames(page, "<html><b>Global 1940</b>");
    SwingUtilities.invokeAndWait(page::showBrowse);
    awaitGames(page, "Qualifier Game 2");
    SwingUtilities.invokeAndWait(() -> page.getThreads().setSelectedIndex(0));
    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () ->
                assertThat(componentsOf(page))
                    .as("on once the forum told the game's posts")
                    .anyMatch(component -> isButton(component, "Watch") && component.isEnabled()));

    SwingUtilities.invokeAndWait(() -> buttonOf(page, "Watch").doClick());

    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () ->
                assertThat(buttonNamed(page, "Watch").getToolTipText())
                    .as("off again, with the forum's answer")
                    .startsWith("The forum did not take it"));
    assertThat(buttonNamed(page, "Watch").isSelected()).as("switched back").isFalse();
    SwingUtilities.invokeAndWait(() -> buttonOf(page, "Watch").doClick());
    awaitButtonStartingWith(page, "✓  Watching");
    assertThat(buttonOf(page, "✓  Watching").getToolTipText())
        .as("what Watch does again, the failure gone")
        .startsWith("Follows the game");
  }

  @DisplayName("A watched game of others is in My games, marked, with a filter, its saves to view")
  @Test
  void watchedGameIsAmongMyGames() throws Exception {
    ClientSetting.tripleaForumSession.setValueAndFlush(SESSION.toCharArray());
    forum.stubFor(
        get(urlEqualTo("/api/user/ann/watched"))
            .willReturn(
                aResponse()
                    .withBody(
                        """
                        {"topics": [{"tid": 4441, "slug": "4441/qualifier",
                          "title": "Qualifier Game 2", "cid": 45, "lastposttime": 1793000000000,
                          "teaser": {"user": {"userslug": "carl"}}}]}
                        """)));
    forum.stubFor(
        get(urlEqualTo("/api/topic/4441/qualifier?page=1&sort=oldest_to_newest"))
            .willReturn(
                aResponse()
                    .withBody(
                        """
                        {"posts": [{"timestamp": 1700000000000, "user": {"username": "carl"},
                          "content": "<p><a href=\\"/assets/uploads/files/1-x.tsvg\\">Save</a></p>"}],
                         "pagination": {"currentPage": 1, "pageCount": 1}, "isFollowing": true}
                        """)));
    final ForumGamesPanel page = givenPage(ANN_SIGNS_IN);
    SwingUtilities.invokeAndWait(page::refresh);
    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () ->
                assertThat(threadsOf(page))
                    .as("Ann's game first; the watched game last, though answered later")
                    .extracting(GameThread::title)
                    .containsExactly("<html><b>Global 1940</b>", "Qualifier Game 2"));

    SwingUtilities.invokeAndWait(() -> buttonOf(page, "Watching").doClick());
    assertThat(threadsOf(page))
        .as("the filter for the watched games")
        .extracting(GameThread::title)
        .containsExactly("Qualifier Game 2");
    SwingUtilities.invokeAndWait(() -> page.getThreads().setSelectedIndex(0));

    awaitButtonStartingWith(page, "View  ·");
    awaitButtonStartingWith(page, "✓  Watching");
    assertThat(componentsOf(page))
        .as("a game of others: Ann may ask to join")
        .anyMatch(component -> isButton(component, "Join game"));
  }

  @DisplayName(
      "Under Browse games the player's own game is no other's: no Join game, saves to play")
  @Test
  void ownGameUnderBrowseIsThePlayers() throws Exception {
    ClientSetting.tripleaForumSession.setValueAndFlush(SESSION.toCharArray());
    forum.stubFor(
        get(urlEqualTo("/api/category/45?sort=recently_replied"))
            .willReturn(
                aResponse()
                    .withBody(
                        """
                        {"topics": [{"tid": 101, "slug": "101/global", "title": "Global 1940",
                          "lastposttime": 1793000000000}]}
                        """)));
    forum.stubFor(
        get(urlEqualTo("/api/topic/101/global?page=1&sort=oldest_to_newest"))
            .willReturn(
                aResponse()
                    .withBody(
                        """
                        {"posts": [{"timestamp": 1700000000000, "user": {"username": "bob"},
                          "content": "<p><a href=\\"/assets/uploads/files/1-x.tsvg\\">Save</a></p>"}],
                         "pagination": {"currentPage": 1, "pageCount": 1}}
                        """)));
    final ForumGamesPanel page = givenPage(ANN_SIGNS_IN);
    SwingUtilities.invokeAndWait(page::refresh);
    awaitGames(page, "<html><b>Global 1940</b>");
    SwingUtilities.invokeAndWait(page::showBrowse);
    awaitGames(page, "Global 1940");

    SwingUtilities.invokeAndWait(() -> page.getThreads().setSelectedIndex(0));

    awaitButtonStartingWith(page, "Open save game");
    assertThat(componentsOf(page))
        .as("Ann's own game, as My games read it")
        .noneMatch(component -> isButton(component, "Join game"));
  }

  @DisplayName("Join game writes the request to join into the answer box, to finish and send")
  @Test
  void joinGameWritesRequest() throws Exception {
    ClientSetting.tripleaForumSession.setValueAndFlush(SESSION.toCharArray());
    final ForumGamesPanel page = givenPage(ANN_SIGNS_IN);
    SwingUtilities.invokeAndWait(page::refresh);
    awaitGames(page, "<html><b>Global 1940</b>");
    assertThat(componentsOf(page))
        .as("no game chosen yet")
        .noneMatch(component -> isButton(component, "Join game"));
    SwingUtilities.invokeAndWait(page::showBrowse);
    awaitGames(page, "Qualifier Game 2");
    SwingUtilities.invokeAndWait(() -> page.getThreads().setSelectedIndex(0));

    SwingUtilities.invokeAndWait(() -> buttonOf(page, "Join game").doClick());

    assertThat(composerOf(page).getText())
        .as("nothing is sent until the player finishes and sends it")
        .isEqualTo("I'd like to join this game. I can take over: ");
    assertThat(forum.findAll(anyRequestedFor(anyUrl())))
        .as("only reads: nothing written to the forum")
        .extracting(request -> request.getMethod().getName())
        .containsOnly("GET");
  }

  @DisplayName("A save game opens to play on in the player's own games, to watch in the others'")
  @Test
  void saveGamesOpenToPlayOnOrToWatch() throws Exception {
    ClientSetting.tripleaForumSession.setValueAndFlush(SESSION.toCharArray());
    final String postWithSave =
        """
        {"posts": [{"timestamp": 1700000000000, "user": {"username": "bob"},
          "content": "<p><a href=\\"/assets/uploads/files/1-x.tsvg\\">Savegame</a></p>"}],
         "pagination": {"currentPage": 1, "pageCount": 1}}
        """;
    forum.stubFor(
        get(urlEqualTo("/api/topic/101/global?page=1&sort=oldest_to_newest"))
            .willReturn(aResponse().withBody(postWithSave)));
    forum.stubFor(
        get(urlEqualTo("/api/topic/4441/qualifier?page=1&sort=oldest_to_newest"))
            .willReturn(aResponse().withBody(postWithSave)));
    final ForumGamesPanel page = givenPage(ANN_SIGNS_IN);
    SwingUtilities.invokeAndWait(page::refresh);
    awaitGames(page, "<html><b>Global 1940</b>");

    SwingUtilities.invokeAndWait(() -> page.getThreads().setSelectedIndex(0));
    awaitButtonStartingWith(page, "Open save game");
    SwingUtilities.invokeAndWait(page::showBrowse);
    awaitGames(page, "Qualifier Game 2");
    SwingUtilities.invokeAndWait(() -> page.getThreads().setSelectedIndex(0));

    awaitButtonStartingWith(page, "View  ·");
  }

  @DisplayName("A login page that cannot open at all gives the Sign in button back")
  @Test
  void signInComesBackWhenLoginPageCannotOpen() throws Exception {
    final ForumGamesPanel page =
        givenPage(
            (parent, forumUrl, onSignedIn, onError) -> {
              throw new NoClassDefFoundError("org/cef/CefApp");
            });
    SwingUtilities.invokeAndWait(page::refresh);

    SwingUtilities.invokeAndWait(() -> buttonOf(page, "Sign in with the forum").doClick());

    assertThat(buttonOf(page, "Sign in with the forum").isEnabled())
        .as("eg the browser's classes are missing from the install: the player may try again")
        .isTrue();
    assertThat(componentsOf(page))
        .as("and is told why it did not open")
        .filteredOn(JTextArea.class::isInstance)
        .extracting(component -> ((JTextArea) component).getText())
        .anyMatch(text -> text.startsWith("The forum's login page could not open"));
  }

  @DisplayName("Browse games shows the newest games of the Play by Forum category to watch")
  @Test
  void browseShowsCategoryGames() throws Exception {
    ClientSetting.tripleaForumSession.setValueAndFlush(SESSION.toCharArray());
    final ForumGamesPanel page = givenPage(ANN_SIGNS_IN);
    SwingUtilities.invokeAndWait(page::refresh);
    awaitGames(page, "<html><b>Global 1940</b>");

    SwingUtilities.invokeAndWait(page::showBrowse);

    awaitGames(page, "Qualifier Game 2");
  }

  @DisplayName("A session that ran out signs out, and so does Sign out")
  @Test
  void endedSessionSignsOut() throws Exception {
    ClientSetting.tripleaForumSession.setValueAndFlush("s%3Aold".toCharArray());
    forum.stubFor(
        get(urlEqualTo("/api/config"))
            .withHeader("Cookie", equalTo("express.sid=s%3Aold"))
            .willReturn(aResponse().withBody("{\"uid\": 0}")));
    final ForumGamesPanel page = givenPage(ANN_SIGNS_IN);

    SwingUtilities.invokeAndWait(page::refresh);

    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () ->
                assertThat(ClientSetting.tripleaForumSession.getValue())
                    .as("the forum takes the session for a guest's: it is forgotten")
                    .isEmpty());
    SwingUtilities.invokeAndWait(() -> buttonOf(page, "Sign in with the forum").doClick());
    awaitGames(page, "<html><b>Global 1940</b>");
    SwingUtilities.invokeAndWait(() -> buttonOf(page, "Sign out").doClick());
    assertThat(ClientSetting.tripleaForumSession.getValue())
        .as("Sign out forgets the session on this computer")
        .isEmpty();
  }

  @DisplayName("Sign in stays off while the login window is open, also on another forum, not after")
  @Test
  void signInIsOffWhileTheWindowIsOpen() throws Exception {
    final List<Consumer<SignedIn>> openWindows = new ArrayList<>();
    final ForumGamesPanel page =
        new ForumGamesPanel(
            model,
            List.of(
                tripleaForum(),
                new Forum(
                    "www.axisandallies.org/forums/",
                    "http://localhost:" + forum.port() + "/aa/",
                    0,
                    ClientSetting.aaForumSession)),
            (parent, forumUrl, onSignedIn, onError) -> openWindows.add(onSignedIn));
    SwingUtilities.invokeAndWait(page::refresh);

    SwingUtilities.invokeAndWait(
        () -> {
          buttonOf(page, "Sign in with the forum").doClick();
          // the player chooses the other forum while the window is open
          componentsOf(page)
              .filter(JComboBox.class::isInstance)
              .map(component -> (JComboBox<?>) component)
              .findFirst()
              .orElseThrow()
              .setSelectedIndex(1);
        });
    assertThat(buttonOf(page, "Sign in with the forum").isEnabled())
        .as("a second window would take the first one's cookie")
        .isFalse();
    SwingUtilities.invokeAndWait(
        () -> openWindows.getFirst().accept(new SignedIn(SESSION, new Player("Ann", "ann"))));

    assertThat(buttonOf(page, "Sign in with the forum").isEnabled())
        .as("with the window closed, the forum shown can be signed in to")
        .isTrue();
    assertThat(componentsOf(page))
        .as("the card no longer sends the player to a window that is gone")
        .filteredOn(JTextArea.class::isInstance)
        .extracting(component -> ((JTextArea) component).getText())
        .contains("Signing in opens the forum's own page in a window of TripleA.")
        .doesNotContain("Sign in in the window that is open.");
    assertThat(ClientSetting.tripleaForumSession.getValue().map(String::valueOf))
        .as("the session is kept for the forum signed in to")
        .contains(SESSION);
  }

  @DisplayName("When the forum could not be reached, a tab asks it again")
  @Test
  void tabAsksAgainAfterForumWasNotReached() throws Exception {
    ClientSetting.tripleaForumSession.setValueAndFlush(SESSION.toCharArray());
    forum.stubFor(
        get(urlEqualTo("/api/config"))
            .inScenario("forum down")
            .whenScenarioStateIs(Scenario.STARTED)
            .willReturn(aResponse().withStatus(500))
            .willSetStateTo("up"));
    forum.stubFor(
        get(urlEqualTo("/api/config"))
            .inScenario("forum down")
            .whenScenarioStateIs("up")
            .willReturn(aResponse().withBody("{\"uid\": 7}")));
    final ForumGamesPanel page = givenPage(ANN_SIGNS_IN);
    SwingUtilities.invokeAndWait(page::refresh);
    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () ->
                assertThat(componentsOf(page))
                    .as("the page says the forum was not reached")
                    .anySatisfy(
                        component ->
                            assertThat(component)
                                .isInstanceOfSatisfying(
                                    JLabel.class,
                                    label ->
                                        assertThat(label.getText())
                                            .contains("Choose a tab to retry"))));
    SwingUtilities.invokeAndWait(() -> searchOf(page).setText("glob"));
    // past the search's wait after the last key
    await().pollDelay(Duration.ofMillis(400)).atMost(Duration.ofSeconds(1)).until(() -> true);
    assertThat(componentsOf(page))
        .as("a search keeps saying why there are no games, not \"No games yet\"")
        .anyMatch(
            component ->
                component instanceof JLabel label
                    && String.valueOf(label.getText()).contains("Choose a tab to retry"));

    SwingUtilities.invokeAndWait(() -> buttonOf(page, "My games").doClick());

    awaitGames(page, "<html><b>Global 1940</b>");
  }

  @DisplayName("The forums to choose are those of play by forum, each with its own sign-in")
  @Test
  void forumsAreThoseOfPlayByForum() {
    assertThat(ForumGamesPanel.FORUMS)
        .as("named as on the setup; only the TripleA forum's category is known to browse")
        .extracting(Forum::name, Forum::playByForumCategory)
        .containsExactly(
            tuple("forums.triplea-game.org", NodeBbGameThreads.TRIPLEA_PLAY_BY_FORUM_CATEGORY),
            tuple("www.axisandallies.org/forums/", 0));
  }

  private ForumGamesPanel givenPage(final SignInPage signInPage) {
    return new ForumGamesPanel(model, List.of(tripleaForum()), signInPage);
  }

  private Forum tripleaForum() {
    return new Forum(
        "forums.triplea-game.org",
        "http://localhost:" + forum.port(),
        NodeBbGameThreads.TRIPLEA_PLAY_BY_FORUM_CATEGORY,
        ClientSetting.tripleaForumSession);
  }

  private static void awaitGames(final ForumGamesPanel page, final String title) {
    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () ->
                assertThat(threadsOf(page))
                    .as("the games, their titles as written")
                    .extracting(GameThread::title)
                    .containsExactly(title));
  }

  private static List<GameThread> threadsOf(final ForumGamesPanel page) throws Exception {
    final List<GameThread> shown = new ArrayList<>();
    // on the EDT, where a search changes the list meanwhile
    SwingUtilities.invokeAndWait(
        () ->
            IntStream.range(0, page.getThreads().getModel().getSize())
                .mapToObj(page.getThreads().getModel()::getElementAt)
                .forEach(shown::add));
    return shown;
  }

  private static AbstractButton buttonOf(final Container container, final String text) {
    return componentsOf(container)
        .filter(component -> isButton(component, text))
        .map(AbstractButton.class::cast)
        .findFirst()
        .orElseThrow();
  }

  private static void awaitButtonStartingWith(final ForumGamesPanel page, final String start) {
    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () ->
                assertThat(componentsOf(page))
                    .as("a button reading " + start)
                    .anyMatch(
                        component ->
                            component instanceof AbstractButton button
                                && String.valueOf(button.getText()).startsWith(start)));
  }

  private static boolean isButton(final Component component, final String text) {
    return component instanceof AbstractButton button && text.equals(button.getText());
  }

  private static JTextField searchOf(final ForumGamesPanel page) {
    return componentsOf(page)
        .filter(JTextField.class::isInstance)
        .map(JTextField.class::cast)
        .filter(
            field -> "Search games".equals(field.getClientProperty("JTextField.placeholderText")))
        .findFirst()
        .orElseThrow();
  }

  /** The text field of the post being written, the page's one text to edit. */
  private static JTextArea composerOf(final ForumGamesPanel page) {
    return componentsOf(page)
        .filter(JTextArea.class::isInstance)
        .map(JTextArea.class::cast)
        .filter(JTextArea::isEditable)
        .findFirst()
        .orElseThrow();
  }

  private static Stream<Component> componentsOf(final Component component) {
    return component instanceof Container container
        ? Stream.concat(
            Stream.of(component),
            Arrays.stream(container.getComponents()).flatMap(ForumGamesPanelTest::componentsOf))
        : Stream.of(component);
  }
}
