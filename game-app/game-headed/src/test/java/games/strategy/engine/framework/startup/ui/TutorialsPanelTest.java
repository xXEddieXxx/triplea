package games.strategy.engine.framework.startup.ui;

import static org.assertj.core.api.Assertions.assertThat;

import games.strategy.engine.framework.I18nEngineFramework;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.swing.JEditorPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

final class TutorialsPanelTest {

  @DisplayName("Every chapter is there in English and German")
  @Test
  void everyChapterIsTranslated() {
    assertThat(IntStream.rangeClosed(1, TutorialsPanel.CHAPTERS))
        .as("a missing chapter would show an error text instead of the tutorial")
        .allMatch(chapter -> TutorialsPanel.readChapter("en", chapter).isPresent())
        .allMatch(chapter -> TutorialsPanel.readChapter("de", chapter).isPresent());
  }

  @DisplayName(
      "The chapters' pictures are the game's own units and drawings, a missing one left out")
  @Test
  void picturesComeFromTheAssetsAndTheDrawings() {
    final String html =
        TutorialsPanel.withPictures(
            "<p><img src=\"asset:units/Germans/infantry.png\" width=\"40\">"
                + "<img src=\"asset:units/Germans/no-such-unit.png\">"
                + "<img src=\"art:route\"><img src=\"art:no-such-drawing\"></p>");

    assertThat(html)
        .as("the infantry of the assets and the drawn move; the missing ones gone, not broken")
        .contains("units/Germans/infantry.png\" width=\"40\">", "tutorial-art/route.png\">")
        .doesNotContain("asset:", "art:", "no-such");
  }

  @DisplayName("Every picture of every chapter is found in the assets or among the drawings")
  @ParameterizedTest(name = "{0}/{1}.html")
  @MethodSource("chapters")
  void everyPictureOfTheChaptersExists(final String folder, final int chapter) throws IOException {
    final String raw = givenChapterFile(folder, chapter);

    final String shown = TutorialsPanel.withPictures(raw);

    assertThat(count(shown, "<img"))
        .as("a mistyped picture would silently be left out")
        .isEqualTo(count(raw, "src=\"asset:") + count(raw, "src=\"art:"));
  }

  /** Every chapter in both languages. */
  private static Stream<Arguments> chapters() {
    return Stream.of("en", "de")
        .flatMap(
            folder ->
                IntStream.rangeClosed(1, TutorialsPanel.CHAPTERS)
                    .mapToObj(chapter -> Arguments.of(folder, chapter)));
  }

  private static String givenChapterFile(final String folder, final int chapter)
      throws IOException {
    try (InputStream file =
        TutorialsPanel.class.getResourceAsStream("tutorials/" + folder + "/" + chapter + ".html")) {
      return new String(file.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  private static long count(final String text, final String part) {
    return text.split(Pattern.quote(part), -1).length - 1L;
  }

  @DisplayName("The reader paints the drawings of a chapter, eg the red arrow of a move")
  @Test
  void readerPaintsTheDrawings() throws Exception {
    final BufferedImage page = new BufferedImage(800, 2000, BufferedImage.TYPE_INT_RGB);
    // the HTML views are built and laid out on the Swing thread only
    SwingUtilities.invokeAndWait(
        () -> {
          final TutorialsPanel tutorial =
              new TutorialsPanel(I18nEngineFramework.get(), new JPanel());
          tutorial.showChapter(5);
          final JEditorPane reader = givenReaderLaidOut(tutorial);

          final Graphics2D g = page.createGraphics();
          reader.paint(g);
          g.dispose();
        });

    assertThat(containsColor(page, 0xDC281E))
        .as("the move of chapter 5 comes from the drawings, not from a file")
        .isTrue();
  }

  private static JEditorPane givenReaderLaidOut(final TutorialsPanel tutorial) {
    final JEditorPane reader =
        (JEditorPane)
            Stream.iterate(
                    List.<Component>of(tutorial),
                    level -> !level.isEmpty(),
                    level ->
                        level.stream()
                            .filter(Container.class::isInstance)
                            .flatMap(c -> Arrays.stream(((Container) c).getComponents()))
                            .toList())
                .flatMap(List::stream)
                .filter(JEditorPane.class::isInstance)
                .findFirst()
                .orElseThrow();
    // high enough for the whole chapter; its pictures get their size only while it is laid out
    reader.setSize(800, 2000);
    return reader;
  }

  private static boolean containsColor(final BufferedImage image, final int rgb) {
    for (int x = 0; x < image.getWidth(); x++) {
      for (int y = 0; y < image.getHeight(); y++) {
        if ((image.getRGB(x, y) & 0xFFFFFF) == rgb) {
          return true;
        }
      }
    }
    return false;
  }

  @DisplayName("Every drawing of the tutorial is drawn, an unknown name is none")
  @Test
  void everyDrawingIsDrawn() {
    assertThat(TutorialArt.names())
        .as("territories and a move, as the mobile port shows them")
        .containsExactlyInAnyOrder("territory-3", "territory-2", "route")
        .allMatch(name -> TutorialArt.draw(name).filter(image -> image.getWidth() > 0).isPresent());
    assertThat(TutorialArt.draw("no-such-drawing")).as("nothing to show").isEmpty();
  }

  @DisplayName("A language without its own chapters reads the English ones")
  @Test
  void otherLanguagesFallBackToEnglish() {
    assertThat(TutorialsPanel.readChapter("xx", 1))
        .as("the English chapter, not nothing")
        .isEqualTo(TutorialsPanel.readChapter("en", 1));
  }
}
