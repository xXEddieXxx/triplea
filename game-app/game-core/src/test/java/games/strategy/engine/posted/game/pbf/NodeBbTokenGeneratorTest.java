package games.strategy.engine.posted.game.pbf;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import games.strategy.triplea.settings.AbstractClientSettingTestCase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

final class NodeBbTokenGeneratorTest extends AbstractClientSettingTestCase {
  private final WireMockServer forum = new WireMockServer(0);

  @BeforeEach
  void givenForum() {
    forum.start();
  }

  @AfterEach
  void stopForum() {
    forum.stop();
  }

  @DisplayName("A forum that shows players only to members says why a password cannot sign in")
  @Test
  void closedPlayerLookupExplainsTheToken() {
    forum.stubFor(
        get(urlEqualTo("/api/user/username/Ann"))
            .willReturn(
                aResponse()
                    .withStatus(401)
                    .withBody("{\"status\":{\"code\":\"not-authorised\"}}")));

    assertThatThrownBy(() -> givenGenerator().generateToken("Ann", "secret", null))
        .as("not that the player does not exist, as forums.triplea-game.org answers today")
        .hasMessage(NodeBbTokenGenerator.PASSWORD_LOGIN_CLOSED);
  }

  @DisplayName("A forum without the route from a password to a token says why")
  @Test
  void missingTokenRouteExplainsTheToken() {
    forum.stubFor(
        get(urlEqualTo("/api/user/username/Ann"))
            .willReturn(
                aResponse().withBody("{\"uid\": 7, \"banned\": false, \"email:confirmed\": 1}")));
    forum.stubFor(
        post(urlEqualTo("/api/v2/users/7/tokens")).willReturn(aResponse().withStatus(404)));

    assertThatThrownBy(() -> givenGenerator().generateToken("Ann", "secret", null))
        .as("the old write API is gone from forums.triplea-game.org")
        .hasMessage(NodeBbTokenGenerator.PASSWORD_LOGIN_CLOSED);
  }

  private NodeBbTokenGenerator givenGenerator() {
    return new NodeBbTokenGenerator("http://localhost:" + forum.port());
  }
}
