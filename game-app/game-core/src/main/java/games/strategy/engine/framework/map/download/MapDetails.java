package games.strategy.engine.framework.map.download;

import games.strategy.engine.framework.lookandfeel.LookAndFeel;
import games.strategy.engine.framework.map.download.MapRow.Actions;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagLayout;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.io.Serial;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.UIManager;
import org.triplea.http.client.lobby.maps.listing.MapDownloadItem;
import org.triplea.swing.JEditorPaneWithClickableLinks;
import org.triplea.swing.JLabelBuilder;
import org.triplea.swing.SwingComponents;
import org.triplea.swing.jpanel.GridBagConstraintsAnchor;
import org.triplea.swing.jpanel.GridBagConstraintsBuilder;

/**
 * The column right of a map list with the selected map: a large preview, its name and creator, its
 * versions, size and status, its buttons and its full description.
 */
final class MapDetails extends JPanel {
  @Serial private static final long serialVersionUID = 6127433920184626413L;
  // the details are never narrower, so the facts and buttons fit; wider windows widen them
  private static final int MIN_WIDTH = 380;
  private static final int PREFERRED_WIDTH = 480;
  private static final int PADDING = 24;

  /** A line of the facts about a map, eg "Size" and "38 MB". */
  record Fact(String label, String value) {}

  private final transient MapPreviews previews;
  private final transient Actions actions;
  private final JLabel status = new JLabel();
  private transient @Nullable ManagedMap shownMap;
  private @Nullable MapPreviewBox preview;

