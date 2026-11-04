package games.strategy.engine.posted.game.pbf;

import java.util.ArrayList;
import java.util.List;
import org.apache.http.Header;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicHeader;
import org.triplea.config.product.ProductVersionReader;

/**
 * Builds {@link CloseableHttpClient} instances configured for talking to NodeBB forum APIs.
 *
 * <p>Each client carries a {@code User-Agent: triplea/<version>} default header identifying TripleA
 * so legitimate forum traffic can be distinguished from anonymous bot traffic by the forum's WAF
 * (e.g. for whitelisting via a Cloudflare rule).
 *
 * <p>Usage:
 *
 * <pre>
 *   try (CloseableHttpClient client = NodeBbHttpClients.builder().build()) { ... }
 *   try (CloseableHttpClient client =
 *       NodeBbHttpClients.builder().bearerToken(token).build()) { ... }
 * </pre>
 */
final class NodeBbHttpClients {
  private static final int CONNECT_TIMEOUT_MILLIS = 10_000;

  /** Long enough for a save game going up to the forum, which answers once it has it all. */
  private static final int READ_TIMEOUT_MILLIS = 60_000;

  private NodeBbHttpClients() {}

  static Builder builder() {
    return new Builder();
  }

  static final class Builder {
    private String bearerToken;
    private String sessionCookie;

    private Builder() {}

    Builder bearerToken(final String bearerToken) {
      this.bearerToken = bearerToken;
      return this;
    }

    /** The player's browser session, the value of NodeBB's "express.sid" cookie. */
    Builder sessionCookie(final String sessionCookie) {
      this.sessionCookie = sessionCookie;
      return this;
    }

    CloseableHttpClient build() {
      final List<Header> headers = new ArrayList<>();
      headers.add(
          new BasicHeader("User-Agent", "triplea/" + ProductVersionReader.getCurrentVersion()));
      if (bearerToken != null) {
        headers.add(new BasicHeader("Authorization", "Bearer " + bearerToken));
      }
      if (sessionCookie != null) {
        headers.add(new BasicHeader("Cookie", "express.sid=" + sessionCookie));
      }
      return HttpClients.custom()
          .setDefaultHeaders(headers)
          // a forum that takes the connection and never answers must not hold a turn forever;
          // a request with a config of its own keeps it
          .setDefaultRequestConfig(
              RequestConfig.custom()
                  .setConnectTimeout(CONNECT_TIMEOUT_MILLIS)
                  .setConnectionRequestTimeout(CONNECT_TIMEOUT_MILLIS)
                  .setSocketTimeout(READ_TIMEOUT_MILLIS)
                  .build())
          .disableCookieManagement()
          .build();
    }
  }
}
