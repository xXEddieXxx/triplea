package games.strategy.triplea.ui;

import games.strategy.engine.data.properties.ColorProperty;
import games.strategy.engine.data.properties.IEditableProperty;
import games.strategy.engine.data.properties.NumberProperty;
import games.strategy.engine.data.properties.PropertiesUi;
import games.strategy.triplea.image.MapImage;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Font;
import java.util.List;
import javax.annotation.Nullable;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * The dialog that changes the font size and the colours of the text on the map: territory names and
 * PUs, unit counts and damage numbers. It is opened from the game's View menu and from the
 * settings; the values are saved at once.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class MapTextStyleEditor {

  /**
   * Shows the dialog over the parent, eg the game window.
   *
   * @param onChange redraws a shown map after the values were changed or reset
   */
  public static void show(final @Nullable Component parent, final Runnable onChange) {
    final NumberProperty fontSize =
        new NumberProperty("Font Size", null, 60, 0, MapImage.getPropertyMapFont().getSize());
    final ColorProperty territoryNameColor =
        new ColorProperty(
            "Territory Name and PU Color",
            null,
            MapImage.getPropertyTerritoryNameAndPuAndCommentColor());
    final ColorProperty unitCountColor =
        new ColorProperty("Unit Count Color", null, MapImage.getPropertyUnitCountColor());
    final ColorProperty unitCountOutline =
        new ColorProperty("Unit Count Outline", null, MapImage.getPropertyUnitCountOutline());
    final ColorProperty factoryDamageColor =
        new ColorProperty(
            "Factory Damage Color", null, MapImage.getPropertyUnitFactoryDamageColor());
    final ColorProperty factoryDamageOutline =
        new ColorProperty(
            "Factory Damage Outline", null, MapImage.getPropertyUnitFactoryDamageOutline());
    final ColorProperty hitDamageColor =
        new ColorProperty("Hit Damage Color", null, MapImage.getPropertyUnitHitDamageColor());
    final ColorProperty hitDamageOutline =
        new ColorProperty("Hit Damage Outline", null, MapImage.getPropertyUnitHitDamageOutline());
    final List<IEditableProperty<?>> properties =
        List.of(
            fontSize,
            territoryNameColor,
            unitCountColor,
            unitCountOutline,
            factoryDamageColor,
            factoryDamageOutline,
            hitDamageColor,
            hitDamageOutline);
    final JPanel ui = new JPanel();
    ui.setLayout(new BorderLayout());
    ui.add(PropertiesUi.newScrollableView(new PropertiesUi(properties, true)), BorderLayout.CENTER);
    ui.add(
        new JLabel(
            "<html>Change the font and color of 'text' (not pictures) on the map. "
                + "<br /><em>(Some people encounter problems with the color picker, "
                + "and this "
                + "<br />is a bug outside of TripleA, located in the 'look and feel' "
                + "that "
                + "<br />you are using. If you have an error come up, try switching to "
                + "the "
                + "<br />basic 'look and feel', then setting the color, then switching "
                + "back.)</em></html>"),
        BorderLayout.NORTH);
    final Object[] options = {"Set Properties", "Reset To Default", "Cancel"};
    final int result =
        JOptionPane.showOptionDialog(
            parent,
            ui,
            "Map Font and Color",
            JOptionPane.YES_NO_CANCEL_OPTION,
            JOptionPane.PLAIN_MESSAGE,
            null,
            options,
            2);
    if (result == 1) {
      MapImage.resetPropertyMapFont();
      MapImage.resetPropertyTerritoryNameAndPuAndCommentColor();
      MapImage.resetPropertyUnitCountColor();
      MapImage.resetPropertyUnitCountOutline();
      MapImage.resetPropertyUnitFactoryDamageColor();
      MapImage.resetPropertyUnitFactoryDamageOutline();
      MapImage.resetPropertyUnitHitDamageColor();
      MapImage.resetPropertyUnitHitDamageOutline();
      onChange.run();
    } else if (result == 0) {
      MapImage.setPropertyMapFont(
          new Font(MapImage.FONT_FAMILY_DEFAULT, Font.BOLD, fontSize.getValue()));
      MapImage.setPropertyTerritoryNameAndPuAndCommentColor(territoryNameColor.getValue());
      MapImage.setPropertyUnitCountColor(unitCountColor.getValue());
      MapImage.setPropertyUnitCountOutline(unitCountOutline.getValue());
      MapImage.setPropertyUnitFactoryDamageColor(factoryDamageColor.getValue());
      MapImage.setPropertyUnitFactoryDamageOutline(factoryDamageOutline.getValue());
      MapImage.setPropertyUnitHitDamageColor(hitDamageColor.getValue());
      MapImage.setPropertyUnitHitDamageOutline(hitDamageOutline.getValue());
      onChange.run();
    }
  }
}
