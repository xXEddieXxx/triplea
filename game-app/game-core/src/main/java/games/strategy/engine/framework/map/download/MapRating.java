package games.strategy.engine.framework.map.download;

import com.formdev.flatlaf.FlatClientProperties;
import com.google.common.annotations.VisibleForTesting;
import games.strategy.engine.framework.lookandfeel.LookAndFeel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.Serial;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.JTextComponent;
import lombok.AccessLevel;
import lombok.Getter;
import org.triplea.swing.JLabelBuilder;
import org.triplea.swing.WrappingText;

/**
 * The ratings of a map below its description, a mockup until the server keeps ratings: the players'
 * average in stars, the player's own rating of one to five stars with an optional name and comment,
 * and the reviews, the player's saved one first. The numbers and the reviews of other players are
 * examples: marked as such here, unmarked in the lists of maps and the details' facts, which show
 * how real ones would look. Save keeps the rating while the window is open; nothing is sent.
 */
final class MapRating extends JPanel {
  @Serial private static final long serialVersionUID = -2350714436985262874L;

  static final int MOST_STARS = 5;

  private static final List<String> WORDS =
      List.of("Poor", "Fair", "Good", "Very good", "Excellent");
  private static final int STAR_SIZE = 22;
  private static final int SMALL_STAR_SIZE = 14;
  private static final int REVIEW_STAR_SIZE = 12;
  private static final int SHOWN_EXAMPLES = 3;
  private static final String SAVED = "Saved. Preview: ratings are not sent yet.";
  private static final String JUST_NOW = "just now · yours";

  // shortcut: the same made-up reviews for every map, until the map server keeps reviews
  private static final List<Review> EXAMPLE_REVIEWS =
      List.of(
          new Review(
              5,
              "IronDuke",
              "2 weeks ago",
              "Great balance between the sides. Our group has played it every Friday for a month."),
          new Review(
              4,
              "",
              "1 month ago",
              "Big and detailed. Plan a few evenings for one game, but it is worth it."),
          new Review(
              5, "SeaLion42", "5 days ago", "Beautiful map art, and the hard AI puts up a fight."),
          new Review(
              3,
              "Marta K.",
              "3 months ago",
              "Fun, though the special rules take a while to learn. Read the game notes first."),
          new Review(4, "", "2 months ago", ""),
          new Review(
              2,
              "OldGuard",
              "6 months ago",
              "The AI hardly uses its fleet here, so better play it against people."),
          new Review(5, "", "1 week ago", "Perfect for new players: short turns and clear rules."));

  /**
   * A review: one to five stars, the author (empty if anonymous), when, eg "2 weeks ago", and the
   * comment (empty if none was written). The player's saved rating is one, from "just now".
   */
  record Review(int stars, String author, String when, String comment) {}

  private final transient Consumer<Review> onSave;
  private final transient List<Review> examples;
  private final JLabel word = new JLabel();

  @Getter(value = AccessLevel.PACKAGE, onMethod_ = @VisibleForTesting)
  private final JTextField author = new JTextField(20);

  @Getter(value = AccessLevel.PACKAGE, onMethod_ = @VisibleForTesting)
  private final JTextArea comment = new JTextArea(3, 20);

  @Getter(value = AccessLevel.PACKAGE, onMethod_ = @VisibleForTesting)
  private final JButton save = new JButton();

  private final JLabel saved = new JLabel();
  private final JPanel reviewList = new JPanel();

  @Getter(value = AccessLevel.PACKAGE, onMethod_ = @VisibleForTesting)
  private int stars;

  private int hoveredStars;
  private transient @Nullable Review savedReview;

  /**
   * @param mapName the map, for its example average and reviews
   * @param given the rating the player saved before, if any
   * @param onSave keeps a saved rating
   */
  MapRating(final String mapName, final @Nullable Review given, final Consumer<Review> onSave) {
    this.onSave = onSave;
    examples = exampleReviews(mapName);
    savedReview = given;
    setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
    setOpaque(false);
    addLeftAligned(LookAndFeel.newSectionLabel("Ratings"));
    add(Box.createVerticalStrut(8));
    addLeftAligned(newAverage(mapName));
    add(Box.createVerticalStrut(16));
    addLeftAligned(new JLabel("Your rating"));
    add(Box.createVerticalStrut(4));
    addLeftAligned(newStarPicker());
    add(Box.createVerticalStrut(8));
    author.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Anonymous");
    addLeftAligned(newLabeled("Name (optional)", author, author));
    add(Box.createVerticalStrut(8));
    comment.setLineWrap(true);
    comment.setWrapStyleWord(true);
    addLeftAligned(newLabeled("Comment (optional)", comment, new JScrollPane(comment)));
    add(Box.createVerticalStrut(8));
    addLeftAligned(newSaveRow());
    add(Box.createVerticalStrut(24));
    addLeftAligned(LookAndFeel.newSectionLabel("Reviews (examples)"));
    add(Box.createVerticalStrut(4));
    reviewList.setLayout(new BoxLayout(reviewList, BoxLayout.Y_AXIS));
    reviewList.setOpaque(false);
    addLeftAligned(reviewList);
    if (given != null) {
      stars = given.stars();
      author.setText(given.author());
      comment.setText(given.comment());
      saved.setText(SAVED);
    }
    showStars();
    showReviews();
  }

