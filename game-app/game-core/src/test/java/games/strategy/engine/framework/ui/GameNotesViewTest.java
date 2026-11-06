package games.strategy.engine.framework.ui;

import static org.assertj.core.api.Assertions.assertThat;

import javax.swing.text.html.CSS;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

final class GameNotesViewTest {

  @DisplayName("Game notes get room around the text from a sheet of their own")
  @Test
  void notesHaveTheirOwnReadableStyles() {
    final StyleSheet notesStyles =
        ((HTMLEditorKit) new GameNotesView().getEditorKit()).getStyleSheet();

    assertThat(notesStyles)
        .as("the kit's default sheet is shared by every HTML view; it must stay as it is")
        .isNotSameAs(new HTMLEditorKit().getStyleSheet());
    assertThat(notesStyles.getRule("body").getAttribute(CSS.Attribute.MARGIN_LEFT))
        .as("room left of the notes")
        .isNotNull();
  }
}
