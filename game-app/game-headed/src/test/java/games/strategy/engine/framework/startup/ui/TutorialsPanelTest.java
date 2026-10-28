package games.strategy.engine.framework.startup.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

final class TutorialsPanelTest {

  @DisplayName("Every chapter is there in English and German")
  @Test
  void everyChapterIsTranslated() {
    assertThat(IntStream.rangeClosed(1, TutorialsPanel.CHAPTERS))
        .as("a missing chapter would show an error text instead of the tutorial")
        .allMatch(chapter -> TutorialsPanel.readChapter("en", chapter).isPresent())
        .allMatch(chapter -> TutorialsPanel.readChapter("de", chapter).isPresent());
  }

  @DisplayName("A language without its own chapters reads the English ones")
  @Test
  void otherLanguagesFallBackToEnglish() {
    assertThat(TutorialsPanel.readChapter("xx", 1))
        .as("the English chapter, not nothing")
        .isEqualTo(TutorialsPanel.readChapter("en", 1));
  }
}
