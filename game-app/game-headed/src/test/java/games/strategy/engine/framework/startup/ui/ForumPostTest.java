package games.strategy.engine.framework.startup.ui;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import com.github.tomakehurst.wiremock.WireMockServer;
import games.strategy.engine.framework.startup.ui.ForumPost.Picture;
import games.strategy.triplea.settings.ClientSetting;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import javax.swing.text.View;
import javax.swing.text.html.CSS;
import javax.swing.text.html.HTMLDocument;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.sonatype.goodies.prefs.memory.MemoryPreferences;

final class ForumPostTest {
  private final WireMockServer forum = new WireMockServer(0);

  @BeforeEach
  void givenForum() {
    // no proxy set, as pictures are read through the proxy of the settings
    ClientSetting.setPreferences(new MemoryPreferences());
    forum.start();
  }

  @AfterEach
  void stopForumAndResetSettings() {
    forum.stop();
    ClientSetting.resetPreferences();
  }

  @DisplayName("A code block keeps its lines and indents, its own \"$\" too, and wraps its lines")
  @Test
  void codeBlockKeepsLinesAndIndents() {
    final String html =
        ForumPost.displayHtml(
            "<pre><code class=\"language-java\">if (a) {\n    b(\"$1\");\n}\n</code></pre>");

    assertThat(html)
        .as("a break per line, the indent kept, the spaces between words breakable")
        .isEqualTo("<div class=\"code\">if (a) {<br>&nbsp;&nbsp;&nbsp;&nbsp;b(\"$1\");<br>}</div>");
  }

  @DisplayName("What a post may not hold is dropped: scripts, styles, objects, forms, stylesheets")
  @Test
  void unsafeElementsAreDropped() {
    final String html =
        ForumPost.displayHtml(
            "<p>a</p><script>alert(1)</script><STYLE>p {}</STYLE>"
                + "<object classid=\"java.lang.Object\"></object><form action=\"/x\">"
                + "<input type=\"text\"><button>go</button></form><isindex action=\"/y\">"
                + "<link rel=\"stylesheet\" href=\"/s.css\"><base href=\"http://elsewhere/\">"
                // split around a tag, each comes together once that goes
                + "<in<input>put type=\"image\" src=\"/r.png\">"
                + "<fr<frame>ameset rows=\"100%\"><fr<frame>ame src=\"/f\"></fr<frame>ameset>"
                + "<st<style>yle>p { background-image: url(/b.png) }</st<style>yle>"
                // split by a code block's own tags, which go only when the block is rewritten
                + "<pre><code><is<code></code>index prompt=\"x\"></code></pre>");

    assertThat(html)
        .as("the text alone stays, also of tags split around others; the code block, emptied")
        .isEqualTo("<p>a</p>go<div class=\"code\"></div>");
  }

  @DisplayName("A field, frame or object a tag left open brings shows nothing and reads nothing")
  @ParameterizedTest
  @ValueSource(
      strings = {
        "<p>a</p><input type=\"image\" src=\"%s\"",
        // the field's prompt, which Swing would show as HTML with its picture
        "<p>a</p><isindex prompt=\"&lt;html&gt;&lt;img src=%s&gt;\""
      })
  void openTagShowsNothing(final String html) throws Exception {
    givenPicture("/router.png", 10, 10);
    final AtomicReference<ForumPost> post = new AtomicReference<>();
    // the field's tag is never closed, so no cleaning finds it; plain Swing builds a field of it
    SwingUtilities.invokeAndWait(
        () ->
            post.set(
                new ForumPost(
                    String.format(Locale.ROOT, html, address("/router.png")), address("/"))));

    final List<View> views = viewsOf(post.get());

    assertThat(views).as("laid out").isNotEmpty();
    assertThat(post.get().getComponentCount())
        .as("no field, whose picture Swing would read by itself, from any address")
        .isZero();
    // a moment for a read in the background: none may come
    await().pollDelay(Duration.ofMillis(300)).atMost(Duration.ofSeconds(1)).until(() -> true);
    forum.verify(0, anyRequestedFor(anyUrl()));
  }

  @DisplayName("A style or background names no picture however written; a post links no stylesheet")
  @Test
  void stylesLoadNoPictures() throws Exception {
    final AtomicReference<ForumPost> post = new AtomicReference<>();
    SwingUtilities.invokeAndWait(
        () ->
            post.set(
                new ForumPost(
                    "<p ,style=\"background-image:url("
                        + address("/a.png")
                        + ")\">a</p>"
                        + "<p \"style\"=\"background: url("
                        + address("/b.png")
                        + ")\">b</p>"
                        + "<table background=\""
                        + address("/c.png")
                        + "\"><tr><td>c</td></tr></table>"
                        + "<ul style=\"list-style-image: url("
                        + address("/d.png")
                        + ")\"><li>d</li></ul>"
                        + "<p style=\"color: red; background-image: url("
                        + address("/e.png")
                        + ")>e",
                    address("/"))));
    final List<View> views = viewsOf(post.get());

    assertThat(views)
        .as("comma before, quoted name, shorthand, background, list picture, quote left open")
        .isNotEmpty()
        .noneMatch(
            view ->
                // the root has no attributes
                view.getAttributes() != null
                    && (view.getAttributes().getAttribute(CSS.Attribute.BACKGROUND_IMAGE) != null
                        || view.getAttributes().getAttribute(CSS.Attribute.LIST_STYLE_IMAGE)
                            != null));
    final URL stylesheet = address("/s.css").toURL();
    SwingUtilities.invokeAndWait(
        () ->
            ((HTMLDocument) post.get().getDocument()).getStyleSheet().importStyleSheet(stylesheet));
    // neither a picture nor the stylesheet asked for
    forum.verify(0, anyRequestedFor(anyUrl()));
  }

