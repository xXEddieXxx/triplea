package games.strategy.engine.framework.startup.ui;

import com.google.common.annotations.VisibleForTesting;
import games.strategy.engine.framework.lookandfeel.LookAndFeel;
import games.strategy.engine.framework.startup.ui.ForumBrowserLogin.SignedIn;
import games.strategy.engine.framework.startup.ui.LineIcon.Kind;
import games.strategy.engine.framework.startup.ui.panels.main.HeadedServerSetupModel;
import games.strategy.engine.posted.game.pbf.NodeBbForumPoster;
import games.strategy.engine.posted.game.pbf.NodeBbGameThreads;
import games.strategy.engine.posted.game.pbf.NodeBbGameThreads.GameThread;
import games.strategy.engine.posted.game.pbf.NodeBbGameThreads.Player;
import games.strategy.engine.posted.game.pbf.NodeBbGameThreads.SaveGame;
import games.strategy.engine.posted.game.pbf.NodeBbGameThreads.ThreadPage;
import games.strategy.engine.posted.game.pbf.NodeBbGameThreads.ThreadPost;
import games.strategy.triplea.UrlConstants;
import games.strategy.triplea.settings.ClientSetting;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.io.Serial;
import java.net.URI;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.annotation.Nullable;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.filechooser.FileNameExtensionFilter;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.triplea.awt.OpenFileUtility;
import org.triplea.swing.DocumentListenerBuilder;
import org.triplea.swing.ScrollableJPanel;
import org.triplea.swing.WrappingText;

/**
 * Play by forum as a page of the main menu, kept to what matters. Signed out, one card: sign in on
 * the forum's own page, in a browser window of TripleA ({@link ForumBrowserLogin}). Signed in, a
 * slim bar (My games and Browse games on the left; forum, player and New game on the right) over
 * the games as a list and the chosen game's posts. My games are the threads the player started or
 * posted in, marked "YOUR TURN" where another player posted last, those first, and the games of
 * others the player watches, marked "WATCHING", those last, with filters for them; Browse games are
 * the newest of the forum's Play by Forum category. A search above the list narrows it by title.
 * Each post is a card with its author's avatar in the forum's colors, the player's own marked in
 * the highlight color of the TripleA themes, its text as the forum formats it ({@link ForumPost}).
 * A save game in a post opens with one click, locally: in the player's own games to play on, in the
 * others' to view only ({@link HeadedServerSetupModel#viewSavedGame}). The page writes to the forum
 * only when the player says so: an answer, written below the posts of any game with a bar for the
 * forum's formatting, eg a request to join a game of others (Join game), and Watch, which follows a
 * game.
 */
@Slf4j
final class ForumGamesPanel extends JPanel {
  @Serial private static final long serialVersionUID = 2671396083316911870L;

  private static final int LIST_WIDTH = 380;
  private static final int SIGN_IN_WIDTH = 440;

  /**
   * A post longer than this many lines, eg a turn summary, shows as high as this many until opened:
   * its paragraphs and headings take more room than lines, so there is always more to show.
   */
  private static final int SHORT_LINES = 6;

  /** The rounding of a game's and a post's card, and the width of the bar that marks one. */
  private static final int ARC = 12;

  private static final int BAR = 3;

  /** The size of the icons of the answer's formatting bar. */
  private static final int FORMAT_ICON = 20;

  private static final DateTimeFormatter TIME =
      DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm").withZone(ZoneId.systemDefault());
  private static final String SIGNED_OUT = "signedOut";
  private static final String GAMES = "games";
  private static final String LIST = "list";
  private static final String MESSAGE = "message";
  private static final String SIGN_IN_HINT =
      "Signing in opens the forum's own page in a window of TripleA.";

  /** What Join game writes into the answer box, for the player to finish and send. */
  private static final String JOIN_REQUEST = "I'd like to join this game. I can take over: ";

  private static final String WATCH = "Watch";
  private static final String WATCHING = "✓  Watching";
  private static final String WATCH_TIP =
      "Follows the game: the forum tells you of new posts, and the game shows under My games";

  /** Marks Watch while the forum is asked to change it. */
  private static final String CHANGING = "ForumGamesPanel.watchChanging";

  /** Runs the forum's requests, which may take seconds, off the EDT. */
  private static final ExecutorService BACKGROUND = Executors.newVirtualThreadPerTaskExecutor();

  /**
   * A forum to play on: its name, address and Play by Forum category (0 when unknown, then there is
   * nothing to browse), and the setting that keeps the player's browser session there, encrypted.
   * Whose session it is the forum tells each time the page opens.
   */
  record Forum(String name, String url, int playByForumCategory, ClientSetting<char[]> session) {
    /** The name the forum choice shows. */
    @Override
    public String toString() {
      return name;
    }
  }

  /** The forums of play by forum, as on its setup screen. */
  static final List<Forum> FORUMS =
      List.of(
          new Forum(
              NodeBbForumPoster.TRIPLEA_FORUM_DISPLAY_NAME,
              UrlConstants.TRIPLEA_FORUM,
              NodeBbGameThreads.TRIPLEA_PLAY_BY_FORUM_CATEGORY,
              ClientSetting.tripleaForumSession),
          new Forum(
              NodeBbForumPoster.AXIS_AND_ALLIES_ORG_DISPLAY_NAME,
              UrlConstants.AXIS_AND_ALLIES_FORUM,
              0,
              ClientSetting.aaForumSession));

  /** Opens a forum's login page and hands the sign-in on; {@link ForumBrowserLogin} in the game. */
  @FunctionalInterface
  interface SignInPage {
    void open(
        Component parent, String forumUrl, Consumer<SignedIn> onSignedIn, Consumer<String> onError);
  }

  /** The games the list shows. */
  private enum View {
    MINE,
    BROWSE
  }

  /**
   * Whose move a game is, as far as the forum tells: who posted last; or a game of others the
   * player watches.
   */
  private enum Status {
    YOUR_TURN,
    WAITING,
    UNKNOWN,
    WATCHING
  }

  private final transient HeadedServerSetupModel model;
  private final transient SignInPage signInPage;
  private final JComboBox<Forum> forumChoice;
  private final JToggleButton mine = newTab("My games");
  private final JToggleButton browse = newTab("Browse games");
  private final JLabel player = new JLabel();
  private final JButton signOut = new JButton("Sign out");
  private final JButton newGame = new JButton("+  New game");
  private final CardLayout pages = new CardLayout();
  private final JPanel content = new JPanel(pages);
  private final JTextArea signInStatus = newSecondaryText();
  private final JButton signIn = new JButton("Sign in with the forum");
  private final CardLayout listPages = new CardLayout();
  private final JPanel listArea = new JPanel(listPages);
  private final JLabel listMessage = new JLabel("", SwingConstants.CENTER);
  private final DefaultListModel<GameThread> threadsModel = new DefaultListModel<>();

  /** The games read for the tab shown, before the search and the filter choose among them. */
  private transient List<GameThread> loaded = List.of();

  /** The player's own games as My games read them last, by topic: in Browse games no others'. */
  private transient Set<Integer> ownGames = Set.of();

  /** While the forum is asked for the games, or whose the session is. */
  private boolean reading;

