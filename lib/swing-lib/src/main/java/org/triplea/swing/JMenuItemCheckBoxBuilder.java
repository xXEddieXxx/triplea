package org.triplea.swing;

import java.awt.Toolkit;
import java.awt.event.ItemEvent;
import java.io.Serial;
import java.util.Optional;
import java.util.function.Consumer;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JToggleButton.ToggleButtonModel;
import javax.swing.KeyStroke;
import org.triplea.swing.key.binding.KeyCode;

/**
 * Builds a JMenuCheckBox. This is a menu item with a checkbox next to it. By default the checkbox
 * will be unselected.
 *
 * <p>Example usage:. <code><pre>
 *   JMenu menu = new JMenuBuilder("Menu Text")
 *     .addMenuItem(new JMenuItemCheckboxBuilder("checkbox", 'C')
 *         .selected(true)
 *         .actionListener(selected -> doAction(selected))
 *         .build())
 *     .build();
 * </pre></code>
 */
public class JMenuItemCheckBoxBuilder {
  private final String title;
  private final KeyCode mnemonic;
  private Consumer<Boolean> action;
  private boolean selected;
  private SettingPersistence settingPersistence;
  private KeyCode accelerator;

  /** Sets the title that appears next to the checkbox. */
  public JMenuItemCheckBoxBuilder(final String title, final KeyCode mnemonic) {
    if (title == null || title.isBlank()) {
      throw new IllegalArgumentException("Menu item title cannot be null or blank");
    }
    if (mnemonic == null) {
      throw new IllegalArgumentException("Menu item mnemonic cannot be null");
    }
    this.title = title;
    this.mnemonic = mnemonic;
  }

  /** Constructs a Swing JMenu using current builder values. */
  public JCheckBoxMenuItem build() {
    if (title == null || title.isBlank()) {
      throw new IllegalArgumentException("Menu item title cannot be null or blank");
    }
    if (action == null && settingPersistence == null) {
      throw new IllegalArgumentException(
          "Must provide either an action or a setting persistence to save the checkbox value");
    }

    final var checkBox = new JCheckBoxMenuItem(title);
    if (settingPersistence == null) {
      checkBox.setSelected(selected);
    } else {
      // the tick always shows the setting, also after something else, eg a settings window, set it
      checkBox.setModel(new SettingButtonModel(settingPersistence));
    }
    checkBox.setMnemonic(mnemonic.getInputEventCode());

    checkBox.addActionListener(
        e -> Optional.ofNullable(action).ifPresent(a -> a.accept(checkBox.isSelected())));

    Optional.ofNullable(accelerator)
        .map(
            accelerator ->
                KeyStroke.getKeyStroke(
                    accelerator.getInputEventCode(),
                    Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()))
        .ifPresent(checkBox::setAccelerator);
    return checkBox;
  }

  public JMenuItemCheckBoxBuilder selected(final boolean selected) {
    this.selected = selected;
    return this;
  }

  public JMenuItemCheckBoxBuilder actionListener(final Consumer<Boolean> actionListener) {
    this.action = actionListener;
    return this;
  }

  /**
   * Binds setting to a persistence object. The persistence object will be used to load the selected
   * value and when selected will be used to save the selected value.
   */
  public JMenuItemCheckBoxBuilder bindSetting(final SettingPersistence settingPersistence) {
    this.settingPersistence = settingPersistence;
    return this;
  }

  /**
   * Sets up an accelerator, or a hotkey for the menu. Typically activated via "control + keyCode".
   */
  public JMenuItemCheckBoxBuilder accelerator(final KeyCode keyCode) {
    this.accelerator = keyCode;
    return this;
  }

  /** A check box state that is the setting itself: reading it reads the setting, ticking saves. */
  private static final class SettingButtonModel extends ToggleButtonModel {
    @Serial private static final long serialVersionUID = 4613447389226104337L;

    private final transient SettingPersistence settingPersistence;

    SettingButtonModel(final SettingPersistence settingPersistence) {
      this.settingPersistence = settingPersistence;
    }

    @Override
    public boolean isSelected() {
      return settingPersistence.getSetting();
    }

    @Override
    public void setSelected(final boolean selected) {
      if (selected != isSelected()) {
        settingPersistence.saveSetting(selected);
        fireStateChanged();
        fireItemStateChanged(
            new ItemEvent(
                this,
                ItemEvent.ITEM_STATE_CHANGED,
                this,
                selected ? ItemEvent.SELECTED : ItemEvent.DESELECTED));
      }
    }
  }
}
