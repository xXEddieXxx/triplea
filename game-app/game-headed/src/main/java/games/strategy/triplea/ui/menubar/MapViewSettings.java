package games.strategy.triplea.ui.menubar;

import games.strategy.triplea.settings.ClientSetting;
import games.strategy.triplea.settings.MapSetting;
import games.strategy.triplea.ui.TripleAFrame;
import games.strategy.triplea.ui.UiContext;
import games.strategy.triplea.ui.UiContext.MapSkin;
import games.strategy.triplea.ui.panels.map.MapPanel;
import java.awt.FlowLayout;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.triplea.swing.JButtonBuilder;

/**
 * The settings of the game's map, which were the View menu: its zoom, the size of its units, the
 * units hidden for a moment and, with beta features, its skin. They show at the top of the In game
 * settings when the game window opens them and change the map at once; the map keeps zoom, unit
 * size and skin per map, as before.
 */
@UtilityClass
@Slf4j
final class MapViewSettings {
  private static final List<Double> UNIT_SIZES =
      List.of(1.25, 1.0, 0.875, 0.8333, 0.75, 0.6666, 0.5625, 0.5);

  static List<MapSetting> of(final TripleAFrame frame) {
    final List<MapSetting> settings = new ArrayList<>();
    settings.add(newZoom(frame.getMapPanel()));
    settings.add(newUnitSize(frame));
    settings.add(newShowUnits(frame));
    final List<MapSkin> skins =
        frame.getUiContext().getSkins(frame.getGame().getData().getMapName());
    if (ClientSetting.showBetaFeatures.getValueOrThrow() && skins.size() >= 2) {
      settings.add(newSkin(frame, skins));
    }
    return settings;
  }

  /** The zoom in percent, with buttons to fit the map into the window or go back to 100%. */
  private static MapSetting newZoom(final MapPanel map) {
    final double min = Math.ceil(map.getMinScale() * 100);
    final double max = UiContext.MAP_SCALE_MAX_VALUE * 100;
    final SpinnerNumberModel model =
        new SpinnerNumberModel(
            Math.clamp((double) Math.round(map.getScale() * 100), min, max), min, max, 1.0);
    model.addChangeListener(e -> map.setScale(model.getNumber().doubleValue() / 100));
    final JPanel control = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
    control.setOpaque(false);
    control.add(
        newSmallButton(
            "Fit width",
            () ->
                model.setValue(
                    percentOf(
                        Math.min(1, (double) map.getWidth() / map.getImageWidth()), map, max))));
    control.add(
        newSmallButton(
            "Fit height",
            () ->
                model.setValue(
                    percentOf((double) map.getHeight() / map.getImageHeight(), map, max))));
    control.add(newSmallButton("100%", () -> model.setValue(100.0)));
    control.add(new JSpinner(model));
    return new MapSetting(
        "Map zoom",
        "How large the map shows, in percent; fit it into the window or go back to 100%.",
        control);
  }

  /** The ratio in whole percent, no smaller than the map allows. */
  private static double percentOf(final double ratio, final MapPanel map, final double max) {
    return Math.clamp(Math.round(Math.max(map.getMinScale(), ratio) * 100), 0, max);
  }

  private static JButton newSmallButton(final String title, final Runnable action) {
    return new JButtonBuilder(title).actionListener(action).build();
  }

  /** The size of the units, kept for this map. */
  private static MapSetting newUnitSize(final TripleAFrame frame) {
    final DecimalFormat percent = new DecimalFormat("0.##");
    final JComboBox<String> sizes =
        new JComboBox<>(
            UNIT_SIZES.stream()
                .map(size -> percent.format(size * 100) + "%")
                .toArray(String[]::new));
    final double current = frame.getUiContext().getUnitImageFactory().getScaleFactor();
    sizes.setSelectedIndex(nearest(current));
    sizes.addActionListener(
        e -> {
          frame.getUiContext().setUnitScaleFactor(UNIT_SIZES.get(sizes.getSelectedIndex()));
          frame.getMapPanel().resetMap();
        });
    return new MapSetting(
        "Unit size", "How large the units show on this map; kept for the next games.", sizes);
  }

  /** The index of the unit size nearest to the factor, eg of a map's own default. */
  private static int nearest(final double factor) {
    int nearest = 0;
    for (int index = 1; index < UNIT_SIZES.size(); index++) {
      if (Math.abs(UNIT_SIZES.get(index) - factor) < Math.abs(UNIT_SIZES.get(nearest) - factor)) {
        nearest = index;
      }
    }
    return nearest;
  }

  /** Units hidden for a moment, to see the map below them; every game starts with them shown. */
  private static MapSetting newShowUnits(final TripleAFrame frame) {
    final JCheckBox show = new JCheckBox();
    show.setSelected(frame.getUiContext().getShowUnits());
    show.addActionListener(
        e -> {
          frame.getUiContext().setShowUnits(show.isSelected());
          frame.getMapPanel().resetMap();
        });
    return new MapSetting(
        "Show the units",
        "Hide them for a moment to see the map below; every game starts with them shown.",
        show);
  }

  /** The map's skin, kept for this map (a beta feature). */
  private static MapSetting newSkin(final TripleAFrame frame, final List<MapSkin> skins) {
    final JComboBox<String> names =
        new JComboBox<>(skins.stream().map(MapSkin::getSkinName).toArray(String[]::new));
    skins.stream()
        .filter(MapSkin::isCurrentSkin)
        .findFirst()
        .ifPresent(skin -> names.setSelectedItem(skin.getSkinName()));
    names.addActionListener(
        e -> {
          try {
            frame.changeMapSkin((String) names.getSelectedItem());
          } catch (final RuntimeException exception) {
            log.error("Could not change the map skin", exception);
          }
        });
    return new MapSetting("Map skin", "Another look of this map, if it has several.", names);
  }
}