  /** Why the games could not be read, until they are asked for again; none when they were. */
  private @Nullable String readError;

  private final JTextField search = new JTextField();
  private final JToggleButton allGames = newFilter("All");
  private final JToggleButton yourTurn = newFilter("Your turn");
  private final JToggleButton waiting = newFilter("Waiting");
  private final JToggleButton watchedGames = newFilter("Watching");
  private final JPanel turnFilters = new JPanel(new FlowLayout(FlowLayout.LEFT, MenuSpacing.XS, 0));

  @Getter(value = AccessLevel.PACKAGE, onMethod_ = @VisibleForTesting)
  private final JList<GameThread> threads = new JList<>(threadsModel);

  private final RoundedPanel threadCard = new RoundedPanel(new BorderLayout());
  private final JPanel postsArea = new JPanel(new BorderLayout());
  private final JTextArea message = new JTextArea(4, 30);
  private final JLabel attachment = new JLabel();
  private final JTextArea sendStatus = newSecondaryText();
  private final JButton sendButton = new JButton("Send");

  /** The game a post is being sent to, until the forum answered. */
  private transient @Nullable GameThread sendingTo;

  private transient Forum forum;

  /** The player the forum's session is for, once the forum told; none while signed out. */
  private transient @Nullable Player signedIn;

  /**
   * The sessions kept in the settings, by forum: read once, as each read decrypts the setting, and
   * kept in step with signing in and out.
   */
  private final transient Map<Forum, Optional<String>> sessions = new HashMap<>();

  /** While a login window is open, a second one would take the first one's cookie away. */
  private boolean signInOpen;

  private View view = View.MINE;
  private transient @Nullable GameThread shownThread;

  /** The shown game's Watch, which knows whether the player follows the game once it is read. */
  private @Nullable JToggleButton watchButton;

  private transient @Nullable Path attachedSave;

  /** Counts the requests for games and posts, so an answer to an older one is dropped. */
  private int threadsRequest;

  private int postsRequest;

  /**
   * @param model opens a downloaded save game and the setup of a new game
   * @param forums the forums to choose from, eg {@link #FORUMS}
   * @param signInPage opens a forum's login page, eg {@link ForumBrowserLogin#signIn}
   */
  ForumGamesPanel(
      final HeadedServerSetupModel model, final List<Forum> forums, final SignInPage signInPage) {
    super(new BorderLayout(0, MenuSpacing.M));
    this.model = model;
    this.signInPage = signInPage;
    setOpaque(false);
    // the forum the player signed in to, else the first
    forum =
        forums.stream()
            .filter(candidate -> sessionOf(candidate).isPresent())
            .findFirst()
            .orElse(forums.getFirst());
    forumChoice = new JComboBox<>(forums.toArray(Forum[]::new));
    forumChoice.setSelectedItem(forum);
    forumChoice.addActionListener(e -> chooseForum((Forum) forumChoice.getSelectedItem()));
    add(newTopBar(), BorderLayout.NORTH);
    content.setOpaque(false);
    content.add(newSignInCard(), SIGNED_OUT);
    content.add(newGamesArea(), GAMES);
    add(content, BorderLayout.CENTER);
  }

  private static JToggleButton newTab(final String text) {
    final JToggleButton tab = new JToggleButton(text);
    tab.putClientProperty("JButton.buttonType", "tab");
    tab.setFocusPainted(false);
    return tab;
  }

  /** A secondary text that wraps, also for messages of the forum: never read as HTML. */
  private static JTextArea newSecondaryText() {
    final JTextArea text = new WrappingText();
    text.setForeground(LookAndFeel.getSecondaryTextColor());
    return text;
  }

  private Optional<String> sessionOf(final Forum forum) {
    return sessions.computeIfAbsent(
        forum,
        unread ->
            unread.session().getValue().filter(value -> value.length > 0).map(String::valueOf));
  }

  private void chooseForum(final Forum chosen) {
    if (!chosen.equals(forum)) {
      forum = chosen;
      // the other forum's thread numbers are others
      ownGames = Set.of();
      view = View.MINE;
      refresh();
    }
  }

