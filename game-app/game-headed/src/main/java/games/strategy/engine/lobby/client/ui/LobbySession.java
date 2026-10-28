package games.strategy.engine.lobby.client.ui;

/**
 * The connection of the lobby screen: it disconnects only once, whether the player leaves, the
 * connection closes or another screen replaces the lobby, and only leaving tells the screen.
 */
final class LobbySession {
  private final Runnable disconnect;
  private final Runnable whenLeft;
  private boolean connected = true;

  /**
   * @param disconnect closes the connection to the lobby
   * @param whenLeft shows what comes after the lobby, eg the main menu
   */
  LobbySession(final Runnable disconnect, final Runnable whenLeft) {
    this.disconnect = disconnect;
    this.whenLeft = whenLeft;
  }

  /** Leaves the lobby, eg by Leave lobby or when the connection closed: disconnects, then goes. */
  void leave() {
    if (connected) {
      disconnect();
      whenLeft.run();
    }
  }

  /** Disconnects, eg when another screen replaced the lobby; does nothing the second time. */
  void disconnect() {
    if (connected) {
      connected = false;
      disconnect.run();
    }
  }
}
