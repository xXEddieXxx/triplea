package games.strategy.engine.posted.game.pbf;

import static com.github.tomakehurst.wiremock.client.WireMock.aMultipart;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import games.strategy.engine.posted.game.pbf.NodeBbGameThreads.GameThread;
import games.strategy.engine.posted.game.pbf.NodeBbGameThreads.Player;
import games.strategy.engine.posted.game.pbf.NodeBbGameThreads.RefusedException;
import games.strategy.engine.posted.game.pbf.NodeBbGameThreads.SaveGame;
import games.strategy.engine.posted.game.pbf.NodeBbGameThreads.ThreadPage;
import games.strategy.triplea.settings.AbstractClientSettingTestCase;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class NodeBbGameThreadsTest extends AbstractClientSettingTestCase {
  private static final URI FORUM = URI.create("https://forums.triplea-game.org/");
  private static final int PLAY_BY_FORUM = NodeBbGameThreads.TRIPLEA_PLAY_BY_FORUM_CATEGORY;
  private static final String SESSION = "s%3Asession";
  private static final GameThread GLOBAL_GAME =
      GameThread.of(101, "101/global-1940", "Global 1940: Bob vs Ann");
  private static final Player ANN = new Player("Ann Lee", "ann-lee");

  /** Topics the player started: a game of the Play by Forum category and a question elsewhere. */
  private static final String STARTED =
      """
      {"topics": [
        {"tid": 101, "slug": "101/global-1940", "title": "Global 1940: Bob vs Ann", "cid": 45,
         "lastposttime": 1700000000000, "postcount": 40,
         "teaser": {"content": "<h3>TripleA Turn Summary: Germans round 5</h3>",
                    "user": {"userslug": "bob"}}},
        {"tid": 102, "title": "Lobby question", "cid": 10, "teaser": null,
         "lastposttime": 1700000100000, "postcount": 3},
        {"tid": 105, "title": "Abandoned game", "cid": 45, "deleted": 1,
         "lastposttime": 1700000500000, "postcount": 2}
      ]}
      """;

  /**
   * Posts of the player: a save game in another category, a later answer in the game above, a post
   * elsewhere and the first post of the deleted game.
   */
  private static final String POSTED =
      """
      {"posts": [
        {"tid": 103, "timestamp": 1700000200000,
         "content": "<p><a href=\\"/assets/uploads/files/1-triplea_103_3rus.tsvg\\">Savegame</a></p>",
         "topic": {"tid": 103, "title": "Pacific &amp; friends", "postcount": 12},
         "category": {"cid": 20, "name": "Off Topic"}},
        {"tid": 101, "timestamp": 1700000300000, "content": "<p>gg</p>",
         "user": {"userslug": "ann-lee"},
         "topic": {"tid": 101, "title": "Global 1940: Bob vs Ann", "postcount": 41},
         "category": {"cid": 46, "name": "Play  By Forum"}},
        {"tid": 104, "timestamp": 1700000400000, "content": "<p>hello</p>",
         "topic": {"tid": 104, "title": "Chat"}, "category": null},
        {"tid": 105, "timestamp": 1700000500000, "content": "<p>Who joins?</p>",
         "topic": {"tid": 105, "title": "Abandoned game", "deleted": 1},
         "category": {"cid": 45, "name": "Play  By Forum"}}
      ]}
      """;

  /** The topics the player follows: the game of another category, answered since by Bob. */
  private static final String WATCHED =
      """
      {"topics": [
        {"tid": 103, "slug": "103/pacific", "title": "Pacific &amp; friends", "cid": 20,
         "lastposttime": 1700000600000, "postcount": 14, "unread": true,
         "teaser": {"content": "<h3>TripleA Turn Summary: Japanese round 4</h3>",
                    "user": {"userslug": "bob"}}},
        {"tid": 106, "title": "Site news", "cid": 1, "lastposttime": 1700000700000},
        {"tid": 107, "slug": "107/others", "title": "Carl vs Dan", "cid": 45,
         "lastposttime": 1700000800000, "teaser": {"user": {"userslug": "carl"}}}
      ]}
      """;

  private static final String PAGE =
      """
      {"posts": [
        {"timestamp": 1700000000000, "pid": 901,
         "user": {"username": "ann", "userslug": "ann", "displayname": "Ann Lee",
                  "icon:text": "A", "icon:bgColor": "#ffc107"},
         "content": "<h3>TripleA Turn Summary: Russians round 3</h3><p>Moves&nbsp;done<br>Attack</p>\
      <p><a href=\\"/assets/uploads/files/1700-triplea_103_3rus.tsvg\\">Savegame</a></p>"},
        {"timestamp": 1, "deleted": 1, "user": {"username": "bob"}, "content": "<p>gone</p>"}
      ],
      "pagination": {"currentPage": 2, "pageCount": 3}, "isFollowing": true}
      """;

  private WireMockServer forum;

  @BeforeEach
  void givenForum() {
    forum = new WireMockServer(0);
    forum.start();
  }

  @AfterEach
  void stopForum() {
    forum.stop();
  }

  @DisplayName("Game threads are Play by Forum topics and topics with the player's save games")
  @Test
  void gameThreadsOfPlayByForumAndSaveGames() {
    final List<GameThread> threads =
        NodeBbGameThreads.gameThreads(
            json(STARTED), json(WATCHED), List.of(json(POSTED)), FORUM, PLAY_BY_FORUM);

    assertThat(threads)
        .as(
            "the question, the chat and the news are no games, the deleted game is gone, Carl's"
                + " game is only followed, to watch; the followed game Ann posted in has Bob's newer"
                + " turn; the last answered game comes first")
        .containsExactly(
            new GameThread(
                107,
                "107/others",
                "Carl vs Dan",
                Instant.ofEpochMilli(1700000800000L),
                0,
                "",
                "carl",
                false,
                true),
            new GameThread(
                103,
                "103/pacific",
                "Pacific & friends",
                Instant.ofEpochMilli(1700000600000L),
                14,
                "Japanese, round 4",
                "bob",
                true,
                false),
            new GameThread(
                101,
                "101/global-1940",
                "Global 1940: Bob vs Ann",
                Instant.ofEpochMilli(1700000300000L),
                41,
                "Germans, round 5",
                "ann-lee",
                false,
                false));
    assertThat(threads)
        .as("Ann's move where Bob wrote last, not where she did, nor in a game she only watches")
        .extracting(game -> game.waitsFor(ANN))
        .containsExactly(false, true, false);
  }

  @DisplayName("A thread page has its posts as text with their save games and turns")
  @Test
  void threadPageReadsPostsAndSaveGames() {
    final ThreadPage page = NodeBbGameThreads.threadPage(json(PAGE), FORUM);

    assertThat(page.posts())
        .as("the deleted post is left out; the text keeps its lines, the save game its turn")
        .singleElement()
        .satisfies(
            post -> {
              assertThat(
                      List.of(
                          post.author(), post.authorSlug(), post.avatarText(), post.avatarColor()))
                  .as("the name shown, the one to know the player by, the forum's avatar")
                  .containsExactly("Ann Lee", "ann", "A", "#ffc107");
              assertThat(post.text())
                  .as("the text, its lines kept")
                  .isEqualTo(
                      "TripleA Turn Summary: Russians round 3\nMoves done\nAttack\nSavegame");
              assertThat(post.html())
                  .as("the forum's HTML, to show with its formatting")
                  .startsWith("<h3>TripleA Turn Summary: Russians round 3</h3><p>Moves&nbsp;done");
              assertThat(post.saveGames())
                  .as("the save game, with the turn the post names")
                  .containsExactly(
                      new SaveGame(
                          URI.create(
                              "https://forums.triplea-game.org/assets/uploads/files/"
                                  + "1700-triplea_103_3rus.tsvg"),
                          "1700-triplea_103_3rus.tsvg",
                          "Russians, round 3"));
            });
    assertThat(List.of(page.page(), page.pageCount()))
        .as("the page and how many there are")
        .containsExactly(2, 3);
    assertThat(page.watched()).as("the player follows the thread").isTrue();
    assertThat(page.posts().getFirst().pid()).as("the post's number, to edit it").isEqualTo(901);
    assertThat(List.of(page.playersBut("bob"), page.playersBut("ann")))
        .as("Ann to mention, but not when Ann writes")
        .containsExactly(List.of("ann"), List.of());
  }

  @DisplayName("Only save games the forum itself keeps count, links elsewhere do not")
  @Test
  void foreignLinksAreNoSaveGames() {
    final String post =
        "<a href=\"https://evil.example/x.tsvg\">a</a>"
            + "<a href=\"http://forums.triplea-game.org/assets/plain.tsvg\">b</a>"
            + "<a href=\"/outgoing?url=https://evil.example/y.tsvg\">c</a>"
            + "<a href=\"/assets/uploads/files/100%25_win.tsvg\">d</a>";
    final String axisAndAllies =
        "<a href=\"https://www.axisandallies.org/other-app/x.tsvg\">a</a>"
            + "<a href=\"https://www.axisandallies.org/forums/../other-app/y.tsvg\">c</a>"
            + "<a href=\"/forums/%2e%2e/other-app/z.tsvg\">d</a>"
            + "<a href=\"/forums/assets/uploads/files/1-game.tsvg\">b</a>";

    assertThat(NodeBbGameThreads.saveGamesIn(post, FORUM))
        .as("another server, plain http and a redirect page are no save games of the thread")
        .extracting(SaveGame::fileName)
        .containsExactly("100%_win.tsvg");
    assertThat(
            NodeBbGameThreads.saveGamesIn(
                axisAndAllies, URI.create("https://www.axisandallies.org/forums/")))
        .as("on a shared host only files below the forum's path")
        .extracting(SaveGame::fileName)
        .containsExactly("1-game.tsvg");
  }

  @DisplayName("Reading a player's games only asks the forum, with the player's session")
  @Test
  void readingOnlyAsks() throws Exception {
    forum.stubFor(
        get(urlEqualTo("/api/user/ann-lee/topics")).willReturn(aResponse().withBody(STARTED)));
    forum.stubFor(
        get(urlPathEqualTo("/api/user/ann-lee/posts")).willReturn(aResponse().withBody(POSTED)));

    final List<GameThread> threads =
        NodeBbGameThreads.withSession(
                "http://localhost:" + forum.port() + "/", PLAY_BY_FORUM, SESSION)
            .threadsOf(ANN);

    assertThat(threads)
        .as("read by the player's name in the forum's addresses; the games as above")
        .extracting(GameThread::topicId)
        .containsExactly(101, 103);
    assertThat(forum.findAll(anyRequestedFor(anyUrl())))
        .as("nothing is posted: every request is a GET with the player's session")
        .allSatisfy(
            request -> {
              assertThat(request.getMethod().getName()).isEqualTo("GET");
              assertThat(request.getHeader("Cookie")).isEqualTo("express.sid=" + SESSION);
            })
        .extracting(LoggedRequest::getUrl)
        .contains("/api/user/ann-lee/posts?page=3");
  }

  @DisplayName("A thread's page is asked for oldest post first, whatever the player's own setting")
  @Test
  void pageIsAskedOldestFirst() throws Exception {
    forum.stubFor(
        get(urlEqualTo("/api/topic/103/pacific?page=2&sort=oldest_to_newest"))
            .willReturn(aResponse().withBody(PAGE)));

    final ThreadPage page =
        NodeBbGameThreads.withSession(
                "http://localhost:" + forum.port() + "/", PLAY_BY_FORUM, SESSION)
            .page(GameThread.of(103, "103/pacific", "Pacific"), 2);

    assertThat(page.pageCount())
        .as("the order named: the forum orders by the signed-in player's setting otherwise")
        .isEqualTo(3);
  }

  @DisplayName("A reply goes to the thread with the session and its token, the save game linked")
  @Test
  void replyLinksTheUploadedSaveGame(@TempDir final Path folder) throws Exception {
    givenSessionWithToken();
    forum.stubFor(
        post(urlEqualTo("/api/post/upload"))
            .withHeader("x-csrf-token", equalTo("token-1"))
            .willReturn(
                aResponse()
                    .withBody(
                        """
                        {"status": {"code": "ok"}, "response": {"images": [
                          {"url": "/assets/uploads/files/1-triplea_101_6ger.tsvg"}]}}
                        """)));
    forum.stubFor(
        post(urlEqualTo("/api/v3/topics/101"))
            .withHeader("x-csrf-token", equalTo("token-1"))
            .willReturn(aResponse().withBody("{\"response\": {\"pid\": 9}}")));
    final Path save = Files.writeString(folder.resolve("triplea_101_6ger.tsvg"), "save");

    final boolean heldBack = givenSignedInReader().reply(GLOBAL_GAME, "Your turn!", save);

    assertThat(heldBack).as("the forum shows the post at once").isFalse();
    forum.verify(
        postRequestedFor(urlEqualTo("/api/post/upload"))
            .withHeader("Cookie", equalTo("express.sid=" + SESSION))
            .withRequestBodyPart(
                aMultipart().withName("files[]").withBody(equalTo("save")).build()));
    forum.verify(
        postRequestedFor(urlEqualTo("/api/v3/topics/101"))
            .withHeader("Cookie", equalTo("express.sid=" + SESSION))
            .withRequestBody(
                equalToJson(
                    "{\"content\": \"Your turn!\\n\\n"
                        + "[Savegame](/assets/uploads/files/1-triplea_101_6ger.tsvg)\"}")));
  }

  @DisplayName("A reply says when the forum holds it back for a moderator, and why it refused one")
  @Test
  void replyHeldBackOrRefused() throws Exception {
    givenSessionWithToken();
    forum.stubFor(
        post(urlEqualTo("/api/v3/topics/101"))
            .withRequestBody(equalToJson("{\"content\": \"Your turn!\"}"))
            // NodeBB answers a post it holds back with 202
            .willReturn(
                aResponse()
                    .withStatus(202)
                    .withBody("{\"response\": {\"queued\": true, \"id\": \"r1\"}}")));
    forum.stubFor(
        post(urlEqualTo("/api/v3/topics/101"))
            .withRequestBody(equalToJson("{\"content\": \"ok\"}"))
            .willReturn(
                aResponse()
                    .withStatus(400)
                    .withBody(
                        "{\"status\": {\"code\": \"bad-request\","
                            + " \"message\": \"Please enter a longer post.\"}}")));

    assertThat(givenSignedInReader().reply(GLOBAL_GAME, "Your turn!"))
        .as("a new member's post waits for a moderator")
        .isTrue();
    assertThatThrownBy(() -> givenSignedInReader().reply(GLOBAL_GAME, "ok"))
        .as("the forum's own reason, not only its status")
        .hasMessage("The forum answered HTTP 400: Please enter a longer post.")
        // a refusal: nothing written, unlike a request without an answer
        .isInstanceOf(RefusedException.class);
  }

  @DisplayName("A forum that fails on its side refuses nothing for sure: it may have written")
  @Test
  void serverErrorIsNoRefusal() {
    givenSessionWithToken();
    forum.stubFor(post(urlEqualTo("/api/v3/topics")).willReturn(aResponse().withStatus(502)));

    assertThatThrownBy(() -> givenSignedInReader().startThread("Global 1940", "Who joins?"))
        .as("so the setup does not start a second thread by mistake")
        .isInstanceOf(IOException.class)
        .isNotInstanceOf(RefusedException.class);
  }

  @DisplayName("A new game thread goes to the Play by Forum category; one held back has no id yet")
  @Test
  void startThreadInPlayByForumCategory() throws Exception {
    givenSessionWithToken();
    forum.stubFor(
        post(urlEqualTo("/api/v3/topics"))
            .withHeader("x-csrf-token", equalTo("token-1"))
            .withRequestBody(
                equalToJson(
                    "{\"cid\": 45, \"title\": \"Global 1940: Ann vs Bob\","
                        + " \"content\": \"Who joins?\"}"))
            .willReturn(
                aResponse()
                    .withBody(
                        "{\"response\": {\"tid\": 120, \"slug\": \"120/global-1940\","
                            + " \"title\": \"Global 1940: Ann vs Bob\"}}")));
    forum.stubFor(
        post(urlEqualTo("/api/v3/topics"))
            .withRequestBody(equalToJson("{\"cid\": 45, \"title\": \"Held\", \"content\": \"x\"}"))
            .willReturn(
                aResponse().withStatus(202).withBody("{\"response\": {\"queued\": true}}")));

    assertThat(givenSignedInReader().startThread("Global 1940: Ann vs Bob", "Who joins?"))
        .as("the thread to post the game's turns to")
        .map(GameThread::topicId)
        .contains(120);
    assertThat(givenSignedInReader().startThread("Held", "x"))
        .as("a new member's thread waits for a moderator: no id to post to yet")
        .isEmpty();
  }

  @DisplayName("A picture goes up as a picture, with the session and its token, as the forum's own")
  @Test
  void pictureIsUploadedAsPicture(@TempDir final Path folder) throws Exception {
    givenSessionWithToken();
    forum.stubFor(
        post(urlEqualTo("/api/post/upload"))
            .withHeader("x-csrf-token", equalTo("token-1"))
            .willReturn(
                aResponse()
                    .withBody(
                        "{\"response\": {\"images\": [{\"url\": \"/assets/uploads/files/1-map.png\"}]}}")));
    final Path picture = Files.write(folder.resolve("map.png"), new byte[] {1, 2, 3});

    final String address = givenSignedInReader().uploadPicture(picture);

    assertThat(address)
        .as("the forum's address of the picture, to link")
        .isEqualTo("/assets/uploads/files/1-map.png");
    forum.verify(
        postRequestedFor(urlEqualTo("/api/post/upload"))
            .withHeader("Cookie", equalTo("express.sid=" + SESSION))
            .withRequestBodyPart(
                aMultipart()
                    .withName("files[]")
                    // the forum takes a file as a picture by its type only
                    .withHeader("Content-Type", containing("image/png"))
                    .build()));
  }

  @DisplayName("Watching follows the thread with the session and its token, and stops again")
  @Test
  void watchFollowsTheThread() throws Exception {
    givenSessionWithToken();
    forum.stubFor(
        put(urlEqualTo("/api/v3/topics/101/follow"))
            .withHeader("x-csrf-token", equalTo("token-1"))
            .willReturn(aResponse().withBody("{\"status\": {\"code\": \"ok\"}}")));
    forum.stubFor(
        delete(urlEqualTo("/api/v3/topics/101/follow"))
            .withHeader("x-csrf-token", equalTo("token-1"))
            .willReturn(aResponse().withBody("{\"status\": {\"code\": \"ok\"}}")));

    givenSignedInReader().watch(GLOBAL_GAME);
    givenSignedInReader().unwatch(GLOBAL_GAME);

    forum.verify(
        putRequestedFor(urlEqualTo("/api/v3/topics/101/follow"))
            .withHeader("Cookie", equalTo("express.sid=" + SESSION)));
    forum.verify(
        deleteRequestedFor(urlEqualTo("/api/v3/topics/101/follow"))
            .withHeader("Cookie", equalTo("express.sid=" + SESSION)));
  }

  @DisplayName("The player's own post is read as written, edited and deleted with the token")
  @Test
  void ownPostIsEditedAndDeleted() throws Exception {
    givenSessionWithToken();
    forum.stubFor(
        get(urlEqualTo("/api/v3/posts/55/raw"))
            .willReturn(aResponse().withBody("{\"response\": {\"content\": \"**Hi** all\"}}")));
    forum.stubFor(
        put(urlEqualTo("/api/v3/posts/55"))
            .withHeader("x-csrf-token", equalTo("token-1"))
            .willReturn(aResponse().withBody("{\"status\": {\"code\": \"ok\"}}")));
    forum.stubFor(
        delete(urlEqualTo("/api/v3/posts/55/state"))
            .withHeader("x-csrf-token", equalTo("token-1"))
            .willReturn(aResponse().withBody("{\"status\": {\"code\": \"ok\"}}")));

    final String raw = givenSignedInReader().rawPost(55);
    givenSignedInReader().editPost(55, "**Hi** everyone");
    givenSignedInReader().deletePost(55);

    assertThat(raw).as("the Markdown as written, to edit").isEqualTo("**Hi** all");
    forum.verify(
        putRequestedFor(urlEqualTo("/api/v3/posts/55"))
            .withRequestBody(equalToJson("{\"content\": \"**Hi** everyone\"}")));
    forum.verify(
        deleteRequestedFor(urlEqualTo("/api/v3/posts/55/state"))
            .withHeader("Cookie", equalTo("express.sid=" + SESSION)));
  }

  private void givenSessionWithToken() {
    forum.stubFor(
        get(urlEqualTo("/api/config"))
            .withHeader("Cookie", equalTo("express.sid=" + SESSION))
            .willReturn(aResponse().withBody("{\"uid\": 7, \"csrf_token\": \"token-1\"}")));
  }

  private NodeBbGameThreads givenSignedInReader() {
    return NodeBbGameThreads.withSession(
        "http://localhost:" + forum.port() + "/", PLAY_BY_FORUM, SESSION);
  }

  @DisplayName("Browsing shows the category's games, the last answered first, without pinned notes")
  @Test
  void browseShowsCategoryGames() {
    final String category =
        """
        {"topics": [
          {"tid": 1, "title": "Rules of this category", "pinned": 1, "lastposttime": 9},
          {"tid": 4133, "slug": "4133/testing", "title": "Testing Global '40", "postcount": 30,
           "lastposttime": 1743505988047,
           "teaser": {"content": "<h3>TripleA Turn Summary: Russians round 1</h3>"}},
          {"tid": 4441, "slug": "4441/qualifier", "title": "Qualifier Game 2",
           "lastposttime": 1793000000000, "teaser": null}
        ]}
        """;

    assertThat(NodeBbGameThreads.categoryGames(json(category)))
        .as("the newest game first, with its last turn where the forum shows one")
        .extracting(GameThread::topicId, GameThread::lastTurn)
        .containsExactly(tuple(4441, ""), tuple(4133, "Russians, round 1"));
  }

  @DisplayName("A save game is downloaded under its name, only from the forum")
  @Test
  void downloadsSaveGame(@TempDir final Path folder) throws Exception {
    forum.stubFor(
        get(urlEqualTo("/assets/uploads/files/1-triplea_103_3rus.tsvg"))
            .willReturn(aResponse().withBody(new byte[] {1, 2, 3})));
    final String forumUrl = "http://localhost:" + forum.port();
    final SaveGame saveGame =
        new SaveGame(
            URI.create(forumUrl + "/assets/uploads/files/1-triplea_103_3rus.tsvg"),
            "1-triplea_103_3rus.tsvg",
            "");

    final Path file =
        NodeBbGameThreads.withSession(forumUrl, PLAY_BY_FORUM, SESSION).download(saveGame, folder);

    assertThat(file).as("named as in the forum").hasFileName("1-triplea_103_3rus.tsvg");
    assertThat(Files.readAllBytes(file)).as("the forum's bytes").containsExactly(1, 2, 3);
    assertThat(folder.toFile().list())
        .as("no part of the download is left beside it")
        .containsExactly("1-triplea_103_3rus.tsvg");
    forum.verify(
        getRequestedFor(urlEqualTo("/assets/uploads/files/1-triplea_103_3rus.tsvg"))
            .withHeader("Cookie", equalTo("express.sid=" + SESSION)));
  }

  @DisplayName("A browser session signs in as its player, whose name the forum tells")
  @Test
  void sessionSignsInItsPlayer() throws Exception {
    forum.stubFor(
        get(urlEqualTo("/api/config"))
            .withHeader("Cookie", equalTo("express.sid=s%3Aabc"))
            .willReturn(aResponse().withBody("{\"uid\": 7176, \"loggedIn\": true}")));
    forum.stubFor(
        get(urlEqualTo("/api/config"))
            .withHeader("Cookie", equalTo("express.sid=s%3Aold"))
            .willReturn(aResponse().withBody("{\"uid\": 0, \"loggedIn\": false}")));
    forum.stubFor(
        get(urlEqualTo("/api/user/uid/7176"))
            .willReturn(
                aResponse().withBody("{\"username\": \"O&#x27;Neil\", \"userslug\": \"oneil\"}")));
    final String forumUrl = "http://localhost:" + forum.port();

    final Optional<Player> player =
        NodeBbGameThreads.withSession(forumUrl, PLAY_BY_FORUM, "s%3Aabc").signedInPlayer();
    final Optional<Player> guest =
        NodeBbGameThreads.withSession(forumUrl, PLAY_BY_FORUM, "s%3Aold").signedInPlayer();

    assertThat(player)
        .as("the session's player, the name unescaped as the forum shows it")
        .contains(new Player("O'Neil", "oneil"));
    assertThat(guest).as("a session that ran out signs in no one").isEmpty();
  }

  @DisplayName("A redirect is not followed, so the player's session stays with the forum")
  @Test
  void redirectsAreNotFollowed() {
    forum.stubFor(
        get(urlEqualTo("/api/user/ann/topics"))
            .willReturn(aResponse().withStatus(302).withHeader("Location", "/elsewhere")));
    final NodeBbGameThreads reader =
        NodeBbGameThreads.withSession("http://localhost:" + forum.port(), PLAY_BY_FORUM, SESSION);

    assertThatThrownBy(() -> reader.threadsOf(new Player("Ann", "ann")))
        .as("the redirect is an error, not a way to another page")
        .isInstanceOf(IOException.class);
    forum.verify(0, getRequestedFor(urlEqualTo("/elsewhere")));
  }

  @DisplayName("A save game from another server is not downloaded")
  @Test
  void foreignSaveGameIsNotDownloaded(@TempDir final Path folder) {
    final NodeBbGameThreads reader =
        NodeBbGameThreads.withSession("http://localhost:" + forum.port(), PLAY_BY_FORUM, SESSION);
    final SaveGame foreign = new SaveGame(URI.create("https://evil.example/x.tsvg"), "x.tsvg", "");

    assertThatThrownBy(() -> reader.download(foreign, folder))
        .as("the player's session would go along to that server")
        .isInstanceOf(IOException.class);
    assertThat(folder.toFile().list()).as("nothing is written").isEmpty();
  }

  @DisplayName("A download that leaves the forum's path with \"..\" is refused before it is asked")
  @Test
  void downloadStaysBelowForumPath(@TempDir final Path folder) {
    final String forumUrl = "http://localhost:" + forum.port() + "/forums/";
    final NodeBbGameThreads reader = NodeBbGameThreads.withSession(forumUrl, 0, SESSION);

    for (final String link : List.of("../elsewhere/x.tsvg", "%2e%2e/elsewhere/x.tsvg")) {
      final SaveGame elsewhere = new SaveGame(URI.create(forumUrl + link), "x.tsvg", "");
      assertThatThrownBy(() -> reader.download(elsewhere, folder))
          .as("%s would take the player's session to another application", link)
          .isInstanceOf(IOException.class);
    }
    forum.verify(0, anyRequestedFor(anyUrl()));
    assertThat(folder.toFile().list()).as("nothing is written").isEmpty();
  }

  @DisplayName("A file name from the forum cannot leave the folder")
  @Test
  void fileNamesAreSafe() {
    assertThat(
            List.of(
                NodeBbGameThreads.safeFileName("../../evil name"),
                NodeBbGameThreads.safeFileName("TURN.TSVG")))
        .as("no slashes or spaces; an upper case ending is kept as it is")
        .containsExactly(".._.._evil_name.tsvg", "TURN.TSVG");
  }

  @DisplayName("The plain text of a post keeps its lines and drops tags and entities")
  @Test
  void plainTextKeepsLines() {
    assertThat(NodeBbGameThreads.plainText("<p>Hi&amp;bye<br/>next</p><ul><li>one</li></ul>"))
        .as("a line per paragraph, break and list item")
        .isEqualTo("Hi&bye\nnext\none");
    assertThat(NodeBbGameThreads.turnOf("<p>no turn here</p>"))
        .as("a post without a TripleA turn names none")
        .isEmpty();
  }

  private static JsonObject json(final String text) {
    return JsonParser.parseString(text).getAsJsonObject();
  }
}
