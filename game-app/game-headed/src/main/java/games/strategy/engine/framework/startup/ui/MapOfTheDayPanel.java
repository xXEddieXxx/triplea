package games.strategy.engine.framework.startup.ui;

import com.google.common.annotations.VisibleForTesting;
import games.strategy.engine.framework.I18nResourceBundle;
import games.strategy.engine.framework.lookandfeel.LookAndFeel;
import games.strategy.engine.framework.map.download.DownloadMapsWindow;
import games.strategy.engine.framework.map.file.system.loader.InstalledMap;
import games.strategy.engine.framework.map.file.system.loader.InstalledMapsListing;
import games.strategy.engine.framework.system.HttpProxy;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Image;
import java.awt.Insets;
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
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.triplea.http.client.lobby.maps.listing.MapDownloadItem;
import org.triplea.swing.JButtonBuilder;
import org.triplea.swing.SwingAction;
import org.triplea.swing.WrappingText;

/**
 * A map of all there are, installed or not, large with its picture, name and a few words: Play
 * starts a game on an installed one, Install fetches one that is not. A different map each day; the
 * arrows show the others. It does not change on its own, the player decides when to look at the
 * next one.
 */
@Slf4j
final class MapOfTheDayPanel extends JPanel {
  @Serial private static final long serialVersionUID = -4120391262013418712L;

  private static final String PREFIX = "startup.MapOfTheDay.";
  private static final Dimension PREVIEW_SIZE = new Dimension(560, 250);

  /** About one line below the name; the map browser has the whole description. */
  private static final int SUMMARY_LENGTH = 110;

  private static final int TIMEOUT_MILLIS = 10_000;
  private static final Pattern ROMAN_NUMERAL = Pattern.compile("[ivx]+");
  private static final String TUTORIAL_MAP = "tutorial";
  private static final String MAP = "map";
  private static final String MESSAGE = "message";
  private static final String LOADING = "loading";

  private final I18nResourceBundle bundle;
  private final CardLayout cards = new CardLayout();
  // on the left edge, as the heading and name are
  private final JLabel picture = new JLabel("", SwingConstants.LEADING);
  private final JLabel name = new JLabel();
  private final JLabel availability = new JLabel();
  private final WrappingText summary = new WrappingText();
  private final JLabel position = new JLabel();
  private final JLabel message = new JLabel();
  private final JButton actionButton;
  private transient List<FeaturedMap> maps = List.of();
  private int shown;
  // the newest reading of a picture; an older one that ends later is ignored
  private transient @Nullable SwingWorker<Optional<ImageIcon>, Void> pictureReader;

