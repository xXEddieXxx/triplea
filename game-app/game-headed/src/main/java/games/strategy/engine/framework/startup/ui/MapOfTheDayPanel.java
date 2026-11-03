package games.strategy.engine.framework.startup.ui;

import com.google.common.annotations.VisibleForTesting;
import games.strategy.engine.framework.I18nResourceBundle;
import games.strategy.engine.framework.lookandfeel.LookAndFeel;
import games.strategy.engine.framework.map.download.DownloadMapsWindow;
import games.strategy.engine.framework.map.file.system.loader.InstalledMap;
import games.strategy.engine.framework.map.file.system.loader.InstalledMapsListing;
import games.strategy.engine.framework.startup.ui.LineIcon.Kind;
import games.strategy.engine.framework.system.HttpProxy;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Insets;
import java.awt.LinearGradientPaint;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serial;
import java.net.URI;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.triplea.http.client.lobby.maps.listing.MapDownloadItem;
import org.triplea.swing.JButtonBuilder;
import org.triplea.swing.SwingAction;

/**
 * The map of the day as the stage of the main menu: its picture fills the area to the window's top,
 * right and bottom edge, dimmed and shaded, so that the texts on it stay readable and its left edge
 * fades into the menu. At the top left the area's name, at the top right the arrows to browse the
 * maps; at the bottom left the map's name, whether it is installed with a line about it, and the
 * one action: Play starts a game on an installed map, Install fetches one that is not. A different
 * map each day, of all there are, installed or not; it does not change on its own, the player
 * browses.
 */
@Slf4j
final class MapOfTheDayPanel extends JPanel {
  @Serial private static final long serialVersionUID = -4120391262013418712L;

  private static final String PREFIX = "startup.MapOfTheDay.";

  /** Narrower, the name and the arrows have no room: the main menu then leaves the stage out. */
  static final int LEAST_WIDTH = 320;

  /**
   * Pictures with a longer side are scaled down once when read; the width of the stage in the
   * largest windows.
   */
  private static final int PICTURE_MOST_SIDE = 2560;

  /**
   * A small picture is enlarged at most this much; the room around it shows a blurred copy, so it
   * never looks coarse.
   */
  private static final double MOST_ENLARGEMENT = 2.0;

  /** The blurred ground is the picture shrunk by this, then drawn large. */
  private static final int BLUR_STEP = 16;

  /** The share of the free height above the picture's middle: maps keep their north in view. */
  private static final double PICTURE_ANCHOR = 0.42;

  /** About one line below the name; the map browser has the whole description. */
  private static final int SUMMARY_LENGTH = 110;

  /** The info on the picture never runs wider than a comfortable line. */
  private static final int INFO_MOST_WIDTH = 900;

  private static final int TIMEOUT_MILLIS = 10_000;
  private static final Pattern ROMAN_NUMERAL = Pattern.compile("[ivx]+");
  private static final String TUTORIAL_MAP = "tutorial";
  private static final String MAP = "map";
  private static final String MESSAGE = "message";
  private static final String LOADING = "loading";

  private final I18nResourceBundle bundle;
  private final JPanel head = new JPanel(new BorderLayout(MenuSpacing.M, 0));
  private final CardLayout infoCards = new CardLayout();
  private final JPanel info = new JPanel(infoCards);
  private final JLabel name = new JLabel();
  private final JLabel availability = new JLabel();
  private final JLabel summary = new JLabel();
  private final JLabel position = new JLabel();
  private final JLabel message = new JLabel();
  private final JPanel browse;
  private final JButton actionButton;
  private transient List<FeaturedMap> maps = List.of();
  private int shown;
  private int gutter = MenuSpacing.L;
  private int bandHeight;
  private transient @Nullable StagePicture picture;
  // the newest reading of a picture; an older one that ends later is ignored
  private transient @Nullable SwingWorker<Optional<StagePicture>, Void> pictureReader;

  /**
   * The map's picture and a tiny copy of it, which, drawn large, is the blurred ground around a
   * picture too small to fill the stage.
   */
  @VisibleForTesting
  record StagePicture(BufferedImage image, BufferedImage blurred) {
    static StagePicture of(final BufferedImage image) {
      return new StagePicture(
          image,
          smoothlyScaled(
              image,
              Math.max(1, image.getWidth() / BLUR_STEP),
              Math.max(1, image.getHeight() / BLUR_STEP)));
    }
  }

