package games.strategy.engine.framework.startup.ui;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.io.ByteStreams;
import games.strategy.engine.framework.lookandfeel.LookAndFeel;
import games.strategy.engine.framework.system.HttpProxy;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serial;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.swing.BorderFactory;
import javax.swing.JEditorPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.Element;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.Position;
import javax.swing.text.StyleConstants;
import javax.swing.text.View;
import javax.swing.text.ViewFactory;
import javax.swing.text.html.CSS;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;
import lombok.extern.slf4j.Slf4j;
import org.triplea.awt.OpenFileUtility;

/**
 * A forum post as the forum formats it: paragraphs and headings, bold, italic and struck text,
 * links, quotes, lists, tables, code and code blocks, and pictures, each at most as wide as the
 * post and an emoji as high as the text. A link opens in the browser. It shows only what a post may
 * hold: scripts, stylesheets, forms and embedded objects are dropped, a style names no picture, and
 * pictures come from the web only.
 */
@Slf4j
final class ForumPost extends JEditorPane {
  @Serial private static final long serialVersionUID = -2715032964874430562L;

  /** A picture is read with at most this many pixels on its longer side, a larger one scaled. */
  private static final int MAX_PICTURE_SIDE = 2048;

  private static final int MAX_PICTURE_BYTES = 10 * 1024 * 1024;
  private static final int TIMEOUT_MILLIS = 15_000;

  /**
   * The pictures read lately, by address, up to about 64 MB of pixels: a game shown again shows
   * them at once.
   */
  private static final Cache<URI, BufferedImage> PICTURES =
      CacheBuilder.newBuilder()
          .maximumWeight(16_000_000)
          .<URI, BufferedImage>weigher(
              (address, picture) -> picture.getWidth() * picture.getHeight())
          .softValues()
          .build();

  /**
   * Reads the pictures, a few at a time. shortcut: each picture shown keeps its pixels, so a post
   * with very many huge pictures still takes much memory; limit the pictures of a post if posts do.
   */
  private static final ExecutorService READER =
      Executors.newFixedThreadPool(4, Thread.ofVirtual().factory());

  /** The document property with the forum's host, set before a post's HTML can change its base. */
  private static final String FORUM_HOST = "ForumPost.forumHost";

