package org.triplea.swing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import java.awt.event.KeyEvent;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.jetbrains.annotations.NonNls;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.triplea.swing.key.binding.KeyCode;

class JMenuItemCheckBoxBuilderTest {
  @NonNls private static final String TITLE = "title";

  @Test
  void verifyTitle() {
    final var checkBoxMenuItem =
        new JMenuItemCheckBoxBuilder(TITLE, KeyCode.A).actionListener(e -> {}).build();
    assertThat(checkBoxMenuItem.getText()).isEqualTo(TITLE);
  }

  @Test
  void verifyMnemonic() {
    final var checkBoxMenuItem =
        new JMenuItemCheckBoxBuilder(TITLE, KeyCode.A).actionListener(e -> {}).build();
    assertThat(checkBoxMenuItem.getMnemonic()).isEqualTo(KeyEvent.VK_A);
  }

  @Test
  void selected() {
    var checkBoxMenuItem =
        new JMenuItemCheckBoxBuilder(TITLE, KeyCode.A).actionListener(e -> {}).build();

    assertThat(checkBoxMenuItem.isSelected()).isFalse();

    checkBoxMenuItem =
        new JMenuItemCheckBoxBuilder(TITLE, KeyCode.A)
            .actionListener(e -> {})
            .selected(true)
            .build();

    assertThat(checkBoxMenuItem.isSelected()).isTrue();
  }

  @DisplayName("A check box bound to a setting shows it, also after something else changed it")
  @Test
  void boundCheckBoxFollowsTheSetting() {
    final AtomicBoolean setting = new AtomicBoolean(true);
    final var checkBoxMenuItem =
        new JMenuItemCheckBoxBuilder(TITLE, KeyCode.A).bindSetting(persistenceOf(setting)).build();

    setting.set(false);

    assertThat(checkBoxMenuItem.isSelected())
        .as("eg the settings window turned the setting off after the menu was built")
        .isFalse();
  }

  @DisplayName("Clicking a check box bound to a setting saves the new value")
  @Test
  void clickingBoundCheckBoxSavesTheSetting() {
    final AtomicBoolean setting = new AtomicBoolean(true);
    final var checkBoxMenuItem =
        new JMenuItemCheckBoxBuilder(TITLE, KeyCode.A).bindSetting(persistenceOf(setting)).build();

    checkBoxMenuItem.doClick();

    assertThat(setting.get()).as("the tick was removed").isFalse();
  }

  private static SettingPersistence persistenceOf(final AtomicBoolean setting) {
    return new SettingPersistence() {
      @Override
      public void saveSetting(final boolean value) {
        setting.set(value);
      }

      @Override
      public boolean getSetting() {
        return setting.get();
      }
    };
  }

  @Nested
  @ExtendWith(MockitoExtension.class)
  final class VerifyActionHandlingTest {
    @Mock private Consumer<Boolean> booleanConsumer;

    @Test
    void addActionListener() {
      var checkBoxMenuItem =
          new JMenuItemCheckBoxBuilder(TITLE, KeyCode.A).actionListener(e -> {}).build();
      assertThat(checkBoxMenuItem.getActionListeners().length).isEqualTo(1);

      checkBoxMenuItem =
          new JMenuItemCheckBoxBuilder(TITLE, KeyCode.A)
              .actionListener(selected -> {})
              .selected(true)
              .build();
      assertThat(checkBoxMenuItem.getActionListeners().length)
          .as(
              "Adding the first action listener replaces the default swing "
                  + "action listener that exists on the component")
          .isEqualTo(1);
    }

    @Test
    void checkBoxNotSelected() {
      final var checkBoxMenuItem =
          new JMenuItemCheckBoxBuilder(TITLE, KeyCode.A).actionListener(booleanConsumer).build();
      checkBoxMenuItem.getActionListeners()[0].actionPerformed(null);
      verify(booleanConsumer).accept(false);
    }

    @Test
    void checkBoxIsSelected() {
      final var checkBoxMenuItem =
          new JMenuItemCheckBoxBuilder(TITLE, KeyCode.A)
              .selected(true)
              .actionListener(booleanConsumer)
              .build();
      checkBoxMenuItem.getActionListeners()[0].actionPerformed(null);
      verify(booleanConsumer).accept(true);
    }
  }
}