  /** Three example reviews that stay the same for the map, taken from a few made-up ones. */
  @VisibleForTesting
  static List<Review> exampleReviews(final String mapName) {
    final int first = Math.floorMod(mapName.hashCode(), EXAMPLE_REVIEWS.size());
    return IntStream.range(first, first + SHOWN_EXAMPLES)
        .mapToObj(index -> EXAMPLE_REVIEWS.get(index % EXAMPLE_REVIEWS.size()))
        .toList();
  }

  private void addLeftAligned(final JComponent component) {
    component.setAlignmentX(Component.LEFT_ALIGNMENT);
    add(component);
  }

  /** The average of the players as stars and a number, eg "4.3 · 27 ratings · example". */
  private static JComponent newAverage(final String mapName) {
    final double average = exampleAverage(mapName);
    final JLabel line =
        new JLabel(
            String.format(
                Locale.ROOT, "%.1f  ·  %d ratings  ·  example", average, exampleCount(mapName)),
            new StarIcon(SMALL_STAR_SIZE, MOST_STARS, () -> average),
            SwingConstants.LEADING);
    line.setIconTextGap(10);
    line.setForeground(LookAndFeel.getSecondaryTextColor());
    return line;
  }

  /** An average from 3.0 to 4.9 that stays the same for the map, until real ratings exist. */
  static double exampleAverage(final String mapName) {
    return 3.0 + Math.floorMod(mapName.hashCode(), 20) / 10.0;
  }

  private static int exampleCount(final String mapName) {
    return 3 + Math.floorMod(mapName.hashCode() / 20, 60);
  }