  /** Elements a post may not hold, with what is in them. */
  private static final Pattern UNSAFE_ELEMENTS =
      Pattern.compile(
          "<(script|style|iframe|object|embed|applet|noscript|template|title|textarea|select)\\b"
              + ".*?</\\1\\s*>",
          Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

  /** Tags a post may not hold, eg a form or a stylesheet's link, also when left open. */
  private static final Pattern UNSAFE_TAGS =
      Pattern.compile(
          "</?(script|style|iframe|object|embed|applet|noscript|template|title|link|meta|base|form"
              + "|input|isindex|button|select|option|textarea|frame|frameset|param)\\b[^>]*>",
          Pattern.CASE_INSENSITIVE);

  private static final Pattern CHECKED_BOX =
      Pattern.compile("<input\\b[^>]*\\bchecked\\b[^>]*>", Pattern.CASE_INSENSITIVE);
  private static final Pattern CHECK_BOX =
      Pattern.compile("<input\\b[^>]*\\bcheckbox\\b[^>]*>", Pattern.CASE_INSENSITIVE);
  private static final Pattern STRUCK =
      Pattern.compile("<(/?)(?:del|strike)\\b[^>]*>", Pattern.CASE_INSENSITIVE);
  private static final Pattern CODE_BLOCK =
      Pattern.compile("<pre\\b[^>]*>(.*?)</pre\\s*>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
  private static final Pattern CODE_TAG =
      Pattern.compile("</?code\\b[^>]*>", Pattern.CASE_INSENSITIVE);

  /** Spaces that a code line keeps: at its start, and two or more in a row. */
  private static final Pattern KEPT_SPACES = Pattern.compile("^ +| {2,}");

  /**
   * @param html the post's HTML as the forum sends it
   * @param forum the forum's address, which the post's links and pictures are relative to
   */
  ForumPost(final String html, final URI forum) {
    setEditable(false);
    setOpaque(false);
    setBorder(BorderFactory.createEmptyBorder());
    final HTMLEditorKit kit = new PostKit();
    setEditorKit(kit);
    final HTMLDocument document = (HTMLDocument) kit.createDefaultDocument();
    addRules(document.getStyleSheet());
    document.putProperty(FORUM_HOST, Objects.toString(forum.getHost(), ""));
    try {
      document.setBase(forum.toURL());
      kit.read(new StringReader(displayHtml(html)), document, 0);
    } catch (final IOException | BadLocationException | IllegalArgumentException e) {
      log.info("A post of the forum could not be shown", e);
    }
    setDocument(document);
    addHyperlinkListener(
        event -> {
          if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED
              && event.getURL() != null
              && isWeb(event.getURL().getProtocol())) {
            OpenFileUtility.openUrl(this, event.getURL().toString());
          }
        });
    // the wrapped height is known only once the width is: lay out again after a resize
    addComponentListener(
        new ComponentAdapter() {
          @Override
          public void componentResized(final ComponentEvent e) {
            revalidate();
          }
        });
  }

  /** As wide as the room it gets, as high as its text needs at that width. */
  @Override
  public Dimension getPreferredSize() {
    return new Dimension(0, super.getPreferredSize().height);
  }

  private static boolean isWeb(final String protocol) {
    return "https".equalsIgnoreCase(protocol) || "http".equalsIgnoreCase(protocol);
  }

  /** The post in the theme's font and colors, its headings, quotes, code and tables as cards. */
  private static void addRules(final StyleSheet styles) {
    final Font font = UIManager.getFont("Label.font");
    final float size = font == null ? 13 : font.getSize2D();
    final Color text = colorOr("Label.foreground", Color.LIGHT_GRAY);
    final Color quiet = LookAndFeel.getSecondaryTextColor();
    final Color line = LookAndFeel.getSeparatorColor();
    // the one color of the TripleA themes, as the forum colors its links
    final Color link =
        colorOr(
            "TripleA.highlightBackground",
            colorOr("Component.linkColor", LookAndFeel.getAccentColor()));
    final String codeFill = hex(MenuColors.mix(MenuColors.windowColor(), text, 0.1));
    styles.addRule(
        "body { font-family: "
            + (font == null ? "Dialog" : font.getFamily())
            + "; font-size: "
            + Math.round(size)
            + "pt; color: "
            + hex(text)
            + "; margin: 0 }");
    styles.addRule("p { margin-top: 0; margin-bottom: " + Math.round(size * 0.6) + "pt }");
    styles.addRule("a { color: " + hex(link) + " }");
    final float[] headings = {1.6f, 1.4f, 1.25f, 1.1f, 1, 1};
    for (int level = 1; level <= headings.length; level++) {
      styles.addRule(
          "h"
              + level
              + " { font-size: "
              + Math.round(size * headings[level - 1])
              + "pt; margin-top: "
              + Math.round(size * 0.4)
              + "pt; margin-bottom: "
              + Math.round(size * 0.4)
              + "pt }");
    }
    styles.addRule(
        "blockquote { margin: 0 0 "
            + Math.round(size * 0.6)
            + "pt 0; padding: 2pt 0 2pt 10pt; border-left: 3px solid "
            + hex(line)
            + "; color: "
            + hex(quiet)
            + " }");
    final String code =
        "font-family: Monospaced; font-size: "
            + Math.round(size)
            + "pt; font-weight: normal; background-color: "
            + codeFill;
    styles.addRule("code { " + code + " }");
    styles.addRule(
        "div.code { " + code + "; padding: 6pt; margin-bottom: " + Math.round(size * 0.6) + "pt }");
    styles.addRule("ul, ol { margin-left: 20pt; margin-top: 0; margin-bottom: 4pt }");
    styles.addRule("li { margin-bottom: 2pt }");
    styles.addRule("table { margin-bottom: " + Math.round(size * 0.6) + "pt }");
    styles.addRule("td, th { padding: 3pt 6pt; border: 1px solid " + hex(line) + " }");
    styles.addRule("th { font-weight: bold }");
  }

  private static Color colorOr(final String key, final Color fallback) {
    final Color color = UIManager.getColor(key);
    return color == null ? fallback : color;
  }

  private static String hex(final Color color) {
    return String.format(Locale.ROOT, "#%06x", color.getRGB() & 0xFFFFFF);
  }

  /**
   * The post's HTML as Swing shows it: without what a post may not hold; a task's box as a mark;
   * struck text as Swing knows it; a code block with its line breaks and indents, each line wrapped
   * only where it is too long, as a block of preformatted text would not wrap.
   */
  @VisibleForTesting
  static String displayHtml(final String html) {
    final String withMarks =
        CHECK_BOX
            .matcher(
                CHECKED_BOX.matcher(UNSAFE_ELEMENTS.matcher(html).replaceAll("")).replaceAll("☑ "))
            .replaceAll("☐ ");
    final String shown =
        CODE_BLOCK
            .matcher(STRUCK.matcher(withMarks).replaceAll("<$1s>"))
            .replaceAll(
                block ->
                    // the code's own "$" and "\" are text, not parts of the replacement
                    Matcher.quoteReplacement(
                        "<div class=\"code\">" + codeLines(block.group(1)) + "</div>"));
    // last, as the other steps drop tags too, and again until nothing is left: a tag split around
    // another one comes together once that goes
    String safe = shown;
    String before;
    do {
      before = safe;
      safe = UNSAFE_TAGS.matcher(UNSAFE_ELEMENTS.matcher(before).replaceAll("")).replaceAll("");
    } while (!safe.equals(before));
    return safe;
  }

  private static String codeLines(final String code) {
    return CODE_TAG
        .matcher(code)
        .replaceAll("")
        .lines()
        .map(line -> line.replace("\t", "    "))
        .map(
            line ->
                KEPT_SPACES
                    .matcher(line)
                    .replaceAll(
                        spaces ->
                            "&nbsp;".repeat(spaces.group().length() - 1)
                                // a space between words stays one to wrap the line at
                                + (spaces.start() == 0 ? "&nbsp;" : " ")))
        .collect(Collectors.joining("<br>"));
  }

  /**
   * Reads a picture from the web, at most {@link #MAX_PICTURE_SIDE} pixels on its longer side: a
   * larger one is read at a lower resolution, as its pixels might not fit into memory.
   */
  @VisibleForTesting
  static BufferedImage readPicture(final URI address, final String forumHost) throws IOException {
    final String host = Objects.toString(address.getHost(), "");
    // a post could make every reader's computer call into their own network, eg their router
    if (!host.equalsIgnoreCase(forumHost)
        && Arrays.stream(InetAddress.getAllByName(host)).anyMatch(ForumPost::isLocal)) {
      throw new IOException("Not a picture of the web: " + address);
    }
    // through the proxy of the settings, like map previews
    final HttpURLConnection connection =
        (HttpURLConnection) address.toURL().openConnection(HttpProxy.getProxy());
    // a redirect could lead there as well
    connection.setInstanceFollowRedirects(false);
    connection.setConnectTimeout(TIMEOUT_MILLIS);
    connection.setReadTimeout(TIMEOUT_MILLIS);
    try (InputStream bytes = ByteStreams.limit(connection.getInputStream(), MAX_PICTURE_BYTES);
        ImageInputStream input = ImageIO.createImageInputStream(bytes)) {
      if (input == null) {
        throw new IOException("No picture at " + address);
      }
      final Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
      if (!readers.hasNext()) {
        throw new IOException("No picture at " + address);
      }
      final ImageReader reader = readers.next();
      try {
        reader.setInput(input, true, true);
        final int side = Math.max(reader.getWidth(0), reader.getHeight(0));
        final int step = Math.max(1, (side + MAX_PICTURE_SIDE - 1) / MAX_PICTURE_SIDE);
        final ImageReadParam scaled = reader.getDefaultReadParam();
        scaled.setSourceSubsampling(step, step, 0, 0);
        return reader.read(0, scaled);
      } catch (final RuntimeException e) {
        // a decoder may fail so on a broken picture, eg a TIFF with "Empty region!": no picture
        throw new IOException("Not a readable picture: " + address, e);
      } finally {
        reader.dispose();
      }
    }
  }

  /** An address of the player's computer or network, which no picture of the web is at. */
  private static boolean isLocal(final InetAddress address) {
    return address.isLoopbackAddress()
        || address.isAnyLocalAddress()
        || address.isLinkLocalAddress()
        || address.isSiteLocalAddress()
        // the private addresses of IPv6, fc00::/7
        || (address instanceof Inet6Address && (address.getAddress()[0] & 0xfe) == 0xfc);
  }

  /**
   * Shows a post's pictures with {@link Picture}, and never what a post's object would build; its
   * styles with {@link PostStyles}.
   */
  private static final class PostKit extends HTMLEditorKit {
    @Serial private static final long serialVersionUID = 3389158006233658164L;

    /** What a post shows nothing of. */
    private static final Set<HTML.Tag> HIDDEN =
        Set.of(
            HTML.Tag.OBJECT,
            HTML.Tag.APPLET,
            HTML.Tag.ISINDEX,
            HTML.Tag.INPUT,
            HTML.Tag.SELECT,
            HTML.Tag.TEXTAREA,
            HTML.Tag.FRAME,
            HTML.Tag.FRAMESET);

    @Override
    public Document createDefaultDocument() {
      final StyleSheet styles = new PostStyles();
      styles.addStyleSheet(getStyleSheet());
      final HTMLDocument document = new HTMLDocument(styles);
      document.setParser(getParser());
      return document;
    }

    private static final ViewFactory VIEWS =
        new HTMLFactory() {
          @Override
          public View create(final Element element) {
            final Object tag = element.getAttributes().getAttribute(StyleConstants.NameAttribute);
            if (tag == HTML.Tag.IMG) {
              return new Picture(element);
            }
            // also where a tag left open got past the cleaning: Swing would build any class an
            // object names, read a picture or page for a field or a frame by itself, and fail on
            // a frameset without rows
            return HIDDEN.contains(tag) ? Picture.nothing(element) : super.create(element);
          }
        };

    @Override
    public ViewFactory getViewFactory() {
      return VIEWS;
    }
  }

  /**
   * A post's styles without the pictures a style or a background may name, however written, and
   * without other stylesheets: Swing would read them by itself, on the screen's thread, from any
   * address.
   */
  private static final class PostStyles extends StyleSheet {
    @Serial private static final long serialVersionUID = -5480339925390669409L;

    @Override
    public AttributeSet translateHTMLToCSS(final AttributeSet html) {
      final AttributeSet css = super.translateHTMLToCSS(html);
      // a new set each time, the element's own
      if (css instanceof MutableAttributeSet own) {
        own.removeAttribute(CSS.Attribute.BACKGROUND_IMAGE);
        own.removeAttribute(CSS.Attribute.LIST_STYLE_IMAGE);
      }
      return css;
    }

    @Override
    public void importStyleSheet(final URL url) {
      // a post's link or @import: the post keeps the forum's look of TripleA
    }
  }

  /**
   * A picture of a post, read in the background: at most as wide as the post, an emoji as high as
   * the text; nothing while it is read, or when it cannot be.
   */
  @VisibleForTesting
  static final class Picture extends View {
    private final @Nullable URI source;

    /** The forum's host, whose pictures are read also where it is in the player's network. */
    private final String forumHost;

    private final boolean emoji;
    private @Nullable BufferedImage image;

    Picture(final Element element) {
      this(
          element,
          sourceOf(
                  element.getAttributes().getAttribute(HTML.Attribute.SRC),
                  (HTMLDocument) element.getDocument())
              .orElse(null));
    }

    private Picture(final Element element, final @Nullable URI source) {
      super(element);
      final AttributeSet attributes = element.getAttributes();
      final HTMLDocument document = (HTMLDocument) getDocument();
      this.source = source;
      // not the document's base, which a post's base tag could change
      forumHost = Objects.toString(document.getProperty(FORUM_HOST), "");
      emoji =
          String.valueOf(attributes.getAttribute(HTML.Attribute.CLASS))
              .toLowerCase(Locale.ROOT)
              .contains("emoji");
      if (source != null) {
        image = PICTURES.getIfPresent(source);
        if (image == null) {
          READER.execute(() -> load(source));
        }
      }
    }

    /** Shows nothing and reads nothing, as a picture without an address. */
    static Picture nothing(final Element element) {
      return new Picture(element, null);
    }

    private static Optional<URI> sourceOf(
        final @Nullable Object source, final HTMLDocument document) {
      if (source == null) {
        return Optional.empty();
      }
      try {
        final URL base = document.getBase();
        final String written = source.toString().strip();
        final URI address = base == null ? URI.create(written) : base.toURI().resolve(written);
        return Optional.of(address).filter(uri -> isWeb(uri.getScheme()));
      } catch (final URISyntaxException | IllegalArgumentException e) {
        return Optional.empty();
      }
    }

    private void load(final URI from) {
      try {
        // one read of an address, its pixels shared by every post of the page that shows it
        final BufferedImage read = PICTURES.get(from, () -> readPicture(from, forumHost));
        SwingUtilities.invokeLater(() -> shown(read));
      } catch (final ExecutionException e) {
        log.debug("A picture of a post could not be read: {}", from, e);
      }
    }

    /** The picture read: the post makes room for it, as the document's other changes do. */
    private void shown(final BufferedImage read) {
      image = read;
      final AbstractDocument document = (AbstractDocument) getDocument();
      document.readLock();
      try {
        preferenceChanged(null, true, true);
      } finally {
        document.readUnlock();
      }
      Optional.ofNullable(getContainer()).ifPresent(Component::repaint);
    }

    @VisibleForTesting
    boolean isRead() {
      return image != null;
    }

    @Override
    public float getPreferredSpan(final int axis) {
      final Dimension size = shownSize();
      return axis == X_AXIS ? size.width : size.height;
    }

    private Dimension shownSize() {
      final BufferedImage shown = image;
      if (shown == null) {
        return new Dimension(0, 0);
      }
      final int width = shown.getWidth();
      final int height = shown.getHeight();
      if (emoji) {
        final int side = textHeight();
        return new Dimension(Math.max(1, width * side / height), side);
      }
      final int room = roomWidth();
      return width <= room
          ? new Dimension(width, height)
          : new Dimension(room, Math.max(1, height * room / width));
    }

    private int textHeight() {
      final Container host = getContainer();
      return host == null ? 16 : Math.round(host.getFont().getSize2D() * 1.3f);
    }

    /** The post's width; unknown before the post is laid out, when the picture keeps its own. */
    private int roomWidth() {
      final Container host = getContainer();
      if (host == null || host.getWidth() <= 0) {
        return Integer.MAX_VALUE;
      }
      final Insets insets = host.getInsets();
      return Math.max(1, host.getWidth() - insets.left - insets.right);
    }

    @Override
    public float getAlignment(final int axis) {
      // on the line's baseline; an emoji a little below, as a letter with a descender
      return axis == Y_AXIS ? (emoji ? 0.8f : 1) : 0.5f;
    }

    @Override
    public void paint(final Graphics g, final Shape allocation) {
      final BufferedImage shown = image;
      if (shown != null) {
        final Rectangle area = allocation.getBounds();
        final Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(
            RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.drawImage(shown, area.x, area.y, area.width, area.height, null);
        g2.dispose();
      }
    }

    @Override
    public Shape modelToView(final int pos, final Shape allocation, final Position.Bias bias)
        throws BadLocationException {
      if (pos < getStartOffset() || pos > getEndOffset()) {
        throw new BadLocationException("Not in the picture", pos);
      }
      final Rectangle area = allocation.getBounds();
      return new Rectangle(
          pos == getEndOffset() ? area.x + area.width : area.x, area.y, 0, area.height);
    }

    @Override
    public int viewToModel(
        final float x, final float y, final Shape allocation, final Position.Bias[] bias) {
      final Rectangle area = allocation.getBounds();
      final boolean before = x < area.x + area.width / 2f;
      // Swing's way to answer the side: in the array it hands over
      bias[0] = before ? Position.Bias.Forward : Position.Bias.Backward;
      return before ? getStartOffset() : getEndOffset();
    }
  }
}
