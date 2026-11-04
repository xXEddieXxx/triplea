package games.strategy.engine.framework.startup.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.commons.text.StringEscapeUtils;

/**
 * A message written for the forum as the forum shows it, for its preview: what the editor's bar
 * writes, headings, bold, italic, code, links, pictures, quotes and lists, in paragraphs whose
 * lines break where they were written. Everything else shows as written: HTML in the message is
 * text, and a link or picture goes only to the web or the forum.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ForumMarkdown {
  private static final Pattern HEADING = Pattern.compile("^(#{1,6})\\s+(.*)$");
  private static final Pattern LIST_ITEM = Pattern.compile("^[-*+]\\s+(.*)$");
  private static final Pattern QUOTE = Pattern.compile("^>\\s?(.*)$");
  private static final Pattern PICTURE = Pattern.compile("!\\[([^\\]]*)\\]\\(([^)\\s]+)\\)");
  private static final Pattern LINK = Pattern.compile("\\[([^\\]]+)\\]\\(([^)\\s]+)\\)");
  private static final Pattern CODE = Pattern.compile("`([^`]+)`");
  private static final Pattern BOLD = Pattern.compile("\\*\\*(.+?)\\*\\*");
  private static final Pattern ITALIC = Pattern.compile("\\*(.+?)\\*");
  private static final Pattern SAFE_ADDRESS = Pattern.compile("(?i)^(https?://|/)[^\"'<>]*$");

  /** Marks HTML made already in a line; a character of Unicode's private use, never written. */
  private static final char HELD = (char) 0xE000;

  private static final Pattern HELD_PART = Pattern.compile(HELD + "(\\d+)" + HELD);

  /** The message as HTML, eg "**Ready**" as "&lt;p&gt;&lt;b&gt;Ready&lt;/b&gt;&lt;/p&gt;". */
  static String toHtml(final String markdown) {
    final StringBuilder html = new StringBuilder();
    final List<String> paragraph = new ArrayList<>();
    final List<String> quote = new ArrayList<>();
    final List<String> list = new ArrayList<>();
    for (final String line : markdown.replace("\r", "").split("\n", -1)) {
      final Matcher heading = HEADING.matcher(line);
      final Matcher item = LIST_ITEM.matcher(line);
      final Matcher quoted = QUOTE.matcher(line);
      if (!item.matches()) {
        html.append(listOf(list));
        list.clear();
      }
      if (!quoted.matches()) {
        html.append(quoteOf(quote));
        quote.clear();
      }
      if (line.isBlank() || heading.matches() || item.matches() || quoted.matches()) {
        html.append(paragraphOf(paragraph));
        paragraph.clear();
      }
      if (heading.matches()) {
        final int level = heading.group(1).length();
        html.append("<h")
            .append(level)
            .append('>')
            .append(inline(heading.group(2)))
            .append("</h")
            .append(level)
            .append('>');
      } else if (item.matches()) {
        list.add(item.group(1));
      } else if (quoted.matches()) {
        quote.add(quoted.group(1));
      } else if (!line.isBlank()) {
        paragraph.add(line);
      }
    }
    return html.append(listOf(list))
        .append(quoteOf(quote))
        .append(paragraphOf(paragraph))
        .toString();
  }

  /** The lines as a paragraph; nothing for none. */
  private static String paragraphOf(final List<String> lines) {
    return lines.isEmpty() ? "" : "<p>" + inlineLines(lines) + "</p>";
  }

  private static String quoteOf(final List<String> lines) {
    return lines.isEmpty() ? "" : "<blockquote><p>" + inlineLines(lines) + "</p></blockquote>";
  }

  private static String listOf(final List<String> items) {
    return items.isEmpty()
        ? ""
        : items.stream()
            .map(item -> "<li>" + inline(item) + "</li>")
            .collect(Collectors.joining("", "<ul>", "</ul>"));
  }

  /** The lines of a paragraph, broken where they were written, as the forum does. */
  private static String inlineLines(final List<String> lines) {
    return String.join("<br>", lines.stream().map(ForumMarkdown::inline).toList());
  }

  /**
   * Bold, italic, code, links and pictures of a line; the rest escaped, so it shows as text. Code,
   * pictures and links are held back as made, so bold and italic never reach into an address.
   */
  private static String inline(final String line) {
    final List<String> held = new ArrayList<>();
    String html = StringEscapeUtils.escapeHtml4(line.replace(HELD, ' '));
    html = replace(CODE, html, code -> hold(held, "<code>" + code.group(1) + "</code>"));
    html =
        replace(
            PICTURE, html, picture -> hold(held, pictureOf(picture.group(1), picture.group(2))));
    html = replace(LINK, html, link -> hold(held, linkOf(formatted(link.group(1)), link.group(2))));
    html = formatted(html);
    // a link may hold code: put back until nothing is held
    while (html.indexOf(HELD) >= 0) {
      html = replace(HELD_PART, html, part -> held.get(Integer.parseInt(part.group(1))));
    }
    return html;
  }

  private static String formatted(final String text) {
    return ITALIC.matcher(BOLD.matcher(text).replaceAll("<b>$1</b>")).replaceAll("<i>$1</i>");
  }

  /** Keeps the made HTML aside; in the line a mark stands for it until it is put back. */
  private static String hold(final List<String> held, final String html) {
    held.add(html);
    return HELD + String.valueOf(held.size() - 1) + HELD;
  }

  private static String pictureOf(final String text, final String address) {
    return isSafe(address) ? "<img src=\"" + address + "\" alt=\"" + text + "\">" : text;
  }

  private static String linkOf(final String text, final String address) {
    return isSafe(address) ? "<a href=\"" + address + "\">" + text + "</a>" : text;
  }

  /** A link or picture to the web or the forum itself; no script, file or other scheme. */
  private static boolean isSafe(final String escapedAddress) {
    return SAFE_ADDRESS.matcher(StringEscapeUtils.unescapeHtml4(escapedAddress)).matches();
  }

  private static String replace(
      final Pattern pattern, final String html, final Function<MatchResult, String> replacement) {
    return pattern
        .matcher(html)
        .replaceAll(match -> Matcher.quoteReplacement(replacement.apply(match)));
  }
}
