package games.strategy.engine.framework.map.download;

import static org.assertj.core.api.Assertions.assertThat;

import games.strategy.engine.framework.map.download.MapRating.Review;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import javax.swing.JLabel;
import javax.swing.plaf.basic.BasicHTML;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

final class MapRatingTest {
  private static final List<String> MAP_NAMES =
      List.of("Global 1940", "270BC", "Pacific", "Big World", "Napoleonic Empires");

  private final List<Review> saved = new ArrayList<>();

  @DisplayName("Save waits for a star; name and comment are optional and kept without outer spaces")
  @Test
  void saveNeedsStarsCommentOptional() {
    final MapRating rating = new MapRating("Global 1940", null, saved::add);
    final boolean saveBeforeStars = rating.getSave().isEnabled();

    rating.rate(4);
    rating.getComment().setText("  Great balance.  ");
    rating.saveRating();

    assertThat(saveBeforeStars).as("a rating without stars says nothing").isFalse();
    assertThat(saved)
        .as("four stars and the comment as written, no name: anonymous")
        .containsExactly(new Review(4, "", "just now · yours", "Great balance."));
  }

  @DisplayName("A rating saved before shows again when the map is shown again")
  @Test
  void givenRatingShowsAgain() {
    final MapRating rating =
        new MapRating(
            "Global 1940", new Review(2, "Marta", "just now · yours", "Too long"), saved::add);

    assertThat(
            List.of(
                rating.getAuthor().getText(),
                rating.getComment().getText(),
                rating.getSave().isEnabled()))
        .as("name and comment are back and the rating can be saved again")
        .containsExactly("Marta", "Too long", true);
  }

  @DisplayName("The saved rating is the first review, with the name the player gave")
  @Test
  void savedRatingIsFirstReview() {
    final MapRating rating = new MapRating("Global 1940", null, saved::add);

    rating.rate(5);
    rating.getAuthor().setText(" Eddie ");
    rating.saveRating();

    assertThat(rating.reviews())
        .as("the player's review above the three examples")
        .hasSize(4)
        .first()
        .isEqualTo(new Review(5, "Eddie", "just now · yours", ""));
  }

  @DisplayName("A name typed like HTML shows as written and is not rendered")
  @Test
  void nameIsPlainText() {
    final JLabel name = MapRating.newNameLabel("<html><img src=http://example.org/x.png>");

    assertThat(name.getClientProperty(BasicHTML.propertyKey))
        .as("no HTML view, so no image is fetched and the layout stays")
        .isNull();
  }

  @DisplayName("Every map shows three example reviews, the same each time, one at least anonymous")
  @Test
  void exampleReviewsAreStableWithAnonymousOne() {
    assertThat(MAP_NAMES)
        .as("so the mockup shows how a review without an author looks on every map")
        .allSatisfy(
            mapName ->
                assertThat(MapRating.exampleReviews(mapName))
                    .hasSize(3)
                    .isEqualTo(MapRating.exampleReviews(mapName))
                    .anyMatch(review -> review.author().isEmpty()));
  }

  @DisplayName("The example average lies between three and five stars and stays per map")
  @Test
  void exampleAverageIsPlausibleAndStable() {
    assertThat(Stream.of("Global 1940", "270BC", "Pacific", "Global 1940"))
        .map(MapRating::exampleAverage)
        .as("it is only an example, so it looks like real ratings and never changes")
        .allMatch(average -> average >= 3.0 && average <= MapRating.MOST_STARS)
        .satisfies(averages -> assertThat(averages.get(0)).isEqualTo(averages.get(3)));
  }

  @DisplayName("A map in the list shows its rating and short downloads, eg 4.3 · 12.4K downloads")
  @Test
  void summaryShowsRatingAndShortDownloads() {
    assertThat(MAP_NAMES)
        .as("at least 120 downloads, so a map never has more ratings than downloads")
        .allSatisfy(
            mapName -> {
              assertThat(MapRating.summaryText(mapName))
                  .matches("[34]\\.\\d · \\d+(\\.\\d)?K? downloads");
              assertThat(MapRating.exampleDownloads(mapName)).isGreaterThanOrEqualTo(120);
              assertThat(MapRating.downloadsText(mapName)).matches("\\d{1,3}(,\\d{3})*");
            });
  }
}