  /**
   * @param play starts a local game on the game XML
   */
  MapOfTheDayPanel(
      final I18nResourceBundle bundle,
      final Supplier<InstalledMapsListing> installedMaps,
      final Supplier<List<MapDownloadItem>> mapsToDownload,
      final Consumer<Path> play) {
    super(null);
    this.bundle = bundle;
    browse = new BrowseChip();
    setOpaque(false);
    actionButton =
        new JButtonBuilder("")
            .actionListener(
                () -> {
                  final FeaturedMap map = maps.get(shown);
                  map.gameXml()
                      .ifPresentOrElse(
                          play,
                          () ->
                              DownloadMapsWindow.showDownloadMapsWindowAndDownload(map.mapName()));
                })
            .build();
    LookAndFeel.setPrimaryButtonStyle(actionButton);
    actionButton.setMargin(new Insets(MenuSpacing.S, 22, MenuSpacing.S, 22));

    head.setOpaque(false);
    head.add(
        SectionHeading.newLabel(
            bundle.getText(PREFIX + "Title"), Kind.MAP, UIManager.getColor("Label.foreground")),
        BorderLayout.WEST);
    browse.setVisible(false);
    head.add(browse, BorderLayout.EAST);
    info.setOpaque(false);
    info.add(newMapInfo(), MAP);
    info.add(newMessageInfo(), MESSAGE);
    info.add(LoadingPlaceholder.newLines(2, 320), LOADING);
    add(head);
    add(info);
    applyGutter(MenuSpacing.L);

    getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
        .put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), "previous");
    getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
        .put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), "next");
    getActionMap().put("previous", SwingAction.of("previous", () -> showMap(shown - 1)));
    getActionMap().put("next", SwingAction.of("next", () -> showMap(shown + 1)));
    infoCards.show(info, LOADING);
    readMaps(installedMaps, mapsToDownload);
  }

  /**
   * The map's name, below it whether it is installed and a line about it, then the one action. The
   * name grows with the window, see {@link #setGutter}.
   */
  private JComponent newMapInfo() {
    // a long name or line ends in "…" instead of running off the picture
    name.setMinimumSize(new Dimension(0, 0));
    // the name comes from the map listing, so it is shown as text, never as HTML
    name.putClientProperty("html.disable", Boolean.TRUE);
    availability.setFont(availability.getFont().deriveFont(Font.BOLD));
    summary.setForeground(LookAndFeel.getSecondaryTextColor());
    summary.setMinimumSize(new Dimension(0, 0));
    final JPanel meta = new JPanel(new BorderLayout(MenuSpacing.S, 0));
    meta.setOpaque(false);
    meta.add(availability, BorderLayout.WEST);
    meta.add(summary, BorderLayout.CENTER);
    final JPanel action = new JPanel(new BorderLayout());
    action.setOpaque(false);
    action.add(actionButton, BorderLayout.WEST);
    action.setBorder(BorderFactory.createEmptyBorder(MenuSpacing.M, 0, 0, 0));

    final JPanel view = new JPanel(new BorderLayout(0, MenuSpacing.S));
    view.setOpaque(false);
    view.add(name, BorderLayout.NORTH);
    view.add(meta, BorderLayout.CENTER);
    view.add(action, BorderLayout.SOUTH);
    return view;
  }

  /** Without maps: a word on how to get some, and the way to them. */
  private JComponent newMessageInfo() {
    final JButton getMaps =
        new JButtonBuilder(bundle.getText(PREFIX + "GetMaps") + "  ›")
            .actionListener(DownloadMapsWindow::showDownloadMapsWindow)
            .build();
    LookAndFeel.setPrimaryButtonStyle(getMaps);
    final JPanel action = new JPanel(new BorderLayout());
    action.setOpaque(false);
    action.add(getMaps, BorderLayout.WEST);
    final JPanel view = new JPanel(new BorderLayout(0, MenuSpacing.M));
    view.setOpaque(false);
    view.add(message, BorderLayout.CENTER);
    view.add(action, BorderLayout.SOUTH);
    return view;
  }

  /**
   * The gutter of the main menu: the room around the texts on the picture. The map's name grows
   * with it, from 26 px in small windows to 36 px in large ones.
   */
  void setGutter(final int newGutter) {
    if (gutter != newGutter) {
      applyGutter(newGutter);
      revalidate();
    }
  }

  private void applyGutter(final int newGutter) {
    gutter = newGutter;
    final Font label = UIManager.getFont("Label.font");
    final float base = label == null ? 13f : label.getSize2D();
    name.setFont(
        name.getFont().deriveFont(Font.BOLD, base + 13 + (newGutter - MenuSpacing.L) / 4f));
  }

  /**
   * The height at the bottom where the news band lies on the stage: the map's info stands above it,
   * and below its top edge the picture turns into a dark floor.
   */
  void setBandHeight(final int height) {
    if (bandHeight != height) {
      bandHeight = height;
      revalidate();
      repaint();
    }
  }

  /** The area's name and the arrows at the top, the map's info at the bottom left. */
  @Override
  public void doLayout() {
    final Dimension headSize = head.getPreferredSize();
    head.setBounds(gutter, gutter * 3 / 5, getWidth() - 2 * gutter, headSize.height);
    final int infoWidth = Math.min(getWidth() - 2 * gutter, INFO_MOST_WIDTH);
    final int infoHeight = info.getPreferredSize().height;
    info.setBounds(gutter, floor() - gutter * 4 / 5 - infoHeight, infoWidth, infoHeight);
  }

  /** Where the floor of the news band begins. */
  private int floor() {
    return getHeight() - bandHeight;
  }

  @Override
  public Dimension getPreferredSize() {
    return new Dimension(
        LEAST_WIDTH, head.getPreferredSize().height + info.getPreferredSize().height);
  }

  /** Reads the maps in the background: the maps folder and the server must not freeze the menu. */
  private void readMaps(
      final Supplier<InstalledMapsListing> installedMaps,
      final Supplier<List<MapDownloadItem>> mapsToDownload) {
    new SwingWorker<List<FeaturedMap>, Void>() {
      @Override
      protected List<FeaturedMap> doInBackground() {
        return featuredMaps(installedMaps.get(), mapsToDownload.get());
      }

      @Override
      protected void done() {
        try {
          maps = get();
        } catch (final InterruptedException e) {
          Thread.currentThread().interrupt();
        } catch (final ExecutionException e) {
          log.warn("Could not read the maps", e);
        }
        if (maps.isEmpty()) {
          message.setText(bundle.getText(PREFIX + "None"));
          infoCards.show(info, MESSAGE);
        } else {
          infoCards.show(info, MAP);
          browse.setVisible(true);
          showMap(mapOfTheDay(maps.size(), LocalDate.now()));
        }
      }
    }.execute();
  }

  /** Shows the map at the index, going round at both ends. */
  private void showMap(final int index) {
    if (maps.isEmpty()) {
      return;
    }
    shown = Math.floorMod(index, maps.size());
    final FeaturedMap map = maps.get(shown);
    name.setText(map.title());
    final boolean installed = map.gameXml().isPresent();
    actionButton.setText(bundle.getText(PREFIX + (installed ? "Play" : "Install")) + "  ›");
    availability.setText(
        installed
            ? bundle.getText(PREFIX + "Installed")
            : bundle.getText(
                PREFIX + "NotInstalled", FileUtils.byteCountToDisplaySize(map.downloadSize())));
    summary.setText(map.summary().isEmpty() ? "" : "·  " + map.summary());
    position.setText(bundle.getText(PREFIX + "Position", shown + 1, maps.size()));
    showPicture(null);
    readPicture(map);
  }

  private void showPicture(final @Nullable StagePicture newPicture) {
    picture = newPicture;
    repaint();
  }

  private void readPicture(final FeaturedMap map) {
    pictureReader =
        new SwingWorker<>() {
          @Override
          protected Optional<StagePicture> doInBackground() {
            return readLimited(map.preview()).map(StagePicture::of);
          }

          @Override
          protected void done() {
            if (pictureReader != this) {
              // the player went on to another map meanwhile
              return;
            }
            try {
              get().ifPresent(MapOfTheDayPanel.this::showPicture);
            } catch (final InterruptedException e) {
              Thread.currentThread().interrupt();
            } catch (final ExecutionException e) {
              log.info("Could not show the picture of {}", map.title(), e);
            }
          }
        };
    pictureReader.execute();
  }

  /**
   * The picture, from the map folder or the internet, no side longer than {@link
   * #PICTURE_MOST_SIDE}; empty if there is none.
   */
  private static Optional<BufferedImage> readLimited(final URI preview) {
    try {
      return read(preview).map(MapOfTheDayPanel::limited);
    } catch (final IOException e) {
      log.info("Could not read the map picture {}: {}", preview, e.getMessage());
      return Optional.empty();
    }
  }

  private static Optional<BufferedImage> read(final URI preview) throws IOException {
    if ("file".equals(preview.getScheme())) {
      final Path file = Path.of(preview);
      return Files.isRegularFile(file)
          ? Optional.ofNullable(ImageIO.read(file.toFile()))
          : Optional.empty();
    }
    // through the proxy of the settings, as the map downloads
    final URLConnection connection = preview.toURL().openConnection(HttpProxy.getProxy());
    connection.setConnectTimeout(TIMEOUT_MILLIS);
    connection.setReadTimeout(TIMEOUT_MILLIS);
    try (InputStream stream = connection.getInputStream()) {
      return Optional.ofNullable(ImageIO.read(stream));
    }
  }

  /**
   * Scales a large picture down so that neither side is longer than {@link #PICTURE_MOST_SIDE},
   * keeping its proportions.
   */
  @VisibleForTesting
  static BufferedImage limited(final BufferedImage image) {
    final double scale = (double) PICTURE_MOST_SIDE / Math.max(image.getWidth(), image.getHeight());
    if (scale >= 1) {
      return image;
    }
    return smoothlyScaled(
        image,
        Math.max(1, (int) Math.round(image.getWidth() * scale)),
        Math.max(1, (int) Math.round(image.getHeight() * scale)));
  }

  private static BufferedImage smoothlyScaled(
      final BufferedImage image, final int width, final int height) {
    final BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    final Graphics2D g2 = scaled.createGraphics();
    // the icon waits until the smoothly scaled picture is complete
    new ImageIcon(image.getScaledInstance(width, height, Image.SCALE_SMOOTH))
        .paintIcon(null, g2, 0, 0);
    g2.dispose();
    return scaled;
  }

  /** The picture with its shades; without a picture only the shades on the window's color. */
  @Override
  protected void paintComponent(final Graphics g) {
    final Graphics2D g2 = (Graphics2D) g.create();
    paintStage(
        g2, picture, new Dimension(getWidth(), getHeight()), floor(), MenuColors.windowColor());
    g2.dispose();
  }

  /**
   * Paints the stage in the size: the picture scaled until no edge stays empty, at most {@link
   * #MOST_ENLARGEMENT} times, a small one on its blurred copy; then dimmed, and shaded in the
   * window's color at the left edge into the menu, toward the floor for the texts, and at the top
   * for the area's name. Below the floor's edge the news band lies on an almost plain ground.
   *
   * @param picture the picture; without one only the shades on a darker window color
   * @param floor the height where the floor of the news band begins; the size's height for none
   */
  @VisibleForTesting
  static void paintStage(
      final Graphics2D g2,
      final @Nullable StagePicture picture,
      final Dimension size,
      final int floor,
      final Color shade) {
    final int width = size.width;
    final int height = size.height;
    g2.setRenderingHint(
        RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
    g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    g2.setColor(MenuColors.mix(shade, Color.BLACK, 0.3));
    g2.fillRect(0, 0, width, height);
    if (picture != null) {
      final BufferedImage image = picture.image();
      final double scale = pictureScale(image.getWidth(), image.getHeight(), width, height);
      if (scale < coverScale(image.getWidth(), image.getHeight(), width, height)) {
        // the blurred copy fills the room the small picture leaves
        final BufferedImage blurred = picture.blurred();
        drawCentered(
            g2,
            blurred,
            coverScale(blurred.getWidth(), blurred.getHeight(), width, height),
            width,
            height);
        g2.setColor(MenuColors.withAlpha(shade, 128));
        g2.fillRect(0, 0, width, height);
      }
      drawCentered(g2, image, scale, width, height);
    }
    g2.setColor(MenuColors.withAlpha(shade, 87));
    g2.fillRect(0, 0, width, height);
    final int leftWidth = Math.max(1, (int) (width * 0.36));
    g2.setPaint(
        new LinearGradientPaint(
            0,
            0,
            leftWidth,
            0,
            new float[] {0f, 0.19f, 0.61f, 1f},
            shades(shade, 255, 219, 89, 0)));
    g2.fillRect(0, 0, leftWidth, height);
    final int bottomHeight = Math.max(1, (int) (floor * 0.62));
    g2.setPaint(
        new LinearGradientPaint(
            0,
            floor,
            0,
            floor - bottomHeight,
            new float[] {0f, 0.32f, 0.71f, 1f},
            shades(shade, 240, 224, 102, 0)));
    g2.fillRect(0, floor - bottomHeight, width, bottomHeight);
    g2.setColor(MenuColors.withAlpha(shade, 240));
    g2.fillRect(0, floor, width, height - floor);
    final int topHeight = Math.max(1, (int) (height * 0.15));
    g2.setPaint(
        new LinearGradientPaint(0, 0, 0, topHeight, new float[] {0f, 1f}, shades(shade, 209, 0)));
    g2.fillRect(0, 0, width, topHeight);
  }

  /** The color at each of the alphas, 0 to 255, for the stops of a shade. */
  private static Color[] shades(final Color color, final int... alphas) {
    return Arrays.stream(alphas)
        .mapToObj(alpha -> MenuColors.withAlpha(color, alpha))
        .toArray(Color[]::new);
  }

  /**
   * The scale the picture is drawn at: covering the size, but enlarged at most {@link
   * #MOST_ENLARGEMENT} times.
   */
  @VisibleForTesting
  static double pictureScale(
      final int imageWidth, final int imageHeight, final int width, final int height) {
    return Math.min(coverScale(imageWidth, imageHeight, width, height), MOST_ENLARGEMENT);
  }

  /** The scale at which the picture covers the size, the larger of width and height ratio. */
  private static double coverScale(
      final int imageWidth, final int imageHeight, final int width, final int height) {
    return Math.max((double) width / imageWidth, (double) height / imageHeight);
  }

  private static void drawCentered(
      final Graphics2D g2,
      final BufferedImage image,
      final double scale,
      final int width,
      final int height) {
    final int scaledWidth = (int) Math.ceil(image.getWidth() * scale);
    final int scaledHeight = (int) Math.ceil(image.getHeight() * scale);
    g2.drawImage(
        image,
        (width - scaledWidth) / 2,
        (int) ((height - scaledHeight) * PICTURE_ANCHOR),
        scaledWidth,
        scaledHeight,
        null);
  }

  /**
   * The arrows to browse the maps and the position between them, on a dark, slightly see-through
   * chip that stays readable on any picture.
   */
  private final class BrowseChip extends JPanel {
    @Serial private static final long serialVersionUID = 7281940235517294025L;

    private static final int ARC = 16;

    BrowseChip() {
      setOpaque(false);
      setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
      setBorder(
          BorderFactory.createEmptyBorder(
              MenuSpacing.XS, MenuSpacing.XS, MenuSpacing.XS, MenuSpacing.XS));
      position.setBorder(BorderFactory.createEmptyBorder(0, MenuSpacing.S, 0, MenuSpacing.S));
      position.setFont(position.getFont().deriveFont(position.getFont().getSize2D() - 1));
      add(newArrow("‹", "Previous", () -> showMap(shown - 1)));
      add(position);
      add(newArrow("›", "Next", () -> showMap(shown + 1)));
    }

    private JButton newArrow(final String arrow, final String key, final Runnable action) {
      final JButton button = new JButtonBuilder(arrow).actionListener(action).build();
      LookAndFeel.setBorderlessButtonStyle(button);
      button.setFont(button.getFont().deriveFont(Font.BOLD, button.getFont().getSize2D() + 4));
      button.setMargin(new Insets(0, MenuSpacing.S, 2, MenuSpacing.S));
      button.getAccessibleContext().setAccessibleName(bundle.getText(PREFIX + key));
      button.setToolTipText(bundle.getText(PREFIX + key));
      return button;
    }

    @Override
    protected void paintComponent(final Graphics g) {
      final Graphics2D g2 = (Graphics2D) g.create();
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2.setColor(MenuColors.withAlpha(MenuColors.windowColor(), 199));
      g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, ARC, ARC);
      final Color text = UIManager.getColor("Label.foreground");
      g2.setColor(MenuColors.withAlpha(text == null ? Color.GRAY : text, 26));
      g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, ARC, ARC);
      g2.dispose();
    }
  }

  /**
   * A map to show: its name, a few words about it and its picture; the game XML to play for an
   * installed map, the size of the download for another.
   */
  @Getter
  @Accessors(fluent = true)
  static final class FeaturedMap {
    private final String mapName;
    private final String title;
    private final String summary;
    private final URI preview;

    @Getter(AccessLevel.NONE)
    private final @Nullable Path gameXml;

    private final long downloadSize;

    private FeaturedMap(
        final String mapName,
        final String summary,
        final URI preview,
        final @Nullable Path gameXml,
        final long downloadSize) {
      this.mapName = mapName;
      this.title = readableMapName(mapName);
      this.summary = summary;
      this.preview = preview;
      this.gameXml = gameXml;
      this.downloadSize = downloadSize;
    }

    static FeaturedMap newInstalled(
        final String mapName, final String summary, final Path preview, final Path gameXml) {
      return new FeaturedMap(mapName, summary, preview.toUri(), gameXml, 0);
    }

    /** A map to download; empty if the server names no valid picture for it. */
    static Optional<FeaturedMap> newToDownload(final MapDownloadItem download) {
      try {
        return Optional.of(
            new FeaturedMap(
                download.getMapName(),
                summaryOf(download.getDescription()),
                URI.create(download.getPreviewImageUrl()),
                null,
                download.getDownloadSizeInBytes()));
      } catch (final IllegalArgumentException e) {
        return Optional.empty();
      }
    }

    /** The game to play; empty for a map that is not installed. */
    Optional<Path> gameXml() {
      return Optional.ofNullable(gameXml);
    }
  }

  /**
   * The installed maps with a game to play and the maps to download that are not installed, sorted
   * by name; never the tutorial, a lesson rather than a map to show off.
   */
  @VisibleForTesting
  static List<FeaturedMap> featuredMaps(
      final InstalledMapsListing listing, final List<MapDownloadItem> mapsToDownload) {
    final List<FeaturedMap> installed =
        listing.getInstalledMaps().stream()
            .filter(map -> !isTutorial(map.getMapName()))
            .map(map -> toFeaturedMap(listing, map))
            .flatMap(Optional::stream)
            .toList();
    final Set<String> installedNames =
        listing.getInstalledMaps().stream()
            .map(map -> InstalledMapsListing.normalizeName(map.getMapName()))
            .collect(Collectors.toSet());
    final Stream<FeaturedMap> toDownload =
        mapsToDownload.stream()
            .filter(map -> !isTutorial(map.getMapName()))
            .filter(
                map ->
                    !installedNames.contains(InstalledMapsListing.normalizeName(map.getMapName())))
            .map(FeaturedMap::newToDownload)
            .flatMap(Optional::stream);
    return Stream.concat(installed.stream(), toDownload)
        .sorted(Comparator.comparing(FeaturedMap::title, String.CASE_INSENSITIVE_ORDER))
        .toList();
  }

  private static boolean isTutorial(final String mapName) {
    return TUTORIAL_MAP.equalsIgnoreCase(mapName);
  }

  private static Optional<FeaturedMap> toFeaturedMap(
      final InstalledMapsListing listing, final InstalledMap map) {
    final Optional<Path> gameXml =
        map.getGameNames().stream().findFirst().flatMap(map::getGameXmlFilePath);
    final Optional<Path> folder = listing.findMapFolderByName(map.getMapName());
    if (gameXml.isEmpty() || folder.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(
        FeaturedMap.newInstalled(
            map.getMapName(),
            readSummary(folder.get().resolve("description.html")),
            folder.get().resolve("preview.png"),
            gameXml.get()));
  }

  private static String readSummary(final Path descriptionFile) {
    try {
      return Files.isRegularFile(descriptionFile)
          ? summaryOf(Files.readString(descriptionFile, StandardCharsets.UTF_8))
          : "";
    } catch (final IOException e) {
      log.info("Could not read the map description {}", descriptionFile, e);
      return "";
    }
  }

  /** "world_war_ii_global" reads "World War II Global". */
  @VisibleForTesting
  static String readableMapName(final String mapName) {
    return Pattern.compile("[_\\-\\s]+")
        .splitAsStream(mapName.strip())
        .filter(word -> !word.isEmpty())
        .map(
            word ->
                ROMAN_NUMERAL.matcher(word.toLowerCase(Locale.ROOT)).matches()
                    ? word.toUpperCase(Locale.ROOT)
                    : Character.toUpperCase(word.charAt(0)) + word.substring(1))
        .collect(Collectors.joining(" "));
  }

  /** The first words of the map's description, as plain text, ending at a word. */
  @VisibleForTesting
  static String summaryOf(final String descriptionHtml) {
    final String text =
        StringEscapeUtils.unescapeHtml4(descriptionHtml.replaceAll("<[^>]*>", " "))
            .replaceAll("\\s+", " ")
            .strip();
    if (text.length() <= SUMMARY_LENGTH) {
      return text;
    }
    final int end = text.lastIndexOf(' ', SUMMARY_LENGTH);
    return text.substring(0, end > 0 ? end : SUMMARY_LENGTH) + " …";
  }

  /** The map of the day: a different one each day, the same one all day. */
  @VisibleForTesting
  static int mapOfTheDay(final int mapCount, final LocalDate day) {
    return Math.floorMod(day.toEpochDay(), mapCount);
  }
}
