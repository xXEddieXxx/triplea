package org.triplea.swing;

import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.io.Serial;
import javax.swing.BorderFactory;
import javax.swing.JTextArea;
import javax.swing.UIManager;

/**
 * Read-only text in the label font that wraps at the width its layout gives it. Unlike a {@code
 * JLabel}, a long text never widens the window: the preferred width is 0 and the preferred height
 * is whatever the wrapped text needs. Use it for descriptions and hints next to fixed-size
 * controls.
 */
public final class WrappingText extends JTextArea {
  @Serial private static final long serialVersionUID = 3870035627284069525L;

  public WrappingText() {
    this("");
  }

  public WrappingText(final String text) {
    super(text);
    setLineWrap(true);
    setWrapStyleWord(true);
    setEditable(false);
    setFocusable(false);
    setHighlighter(null);
    setOpaque(false);
    setCursor(Cursor.getDefaultCursor());
    // the wrapped height is known only once the width is: lay out again after a resize
    addComponentListener(
        new ComponentAdapter() {
          @Override
          public void componentResized(final ComponentEvent e) {
            revalidate();
          }
        });
  }

  /** Installs the text area look of the theme, then keeps looking like a label. */
  @Override
  public void updateUI() {
    super.updateUI();
    setBorder(BorderFactory.createEmptyBorder());
    setFont(UIManager.getFont("Label.font"));
  }

  @Override
  public Dimension getPreferredSize() {
    return new Dimension(0, super.getPreferredSize().height);
  }
}
