package games.strategy.engine.framework.ui;

import games.strategy.engine.framework.lookandfeel.LookAndFeel;
import games.strategy.engine.framework.map.download.MapBrowserView;
import games.strategy.engine.framework.map.download.MapPreviewBox;
import games.strategy.engine.framework.map.download.MapPreviews;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.io.Serial;
import java.util.HashMap;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListCellRenderer;
import org.triplea.swing.JLabelBuilder;

/**
 * A game of Change map in the view the player chose, as the maps window shows maps: a line with the
 * game and its map, a row with the map's picture, or a tile with a large picture on top. The
 * pictures are the installed maps' own; they load in the background, and the list repaints when one
 * arrives.
 */
final class GameCellRenderer implements ListCellRenderer<DefaultGameChooserEntry> {
  static final int PADDING = 8;

  private final MapBrowserView view;
  private final MapPreviews previews;
  private final Runnable repaintList;
  // the picture of each map, as a URL for the previews; "" for a map without one
  private final Map<String, String> previewUrls = new HashMap<>();
  private final DefaultListCellRenderer line = new DefaultListCellRenderer();
  private final JPanel cell;
  private final Picture picture;
  private final JLabel gameName = new JLabelBuilder().bold().build();
  private final JLabel mapName = new JLabel();

  /**
   * @param previews For the size of the view: rows for the list with pictures, tiles for tiles.
   */
  GameCellRenderer(
      final MapBrowserView view, final MapPreviews previews, final Runnable repaintList) {
    this.view = view;
    this.previews = previews;
    this.repaintList = repaintList;
    picture = new Picture();
    final JPanel texts = new JPanel(new GridLayout(0, 1, 0, 2));
    texts.setOpaque(false);
    texts.add(gameName);
    texts.add(mapName);
    cell =
        view == MapBrowserView.TILES
            ? new JPanel(new BorderLayout(0, 6))
            : new JPanel(new BorderLayout(14, 0));
    cell.setBorder(BorderFactory.createEmptyBorder(PADDING, PADDING, PADDING, PADDING));
    cell.add(picture, view == MapBrowserView.TILES ? BorderLayout.NORTH : BorderLayout.WEST);
    cell.add(texts, BorderLayout.CENTER);
  }

  @Override
  public Component getListCellRendererComponent(
      final JList<? extends DefaultGameChooserEntry> list,
      final DefaultGameChooserEntry game,
      final int index,
      final boolean isSelected,
      final boolean cellHasFocus) {
    final String map = game.getInstalledMap().getMapName();
    if (view == MapBrowserView.LIST) {
      // the game with its map, which tells apart games of the same name on different maps
      return line.getListCellRendererComponent(
          list, game.getGameName() + "  ·  " + map, index, isSelected, cellHasFocus);
    }
    // once per map: painting must not look at the disk each time
    picture.showPicture(previewUrls.computeIfAbsent(map, name -> game.findPreviewUrl()));
    gameName.setText(game.getGameName());
    mapName.setText(map);
    cell.setBackground(isSelected ? list.getSelectionBackground() : list.getBackground());
    gameName.setForeground(isSelected ? list.getSelectionForeground() : list.getForeground());
    mapName.setForeground(
        isSelected ? list.getSelectionForeground() : LookAndFeel.getSecondaryTextColor());
    return cell;
  }

  /**
   * The map's picture in the size of the view's previews; one for all cells, as a renderer paints
   * each cell with the same components, so it shows the picture of the cell painted next.
   */
  private final class Picture extends JComponent {
    @Serial private static final long serialVersionUID = 1L;
    private String url = "";

    Picture() {
      setPreferredSize(
          new Dimension(previews.getSize().getWidth(), previews.getSize().getHeight()));
    }

    /** The picture of the next cell to paint, as a URL for the previews; "" for none. */
    void showPicture(final String pictureUrl) {
      url = pictureUrl;
    }

    @Override
    protected void paintComponent(final Graphics g) {
      MapPreviewBox.paintPreview(
          (Graphics2D) g, previews, url, new Rectangle(0, 0, getWidth(), getHeight()), repaintList);
    }
  }
}
