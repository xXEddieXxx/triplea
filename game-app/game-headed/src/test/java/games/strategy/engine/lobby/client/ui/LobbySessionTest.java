package games.strategy.engine.lobby.client.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

final class LobbySessionTest {
  private final List<String> events = new ArrayList<>();
  private final LobbySession session =
      new LobbySession(() -> events.add("disconnect"), () -> events.add("main menu"));

  @DisplayName("Leaving disconnects, then shows what comes after the lobby, once")
  @Test
  void leaveOnce() {
    session.leave();
    // eg the connection closes after Leave lobby
    session.leave();

    assertThat(events)
        .as("a second leave must not shut down the model or show the menu again")
        .containsExactly("disconnect", "main menu");
  }

  @DisplayName("Another screen replacing the lobby disconnects without showing the main menu")
  @Test
  void replacedLobbyOnlyDisconnects() {
    session.disconnect();
    // eg the closed connection reports after the replacement
    session.leave();

    assertThat(events).as("the screen that replaced the lobby stays").containsExactly("disconnect");
  }
}
