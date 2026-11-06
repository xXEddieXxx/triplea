package games.strategy.engine.framework.map.download;

import com.formdev.flatlaf.FlatClientProperties;
import com.google.common.annotations.VisibleForTesting;
import games.strategy.engine.framework.lookandfeel.LookAndFeel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagLayout;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.Serial;
import java.text.NumberFormat;
import java.util.Arrays;
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
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.JTextComponent;
import lombok.AccessLevel;
import lombok.Getter;
import org.triplea.swing.JButtonBuilder;
import org.triplea.swing.JLabelBuilder;
import org.triplea.swing.ScrollableJPanel;
import org.triplea.swing.SwingComponents;
import org.triplea.swing.WrappingText;
import org.triplea.swing.jpanel.GridBagConstraintsAnchor;
import org.triplea.swing.jpanel.GridBagConstraintsBuilder;
import org.triplea.swing.jpanel.GridBagConstraintsFill;

/**
 * The ratings of a map in a tab of its details, a mockup until the server keeps ratings: an
 * overview with the players' average and a bar for each number of stars, and the reviews, the
 * player's saved one first, long ones shortened until "Read more"; these scroll. Below them stays
 * "Write a review", which opens the player's own rating of one to five stars with an optional name
 * and comment in its place, so writing a long review keeps the form in view. The numbers and the
 * reviews of other players are examples: marked as such here, unmarked in the lists of maps and the
 * details' facts, which show how real ones would look. Save keeps the rating while the window is
 * open; nothing is sent.
 */
final class MapRating extends JPanel {
  @Serial private static final long serialVersionUID = -2350714436985262874L;

  static final int MOST_STARS = 5;