  /**
   * @param play starts a local game on the game XML
   */
  MapOfTheDayPanel(
      final I18nResourceBundle bundle,
      final Supplier<InstalledMapsListing> installedMaps,
      final Supplier<List<MapDownloadItem>> mapsToDownload,
      final Consumer<Path> play) {
    this.bundle = bundle;
    setLayout(cards);
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
    add(newMapView(), MAP);
    add(newMessageView(), MESSAGE);
    add(newLoadingView(), LOADING);
    getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
        .put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), "previous");
    getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
        .put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), "next");
    getActionMap().put("previous", SwingAction.of("previous", () -> showMap(shown - 1)));
    getActionMap().put("next", SwingAction.of("next", () -> showMap(shown + 1)));
    cards.show(this, LOADING);
    readMaps(installedMaps, mapsToDownload);
  }

  /**
   * From top to bottom: the heading with the quiet arrows to browse, the picture, the name and
   * whether the map is installed, a line about the map, and the one action, at the left edge as
   * everything else.
   */
  private JComponent newMapView() {
    picture.setPreferredSize(PREVIEW_SIZE);
    name.setFont(name.getFont().deriveFont(Font.BOLD, name.getFont().getSize2D() + 10));
    availability.setForeground(LookAndFeel.getSecondaryTextColor());
    summary.setForeground(LookAndFeel.getSecondaryTextColor());
    actionButton.setMargin(new Insets(6, 22, 6, 22));

    final JButton previous = newArrow("‹", "Previous", () -> showMap(shown - 1));
    final JButton next = newArrow("›", "Next", () -> showMap(shown + 1));
    position.setForeground(LookAndFeel.getSecondaryTextColor());
    final JPanel browse = new JPanel(new BorderLayout(2, 0));
    browse.setOpaque(false);
    browse.add(previous, BorderLayout.WEST);
    browse.add(position, BorderLayout.CENTER);
    browse.add(next, BorderLayout.EAST);
    final JPanel heading = new JPanel(new BorderLayout());
    heading.setOpaque(false);
    heading.add(LookAndFeel.newSectionLabel(bundle.getText(PREFIX + "Title")), BorderLayout.WEST);
    heading.add(browse, BorderLayout.EAST);

    final JPanel nameAndAvailability = new JPanel(new BorderLayout(0, 4));
    nameAndAvailability.setOpaque(false);
    nameAndAvailability.add(name, BorderLayout.NORTH);
    nameAndAvailability.add(availability, BorderLayout.CENTER);
    final JPanel action = new JPanel(new BorderLayout());
    action.setOpaque(false);
    action.add(actionButton, BorderLayout.WEST);
    final JPanel texts = new JPanel(new BorderLayout(0, 10));
    texts.setOpaque(false);
    texts.add(nameAndAvailability, BorderLayout.NORTH);
    texts.add(summary, BorderLayout.CENTER);
    texts.add(action, BorderLayout.SOUTH);

    final JPanel view = new JPanel(new BorderLayout(0, 14));
    view.setOpaque(false);
    view.add(heading, BorderLayout.NORTH);
    view.add(picture, BorderLayout.CENTER);
    view.add(texts, BorderLayout.SOUTH);
    return view;
  }

  /** While the maps are read: the heading and the shape of picture, name and line. */
  private JComponent newLoadingView() {
    final JPanel view = new JPanel(new BorderLayout(0, 14));
    view.setOpaque(false);
    view.add(LookAndFeel.newSectionLabel(bundle.getText(PREFIX + "Title")), BorderLayout.NORTH);
    view.add(
        LoadingPlaceholder.newPicture(PREVIEW_SIZE.width, PREVIEW_SIZE.height),
        BorderLayout.CENTER);
    return view;
  }

  private JButton newArrow(final String arrow, final String key, final Runnable action) {
    final JButton button = new JButtonBuilder(arrow).actionListener(action).build();
    LookAndFeel.setBorderlessButtonStyle(button);
    button.setFont(button.getFont().deriveFont(button.getFont().getSize2D() + 8));
    button.getAccessibleContext().setAccessibleName(bundle.getText(PREFIX + key));
    button.setToolTipText(bundle.getText(PREFIX + key));
    return button;
  }

  /** Without maps: a word on how to get some, and the way to them. */
  private JComponent newMessageView() {
    final JButton getMaps =
        new JButtonBuilder(bundle.getText(PREFIX + "GetMaps") + "  ›")
            .actionListener(DownloadMapsWindow::showDownloadMapsWindow)
            .build();
    LookAndFeel.setBorderlessButtonStyle(getMaps);
    getMaps.setHorizontalAlignment(SwingConstants.LEADING);
    message.setForeground(LookAndFeel.getSecondaryTextColor());
    final JPanel view = new JPanel(new BorderLayout(0, 8));
    view.setOpaque(false);
    view.add(LookAndFeel.newSectionLabel(bundle.getText(PREFIX + "Title")), BorderLayout.NORTH);
    view.add(message, BorderLayout.CENTER);
    view.add(getMaps, BorderLayout.SOUTH);
    return view;
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
          showMessage(bundle.getText(PREFIX + "None"));
        } else {
          cards.show(MapOfTheDayPanel.this, MAP);
          showMap(mapOfTheDay(maps.size(), LocalDate.now()));
        }
      }
    }.execute();
  }

  private void showMessage(final String text) {
    message.setText(text);
    cards.show(this, MESSAGE);
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
    summary.setText(map.summary());
    position.setText(bundle.getText(PREFIX + "Position", shown + 1, maps.size()));
    picture.setIcon(null);
    picture.setText("");
    readPicture(map);
  }

  private void readPicture(final FeaturedMap map) {
    pictureReader =
        new SwingWorker<>() {
          @Override
          protected Optional<ImageIcon> doInBackground() {
            return readScaled(map.preview());
          }

          @Override
          protected void done() {
            if (pictureReader != this) {
              // the player went on to another map meanwhile
              return;
            }
            try {
              get()
                  .ifPresentOrElse(
                      picture::setIcon,
                      () -> picture.setText(bundle.getText(PREFIX + "NoPicture")));
            } catch (final InterruptedException e) {
              Thread.currentThread().interrupt();
            } catch (final ExecutionException e) {
              log.info("Could not show the picture of {}", map.title(), e);
            }
          }
        };
    pictureReader.execute();
  }

  /** The picture, from the map folder or the internet, scaled to fit; empty if there is none. */
  private static Optional<ImageIcon> readScaled(final URI preview) {
    try {
      return read(preview).map(MapOfTheDayPanel::scaled);
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

  /** Fits the picture into the frame, keeping its proportions. */
  private static ImageIcon scaled(final BufferedImage image) {
    final double scale =
        Math.min(
            PREVIEW_SIZE.getWidth() / image.getWidth(),
            PREVIEW_SIZE.getHeight() / image.getHeight());
    return new ImageIcon(
        image.getScaledInstance(
            (int) (image.getWidth() * scale),
            (int) (image.getHeight() * scale),
            Image.SCALE_SMOOTH));
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
