package games.strategy.engine.framework.startup.ui;

import static org.assertj.core.api.Assertions.assertThat;

import games.strategy.engine.framework.I18nEngineFramework;
import java.awt.Component;
import java.awt.Container;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Stream;
import javax.swing.JButton;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.triplea.http.client.latest.version.LatestVersionResponse;
import org.triplea.http.client.latest.version.LatestVersionResponse.RecommendedAction;

final class MenuFooterTest {
  @DisplayName("The menu's footer points to a newer version only when the lobby recommends one")
  @Test
  void newVersionLinkOnlyWhenRecommended() {
    final MenuFooter upToDate = givenFooter();
    final MenuFooter outdated = givenFooter();

    upToDate.showVersion(givenResponse(RecommendedAction.NO_UPDATE));
    outdated.showVersion(givenResponse(RecommendedAction.UPDATE_RECOMMENDED));

    assertThat(Stream.of(upToDate, outdated).map(footer -> buttonsOf(footer).count()).toList())
        .as("the latest version gets no link, an older one the way to the download page")
        .containsExactly(0L, 1L);
  }

  private static MenuFooter givenFooter() {
    return new MenuFooter(I18nEngineFramework.get(), "Tip: a tip", "Version 1.0", Optional::empty);
  }

  private static LatestVersionResponse givenResponse(final RecommendedAction action) {
    return LatestVersionResponse.builder()
        .recommendedUpdateAction(action.toString())
        .upgradeMessageHtml("")
        .downloadPageUrl("https://triplea-game.org/download/")
        .build();
  }

  private static Stream<JButton> buttonsOf(final Component component) {
    if (component instanceof JButton button) {
      return Stream.of(button);
    }
    return component instanceof Container container
        ? Arrays.stream(container.getComponents()).flatMap(MenuFooterTest::buttonsOf)
        : Stream.empty();
  }
}
