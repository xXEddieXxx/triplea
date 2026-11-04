package games.strategy.engine.framework.startup.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

final class ForumMarkdownTest {

  @DisplayName("What the editor's bar writes shows as the forum shows it")
  @Test
  void barMarksShowFormatted() {
    final String html =
        ForumMarkdown.toHtml(
"""
**Ready** for *round 2*
see [the map](https://example.org/map)

> Germans attack

- Moscow
- Leningrad

![front](/assets/uploads/front.png)""");

    assertThat(html)
        .as("bold, italic, a link, a quote, a list and a picture; lines break where written")
        .isEqualTo(
            "<p><b>Ready</b> for <i>round 2</i><br>see"
                + " <a href=\"https://example.org/map\">the map</a></p>"
                + "<blockquote><p>Germans attack</p></blockquote>"
                + "<ul><li>Moscow</li><li>Leningrad</li></ul>"
                + "<p><img src=\"/assets/uploads/front.png\" alt=\"front\"></p>");
  }

  @DisplayName("HTML and links to other schemes show as text, as a forum post may not hold them")
  @Test
  void unsafeShowsAsText() {
    assertThat(
            List.of(
                ForumMarkdown.toHtml("<script>alert(1)</script>"),
                ForumMarkdown.toHtml("[click](javascript:void)")))
        .as("written HTML is escaped; a script link keeps only its text")
        .containsExactly("<p>&lt;script&gt;alert(1)&lt;/script&gt;</p>", "<p>click</p>");
  }

  @DisplayName("Bold and italic stay out of addresses and pictures, so links and pictures work")
  @Test
  void marksStayOutOfAddresses() {
    assertThat(
            List.of(
                ForumMarkdown.toHtml("[rules](https://f.org/a*b*c)"),
                ForumMarkdown.toHtml("![map*](/p.png) and *this*"),
                ForumMarkdown.toHtml("[`code` **here**](/x)")))
        .as("an address keeps its stars; the text around a picture is formatted on its own")
        .containsExactly(
            "<p><a href=\"https://f.org/a*b*c\">rules</a></p>",
            "<p><img src=\"/p.png\" alt=\"map*\"> and <i>this</i></p>",
            "<p><a href=\"/x\"><code>code</code> <b>here</b></a></p>");
  }
}