  MapDetails(final MapPreviews previews, final Actions actions) {
    super(new BorderLayout(0, 16));
    this.previews = previews;
    this.actions = actions;
    setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 1, 0, 0, LookAndFeel.getSeparatorColor()),
            BorderFactory.createEmptyBorder(PADDING, PADDING, PADDING, PADDING)));
    // the preview fills the width of the details
    addComponentListener(
        new ComponentAdapter() {
          @Override
          public void componentResized(final ComponentEvent e) {
            fitPreview();
          }
        });
  }

  private void fitPreview() {
    final int width = getWidth() - getInsets().left - getInsets().right;
    if (preview != null && width > 0 && width != preview.getPreferredSize().width) {
      preview.resize(width);
    }
  }

  /** Shows the map, eg the one selected in the list, or its new status after a change. */
  void show(final ManagedMap map) {
    shownMap = map;
    removeAll();
    final MapDownloadItem download = map.getMapDownloadItem();
    final MapPreviewBox mapPreview = new MapPreviewBox(previews, download.getPreviewImageUrl());
    if (map.getMapStatus() == ManagedMapStatus.DOWNLOADING) {
      mapPreview.showProgress(0);
    }
    preview = mapPreview;
    fitPreview();
    status.setText(statusName(map.getMapStatus()));

    final JPanel top = new JPanel();
    top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
    top.setOpaque(false);
    addLeftAligned(top, mapPreview);
    top.add(Box.createVerticalStrut(16));
    addLeftAligned(top, new JLabelBuilder(map.getMapName()).bold().adjustFontSize(4).build());
    MapRow.creatorOf(download.getDescription())
        .ifPresent(
            creator ->
                addLeftAligned(
                    top,
                    new JLabelBuilder("by " + creator)
                        .foregroundColor(LookAndFeel.getSecondaryTextColor())
                        .build()));
    top.add(Box.createVerticalStrut(12));
    addLeftAligned(top, newFactsGrid(map));
    final JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
    buttons.setOpaque(false);
    MapRow.newButtons(map, actions).forEach(buttons::add);
    if (buttons.getComponentCount() > 0) {
      top.add(Box.createVerticalStrut(16));
      addLeftAligned(top, buttons);
    }
    add(top, BorderLayout.NORTH);
    if (!MapRow.isMissingDescription(download.getDescription())) {
      add(newDescription(download.getDescription()), BorderLayout.CENTER);
    }
    revalidate();
    repaint();
  }

  private static void addLeftAligned(final JPanel panel, final JComponent component) {
    component.setAlignmentX(Component.LEFT_ALIGNMENT);
    panel.add(component);
  }

  /** The facts as two columns, the label in the secondary color; the status follows downloads. */
  private JPanel newFactsGrid(final ManagedMap map) {
    final JPanel grid = new JPanel(new GridBagLayout());
    grid.setOpaque(false);
    final List<Fact> facts = facts(map);
    for (int row = 0; row < facts.size(); row++) {
      final Fact fact = facts.get(row);
      grid.add(
          new JLabelBuilder(fact.label())
              .foregroundColor(LookAndFeel.getSecondaryTextColor())
              .build(),
          new GridBagConstraintsBuilder(0, row)
              .anchor(GridBagConstraintsAnchor.WEST)
              .insets(2, 0, 2, 16)
              .build());
      final JLabel value = row == 0 ? status : new JLabel(fact.value());
      grid.add(
          value,
          new GridBagConstraintsBuilder(1, row)
              .weightX(1)
              .anchor(GridBagConstraintsAnchor.WEST)
              .insets(2, 0, 2, 0)
              .build());
    }
    return grid;
  }

  /**
   * The facts about the map, the status first, eg "Status: Update available", "Installed version: 3
   * Mar 2024", "Newest version: 9 Jun 2025" and "Size: 38 MB"; facts the map lacks are left out.
   */
  static List<Fact> facts(final ManagedMap map) {
    final List<Fact> facts = new ArrayList<>();
    facts.add(new Fact("Status", statusName(map.getMapStatus())));
    if (map.getMapStatus() != ManagedMapStatus.AVAILABLE) {
      // a removed map keeps the installed map that was read at the start
      MapRow.installedDate(map).ifPresent(date -> facts.add(new Fact("Installed version", date)));
    }
    MapRow.newestDate(map.getMapDownloadItem())
        .ifPresent(date -> facts.add(new Fact("Newest version", date)));
    MapRow.sizeText(map.getMapDownloadItem()).ifPresent(size -> facts.add(new Fact("Size", size)));
    return facts;
  }

  private static String statusName(final ManagedMapStatus mapStatus) {
    return switch (mapStatus) {
      case AVAILABLE -> "Not installed";
      case DOWNLOADING -> "Downloading...";
      case INSTALLED -> "Installed, up to date";
      case UPDATE_AVAILABLE -> "Update available";
      case REMOVING -> "Removing...";
      case ERROR -> "Download failed";
      default -> throw new IllegalStateException("Unknown map status: " + mapStatus);
    };
  }

  /**
   * The full description, which is HTML, in the normal text size, scrolling if it is long; its
   * links open the browser.
   */
  private static JScrollPane newDescription(final String html) {
    final JEditorPane description = new JEditorPaneWithClickableLinks(html);
    // the text of the app instead of the small default font of HTML
    description.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
    description.setFont(UIManager.getFont("Label.font"));
    description.setBorder(BorderFactory.createEmptyBorder());
    description.setCaretPosition(0);
    final JScrollPane scroll = SwingComponents.newJScrollPane(description);
    scroll.setOpaque(false);
    scroll.getViewport().setOpaque(false);
    scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    return scroll;
  }

  /** Shows the progress of the shown map's download on its preview and in its status. */
  void showProgress(final MapDownloadItem download, final long bytesReceived) {
    if (shownMap == null
        || preview == null
        || !shownMap.getMapName().equals(download.getMapName())) {
      return;
    }
    status.setText(MapRow.progressText(download, bytesReceived));
    preview.showProgress(MapRow.percentOf(download, bytesReceived));
  }

  @Override
  public Dimension getPreferredSize() {
    return new Dimension(PREFERRED_WIDTH, super.getPreferredSize().height);
  }

  @Override
  public Dimension getMinimumSize() {
    return new Dimension(MIN_WIDTH, super.getMinimumSize().height);
  }
}
