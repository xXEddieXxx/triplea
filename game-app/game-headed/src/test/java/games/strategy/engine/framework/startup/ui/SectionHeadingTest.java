package games.strategy.engine.framework.startup.ui;

import static org.assertj.core.api.Assertions.assertThat;

import games.strategy.engine.framework.startup.ui.LineIcon.Kind;
import java.awt.Dimension;
import javax.swing.JButton;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

final class SectionHeadingTest {
  @DisplayName("A title bar with a small link beside it is as high as one without")
  @Test
  void headingsHaveOneHeight() {
    final SectionHeading plain = SectionHeading.newHeading("Updates", Kind.UPDATES);
    final SectionHeading withLink =
        SectionHeading.newHeading("New in the forum", Kind.FORUM, givenLink(10));

    assertThat(withLink.getPreferredSize().height)
        .as("so the rows of both areas of the band start on one line")
        .isEqualTo(plain.getPreferredSize().height);
    assertThat(withLink.getPreferredSize().width)
        .as("the link still needs its room beside the name")
        .isGreaterThan(plain.getPreferredSize().width);
  }

  @DisplayName("A link taller than the title bar gets its height, nothing of it is cut off")
  @Test
  void tallLinkKeepsItsHeight() {
    final SectionHeading withTallLink =
        SectionHeading.newHeading("New in the forum", Kind.FORUM, givenLink(60));

    assertThat(withTallLink.getPreferredSize().height)
        .as("the link must stay whole, also its hover background")
        .isGreaterThanOrEqualTo(60);
  }

  private static JButton givenLink(final int height) {
    final JButton link = new JButton("All topics");
    link.setPreferredSize(new Dimension(80, height));
    return link;
  }
}