  /** My games and Browse games on the left; the forum, the player and New game on the right. */
  private JComponent newTopBar() {
    final ButtonGroup tabs = new ButtonGroup();
    tabs.add(mine);
    tabs.add(browse);
    mine.addActionListener(e -> showView(View.MINE));
    browse.addActionListener(e -> showView(View.BROWSE));
    final JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    left.setOpaque(false);
    left.add(mine);
    left.add(browse);
    LookAndFeel.setSmallStyle(forumChoice);
    player.setForeground(LookAndFeel.getSecondaryTextColor());
    player.putClientProperty("html.disable", Boolean.TRUE);
    LookAndFeel.setBorderlessButtonStyle(signOut);
    signOut.setToolTipText("Forgets the sign-in on this computer");
    signOut.addActionListener(e -> signOut());
    LookAndFeel.setPrimaryButtonStyle(newGame);
    newGame.setToolTipText("Choose a map and the players, then post the first turn in a thread");
    newGame.addActionListener(e -> model.showPbf());
    final JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, MenuSpacing.S, 0));
    right.setOpaque(false);
    right.add(forumChoice);
    right.add(player);
    right.add(signOut);
    right.add(newGame);
    final JPanel bar = new JPanel(new BorderLayout());
    bar.setOpaque(false);
    bar.add(left, BorderLayout.WEST);
    bar.add(right, BorderLayout.EAST);
    return bar;
  }

  /** Signed out the one card in the middle: what the page is for, and the way in. */
  private JComponent newSignInCard() {
    final RoundedPanel card = new RoundedPanel(null);
    card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
    card.setBorder(
        BorderFactory.createEmptyBorder(
            MenuSpacing.L, MenuSpacing.L, MenuSpacing.L, MenuSpacing.L));
    final JLabel title =
        new JLabel(
            "Play by forum",
            new LineIcon(Kind.FORUM, 32, LookAndFeel.getSecondaryTextColor()),
            SwingConstants.LEADING);
    title.setIconTextGap(MenuSpacing.M);
    title.setFont(title.getFont().deriveFont(Font.BOLD, title.getFont().getSize2D() + 6));
    final JTextArea about = newSecondaryText();
    about.setText(
        "Sign in with your forum account to see your games, follow the games of others and open"
            + " their save games with one click.");
    LookAndFeel.setPrimaryButtonStyle(signIn);
    signIn.addActionListener(e -> signIn());
    for (final JComponent part : List.of(title, about, signIn, signInStatus)) {
      part.setAlignmentX(Component.LEFT_ALIGNMENT);
      card.add(part);
      card.add(Box.createVerticalStrut(MenuSpacing.M));
    }
    // the texts wrap at this width; their room is known only then, so a little more is given
    card.setPreferredSize(
        new Dimension(SIGN_IN_WIDTH, card.getPreferredSize().height + MenuSpacing.L));
    final JPanel middle = new JPanel(new GridBagLayout());
    middle.setOpaque(false);
    middle.add(card);
    return middle;
  }

  /** The games on the left, the chosen game on the right. */
  private JComponent newGamesArea() {
    // the composer below a game's posts is made anew for each game, with this one Send
    LookAndFeel.setPrimaryButtonStyle(sendButton);
    sendButton.addActionListener(e -> send());
    final RoundedPanel listCard = new RoundedPanel(new BorderLayout());
    listCard.setPreferredSize(new Dimension(LIST_WIDTH, 0));
    listCard.setBorder(
        BorderFactory.createEmptyBorder(
            MenuSpacing.S, MenuSpacing.S, MenuSpacing.S, MenuSpacing.S));
    threads.setOpaque(false);
    threads.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    threads.setCellRenderer(
        (list, thread, index, selected, focused) ->
            new ThreadCell(thread, selected, statusOf(thread)));
    threads.addListSelectionListener(
        e -> {
          final GameThread chosen = threads.getSelectedValue();
          // the game shown stays as the search narrows the list: no need to read it again
          if (!e.getValueIsAdjusting() && chosen != null && !isSameGame(chosen, shownThread)) {
            showThread(chosen, 0);
          }
        });
    listArea.setOpaque(false);
    listArea.add(newTransparentScroll(threads), LIST);
    listMessage.setForeground(LookAndFeel.getSecondaryTextColor());
    listMessage.putClientProperty("html.disable", Boolean.TRUE);
    listArea.add(listMessage, MESSAGE);
    listCard.add(newListTools(), BorderLayout.NORTH);
    listCard.add(listArea, BorderLayout.CENTER);
    postsArea.setOpaque(false);
    final JPanel area = new JPanel(new BorderLayout(MenuSpacing.M, 0));
    area.setOpaque(false);
    area.add(listCard, BorderLayout.WEST);
    area.add(threadCard, BorderLayout.CENTER);
    return area;
  }

  /** Above the games: a search by title, and in the player's own games whose move it is. */
  private JComponent newListTools() {
    search.putClientProperty("JTextField.placeholderText", "Search games");
    search.putClientProperty("JTextField.showClearButton", Boolean.TRUE);
    // the builder calls back on a timer's thread
    new DocumentListenerBuilder(() -> SwingUtilities.invokeLater(this::showFound)).attachTo(search);
    final ButtonGroup filters = new ButtonGroup();
    for (final JToggleButton filter : List.of(allGames, yourTurn, waiting, watchedGames)) {
      filters.add(filter);
      filter.addActionListener(e -> showFound());
      turnFilters.add(filter);
    }
    allGames.setSelected(true);
    yourTurn.setToolTipText("Games where another player posted last, eg the turn before yours");
    waiting.setToolTipText("Games where you posted last: the others are to move");
    watchedGames.setToolTipText("Games of others you watch");
    turnFilters.setOpaque(false);
    final JPanel tools = new JPanel(new BorderLayout(0, MenuSpacing.XS));
    tools.setOpaque(false);
    tools.setBorder(BorderFactory.createEmptyBorder(0, 0, MenuSpacing.S, 0));
    tools.add(search, BorderLayout.NORTH);
    tools.add(turnFilters, BorderLayout.SOUTH);
    return tools;
  }

  private static JToggleButton newFilter(final String text) {
    final JToggleButton filter = new JToggleButton(text);
    filter.putClientProperty("JButton.buttonType", "roundRect");
    LookAndFeel.setSmallStyle(filter);
    filter.setFocusPainted(false);
    return filter;
  }

  /** Whose move a game of the list is, as far as the forum tells; only in the player's games. */
  private Status statusOf(final GameThread game) {
    if (view != View.MINE || signedIn == null) {
      return Status.UNKNOWN;
    }
    if (game.onlyWatched()) {
      return Status.WATCHING;
    }
    if (game.lastPosterSlug().isEmpty()) {
      return Status.UNKNOWN;
    }
    return game.waitsFor(signedIn) ? Status.YOUR_TURN : Status.WAITING;
  }

  /**
   * Shows the games read as the search and the filter choose them: in the player's own games, those
   * waiting for the player first and those the player watches last, each part the last answered
   * first.
   */
  private void showFound() {
    if (reading) {
      // the games are still read: the list keeps saying so
      return;
    }
    if (readError != null) {
      // no games to search: the list keeps saying why
      showListMessage(readError);
      return;
    }
    final String wanted = search.getText().strip().toLowerCase(Locale.ROOT);
    final GameThread chosen = threads.getSelectedValue();
    threadsModel.clear();
    loaded.stream()
        .filter(game -> wanted.isEmpty() || game.title().toLowerCase(Locale.ROOT).contains(wanted))
        .filter(
            game ->
                allGames.isSelected()
                    || (yourTurn.isSelected() && statusOf(game) == Status.YOUR_TURN)
                    || (waiting.isSelected() && statusOf(game) == Status.WAITING)
                    || (watchedGames.isSelected() && statusOf(game) == Status.WATCHING))
        // a stable sort: the forum's order, the last answered first, within each part
        .sorted(Comparator.comparingInt(this::rankOf))
        .forEach(threadsModel::addElement);
    if (threadsModel.isEmpty()) {
      showListMessage(
          loaded.isEmpty()
              ? (view == View.MINE
                  ? "No games yet. Start one with New game"
                      + (forum.playByForumCategory() > 0
                          ? ", or watch others under Browse games."
                          : ".")
                  : "No games in the forum's Play by Forum category.")
              : "No game matches the search or the filter.");
      return;
    }
    listPages.show(listArea, LIST);
    if (chosen != null) {
      IntStream.range(0, threadsModel.size())
          .filter(index -> isSameGame(threadsModel.get(index), chosen))
          .findFirst()
          .ifPresent(threads::setSelectedIndex);
    }
  }

  /** The games waiting for the player first, the games of others the player watches last. */
  private int rankOf(final GameThread game) {
    return switch (statusOf(game)) {
      case YOUR_TURN -> 0;
      case WATCHING -> 2;
      default -> 1;
    };
  }

  private static JScrollPane newTransparentScroll(final JComponent view) {
    final JScrollPane scroll = new JScrollPane(view);
    scroll.setBorder(BorderFactory.createEmptyBorder());
    scroll.setOpaque(false);
    scroll.getViewport().setOpaque(false);
    scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    scroll.getVerticalScrollBar().setUnitIncrement(24);
    return scroll;
  }

  /**
   * Shows the page anew when it opens. With a session kept, it asks the forum whose it is, as a
   * session runs out; then reads the games.
   */
  void refresh() {
    final Forum asked = forum;
    // a game of the forum shown before is not this forum's
    showNoThread();
    final Optional<String> session = sessionOf(asked);
    if (session.isEmpty()) {
      showSignedOut(SIGN_IN_HINT);
      return;
    }
    // the bar shows the games' place while the forum tells whose the session is
    showSignedIn(null);
    loaded = List.of();
    threadsModel.clear();
    final int request = ++threadsRequest;
    reading = true;
    readError = null;
    showListMessage("Signing in...");
    final NodeBbGameThreads reader = newReader();
    inBackground(
        reader::signedInPlayer,
        found -> {
          if (request == threadsRequest && asked.equals(forum)) {
            reading = false;
            found.ifPresentOrElse(
                who -> {
                  showSignedIn(who);
                  showView(view);
                },
                () -> {
                  forgetSession(asked);
                  showSignedOut("Your sign-in ran out. Sign in again.");
                });
          }
        },
        error -> {
          if (request == threadsRequest) {
            reading = false;
            readError = "The forum could not be reached: " + error + ". Choose a tab to retry.";
            showListMessage(readError);
          }
        });
  }

  private void forgetSession(final Forum signedOutOf) {
    signedOutOf.session().setValueAndFlush(null);
    sessions.put(signedOutOf, Optional.empty());
  }

  /** Signed in as the player, or with the player not yet known while the forum is asked. */
  private void showSignedIn(final @Nullable Player who) {
    signedIn = who;
    player.setText(who == null ? "" : who.username());
    mine.setVisible(true);
    browse.setVisible(true);
    browse.setEnabled(forum.playByForumCategory() > 0);
    player.setVisible(true);
    signOut.setVisible(true);
    newGame.setVisible(true);
    pages.show(content, GAMES);
    revalidate();
    repaint();
  }

  private void showSignedOut(final String status) {
    // an answer still on its way belongs to the player who signed out
    threadsRequest++;
    reading = false;
    readError = null;
    signedIn = null;
    loaded = List.of();
    ownGames = Set.of();
    threadsModel.clear();
    mine.setVisible(false);
    browse.setVisible(false);
    player.setVisible(false);
    signOut.setVisible(false);
    // signed out the card's one button is all; a new game is posted with the sign-in
    newGame.setVisible(false);
    signIn.setEnabled(!signInOpen);
    signInStatus.setText(signInOpen ? "Sign in in the window that is open." : status);
    pages.show(content, SIGNED_OUT);
    revalidate();
    repaint();
  }

  /** Opens the forum's login page; once the player signed in there, keeps the session. */
  private void signIn() {
    final Forum chosen = forum;
    signInOpen = true;
    signIn.setEnabled(false);
    signInStatus.setText("Sign in in the window that opens.");
    final Consumer<String> failed =
        error -> {
          signInOpen = false;
          signIn.setEnabled(true);
          signInStatus.setText(error);
        };
    try {
      signInPage.open(
          this,
          chosen.url(),
          done -> {
            signInOpen = false;
            keep(chosen, done);
          },
          failed);
    } catch (final RuntimeException | LinkageError e) {
      // eg the browser's classes missing from an install: else the button stayed off
      log.info("The forum's login page could not open", e);
      failed.accept("The forum's login page could not open: " + e.getMessage());
    }
  }

  /** Keeps the session in the forum's setting, encrypted; the forum told whose it is. */
  private void keep(final Forum signedInto, final SignedIn done) {
    keepSession(signedInto, done.session());
    if (signedInto.equals(forum)) {
      view = View.MINE;
      showSignedIn(done.player());
      showView(view);
    } else if (sessionOf(forum).isEmpty()) {
      // the player chose another forum meanwhile: its card said to use the open window
      showSignedOut(SIGN_IN_HINT);
    }
  }

  private void keepSession(final Forum signedInto, final String session) {
    signedInto.session().setValueAndFlush(session.toCharArray());
    sessions.put(signedInto, Optional.of(session));
  }

  /**
   * Forgets the session on this computer. The forum ends it itself when it runs out or the player
   * signs out in a browser.
   */
  private void signOut() {
    forgetSession(forum);
    showSignedOut("Signed out. " + SIGN_IN_HINT);
  }

  /** Shows the tab's games; while the player is not known, eg the forum was not reached, asks. */
  private void showView(final View shown) {
    view = shown;
    mine.setSelected(shown == View.MINE);
    browse.setSelected(shown == View.BROWSE);
    // whose move it is the forum tells only of the player's own games
    turnFilters.setVisible(shown == View.MINE);
    allGames.setSelected(true);
    showNoThread();
    if (signedIn == null) {
      refresh();
    } else {
      loadThreads(signedIn);
    }
  }

  /** Reads with the browser session if there is one, else as a guest. */
  private NodeBbGameThreads newReader() {
    return sessionOf(forum)
        .map(
            session ->
                NodeBbGameThreads.withSession(forum.url(), forum.playByForumCategory(), session))
        .orElseGet(() -> NodeBbGameThreads.asGuest(forum.url(), forum.playByForumCategory()));
  }

  private void loadThreads(final Player who) {
    final int request = ++threadsRequest;
    final View shown = view;
    loaded = List.of();
    threadsModel.clear();
    reading = true;
    readError = null;
    showListMessage("Reading the games...");
    // made here: the forum and its session are read on the EDT, where the player changes them
    final NodeBbGameThreads reader = newReader();
    inBackground(
        () -> shown == View.MINE ? reader.threadsOf(who) : reader.browse(),
        found -> {
          if (request == threadsRequest) {
            reading = false;
            loaded = found;
            if (shown == View.MINE) {
              ownGames =
                  found.stream()
                      .filter(game -> !game.onlyWatched())
                      .map(GameThread::topicId)
                      .collect(Collectors.toSet());
            }
            showFound();
          }
        },
        error -> {
          if (request == threadsRequest) {
            reading = false;
            readError = "The forum could not be read: " + error;
            showListMessage(readError);
          }
        });
  }

  private void showListMessage(final String text) {
    listMessage.setText(text);
    listMessage.setToolTipText(text);
    listPages.show(listArea, MESSAGE);
  }

  /** The game's side without a game: a quiet word in its middle. */
  private void showNoThread() {
    shownThread = null;
    postsRequest++;
    threadCard.removeAll();
    final JLabel hint =
        new JLabel(
            "Choose a game to read its posts and open its save games.",
            new LineIcon(Kind.FORUM, 32, LookAndFeel.getSecondaryTextColor()),
            SwingConstants.CENTER);
    hint.setHorizontalTextPosition(SwingConstants.CENTER);
    hint.setVerticalTextPosition(SwingConstants.BOTTOM);
    hint.setIconTextGap(MenuSpacing.M);
    hint.setForeground(LookAndFeel.getSecondaryTextColor());
    threadCard.add(hint, BorderLayout.CENTER);
    threadCard.revalidate();
    threadCard.repaint();
  }

  /**
   * Shows a page of the game, 0 for its newest one, where the last turn is. Another page of the
   * same game keeps the post being written.
   */
  private void showThread(final GameThread thread, final int page) {
    if (!thread.equals(shownThread)) {
      shownThread = thread;
      threadCard.removeAll();
      threadCard.add(newThreadHeading(thread), BorderLayout.NORTH);
      threadCard.add(postsArea, BorderLayout.CENTER);
      // an answer may go to any game, also to one the player watches only
      threadCard.add(newComposer(thread), BorderLayout.SOUTH);
    }
    final int request = ++postsRequest;
    final JTextArea loading = newSecondaryText();
    loading.setText("Reading the posts...");
    loading.setBorder(BorderFactory.createEmptyBorder(MenuSpacing.M, MenuSpacing.L, 0, 0));
    showInPostsArea(loading);
    final NodeBbGameThreads reader = newReader();
    inBackground(
        () -> page == 0 ? reader.newestPage(thread) : reader.page(thread, page),
        posts -> {
          if (request == postsRequest) {
            showWatched(posts.watched());
            final JScrollPane scroll = newPosts(thread, posts);
            showInPostsArea(scroll);
            // the newest post at the bottom is the one to show first: after the wrapped texts
            // laid out again with their width, which they learn in the first layout
            threadCard.validate();
            SwingUtilities.invokeLater(
                () ->
                    SwingUtilities.invokeLater(
                        () ->
                            scroll
                                .getVerticalScrollBar()
                                .setValue(scroll.getVerticalScrollBar().getMaximum())));
          }
        },
        error -> loading.setText("The posts could not be read: " + error));
  }

  private void showInPostsArea(final JComponent shown) {
    postsArea.removeAll();
    postsArea.add(shown, BorderLayout.CENTER);
    threadCard.revalidate();
    threadCard.repaint();
  }

  /**
   * The game's title, its last turn and how many posts it has; Join game in a game of others, Watch
   * and the way to the browser.
   */
  private JComponent newThreadHeading(final GameThread thread) {
    final JPanel heading = new JPanel(new BorderLayout(MenuSpacing.M, MenuSpacing.XS));
    heading.setOpaque(false);
    heading.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, LookAndFeel.getSeparatorColor()),
            BorderFactory.createEmptyBorder(
                MenuSpacing.M, MenuSpacing.L, MenuSpacing.M, MenuSpacing.L)));
    heading.add(NewsArea.newShrinkingLabel(thread.title(), Font.BOLD, 3), BorderLayout.NORTH);
    final JLabel details =
        NewsArea.newShrinkingLabel(
            // the turn comes from the newest post only, which may be a chat
            (thread.lastTurn().isEmpty() ? "" : "Last turn: " + thread.lastTurn() + "  ·  ")
                + thread.posts()
                + " posts",
            Font.PLAIN,
            0);
    details.setForeground(LookAndFeel.getSecondaryTextColor());
    heading.add(details, BorderLayout.CENTER);
    final JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, MenuSpacing.S, 0));
    actions.setOpaque(false);
    if (isOthersGame(thread)) {
      final JButton join = new JButton("Join game");
      join.setToolTipText(
          "Asks the game's players in its thread whether you can take over a nation");
      join.addActionListener(e -> askToJoin());
      actions.add(join);
    }
    watchButton = newWatchButton(thread);
    actions.add(watchButton);
    final JButton browser = new JButton("Open in browser  ›");
    LookAndFeel.setBorderlessButtonStyle(browser);
    final String page = newReader().browserPage(thread).toString();
    browser.addActionListener(e -> OpenFileUtility.openUrl(this, page));
    actions.add(browser);
    heading.add(actions, BorderLayout.EAST);
    return heading;
  }

  /**
   * A game of others: one the player watches without playing, or one of the forum's games that is
   * not among the player's own.
   */
  private boolean isOthersGame(final GameThread game) {
    return game.onlyWatched() || (view == View.BROWSE && !ownGames.contains(game.topicId()));
  }

  /** Fills the answer box with a request to join the game, for the player to finish and send. */
  private void askToJoin() {
    if (message.getText().isBlank()) {
      message.setText(JOIN_REQUEST);
    }
    message.setCaretPosition(message.getText().length());
    message.requestFocusInWindow();
  }

  /** Watch, off until the forum told whether the player follows the game. */
  private JToggleButton newWatchButton(final GameThread thread) {
    final JToggleButton watch = new JToggleButton(WATCH);
    watch.putClientProperty("JButton.buttonType", "roundRect");
    watch.setFocusPainted(false);
    watch.setEnabled(false);
    watch.setToolTipText(WATCH_TIP);
    watch.addActionListener(e -> changeWatched(thread, watch));
    return watch;
  }

  /** Whether the player follows the game shown, as the forum told with its posts. */
  private void showWatched(final boolean watched) {
    if (watchButton != null && watchButton.getClientProperty(CHANGING) == null) {
      showWatched(watchButton, watched);
    }
  }

  private static void showWatched(final JToggleButton watch, final boolean watched) {
    watch.setSelected(watched);
    watch.setText(watched ? WATCHING : WATCH);
    watch.setToolTipText(WATCH_TIP);
    watch.setEnabled(true);
  }

  /** Follows the game in the forum, or no more, as the player just switched Watch. */
  private void changeWatched(final GameThread thread, final JToggleButton watch) {
    final boolean watched = watch.isSelected();
    watch.setEnabled(false);
    // while the forum answers, the posts read meanwhile do not switch it back
    watch.putClientProperty(CHANGING, Boolean.TRUE);
    final NodeBbGameThreads writer = newReader();
    inBackground(
        () -> {
          if (watched) {
            writer.watch(thread);
          } else {
            writer.unwatch(thread);
          }
          return watched;
        },
        done -> {
          watch.putClientProperty(CHANGING, null);
          showWatched(watch, done);
        },
        error -> {
          watch.putClientProperty(CHANGING, null);
          showWatched(watch, !watched);
          watch.setToolTipText("The forum did not take it: " + error);
        });
  }

  /** The page's posts, each a card with room between them, the other pages' way above. */
  private JScrollPane newPosts(final GameThread thread, final ThreadPage page) {
    final JPanel posts = new JPanel();
    posts.setLayout(new BoxLayout(posts, BoxLayout.Y_AXIS));
    posts.setOpaque(false);
    posts.setBorder(
        BorderFactory.createEmptyBorder(
            MenuSpacing.M, MenuSpacing.M, MenuSpacing.M, MenuSpacing.M));
    if (page.pageCount() > 1) {
      addLeftAligned(posts, newPageLinks(thread, page));
    }
    final Dimension gap = new Dimension(0, MenuSpacing.S);
    page.posts()
        .forEach(
            post -> {
              addLeftAligned(posts, new Box.Filler(gap, gap, gap));
              addLeftAligned(posts, newPost(post));
            });
    // as wide as the view, so the texts wrap; the posts at the top, each as high as it needs
    final JPanel shown = new ScrollableJPanel();
    shown.setLayout(new BorderLayout());
    shown.setOpaque(false);
    shown.add(posts, BorderLayout.NORTH);
    return newTransparentScroll(shown);
  }

  private JComponent newPageLinks(final GameThread thread, final ThreadPage page) {
    final JPanel pageLinks = new JPanel(new FlowLayout(FlowLayout.LEFT, MenuSpacing.S, 0));
    pageLinks.setOpaque(false);
    pageLinks.setBorder(BorderFactory.createEmptyBorder(MenuSpacing.S, MenuSpacing.M, 0, 0));
    if (page.page() > 1) {
      final JButton earlier = new JButton("‹  Earlier posts");
      LookAndFeel.setBorderlessButtonStyle(earlier);
      earlier.addActionListener(e -> showThread(thread, page.page() - 1));
      pageLinks.add(earlier);
    }
    if (page.page() < page.pageCount()) {
      final JButton newer = new JButton("Newer posts  ›");
      LookAndFeel.setBorderlessButtonStyle(newer);
      newer.addActionListener(e -> showThread(thread, page.page() + 1));
      pageLinks.add(newer);
    }
    return pageLinks;
  }

  private static void addLeftAligned(final JPanel panel, final JComponent component) {
    component.setAlignmentX(Component.LEFT_ALIGNMENT);
    panel.add(component);
  }

  /**
   * A post as a card: who wrote it, and when, above its text as the forum formats it and a button
   * for each save game in it; the player's own posts carry a bar in the highlight color. A long
   * text, eg a turn summary with its whole history, shows its first lines until opened.
   */
  private JComponent newPost(final ThreadPost post) {
    // by the slug: the name shown may be any the author chose
    final boolean own =
        signedIn != null
            && !post.authorSlug().isEmpty()
            && post.authorSlug().equals(signedIn.userslug());
    final JPanel row = new PostCard(own);
    row.setBorder(
        BorderFactory.createEmptyBorder(
            MenuSpacing.M, MenuSpacing.L, MenuSpacing.M, MenuSpacing.L));
    final JPanel byline = new JPanel(new BorderLayout(MenuSpacing.S, 0));
    byline.setOpaque(false);
    // the forum's colors: each author's avatar as there
    byline.add(new Avatar(post), BorderLayout.WEST);
    byline.add(NewsArea.newShrinkingLabel(post.author(), Font.BOLD, 0), BorderLayout.CENTER);
    final JLabel time = new JLabel(TIME.format(post.time()));
    time.setForeground(LookAndFeel.getSecondaryTextColor());
    byline.add(time, BorderLayout.EAST);
    row.add(byline, BorderLayout.NORTH);
    final ForumPost text = new ForumPost(post.html(), forumAddress());
    final JPanel body = new JPanel(new BorderLayout(0, MenuSpacing.XS));
    body.setOpaque(false);
    if (post.text().lines().count() > SHORT_LINES) {
      final FoldedPost folded =
          new FoldedPost(text, SHORT_LINES * getFontMetrics(text.getFont()).getHeight());
      body.add(folded, BorderLayout.CENTER);
      final JButton all = new JButton("Show all");
      LookAndFeel.setBorderlessButtonStyle(all);
      final JPanel allRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
      allRow.setOpaque(false);
      allRow.add(all);
      all.addActionListener(
          e -> {
            folded.open();
            body.remove(allRow);
            body.revalidate();
            body.repaint();
          });
      body.add(allRow, BorderLayout.SOUTH);
    } else {
      body.add(text, BorderLayout.CENTER);
    }
    row.add(body, BorderLayout.CENTER);
    if (!post.saveGames().isEmpty()) {
      final JPanel saves = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, MenuSpacing.XS));
      saves.setOpaque(false);
      post.saveGames().forEach(saveGame -> saves.add(newOpenButton(saveGame)));
      row.add(saves, BorderLayout.SOUTH);
    }
    return row;
  }

  /** The forum's address, which the links and pictures of its posts are relative to. */
  private URI forumAddress() {
    return URI.create(forum.url().endsWith("/") ? forum.url() : forum.url() + "/");
  }

  /**
   * A save game's button: in the player's own games it opens the save to play on; in the games of
   * others to look at it, without playing or posting.
   */
  private JButton newOpenButton(final SaveGame saveGame) {
    final boolean viewOnly = shownThread != null && isOthersGame(shownThread);
    final String label =
        (viewOnly ? "View  ·  " : "Open save game  ·  ")
            + (saveGame.turn().isEmpty() ? saveGame.fileName() : saveGame.turn());
    final Consumer<Path> opening = viewOnly ? model::viewSavedGame : model::continueSavedGame;
    final JButton open = new JButton(label);
    LookAndFeel.setPrimaryButtonStyle(open);
    // a tool tip of its own, which would read a name starting with "<html>" as HTML
    open.setToolTipText(
        (viewOnly
                ? "Look at the map, the units and the history; nothing is played or posted. "
                : "")
            + "File: "
            + saveGame.fileName());
    open.addActionListener(
        e -> {
          open.setEnabled(false);
          open.setText("Downloading...");
          final NodeBbGameThreads reader = newReader();
          final Path folder = ClientSetting.saveGamesFolderPath.getValueOrThrow().resolve("forum");
          inBackground(
              () -> reader.download(saveGame, folder),
              file -> {
                open.setEnabled(true);
                open.setText(label);
                // not when the player went on meanwhile, eg into the lobby, which it would end
                if (open.isShowing()) {
                  opening.accept(file);
                }
              },
              error -> {
                open.setEnabled(true);
                open.setText("Download failed: " + error);
              });
        });
    return open;
  }

  /** The next post: a message, a save game or both; Send posts it to the game's thread. */
  private JComponent newComposer(final GameThread thread) {
    final JPanel composer = new JPanel(new BorderLayout(0, MenuSpacing.S));
    composer.setOpaque(false);
    composer.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, LookAndFeel.getSeparatorColor()),
            BorderFactory.createEmptyBorder(
                MenuSpacing.M, MenuSpacing.L, MenuSpacing.M, MenuSpacing.L)));
    message.setText("");
    message.setLineWrap(true);
    message.setWrapStyleWord(true);
    message.putClientProperty(
        "JTextField.placeholderText", "Write an answer; the forum formats it as Markdown");
    composer.add(newFormatBar(), BorderLayout.NORTH);
    composer.add(new JScrollPane(message), BorderLayout.CENTER);
    final JPanel actions = new JPanel(new BorderLayout(MenuSpacing.S, 0));
    actions.setOpaque(false);
    final JButton attach = new JButton("Attach save game...");
    LookAndFeel.setBorderlessButtonStyle(attach);
    attach.addActionListener(e -> chooseSave());
    attachedSave = null;
    attachment.setText("");
    attachment.setForeground(LookAndFeel.getSecondaryTextColor());
    final JPanel attaching = new JPanel(new FlowLayout(FlowLayout.LEFT, MenuSpacing.S, 0));
    attaching.setOpaque(false);
    attaching.add(attach);
    attaching.add(attachment);
    actions.add(attaching, BorderLayout.CENTER);
    // one post at a time: a second Send meanwhile, eg after going to another game and back, would
    // post twice
    sendButton.setEnabled(sendingTo == null);
    actions.add(sendButton, BorderLayout.EAST);
    sendStatus.setText(isSameGame(thread, sendingTo) ? "Sending..." : "");
    actions.add(sendStatus, BorderLayout.SOUTH);
    composer.add(actions, BorderLayout.SOUTH);
    return composer;
  }

  /**
   * Bold, italic, a quote, a list and a link, written into the answer as the forum reads them, and
   * a picture, uploaded and linked as the forum's editor does; letters and icons in the text color,
   * large enough to read at a glance.
   */
  private JComponent newFormatBar() {
    final JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
    bar.setOpaque(false);
    final JButton bold = newFormatButton("Bold", () -> wrapSelection("**", "**"));
    final float size = bold.getFont().getSize2D();
    bold.setText("B");
    bold.setFont(bold.getFont().deriveFont(Font.BOLD, size + 3));
    bar.add(bold);
    final JButton italic = newFormatButton("Italic", () -> wrapSelection("*", "*"));
    italic.setText("I");
    // a serif "I" leans so it reads as italic, unlike a sans serif bar
    italic.setFont(new Font(Font.SERIF, Font.BOLD | Font.ITALIC, Math.round(size + 4)));
    bar.add(italic);
    bar.add(newIconButton("Quote", Kind.QUOTE, () -> startLines("> ")));
    bar.add(newIconButton("List", Kind.LIST, () -> startLines("- ")));
    bar.add(newIconButton("Link", Kind.LINK, () -> wrapSelection("[", "](https://)")));
    bar.add(newIconButton("Picture", Kind.PICTURE, this::choosePicture));
    return bar;
  }

  /** A button of the bar: flat until the mouse is over it, as in a tool bar. */
  private JButton newFormatButton(final String what, final Runnable format) {
    final JButton button = new JButton();
    button.putClientProperty("JButton.buttonType", "toolBarButton");
    button.setFocusPainted(false);
    button.setMargin(new Insets(MenuSpacing.XS, MenuSpacing.S, MenuSpacing.XS, MenuSpacing.S));
    button.setToolTipText(what);
    button.getAccessibleContext().setAccessibleName(what);
    button.addActionListener(
        e -> {
          format.run();
          message.requestFocusInWindow();
        });
    return button;
  }

  private JButton newIconButton(final String what, final Kind kind, final Runnable format) {
    final JButton button = newFormatButton(what, format);
    final Color text = UIManager.getColor("Label.foreground");
    button.setIcon(new LineIcon(kind, FORMAT_ICON, text == null ? Color.GRAY : text));
    return button;
  }

  /** Lets the player choose a picture on this computer to attach. */
  private void choosePicture() {
    final JFileChooser chooser = new JFileChooser();
    chooser.setFileFilter(
        new FileNameExtensionFilter("Pictures", "png", "jpg", "jpeg", "gif", "webp"));
    if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
      attachPicture(chooser.getSelectedFile().toPath());
    }
  }

  /**
   * Uploads the picture to the forum, then links it at the caret, as the forum's editor does: the
   * post shows it once sent.
   */
  @VisibleForTesting
  void attachPicture(final Path picture) {
    final GameThread thread = shownThread;
    if (thread == null) {
      return;
    }
    sendStatus.setText("Uploading " + picture.getFileName() + "...");
    final NodeBbGameThreads writer = newReader();
    inBackground(
        () -> writer.uploadPicture(picture),
        address -> {
          if (isSameGame(thread, shownThread)) {
            message.insert(
                "![" + pictureName(picture) + "](" + address + ")", message.getCaretPosition());
            sendStatus.setText("");
          } else {
            // the answer box is another game's now
            sendStatus.setText(
                "The picture went up while another game was shown: attach it again.");
          }
        },
        error -> sendStatus.setText("The picture was not uploaded: " + error));
  }

  /** The file's name without its type, as the picture's text where it is not shown. */
  private static String pictureName(final Path picture) {
    final String name = String.valueOf(picture.getFileName());
    final int dot = name.lastIndexOf('.');
    return (dot > 0 ? name.substring(0, dot) : name).replaceAll("[\\[\\]]", " ");
  }

  /** Puts the marks around the text chosen; with none chosen, the caret between them. */
  private void wrapSelection(final String before, final String after) {
    final int start = message.getSelectionStart();
    final String chosen = Optional.ofNullable(message.getSelectedText()).orElse("");
    message.replaceSelection(before + chosen + after);
    if (chosen.isEmpty()) {
      message.setCaretPosition(start + before.length());
    }
  }

  /** Starts each line chosen, or the caret's line, with the mark, eg "> " for a quote. */
  private void startLines(final String mark) {
    final String text = message.getText();
    // a negative start finds no line break: the first line
    final int from = text.lastIndexOf('\n', message.getSelectionStart() - 1) + 1;
    // a selection that ends with a line break ends on that line, not the next
    final int end = message.getSelectionEnd();
    final boolean endsWithBreak = end > message.getSelectionStart() && text.charAt(end - 1) == '\n';
    final int lineEnd = text.indexOf('\n', endsWithBreak ? end - 1 : end);
    final int to = lineEnd < 0 ? text.length() : lineEnd;
    final String marked = mark + text.substring(from, to).replace("\n", "\n" + mark);
    message.replaceRange(marked, from, to);
    message.setCaretPosition(from + marked.length());
  }

  private void chooseSave() {
    final JFileChooser chooser =
        new JFileChooser(ClientSetting.saveGamesFolderPath.getValueOrThrow().toFile());
    chooser.setFileFilter(new FileNameExtensionFilter("TripleA save games", "tsvg"));
    if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
      attachedSave = chooser.getSelectedFile().toPath();
      attachment.setText(String.valueOf(attachedSave.getFileName()));
    }
  }

  /**
   * Posts the message and the attached save game to the game shown; the game then shows its newest
   * posts. The answer is told also when the player went to another game meanwhile.
   */
  private void send() {
    final GameThread thread = shownThread;
    final String text = message.getText().strip();
    final Path save = attachedSave;
    if (thread == null || (text.isEmpty() && save == null)) {
      sendStatus.setText("Write a message or attach a save game.");
      return;
    }
    sendingTo = thread;
    sendButton.setEnabled(false);
    sendStatus.setText("Sending...");
    final NodeBbGameThreads reader = newReader();
    inBackground(
        () -> save == null ? reader.reply(thread, text) : reader.reply(thread, text, save),
        heldBack -> {
          sendingTo = null;
          sendButton.setEnabled(true);
          final boolean shown = isSameGame(thread, shownThread);
          sendStatus.setText(
              "Sent"
                  + (shown ? "" : " to \"" + thread.title() + "\"")
                  + (heldBack ? ". The forum shows it once a moderator approved it." : "."));
          // what the player wrote meanwhile stays
          if (message.getText().strip().equals(text)) {
            message.setText("");
          }
          if (Objects.equals(attachedSave, save)) {
            attachedSave = null;
            attachment.setText("");
          }
          if (shown) {
            showThread(shownThread, 0);
          }
        },
        error -> {
          sendingTo = null;
          sendButton.setEnabled(true);
          final boolean shown = isSameGame(thread, shownThread);
          sendStatus.setText(
              "Not sent" + (shown ? "" : " to \"" + thread.title() + "\"") + ": " + error);
          // the game's composer was made anew meanwhile: what was not sent comes back to it
          if (shown && message.getText().isBlank() && attachedSave == null) {
            message.setText(text);
            attachedSave = save;
            attachment.setText(save == null ? "" : String.valueOf(save.getFileName()));
          }
        });
  }

  /** The same game, also when the list was read again since and the game has newer posts. */
  private static boolean isSameGame(final GameThread game, final @Nullable GameThread other) {
    return other != null && game.topicId() == other.topicId();
  }

  @VisibleForTesting
  void showBrowse() {
    showView(View.BROWSE);
  }

  /** Runs the task off the EDT, then hands its result or the reason it failed to the EDT. */
  private static <T> void inBackground(
      final Callable<T> task, final Consumer<T> onDone, final Consumer<String> onError) {
    CompletableFuture.supplyAsync(
            () -> {
              try {
                return task.call();
              } catch (final Exception e) {
                throw new CompletionException(e);
              }
            },
            BACKGROUND)
        .whenComplete(
            (result, error) ->
                SwingUtilities.invokeLater(
                    () -> {
                      if (error == null) {
                        onDone.accept(result);
                      } else {
                        onError.accept(reasonOf(error));
                      }
                    }));
  }

  private static String reasonOf(final Throwable error) {
    final Throwable cause =
        error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
    log.info("Play by forum: {}", cause.getMessage());
    return Optional.ofNullable(cause.getMessage()).orElse(cause.getClass().getSimpleName());
  }

  /**
   * A game in the list: its title, its last turn and when it was last answered, marked "YOUR TURN"
   * in the highlight color when another player posted last in the player's game, "WAITING" when the
   * player did, "WATCHING" when it is a game of others the player watches, "NEW" when it has posts
   * not read yet; a soft rounded fill and a bar in the highlight color when chosen.
   */
  private static final class ThreadCell extends JPanel {
    @Serial private static final long serialVersionUID = -3150741935406620573L;

    private final boolean chosen;

    private ThreadCell(final GameThread thread, final boolean chosen, final Status status) {
      super(new BorderLayout(MenuSpacing.M, 2));
      this.chosen = chosen;
      setOpaque(false);
      setBorder(
          BorderFactory.createEmptyBorder(
              MenuSpacing.S, MenuSpacing.M, MenuSpacing.S, MenuSpacing.S));
      final JPanel titleRow = new JPanel(new BorderLayout(MenuSpacing.S, 0));
      titleRow.setOpaque(false);
      // shrinking labels: a long title ends in "…" instead of widening the list
      titleRow.add(NewsArea.newShrinkingLabel(thread.title(), Font.BOLD, 0), BorderLayout.CENTER);
      final JPanel marks = new JPanel(new FlowLayout(FlowLayout.RIGHT, MenuSpacing.XS, 0));
      marks.setOpaque(false);
      if (thread.unread()) {
        marks.add(Pill.newAccented("NEW"));
      }
      switch (status) {
        case YOUR_TURN -> marks.add(Pill.newAccented("YOUR TURN"));
        case WAITING -> marks.add(Pill.newQuiet("WAITING"));
        case WATCHING -> marks.add(Pill.newQuiet("WATCHING"));
        default -> {
          // the forum does not tell, eg of the games of others
        }
      }
      titleRow.add(marks, BorderLayout.EAST);
      add(titleRow, BorderLayout.NORTH);
      final JLabel details =
          NewsArea.newShrinkingLabel(
              (thread.lastTurn().isEmpty() ? "Last post" : thread.lastTurn())
                  + "  ·  "
                  + TIME.format(thread.lastPost()),
              Font.PLAIN,
              0);
      details.setForeground(LookAndFeel.getSecondaryTextColor());
      add(details, BorderLayout.CENTER);
    }

    @Override
    protected void paintComponent(final Graphics g) {
      if (chosen) {
        final Color fill = UIManager.getColor("TextField.background");
        paintSoftCard(g, this, fill == null ? getBackground() : fill, true);
      }
      super.paintComponent(g);
    }
  }

  /** A long post's first lines until opened: the post goes on below them, cut off. */
  private static final class FoldedPost extends JPanel {
    @Serial private static final long serialVersionUID = 6915541932185412378L;

    private final int foldedHeight;
    private boolean open;

    private FoldedPost(final JComponent post, final int foldedHeight) {
      super(null);
      this.foldedHeight = foldedHeight;
      setOpaque(false);
      add(post);
    }

    private void open() {
      open = true;
      revalidate();
      repaint();
    }

    @Override
    public Dimension getPreferredSize() {
      final int height = getComponent(0).getPreferredSize().height;
      return new Dimension(0, open ? height : Math.min(height, foldedHeight));
    }

    @Override
    public void doLayout() {
      final Component post = getComponent(0);
      // its width first: the post wraps at it, then is as high as it needs, the rest cut off
      post.setSize(getWidth(), Math.max(1, post.getHeight()));
      post.setBounds(0, 0, getWidth(), post.getPreferredSize().height);
    }
  }

  /** A post as a card of its own, a little lighter than the game's card around it. */
  private static final class PostCard extends JPanel {
    @Serial private static final long serialVersionUID = 4013517431566154381L;

    /** The player's own post, marked with the bar. */
    private final boolean own;

    private PostCard(final boolean own) {
      super(new BorderLayout(0, MenuSpacing.S));
      this.own = own;
      setOpaque(false);
    }

    @Override
    protected void paintComponent(final Graphics g) {
      final Color text = UIManager.getColor("Label.foreground");
      paintSoftCard(g, this, MenuColors.withAlpha(text == null ? Color.GRAY : text, 12), own);
      super.paintComponent(g);
    }
  }

  /** A soft rounded fill; with a bar in the highlight color on its left when marked. */
  private static void paintSoftCard(
      final Graphics g, final JComponent card, final Color fill, final boolean marked) {
    final Graphics2D g2 = (Graphics2D) g.create();
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g2.setColor(fill);
    g2.fillRoundRect(0, 0, card.getWidth(), card.getHeight(), ARC, ARC);
    if (marked) {
      g2.setColor(highlightColor());
      g2.fillRoundRect(0, MenuSpacing.S, BAR, card.getHeight() - 2 * MenuSpacing.S, BAR, BAR);
    }
    g2.dispose();
  }

  /** The one color of the TripleA themes, eg of Multiplayer in the main menu. */
  private static Color highlightColor() {
    final Color highlight = UIManager.getColor("TripleA.highlightBackground");
    return highlight == null ? LookAndFeel.getAccentColor() : highlight;
  }

  /**
   * The author's avatar as the forum draws one without a picture: the letter on the forum's color
   * for the author, else on a color of the author's name.
   */
  private static final class Avatar extends JComponent {
    @Serial private static final long serialVersionUID = -6080311412434745806L;

    private static final int SIZE = 28;

    private final String letter;
    private final Color color;

    private Avatar(final ThreadPost post) {
      final String text = post.avatarText().isEmpty() ? post.author() : post.avatarText();
      letter = text.isEmpty() ? "?" : text.substring(0, 1).toUpperCase(Locale.ROOT);
      color = colorOf(post);
      setPreferredSize(new Dimension(SIZE, SIZE));
      setMinimumSize(getPreferredSize());
      setMaximumSize(getPreferredSize());
      setFont(UIManager.getFont("Label.font"));
    }

    private static Color colorOf(final ThreadPost post) {
      try {
        return Color.decode(post.avatarColor());
      } catch (final NumberFormatException expected) {
        // eg no color told: one of the name, the same for each of the author's posts
        return Color.getHSBColor((post.author().hashCode() & 0xFF) / 255f, 0.55f, 0.75f);
      }
    }

    @Override
    protected void paintComponent(final Graphics g) {
      final Graphics2D g2 = (Graphics2D) g.create();
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2.setRenderingHint(
          RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
      g2.setColor(color);
      g2.fillOval(0, 0, SIZE, SIZE);
      // dark or light text, whichever reads on the color
      final double luminance =
          0.299 * color.getRed() + 0.587 * color.getGreen() + 0.114 * color.getBlue();
      g2.setColor(luminance > 160 ? Color.BLACK : Color.WHITE);
      g2.setFont(getFont().deriveFont(Font.BOLD, SIZE * 0.5f));
      final var metrics = g2.getFontMetrics();
      g2.drawString(
          letter,
          (SIZE - metrics.stringWidth(letter)) / 2,
          (SIZE - metrics.getHeight()) / 2 + metrics.getAscent());
      g2.dispose();
    }
  }
}