  /** Five stars to click, lit up to the one under the mouse; the word for the rating beside. */
  private JComponent newStarPicker() {
    final JPanel picker = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    picker.setOpaque(false);
    IntStream.rangeClosed(1, MOST_STARS).mapToObj(this::newStar).forEach(picker::add);
    word.setForeground(LookAndFeel.getSecondaryTextColor());
    word.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0));
    picker.add(word);
    return picker;
  }

  private JButton newStar(final int value) {
    final JButton star =
        new JButton(new StarIcon(STAR_SIZE, 1, () -> value <= shownStars() ? 1.0 : 0.0));
    star.setContentAreaFilled(false);
    star.setBorderPainted(false);
    star.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
    star.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    star.getAccessibleContext()
        .setAccessibleName(value + " of " + MOST_STARS + " stars, " + WORDS.get(value - 1));
    star.addActionListener(e -> rate(value));
    star.addMouseListener(
        new MouseAdapter() {
          @Override
          public void mouseEntered(final MouseEvent e) {
            hoveredStars = value;
            showStars();
          }

          @Override
          public void mouseExited(final MouseEvent e) {
            hoveredStars = 0;
            showStars();
          }
        });
    return star;
  }

  private int shownStars() {
    return hoveredStars > 0 ? hoveredStars : stars;
  }

  @VisibleForTesting
  void rate(final int newStars) {
    stars = newStars;
    saved.setText("");
    showStars();
  }

  private void showStars() {
    word.setText(shownStars() == 0 ? "Click a star" : WORDS.get(shownStars() - 1));
    save.setEnabled(stars > 0);
    repaint();
  }

  /**
   * A field below its label in the secondary color; shown is the field or its scroll pane. A change
   * of the text clears "Saved".
   */
  private JComponent newLabeled(
      final String title, final JTextComponent field, final JComponent shown) {
    final JPanel box = new JPanel(new BorderLayout(0, 4));
    box.setOpaque(false);
    final JLabel label = new JLabel(title);
    label.setForeground(LookAndFeel.getSecondaryTextColor());
    label.setLabelFor(field);
    box.add(label, BorderLayout.NORTH);
    box.add(shown, BorderLayout.CENTER);
    field
        .getDocument()
        .addDocumentListener(
            new DocumentListener() {
              @Override
              public void insertUpdate(final DocumentEvent e) {
                saved.setText("");
              }

              @Override
              public void removeUpdate(final DocumentEvent e) {
                saved.setText("");
              }

              @Override
              public void changedUpdate(final DocumentEvent e) {
                saved.setText("");
              }
            });
    return box;
  }

  /** The Save button and beside it "Saved", cut with "…" where the details are narrow. */
  private JComponent newSaveRow() {
    final JPanel row = new JPanel(new BorderLayout(12, 0));
    row.setOpaque(false);
    save.setText("Save rating");
    LookAndFeel.setPrimaryButtonStyle(save);
    save.addActionListener(e -> saveRating());
    row.add(save, BorderLayout.WEST);
    saved.setForeground(LookAndFeel.getSecondaryTextColor());
    row.add(saved, BorderLayout.CENTER);
    return row;
  }

  @VisibleForTesting
  void saveRating() {
    final Review review =
        new Review(stars, author.getText().strip(), JUST_NOW, comment.getText().strip());
    savedReview = review;
    onSave.accept(review);
    saved.setText(SAVED);
    showReviews();
  }

  private void showReviews() {
    reviewList.removeAll();
    reviews().stream().map(MapRating::newReview).forEach(reviewList::add);
    reviewList.revalidate();
    reviewList.repaint();
  }

  /** The reviews to show: the player's saved one first, then the examples. */
  @VisibleForTesting
  List<Review> reviews() {
    return Stream.concat(Stream.ofNullable(savedReview), examples.stream()).toList();
  }

  /**
   * A review below a separator line: its stars, the author or "Anonymous" and when, then the
   * comment if there is one.
   */
  private static JComponent newReview(final Review review) {
    final JLabel starLabel = new JLabel(new StarIcon(REVIEW_STAR_SIZE, MOST_STARS, review::stars));
    starLabel
        .getAccessibleContext()
        .setAccessibleName(review.stars() + " of " + MOST_STARS + " stars");
    final JLabel authorLabel =
        review.author().isEmpty()
            ? new JLabelBuilder("Anonymous")
                .foregroundColor(LookAndFeel.getSecondaryTextColor())
                .build()
            : newNameLabel(review.author());
    final JPanel header = new JPanel();
    header.setLayout(new BoxLayout(header, BoxLayout.X_AXIS));
    header.setOpaque(false);
    header.add(starLabel);
    header.add(Box.createHorizontalStrut(10));
    header.add(authorLabel);
    header.add(
        new JLabelBuilder(" · " + review.when())
            .foregroundColor(LookAndFeel.getSecondaryTextColor())
            .build());

    final JPanel entry = new JPanel(new BorderLayout(0, 6));
    entry.setOpaque(false);
    entry.setAlignmentX(Component.LEFT_ALIGNMENT);
    entry.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, LookAndFeel.getSeparatorColor()),
            BorderFactory.createEmptyBorder(10, 0, 10, 0)));
    entry.add(header, BorderLayout.NORTH);
    if (!review.comment().isEmpty()) {
      entry.add(new WrappingText(review.comment()), BorderLayout.CENTER);
    }
    return entry;
  }

  /**
   * The name a player typed, in bold and as plain text: a name like "&lt;html&gt;..." shows as
   * written. Swing reads the switch when the text is set, so it comes first.
   */
  @VisibleForTesting
  static JLabel newNameLabel(final String name) {
    final JLabel label = new JLabel();
    label.putClientProperty("html.disable", Boolean.TRUE);
    label.setText(name);
    label.setFont(label.getFont().deriveFont(Font.BOLD));
    return label;
  }

  /** The rating and downloads of a map in the lists: a star, then eg "4.3 · 12.4K downloads". */
  static JLabel newSummary(final String mapName) {
    final String text = summaryText(mapName);
    final JLabel summary =
        new JLabel(text, new StarIcon(REVIEW_STAR_SIZE, 1, () -> 1.0), SwingConstants.LEADING);
    summary.setIconTextGap(4);
    summary.setForeground(LookAndFeel.getSecondaryTextColor());
    // the star alone does not say that the number is a rating
    summary.getAccessibleContext().setAccessibleName("Rated " + text);
    return summary;
  }

  @VisibleForTesting
  static String summaryText(final String mapName) {
    final NumberFormat downloads =
        NumberFormat.getCompactNumberInstance(Locale.ENGLISH, NumberFormat.Style.SHORT);
    downloads.setMaximumFractionDigits(1);
    return String.format(
        Locale.ROOT,
        "%.1f · %s downloads",
        exampleAverage(mapName),
        downloads.format(exampleDownloads(mapName)));
  }

  /** The example downloads of the map in full, eg "12,431". */
  static String downloadsText(final String mapName) {
    return String.format(Locale.ENGLISH, "%,d", exampleDownloads(mapName));
  }

  /** Downloads that stay the same for the map, 40 to 439 per rating, until real numbers exist. */
  static int exampleDownloads(final String mapName) {
    return exampleCount(mapName) * (40 + Math.floorMod(mapName.hashCode() / 1200, 400));
  }
}