  @DisplayName("Struck text as Swing knows it, a task's box as a mark")
  @Test
  void struckTextAndTasks() {
    assertThat(
            ForumPost.displayHtml(
                "<del>old</del> <input type=\"checkbox\" disabled=\"\" checked=\"\"> done"
                    + " <input type=\"checkbox\" disabled=\"\"> open"))
        .as("a box ticked or not; no field the reader could type into")
        .isEqualTo("<s>old</s> ☑  done ☐  open");
  }

  @DisplayName("A picture larger than any screen is read at a lower resolution")
  @Test
  void largePictureIsReadSmaller() throws Exception {
    givenPicture("/big.png", 5000, 20);

    final BufferedImage read = ForumPost.readPicture(address("/big.png"), "localhost");

    assertThat(read.getWidth()).as("every third pixel of 5000").isEqualTo(1667);
  }

  @DisplayName("An address without a picture is no picture")
  @Test
  void noPictureFails() {
    forum.stubFor(get(urlEqualTo("/page")).willReturn(aResponse().withBody("<html></html>")));

    assertThatThrownBy(() -> ForumPost.readPicture(address("/page"), "localhost"))
        .as("no reader for a page: nothing shows")
        .isInstanceOf(IOException.class);
  }

  @DisplayName("A broken picture, on which its decoder fails, is no picture rather than an error")
  @Test
  void brokenPictureIsNoPicture() {
    // a TIFF with a width only: Java's decoder throws an IllegalArgumentException, "Empty region!"
    final byte[] tiff = {
      'I', 'I', 42, 0, 8, 0, 0, 0, 1, 0, 0, 1, 3, 0, 1, 0, 0, 0, (byte) 0xff, 0x7f, 0, 0, 0, 0, 0, 0
    };
    forum.stubFor(get(urlEqualTo("/broken.tif")).willReturn(aResponse().withBody(tiff)));

    assertThatThrownBy(() -> ForumPost.readPicture(address("/broken.tif"), "localhost"))
        .as("the post leaves the picture out, as one it cannot reach")
        .isInstanceOf(IOException.class);
  }

  @DisplayName("A picture in the player's own computer or network is not read, but the forum's")
  @Test
  void localPictureOfAnotherHostIsNotRead() throws Exception {
    givenPicture("/router.png", 10, 10);

    assertThatThrownBy(() -> ForumPost.readPicture(address("/router.png"), "forum.example.org"))
        .as("a post could make every reader's computer call it")
        .isInstanceOf(IOException.class);
    forum.verify(0, anyRequestedFor(anyUrl()));
  }

  @DisplayName("A redirect is not followed: it could lead into the player's own network")
  @Test
  void redirectIsNotFollowed() throws Exception {
    givenPicture("/router.png", 10, 10);
    forum.stubFor(
        get(urlEqualTo("/moved.png"))
            .willReturn(aResponse().withStatus(302).withHeader("Location", "/router.png")));

    assertThatThrownBy(() -> ForumPost.readPicture(address("/moved.png"), "localhost"))
        .as("the redirect is no picture")
        .isInstanceOf(IOException.class);
    forum.verify(0, getRequestedFor(urlEqualTo("/router.png")));
  }

  @DisplayName("A picture is at most as wide as the post, an emoji as high as the text")
  @Test
  void picturesFitThePost() throws Exception {
    givenPicture("/assets/map.png", 1200, 600);
    givenPicture("/emoji/smile.png", 64, 64);
    final AtomicReference<ForumPost> post = new AtomicReference<>();
    SwingUtilities.invokeAndWait(
        () ->
            post.set(
                new ForumPost(
                    "<p><img src=\"/assets/map.png\"> and"
                        + " <img class=\"not-responsive emoji\" src=\"/emoji/smile.png\"></p>",
                    address("/"))));
    SwingUtilities.invokeAndWait(() -> post.get().setSize(300, 100));

    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () ->
                assertThat(picturesOf(post.get()))
                    .as("both read")
                    .hasSize(2)
                    .allMatch(Picture::isRead));
    final List<Picture> pictures = picturesOf(post.get());
    assertThat(pictures.get(0).getPreferredSpan(View.X_AXIS))
        .as("the map as wide as the post, its height in proportion")
        .isEqualTo(300);
    assertThat(pictures.get(0).getPreferredSpan(View.Y_AXIS)).as("half as high").isEqualTo(150);
    assertThat(pictures.get(1).getPreferredSpan(View.Y_AXIS))
        .as("the emoji a little higher than the text")
        .isEqualTo(Math.round(post.get().getFont().getSize2D() * 1.3f));
  }

  private void givenPicture(final String path, final int width, final int height)
      throws IOException {
    final ByteArrayOutputStream png = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", png);
    forum.stubFor(get(urlEqualTo(path)).willReturn(aResponse().withBody(png.toByteArray())));
  }

  private URI address(final String path) {
    return URI.create("http://localhost:" + forum.port() + path);
  }

  private static List<Picture> picturesOf(final ForumPost post) throws Exception {
    return viewsOf(post).stream()
        .filter(Picture.class::isInstance)
        .map(Picture.class::cast)
        .toList();
  }

  private static List<View> viewsOf(final ForumPost post) throws Exception {
    final List<View> found = new ArrayList<>();
    SwingUtilities.invokeAndWait(
        () -> {
          // laid out at the post's width: a paragraph has its lines, and their pictures, only then
          post.getPreferredSize();
          collect(post.getUI().getRootView(post), found);
        });
    return found;
  }

  private static void collect(final View view, final List<View> found) {
    found.add(view);
    for (int index = 0; index < view.getViewCount(); index++) {
      collect(view.getView(index), found);
    }
  }
}
