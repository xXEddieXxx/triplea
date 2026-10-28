package games.strategy.triplea.settings.lobby;

import static org.assertj.core.api.Assertions.assertThat;

import games.strategy.triplea.settings.AbstractClientSettingTestCase;
import games.strategy.triplea.settings.GameSetting;
import games.strategy.triplea.settings.SelectionComponent.SaveContext;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import javax.swing.JTextField;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

final class LobbySelectionViewTest extends AbstractClientSettingTestCase {
  private final List<String> reportedErrors = new ArrayList<>();

  @DisplayName("An invalid lobby address is a failure the settings window shows before closing")
  @Test
  void invalidAddressIsReported() {
    final LobbySelectionView lobby = LobbySelectionViewFactory.build();
    findTextField(lobby.getUiComponent()).orElseThrow().setText("localhost:3000");

    lobby.save(new RecordingSaveContext());

    assertThat(reportedErrors)
        .as("reported, the player can correct the address instead of losing it")
        .containsExactly("must start with 'https://' or 'http://'");
  }

  private static Optional<JTextField> findTextField(final Component component) {
    if (component instanceof JTextField textField) {
      return Optional.of(textField);
    }
    return component instanceof Container container
        ? Arrays.stream(container.getComponents())
            .map(LobbySelectionViewTest::findTextField)
            .flatMap(Optional::stream)
            .findFirst()
        : Optional.empty();
  }

  private final class RecordingSaveContext implements SaveContext {
    @Override
    public void reportError(
        final GameSetting<?> gameSetting,
        final String message,
        final @Nullable Object value,
        final ValueSensitivity valueSensitivity) {
      reportedErrors.add(message);
    }

    @Override
    public <T> void setValue(
        final GameSetting<T> gameSetting,
        final @Nullable T value,
        final ValueSensitivity valueSensitivity) {}
  }
}