  private static final List<String> WORDS =
      List.of("Poor", "Fair", "Good", "Very good", "Excellent");
  private static final int STAR_SIZE = 22;
  private static final int SMALL_STAR_SIZE = 14;
  private static final int REVIEW_STAR_SIZE = 12;
  private static final int SHOWN_EXAMPLES = 5;
  // a longer comment is shortened to about this many characters until "Read more"
  private static final int SHORT_COMMENT = 280;
  private static final String WRITE = "Write a review";
  private static final String EDIT = "Edit your review";
  private static final String READ_MORE = "Read more";
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
          new Review(5, "", "1 week ago", "Perfect for new players: short turns and clear rules."),
          new Review(
              4,
              "Kriegsspiel",
              "3 weeks ago",
              "We finished our third game of this map last weekend, so here is a longer take. The"
                  + " opening is the strongest part: both sides have real choices in the first"
                  + " three rounds, and a bad purchase in round one is not the end. The middle game"
                  + " slows down once the big fleets meet, and the battle calculator becomes your"
                  + " best friend. Economically the map rewards holding the islands more than it"
                  + " looks at first. Our only complaint: the victory cities make the end drag a"
                  + " little, so we play with the lower count from the game options. Still one of"
                  + " the best maps for a long evening with friends."),
          new Review(5, "Brandenburg", "4 days ago", ""));

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
  private final JTextArea comment = new JTextArea(6, 20);

  /** Opens the form to write or edit the player's review; it stays at the bottom of the tab. */
  @Getter(value = AccessLevel.PACKAGE, onMethod_ = @VisibleForTesting)
  private final JButton write = new JButton();

  private final JPanel form = new JPanel();

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
    setLayout(new BorderLayout());
    setOpaque(false);
    // reading first: the overview and the reviews scroll, writing stays at the bottom
    final JPanel reading = new JPanel();
    reading.setLayout(new BoxLayout(reading, BoxLayout.Y_AXIS));
    reading.setOpaque(false);
    addLeftAligned(reading, newOverview(mapName));
    reading.add(Box.createVerticalStrut(24));
    addLeftAligned(reading, LookAndFeel.newSectionLabel("Reviews (examples)"));
    reading.add(Box.createVerticalStrut(4));
    reviewList.setLayout(new BoxLayout(reviewList, BoxLayout.Y_AXIS));
    reviewList.setOpaque(false);
    addLeftAligned(reading, reviewList);
    add(newScroll(reading), BorderLayout.CENTER);
    add(newWritingArea(), BorderLayout.SOUTH);
    if (given != null) {
      stars = given.stars();
      author.setText(given.author());
      comment.setText(given.comment());
      saved.setText(SAVED);
    }
    write.setText(given == null ? WRITE : EDIT);
    showStars();
    showReviews();
  }

  /** The reading part, which scrolls on its own, as wide as the tab. */
  private static JScrollPane newScroll(final JComponent reading) {
    final JPanel view = new ScrollableJPanel();
    view.setLayout(new BorderLayout());
    view.setOpaque(false);
    view.add(reading, BorderLayout.NORTH);
    final JScrollPane scroll = SwingComponents.newJScrollPane(view);
    scroll.setOpaque(false);
    scroll.getViewport().setOpaque(false);
    scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    return scroll;
  }

  /**
   * Below the reviews, never scrolled away: the button that opens the form, and the form itself
   * with the stars, the name and a comment field large enough for a long review.
   */
  private JComponent newWritingArea() {
    final JPanel area = new JPanel(new BorderLayout(0, 12));
    area.setOpaque(false);
    area.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, LookAndFeel.getSeparatorColor()),
            BorderFactory.createEmptyBorder(12, 0, 0, 0)));
    final JPanel buttonRow = new JPanel(new BorderLayout(12, 0));
    buttonRow.setOpaque(false);
    LookAndFeel.setPrimaryButtonStyle(write);
    write.addActionListener(e -> showForm(true));
    buttonRow.add(write, BorderLayout.WEST);
    saved.setForeground(LookAndFeel.getSecondaryTextColor());
    buttonRow.add(saved, BorderLayout.CENTER);
    area.add(buttonRow, BorderLayout.NORTH);

    form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
    form.setOpaque(false);
    addLeftAligned(form, new JLabel("Your rating"));
    form.add(Box.createVerticalStrut(4));
    addLeftAligned(form, newStarPicker());
    form.add(Box.createVerticalStrut(8));
    author.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Anonymous");
    addLeftAligned(form, newLabeled("Name (optional)", author, author));
    form.add(Box.createVerticalStrut(8));
    comment.setLineWrap(true);
    comment.setWrapStyleWord(true);
    addLeftAligned(form, newLabeled("Comment (optional)", comment, new JScrollPane(comment)));
    form.add(Box.createVerticalStrut(8));
    addLeftAligned(form, newSaveRow());
    form.setVisible(false);
    area.add(form, BorderLayout.CENTER);
    return area;
  }

  /** Shows the form instead of the button, or the button again after saving or cancelling. */
  private void showForm(final boolean shown) {
    form.setVisible(shown);
    write.setVisible(!shown);
    if (shown) {
      saved.setText("");
    }
    revalidate();
    repaint();
  }

  @VisibleForTesting
  boolean isWriting() {
    return form.isVisible();
  }

  /** Five example reviews that stay the same for the map, taken from a few made-up ones. */
  @VisibleForTesting
  static List<Review> exampleReviews(final String mapName) {
    final int first = Math.floorMod(mapName.hashCode(), EXAMPLE_REVIEWS.size());
    return IntStream.range(first, first + SHOWN_EXAMPLES)
        .mapToObj(index -> EXAMPLE_REVIEWS.get(index % EXAMPLE_REVIEWS.size()))
        .toList();
  }

  private static void addLeftAligned(final JPanel panel, final JComponent component) {
    component.setAlignmentX(Component.LEFT_ALIGNMENT);
    panel.add(component);
  }

  /**
   * The players' ratings at a glance, as stores show them: the average as a large number over its
   * stars and the count; beside it a bar for each number of stars, how many gave it.
   */
  private static JComponent newOverview(final String mapName) {
    final double average = exampleAverage(mapName);
    final JPanel score = new JPanel();
    score.setLayout(new BoxLayout(score, BoxLayout.Y_AXIS));
    score.setOpaque(false);
    addLeftAligned(
        score,
        new JLabelBuilder(String.format(Locale.ROOT, "%.1f", average))
            .bold()
            .adjustFontSize(18)
            .build());
    addLeftAligned(score, new JLabel(new StarIcon(SMALL_STAR_SIZE, MOST_STARS, () -> average)));
    score.add(Box.createVerticalStrut(4));
    addLeftAligned(
        score,
        new JLabelBuilder(exampleCount(mapName) + " ratings (examples)")
            .foregroundColor(LookAndFeel.getSecondaryTextColor())
            .build());

    final List<Integer> distribution = exampleDistribution(mapName);
    final int most = distribution.stream().mapToInt(Integer::intValue).max().orElse(1);
    final JPanel bars = new JPanel(new GridBagLayout());
    bars.setOpaque(false);
    for (int starCount = MOST_STARS; starCount >= 1; starCount--) {
      final int given = distribution.get(starCount - 1);
      final int row = MOST_STARS - starCount;
      final JLabel label =
          new JLabel(
              String.valueOf(starCount),
              new StarIcon(REVIEW_STAR_SIZE, 1, () -> 1.0),
              SwingConstants.LEADING);
      label.setHorizontalTextPosition(SwingConstants.LEADING);
      label.setIconTextGap(3);
      bars.add(label, new GridBagConstraintsBuilder(0, row).insets(2, 0, 2, 8).build());
      bars.add(
          new ShareBar((double) given / most),
          new GridBagConstraintsBuilder(1, row)
              .weightX(1)
              .fill(GridBagConstraintsFill.HORIZONTAL)
              .insets(2, 0, 2, 8)
              .build());
      bars.add(
          new JLabelBuilder(String.valueOf(given))
              .foregroundColor(LookAndFeel.getSecondaryTextColor())
              .build(),
          new GridBagConstraintsBuilder(2, row)
              .anchor(GridBagConstraintsAnchor.EAST)
              .insets(2, 0, 2, 0)
              .build());
    }

    final JPanel overview = new JPanel(new BorderLayout(28, 0));
    overview.setOpaque(false);
    overview.add(score, BorderLayout.WEST);
    overview.add(bars, BorderLayout.CENTER);
    return overview;
  }

  /**
   * How many of the example ratings gave one to five stars, the first for one star: most near the
   * average, fewer the further from it, adding up to the count of ratings.
   */
  @VisibleForTesting
  static List<Integer> exampleDistribution(final String mapName) {
    final double average = exampleAverage(mapName);
    final int count = exampleCount(mapName);
    final double[] weights =
        IntStream.rangeClosed(1, MOST_STARS)
            .mapToDouble(
                starCount -> Math.pow(Math.max(0.05, 1 - Math.abs(starCount - average) / 2.5), 2))
            .toArray();
    final double total = Arrays.stream(weights).sum();
    final int[] given = Arrays.stream(weights).mapToInt(w -> (int) (w / total * count)).toArray();
    // what rounding down left goes to the stars nearest the average
    final int nearest = (int) Math.round(average) - 1;
    given[nearest] += count - Arrays.stream(given).sum();
    return Arrays.stream(given).boxed().toList();
  }

  /** A bar filled to a share, from 0 to 1, of its width. */
  private static final class ShareBar extends JComponent {
    @Serial private static final long serialVersionUID = 1L;
    private static final int HEIGHT = 8;
    private final double share;

    ShareBar(final double share) {
      this.share = share;
      setPreferredSize(new Dimension(80, HEIGHT));
    }

    @Override
    protected void paintComponent(final Graphics g) {
      final Graphics2D g2 = (Graphics2D) g.create();
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      final int top = (getHeight() - HEIGHT) / 2;
      g2.setColor(LookAndFeel.getSeparatorColor());
      g2.fillRoundRect(0, top, getWidth(), HEIGHT, HEIGHT, HEIGHT);
      g2.setColor(LookAndFeel.getAccentColor());
      g2.fillRoundRect(0, top, (int) Math.round(getWidth() * share), HEIGHT, HEIGHT, HEIGHT);
      g2.dispose();
    }
  }

  /** An average from 3.0 to 4.9 that stays the same for the map, until real ratings exist. */
  static double exampleAverage(final String mapName) {
    return 3.0 + Math.floorMod(mapName.hashCode(), 20) / 10.0;
  }

  @VisibleForTesting
  static int exampleCount(final String mapName) {
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

  /**
   * Save, and Cancel, which closes the form and keeps what was typed for the next time it opens.
   */
  private JComponent newSaveRow() {
    final JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    row.setOpaque(false);
    save.setText("Save rating");
    LookAndFeel.setPrimaryButtonStyle(save);
    save.addActionListener(e -> saveRating());
    row.add(save);
    row.add(Box.createHorizontalStrut(8));
    row.add(new JButtonBuilder("Cancel").actionListener(() -> showForm(false)).build());
    return row;
  }

  @VisibleForTesting
  void saveRating() {
    final Review review =
        new Review(stars, author.getText().strip(), JUST_NOW, comment.getText().strip());
    savedReview = review;
    onSave.accept(review);
    write.setText(EDIT);
    showForm(false);
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
   * comment if there is one. A rating without a comment is just that line.
   */
  private static JComponent newReview(final Review review) {
    final JLabel starLabel = new JLabel(new StarIcon(SMALL_STAR_SIZE, MOST_STARS, review::stars));
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

    final JPanel entry = new JPanel(new BorderLayout(0, 8));
    entry.setOpaque(false);
    entry.setAlignmentX(Component.LEFT_ALIGNMENT);
    final int padding = review.comment().isEmpty() ? 8 : 14;
    entry.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, LookAndFeel.getSeparatorColor()),
            BorderFactory.createEmptyBorder(padding, 0, padding, 0)));
    entry.add(header, BorderLayout.NORTH);
    if (!review.comment().isEmpty()) {
      entry.add(newComment(review.comment()), BorderLayout.CENTER);
    }
    return entry;
  }

  /** The comment, wrapped; a long one shortened, with "Read more" to show all of it and back. */
  private static JComponent newComment(final String comment) {
    final String shortened = shortened(comment);
    final WrappingText text = new WrappingText(shortened);
    if (shortened.equals(comment)) {
      return text;
    }
    final JButton more = new JButtonBuilder(READ_MORE).build();
    LookAndFeel.setBorderlessButtonStyle(more);
    more.addActionListener(
        e -> {
          final boolean expand = READ_MORE.equals(more.getText());
          text.setText(expand ? comment : shortened);
          more.setText(expand ? "Show less" : READ_MORE);
        });
    final JPanel panel = new JPanel(new BorderLayout(0, 2));
    panel.setOpaque(false);
    panel.add(text, BorderLayout.CENTER);
    final JPanel link = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    link.setOpaque(false);
    link.add(more);
    panel.add(link, BorderLayout.SOUTH);
    return panel;
  }

  /** The comment, or its start cut at a word with "…" if it is longer than about 280 characters. */
  @VisibleForTesting
  static String shortened(final String comment) {
    if (comment.length() <= SHORT_COMMENT) {
      return comment;
    }
    final int lastSpace = comment.lastIndexOf(' ', SHORT_COMMENT);
    return comment.substring(0, lastSpace > 0 ? lastSpace : SHORT_COMMENT).strip() + "…";
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
