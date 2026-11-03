package games.strategy.engine.posted.game.pbf;

import static com.github.tomakehurst.wiremock.client.WireMock.aMultipart;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import games.strategy.engine.posted.game.pbf.NodeBbForumPoster.ForumPostingParameters;
import games.strategy.engine.posted.game.pbf.NodeBbForumPoster.SaveGameParameter;
import games.strategy.triplea.settings.AbstractClientSettingTestCase;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.triplea.test.common.TestDataFileReader;

// the proxy settings are client settings
class NodeBbForumPosterTest extends AbstractClientSettingTestCase {
  private static final String SESSION = "s%3Asession";
  private static final String TURN = "### TripleA Turn Summary: Germans round 5\nMoves done";

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

  /** sample response from https://forums.triplea-game.org/ */
  @Test
  void uploadResponseParsing_tripleaForums() {
    String sampleResponse =
        TestDataFileReader.readContents("forums/upload_response_triplea_forums.json");
    String saveGameUrl = NodeBbForumPoster.parseSaveGameUrlFromJsonResponse(sampleResponse);
    assertThat(saveGameUrl).isEqualTo("/assets/uploads/files/1665366598238-triplea_3321_1car.tsvg");
  }

  /** sample response from https://www.axisandallies.org/ */
  @Test
  void uploadResponseParsing_axisAndAlliesOrg() {
    String sampleResponse =
        TestDataFileReader.readContents("forums/upload_response_axis_and_allies_org.json");
    String saveGameUrl = NodeBbForumPoster.parseSaveGameUrlFromJsonResponse(sampleResponse);
    assertThat(saveGameUrl)
        .isEqualTo("/forums/assets/uploads/files/1665368873282-triplea_20781_1car.tsvg");
  }

  @DisplayName("Signed in, the turn goes to the game's thread with the session")
  @Test
  void turnIsPostedWithSession() throws Exception {
    givenSessionWithToken();
    forum.stubFor(
        post(urlEqualTo("/api/v3/topics/101"))
            .willReturn(aResponse().withBody("{\"response\": {\"pid\": 9}}")));

    final String result =
        givenPosterWithSession()
            .postTurnSummary("Moves done", "TripleA Turn Summary: Germans round 5", null)
            .get();

    assertThat(result).as("the forum shows the post at once").isEqualTo("Successfully posted!");
    forum.verify(
        postRequestedFor(urlEqualTo("/api/v3/topics/101"))
            .withHeader("Cookie", equalTo("express.sid=" + SESSION))
            .withHeader("x-csrf-token", equalTo("token-1"))
            .withRequestBody(equalToJson("{\"content\": \"" + TURN.replace("\n", "\\n") + "\"}")));
  }

  @DisplayName("Signed in, the save game is uploaded under the game's name for it and linked")
  @Test
  void turnIsPostedWithSaveGame(@TempDir final Path folder) throws Exception {
    givenSessionWithToken();
    forum.stubFor(
        post(urlEqualTo("/api/post/upload"))
            .willReturn(
                aResponse()
                    .withBody(
                        "{\"response\": {\"images\": ["
                            + "{\"url\": \"/assets/uploads/files/1-triplea_101_5ger.tsvg\"}]}}")));
    forum.stubFor(
        post(urlEqualTo("/api/v3/topics/101"))
            .willReturn(aResponse().withBody("{\"response\": {\"pid\": 9}}")));
    final Path save = Files.writeString(folder.resolve("triplea8031.tsvg"), "save");

    final String result =
        givenPosterWithSession()
            .postTurnSummary(
                "Moves done",
                "TripleA Turn Summary: Germans round 5",
                // a name with a space, eg of a nation "U S": the forum's file name has none
                SaveGameParameter.builder().path(save).displayName("triplea_101_5U S.tsvg").build())
            .get();

    assertThat(result).as("the forum shows the turn at once").isEqualTo("Successfully posted!");
    forum.verify(
        postRequestedFor(urlEqualTo("/api/post/upload"))
            .withRequestBodyPart(
                aMultipart()
                    .withName("files[]")
                    .withHeader(
                        "Content-Disposition", containing("filename=\"triplea_101_5U_S.tsvg\""))
                    .withBody(equalTo("save"))
                    .build()));
    forum.verify(
        postRequestedFor(urlEqualTo("/api/v3/topics/101"))
            .withRequestBody(
                equalToJson(
                    "{\"content\": \""
                        + TURN.replace("\n", "\\n")
                        + "\\n\\n[Savegame](/assets/uploads/files/1-triplea_101_5ger.tsvg)\"}")));
    try (Stream<Path> left = Files.list(folder)) {
      assertThat(left).as("the save game itself stays for the caller to delete").hasSize(1);
    }
  }

  @DisplayName("Signed in, the player hears when the forum holds the turn back for a moderator")
  @Test
  void heldBackTurnIsTold() throws Exception {
    givenSessionWithToken();
    forum.stubFor(
        post(urlEqualTo("/api/v3/topics/101"))
            .willReturn(
                aResponse()
                    .withStatus(202)
                    .withBody("{\"response\": {\"queued\": true, \"id\": \"r1\"}}")));

    final String result =
        givenPosterWithSession().postTurnSummary("Moves done", "TripleA Turn Summary", null).get();

    assertThat(result)
        .as("a new member's post waits for a moderator")
        .isEqualTo(NodeBbForumPoster.HELD_BACK);
  }

  @DisplayName("Without a session the login token posts the turn, as on the A&A forum")
  @Test
  void turnIsPostedWithTokenWithoutSession() throws Exception {
    forum.stubFor(post(urlEqualTo("/api/v2/topics/101")).willReturn(aResponse()));

    final String result =
        new NodeBbForumPoster(
                ForumPostingParameters.builder()
                    .topicId(101)
                    .token("token".toCharArray())
                    .session(new char[0])
                    .forumUrl(forumUrl())
                    .build())
            .postTurnSummary("Moves done", "TripleA Turn Summary: Germans round 5", null)
            .get();

    assertThat(result)
        .as("the old way, kept for forums without a session")
        .isEqualTo("Successfully posted!");
    forum.verify(
        postRequestedFor(urlEqualTo("/api/v2/topics/101"))
            .withHeader("Authorization", equalTo("Bearer token")));
  }

  private void givenSessionWithToken() {
    forum.stubFor(
        get(urlEqualTo("/api/config"))
            .withHeader("Cookie", equalTo("express.sid=" + SESSION))
            .willReturn(aResponse().withBody("{\"uid\": 7, \"csrf_token\": \"token-1\"}")));
  }

  private NodeBbForumPoster givenPosterWithSession() {
    return new NodeBbForumPoster(
        ForumPostingParameters.builder()
            .topicId(101)
            .token("old token".toCharArray())
            .session(SESSION.toCharArray())
            .forumUrl(forumUrl())
            .build());
  }

  private String forumUrl() {
    return "http://localhost:" + forum.port();
  }
}
