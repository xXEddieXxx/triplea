package games.strategy.triplea.settings.lobby;

import static org.assertj.core.api.Assertions.assertThat;
import static org.triplea.ui.events.queue.test.support.EventQueueAssertions.controllerEventIs;

import games.strategy.triplea.settings.AbstractClientSettingTestCase;
import games.strategy.triplea.settings.ClientSetting;
import games.strategy.triplea.settings.lobby.LobbySelectionViewData.LobbyChoice;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.triplea.ui.events.queue.test.support.EventQueueTestSupport;

class LobbySelectionViewControllerTest extends AbstractClientSettingTestCase {
  final EventQueueTestSupport<
          LobbySelectionSwingEventQueue,
          LobbySelectionViewController.Events,
          LobbySelectionViewData>
      eventQueueTestSupport = new EventQueueTestSupport<>(LobbySelectionSwingEventQueue.class);

  final LobbySelectionViewController lobbySelectionViewController =
      new LobbySelectionViewController(eventQueueTestSupport.getEventQueue());

  /** Ensure we handle all possible events. */
  @ParameterizedTest
  @EnumSource(LobbySelectionView.Events.class)
  void allEventTypesAreHandled(final LobbySelectionView.Events event) {
    lobbySelectionViewController.handleEvent(
        event, LobbySelectionViewControllerTestData.lobbySelectionViewData);
  }

  /** Simulate user clicking radio buttons to select different lobbies. */
  @ParameterizedTest
  @EnumSource(LobbyChoice.class)
  void radioButtonChanged(final LobbyChoice lobbyChoice) {
    final var inputData =
        LobbySelectionViewData.builder()
            .selectedRadioButton(lobbyChoice)
            .uriFieldEnabled(true)
            .uriFieldValue("some value")
            .build();

    // local radio button is being changed from 'other' to 'local'
    // note how uri field value is empty and enabled, should be updated by controller
    // to local host URI and disabled
    lobbySelectionViewController.handleEvent(
        LobbySelectionView.Events.RADIO_BUTTON_CHANGED, inputData);

    assertThat(eventQueueTestSupport.popFirstControllerEvent())
        .is(
            controllerEventIs(
                LobbySelectionViewController.Events.UPDATE_UI_DATA,
                inputData,
                LobbySelectionViewData.builder()
                    .selectedRadioButton(lobbyChoice)
                    .uriFieldEnabled(!lobbyChoice.isReadOnly())
                    .uriFieldValue(lobbyChoice.getUri())
                    .build()));
  }

  /**
   * Send a 'reset to default event', controller should respond with data change event that sets all
   * fields back to default lobby URI values.
   */
  @Test
  void resetToDefault() {
    final LobbySelectionViewData inputData =
        LobbySelectionViewData.builder()
            .selectedRadioButton(LobbyChoice.LOCAL)
            .uriFieldEnabled(true)
            .uriFieldValue("")
            .build();
    lobbySelectionViewController.handleEvent(LobbySelectionView.Events.RESET_TO_DEFAULT, inputData);

    final LobbyChoice defaultLobbyChoice =
        LobbyChoice.fromUri(ClientSetting.lobbyUri.getDefaultValue().orElseThrow());

    assertThat(eventQueueTestSupport.popFirstControllerEvent())
        .is(
            controllerEventIs(
                LobbySelectionViewController.Events.UPDATE_UI_DATA,
                inputData,
                LobbySelectionViewData.builder()
                    .selectedRadioButton(defaultLobbyChoice)
                    .uriFieldEnabled(!defaultLobbyChoice.isReadOnly())
                    .uriFieldValue(defaultLobbyChoice.getUri())
                    .build()));
  }
}
