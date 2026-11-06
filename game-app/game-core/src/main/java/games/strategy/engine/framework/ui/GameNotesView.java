package games.strategy.engine.framework.ui;

import java.awt.Color;
import java.awt.Container;
import java.awt.Font;
import java.awt.Rectangle;
import java.io.Serial;
import java.util.Optional;
import javax.swing.JEditorPane;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.text.Element;
import javax.swing.text.StyleConstants;
import javax.swing.text.View;
import javax.swing.text.ViewFactory;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.ImageView;
import javax.swing.text.html.StyleSheet;

/** Component for displaying HTML-formatted game notes. */
public class GameNotesView extends JEditorPane {
  public GameNotesView() {
    setEditable(false);
    setEditorKit(new ScalingHtmlEditorKit());
    setContentType("text/html");
    // Render CSS lengths per the W3C spec (1px = 1 screen pixel). Without this, Swing's legacy
    // renderer treats CSS px as pt and inflates dimensions by ~33% at 96 DPI. See issue #6157.
    putClientProperty(JEditorPane.W3C_LENGTH_UNITS, Boolean.TRUE);
    // If the foreground color isn't set, a dark theme may make the text color white, which will be
    // unreadable if the html sets a light background color.
    setForeground(Color.BLACK);
    // If the background color isn't set, a dark theme may have a very dark window background, which
    // will make it hard to read the black text against. Note: For some reason, some dark themes
    // show the background as light gray even if white is set, but it doesn't actually look too bad.
    setBackground(Color.WHITE);
  }

  @Override
  public void setText(String text) {
    super.setText(text);
    // Scroll to the top of the notes screen when the text is updated.
    SwingUtilities.invokeLater(() -> scrollRectToVisible(new Rectangle()));
  }

  /**
   * HTML kit that scales embedded images down to fit the viewport width, and sets the notes in a
   * calm, readable way: the font of the interface a bit larger, room around the text and between
   * paragraphs. A map's own styles still win.
   */
  private static final class ScalingHtmlEditorKit extends HTMLEditorKit {
    @Serial private static final long serialVersionUID = 1L;

    // its own sheet: the kit's default one is shared by every HTML view of the client
    private final StyleSheet styles = newReadableStyles();

    @Override
    public StyleSheet getStyleSheet() {
      return styles;
    }

    private static StyleSheet newReadableStyles() {
      final StyleSheet readable = new StyleSheet();
      readable.addStyleSheet(new HTMLEditorKit().getStyleSheet());
      final Font font =
          Optional.ofNullable(UIManager.getFont("Label.font"))
              .orElseGet(() -> new Font(Font.SANS_SERIF, Font.PLAIN, 13));
      readable.addRule(
          "body { font-family: '"
              + font.getFamily()
              + "'; font-size: "
              + (font.getSize() + 2)
              + "px; margin: 12px 20px 20px 20px; }");
      readable.addRule("p { margin-top: 0; margin-bottom: 10px; }");
      readable.addRule("li { margin-bottom: 4px; }");
      readable.addRule("h1, h2, h3 { margin-top: 14px; margin-bottom: 6px; }");
      return readable;
    }

    @Override
    public ViewFactory getViewFactory() {
      final ViewFactory delegate = super.getViewFactory();
      return elem -> {
        final Object name = elem.getAttributes().getAttribute(StyleConstants.NameAttribute);
        if (name == HTML.Tag.IMG) {
          return new ScaledImageView(elem);
        }
        return delegate.create(elem);
      };
    }
  }

  /**
   * ImageView that clamps its width to the enclosing viewport, scaling height proportionally so
   * embedded PNGs in game notes don't force the dialog to grow past the viewport.
   */
  private static final class ScaledImageView extends ImageView {
    ScaledImageView(Element elem) {
      super(elem);
    }

    @Override
    public float getPreferredSpan(int axis) {
      final float naturalWidth = super.getPreferredSpan(View.X_AXIS);
      final float naturalHeight = super.getPreferredSpan(View.Y_AXIS);
      final int viewport = getViewportWidth();
      if (viewport <= 0 || naturalWidth <= 0 || naturalWidth <= viewport) {
        return super.getPreferredSpan(axis);
      }
      final float scale = viewport / naturalWidth;
      return axis == View.X_AXIS ? viewport : naturalHeight * scale;
    }

    private int getViewportWidth() {
      Container c = getContainer();
      while (c != null && !(c instanceof JViewport)) {
        c = c.getParent();
      }
      return c == null ? 0 : c.getWidth();
    }
  }
}
