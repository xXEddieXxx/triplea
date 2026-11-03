package games.strategy.engine.posted.game.pbf;

import com.google.common.annotations.VisibleForTesting;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import games.strategy.engine.framework.system.HttpProxy;
import games.strategy.engine.posted.game.pbf.NodeBbHttpClients.Builder;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serial;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import lombok.AccessLevel;
import lombok.With;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.StringEscapeUtils;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;

/**
 * Play-by-forum games of a NodeBB forum: the game threads the player started or posted in, the
 * newest games of the Play by Forum category, the posts of a thread page by page, and the save
 * games attached to them; and the writes of the player: an answer in a thread ({@link #reply}), a
 * picture for one ({@link #uploadPicture}), a new game thread ({@link #startThread}) and following
 * a game ({@link #watch}). Requests go to the forum alone and follow no redirects; with the
 * player's browser session the forum also shows what its guests cannot see, as the Play by Forum
 * category of forums.triplea-game.org. Blocks: call it off the EDT.
 */
@Slf4j
public final class NodeBbGameThreads {
  /** The "Play By Forum" category of forums.triplea-game.org, see {@code HelpTexts}. */
  public static final int TRIPLEA_PLAY_BY_FORUM_CATEGORY = 45;

  /** The player's posts are read up to this many pages back (20 each) to find their games. */
  private static final int POST_PAGES = 3;

  /** NodeBB takes a write with a session only with the session's token in this header. */
  private static final String CSRF_HEADER = "x-csrf-token";

  private static final int CONNECT_TIMEOUT_MILLIS = 10_000;
  private static final int READ_TIMEOUT_MILLIS = 30_000;

  /** The no-break space of "&amp;nbsp;", read as a plain space. */
  private static final char NO_BREAK_SPACE = (char) 0xA0;

  private static final Pattern SAVE_GAME_LINK =
      Pattern.compile("href=\"([^\"]+?\\.tsvg)\"", Pattern.CASE_INSENSITIVE);
  private static final Pattern TURN =
      Pattern.compile(
          "TripleA (?:Turn Summary|Move Summary|Manual Gamesave Post): (.+?) round (\\d+)");
  private static final Pattern PLAY_BY_FORUM_NAME =
      Pattern.compile("play\\W*by\\W*forum|\\bpbf\\b", Pattern.CASE_INSENSITIVE);

  private final URI forum;
  private final int playByForumCategory;

  /** How the requests sign in: as a guest or with the player's browser session. */
  private final UnaryOperator<Builder> login;

  /**
   * The player the forum signs a request in as: the name as shown, eg "O'Neil", and the name in the
   * forum's addresses, eg "oneil".
   */
  public record Player(String username, String userslug) {}

  /**
   * A game thread: its topic and its address in the forum (eg "101/global-1940", empty when
   * unknown), its last post and the last turn posted in it, eg "Germans, round 5", or an empty text
   * when none was found; the slug of who wrote the last post (empty when unknown), whether the
   * signed-in player has posts in it not read yet, and whether the player only watches it: a game
   * of others the player follows in the forum.
   */
  public record GameThread(
      int topicId,
      String slug,
      String title,
      Instant lastPost,
      int posts,
      String lastTurn,
      String lastPosterSlug,
      boolean unread,
      @With(AccessLevel.PRIVATE) boolean onlyWatched) {

    /** A thread known by its address only, eg to post to. */
    public static GameThread of(final int topicId, final String slug, final String title) {
      return new GameThread(topicId, slug, title, Instant.EPOCH, 0, "", "", false, false);
    }

    /**
     * Whether the player is likely to move: the last post is another player's, eg the turn before.
     * A guess the forum allows: it does not know the game's order of play. Never in a game the
     * player only watches.
     */
    public boolean waitsFor(final Player player) {
      return !onlyWatched && !lastPosterSlug.isEmpty() && !lastPosterSlug.equals(player.userslug());
    }
  }

  /** A save game attached to a post, with the turn the post names, or an empty text. */
  public record SaveGame(URI url, String fileName, String turn) {}

  /**
   * A post of a thread as plain text and as the forum's HTML, with the save games attached to it;
   * by its author's name as shown and the author's slug, as {@link Player#userslug} unique, and the
   * author's avatar as the forum draws it without a picture: a letter on a color, eg "L" on
   * "#ffc107" (empty when none).
   */
  public record ThreadPost(
      String author,
      String authorSlug,
      String avatarText,
      String avatarColor,
      Instant time,
      String text,
      String html,
      List<SaveGame> saveGames) {}

  /**
   * A page of a thread's posts, oldest first; pages count from 1. Whether the signed-in player
   * follows the thread, so the forum tells of its new posts.
   */
  public record ThreadPage(List<ThreadPost> posts, int page, int pageCount, boolean watched) {}

  private NodeBbGameThreads(
      final String forumUrl, final int playByForumCategory, final UnaryOperator<Builder> login) {
    this.forum = URI.create(forumUrl.endsWith("/") ? forumUrl : forumUrl + "/");
    this.playByForumCategory = playByForumCategory;
    this.login = login;
  }

  /**
   * Reads as a guest, who sees no hidden categories.
   *
   * @param forumUrl the forum, eg "https://forums.triplea-game.org/"
   * @param playByForumCategory the forum's Play by Forum category, eg {@link
   *     #TRIPLEA_PLAY_BY_FORUM_CATEGORY}; 0 when none is known
   */
  public static NodeBbGameThreads asGuest(final String forumUrl, final int playByForumCategory) {
    return new NodeBbGameThreads(forumUrl, playByForumCategory, UnaryOperator.identity());
  }

  /**
   * Reads as the player signed in to the forum in the browser, with the value of the browser's
   * "express.sid" cookie; it lasts as long as that browser session.
   */
  public static NodeBbGameThreads withSession(
      final String forumUrl, final int playByForumCategory, final String session) {
    return new NodeBbGameThreads(
        forumUrl, playByForumCategory, builder -> builder.sessionCookie(session));
  }

  /**
   * The player the forum signs these requests in as; none for a guest, eg when the browser session
   * ran out: the forum's config names the player's number, 0 for a guest, the player's page the
   * name. Fails only when the forum cannot be asked.
   */
  public Optional<Player> signedInPlayer() throws IOException {
    try (CloseableHttpClient client = newClient()) {
      final int userId = intOf(readObject(client, "api/config"), "uid");
      if (userId <= 0) {
        return Optional.empty();
      }
      final JsonObject user = readObject(client, "api/user/uid/" + userId);
      // the forum sends names HTML-escaped, eg "O&#x27;Neil"
      final String username = StringEscapeUtils.unescapeHtml4(stringOf(user, "username"));
      final String userslug = stringOf(user, "userslug");
      return username.isEmpty() || userslug.isEmpty()
          ? Optional.empty()
          : Optional.of(new Player(username, userslug));
    }
  }

  /**
   * The games of the Play by Forum category, the last answered first: its newest page, without the
   * pinned topics, which are rules and notes. None for a forum without a known category.
   */
  public List<GameThread> browse() throws IOException {
    if (playByForumCategory <= 0) {
      return List.of();
    }
    try (CloseableHttpClient client = newClient()) {
      // the order named: the forum orders by the signed-in player's own setting otherwise
      return categoryGames(
          readObject(client, "api/category/" + playByForumCategory + "?sort=recently_replied"));
    }
  }

  /** The games of a category page of the forum. */
  @VisibleForTesting
  static List<GameThread> categoryGames(final JsonObject category) {
    return objectsIn(category, "topics").stream()
        .filter(topic -> !isSet(topic, "deleted") && !isSet(topic, "pinned"))
        .map(NodeBbGameThreads::topicThread)
        .sorted(Comparator.comparing(GameThread::lastPost).reversed())
        .toList();
  }

  /** A topic as a game thread, with the turn and the author of its newest post, the teaser. */
  private static GameThread topicThread(final JsonObject topic) {
    final Optional<JsonObject> teaser = objectOf(topic, "teaser");
    return toThread(
        topic,
        0,
        turnOf(teaser.map(it -> stringOf(it, "content")).orElse("")),
        teaser
            .flatMap(it -> objectOf(it, "user"))
            .map(user -> stringOf(user, "userslug"))
            .orElse(""));
  }

  /** The game threads the player started or posted in, the last answered first. */
  public List<GameThread> threadsOf(final Player player) throws IOException {
    try (CloseableHttpClient client = newClient()) {
      final String userPath = "api/user/" + pathSegment(player.userslug());
      final JsonObject started = readObject(client, userPath + "/topics");
      final List<JsonObject> posted = new ArrayList<>();
      for (int page = 1; page <= POST_PAGES; page++) {
        final JsonObject posts = readObject(client, userPath + "/posts?page=" + page);
        posted.add(posts);
        if (objectsIn(posts, "posts").isEmpty()) {
          break;
        }
      }
      return gameThreads(started, watched(client, userPath), posted, forum, playByForumCategory);
    }
  }

  /**
   * The topics the player follows, as the forum follows those one posts in: with their newest post,
   * also of games the player did not start. None when the forum does not tell, eg to a guest.
   */
  private JsonObject watched(final CloseableHttpClient client, final String userPath) {
    try {
      return readObject(client, userPath + "/watched");
    } catch (final IOException e) {
      log.debug("The forum told no followed topics", e);
      return new JsonObject();
    }
  }

  private CloseableHttpClient newClient() {
    return login.apply(NodeBbHttpClients.builder()).build();
  }

  private JsonObject readObject(final CloseableHttpClient client, final String path)
      throws IOException {
    return answerOf(client, newGet(forum.resolve(path)), path);
  }

  private static JsonObject answerOf(
      final CloseableHttpClient client, final HttpRequestBase request, final String what)
      throws IOException {
    try (CloseableHttpResponse response = client.execute(request)) {
      checkOk(response);
      final String json = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
      try {
        return JsonParser.parseString(json).getAsJsonObject();
      } catch (final JsonParseException | IllegalStateException e) {
        throw new IOException("The forum's answer to " + what + " is no JSON object", e);
      }
    }
  }

  private static HttpGet newGet(final URI url) {
    return limited(HttpGet::new, url);
  }

  /**
   * A request by the method given, made to give up on a forum that does not answer and to follow no
   * redirect.
   */
  private static <T extends HttpRequestBase> T limited(
      final Function<URI, T> method, final URI url) {
    final T request = method.apply(url);
    request.setConfig(
        RequestConfig.custom()
            .setConnectTimeout(CONNECT_TIMEOUT_MILLIS)
            .setConnectionRequestTimeout(CONNECT_TIMEOUT_MILLIS)
            .setSocketTimeout(READ_TIMEOUT_MILLIS)
            .setRedirectsEnabled(false)
            .build());
    HttpProxy.addProxy(request);
    return request;
  }

  private static void checkOk(final CloseableHttpResponse response) throws IOException {
    final int status = response.getStatusLine().getStatusCode();
    if (status == 401 || status == 403) {
      // with the forum's reason: a reply may be refused for a locked thread, not the login
      throw new RefusedException(
          "The forum asks for a valid login (HTTP " + status + ")" + reasonOf(response));
    }
    if (status == 404) {
      throw new RefusedException("The forum has no such page (HTTP 404)");
    }
    // 202: a write the forum holds back for a moderator, as it does for new members
    if (status != 200 && status != 202) {
      final String answer = "The forum answered HTTP " + status + reasonOf(response);
      // only a 4xx says no for sure; eg a 5xx may come after the forum wrote
      throw status >= 400 && status < 500 ? new RefusedException(answer) : new IOException(answer);
    }
  }

  /**
   * The forum answered and said no, eg to a title too short or a login it does not take: unlike a
   * request without an answer, nothing was written.
   */
  public static final class RefusedException extends IOException {
    @Serial private static final long serialVersionUID = 6383914417618264721L;

    RefusedException(final String message) {
      super(message);
    }
  }

  /** The reason NodeBB gives for a refusal, eg a post too short, after a colon; else nothing. */
  private static String reasonOf(final CloseableHttpResponse response) {
    try {
      final String json = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
      return objectOf(JsonParser.parseString(json).getAsJsonObject(), "status")
          .map(status -> stringOf(status, "message"))
          .filter(message -> !message.isEmpty())
          .map(message -> ": " + message)
          .orElse("");
    } catch (final IOException
        | JsonParseException
        | IllegalStateException
        // an answer without a body
        | IllegalArgumentException e) {
      return "";
    }
  }

  /** A name as one part of an address: a space as "%20", not as the "+" of forms. */
  private static String pathSegment(final String name) {
    return URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20");
  }

  /** The newest page of the thread, where the last turn is. */
  public ThreadPage newestPage(final GameThread thread) throws IOException {
    final ThreadPage first = page(thread, 1);
    return first.pageCount() > 1 ? page(thread, first.pageCount()) : first;
  }

  /** A page of the thread, from 1 to its page count. */
  public ThreadPage page(final GameThread thread, final int page) throws IOException {
    try (CloseableHttpClient client = newClient()) {
      // the order named, as the forum orders by the signed-in player's own setting otherwise
      return threadPage(
          readObject(
              client,
              "api/topic/" + topicPath(thread) + "?page=" + page + "&sort=oldest_to_newest"),
          forum);
    }
  }

  private static String topicPath(final GameThread thread) {
    return thread.slug().isEmpty() ? String.valueOf(thread.topicId()) : thread.slug();
  }

  /** The thread's page in the browser. */
  public URI browserPage(final GameThread thread) {
    return forum.resolve("topic/" + topicPath(thread));
  }

  /**
   * Downloads the save game into the folder and returns the file, named as in the forum; only a
   * file the forum keeps, so the player's session never goes elsewhere. A failed download leaves no
   * broken file.
   */
  public Path download(final SaveGame saveGame, final Path folder) throws IOException {
    if (!isOnForum(saveGame.url().normalize(), forum)) {
      throw new IOException("Not a file of the forum: " + saveGame.url());
    }
    Files.createDirectories(folder);
    final Path file = folder.resolve(safeFileName(saveGame.fileName()));
    final Path part = Files.createTempFile(folder, "download-", ".part");
    try (CloseableHttpClient client = newClient();
        CloseableHttpResponse response = client.execute(newGet(saveGame.url()))) {
      checkOk(response);
      try (InputStream content = response.getEntity().getContent()) {
        Files.copy(content, part, StandardCopyOption.REPLACE_EXISTING);
      }
      Files.move(part, file, StandardCopyOption.REPLACE_EXISTING);
    } finally {
      Files.deleteIfExists(part);
    }
    return file;
  }

  /** The file name without characters a file system may not take, eg slashes. */
  @VisibleForTesting
  static String safeFileName(final String fileName) {
    final String safe = fileName.replaceAll("[^A-Za-z0-9._-]", "_");
    return safe.toLowerCase(Locale.ROOT).endsWith(".tsvg") ? safe : safe + ".tsvg";
  }

  /**
   * Answers in the thread as the signed-in player, as the forum's page does: with the session and
   * the forum's CSRF token.
   *
   * @return whether the forum holds the post back until a moderator approved it, as it does for new
   *     members
   */
  public boolean reply(final GameThread thread, final String message) throws IOException {
    try (CloseableHttpClient client = newClient()) {
      return post(client, csrfToken(client), thread, message);
    }
  }

  /**
   * Answers with the save game uploaded to the forum and linked below the message, as TripleA's
   * turn posts link it.
   */
  public boolean reply(final GameThread thread, final String message, final Path saveGame)
      throws IOException {
    try (CloseableHttpClient client = newClient()) {
      final String csrfToken = csrfToken(client);
      final String link =
          "[Savegame]("
              + upload(client, csrfToken, saveGame, ContentType.APPLICATION_OCTET_STREAM)
              + ")";
      return post(client, csrfToken, thread, message.isBlank() ? link : message + "\n\n" + link);
    }
  }

  /** The token the forum asks for with every write, given with its config to the session. */
  private String csrfToken(final CloseableHttpClient client) throws IOException {
    return stringOf(readObject(client, "api/config"), "csrf_token");
  }

  private String upload(
      final CloseableHttpClient client,
      final String csrfToken,
      final Path file,
      final ContentType type)
      throws IOException {
    final HttpPost upload = limited(HttpPost::new, forum.resolve("api/post/upload"));
    upload.setHeader(CSRF_HEADER, csrfToken);
    upload.setEntity(
        MultipartEntityBuilder.create()
            .addBinaryBody("files[]", file.toFile(), type, String.valueOf(file.getFileName()))
            .build());
    try (CloseableHttpResponse response = client.execute(upload)) {
      checkOk(response);
      // the forum's answer as TripleA's turn posts read it, of newer and older forums alike
      return NodeBbForumPoster.parseSaveGameUrlFromJsonResponse(
          EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8));
    }
  }

  private boolean post(
      final CloseableHttpClient client,
      final String csrfToken,
      final GameThread thread,
      final String content)
      throws IOException {
    final HttpPost post =
        limited(HttpPost::new, forum.resolve("api/v3/topics/" + thread.topicId()));
    post.setHeader(CSRF_HEADER, csrfToken);
    final JsonObject body = new JsonObject();
    body.addProperty("content", content);
    post.setEntity(new StringEntity(body.toString(), ContentType.APPLICATION_JSON));
    return objectOf(answerOf(client, post, "the post"), "response")
        .filter(response -> isSet(response, "queued"))
        .isPresent();
  }

  /**
   * Starts a game thread in the forum's Play by Forum category as the signed-in player, the content
   * its first post; with the session and the forum's CSRF token, as {@link #reply}.
   *
   * @return the new thread; empty when the forum holds it back until a moderator approved it, as it
   *     does for new members
   * @throws IOException also when the forum's Play by Forum category is not known
   */
  public Optional<GameThread> startThread(final String title, final String content)
      throws IOException {
    if (playByForumCategory <= 0) {
      throw new IOException("The forum's Play by Forum category is not known");
    }
    try (CloseableHttpClient client = newClient()) {
      final HttpPost post = limited(HttpPost::new, forum.resolve("api/v3/topics"));
      post.setHeader(CSRF_HEADER, csrfToken(client));
      final JsonObject body = new JsonObject();
      body.addProperty("cid", playByForumCategory);
      body.addProperty("title", title);
      body.addProperty("content", content);
      post.setEntity(new StringEntity(body.toString(), ContentType.APPLICATION_JSON));
      return objectOf(answerOf(client, post, "the new thread"), "response")
          .filter(topic -> !isSet(topic, "queued") && intOf(topic, "tid") > 0)
          .map(NodeBbGameThreads::topicThread);
    }
  }

  /**
   * Follows the game as the signed-in player, as the forum's Watch does: the forum tells of its new
   * posts, and a game of others shows among the player's games, to watch.
   */
  public void watch(final GameThread thread) throws IOException {
    follow(HttpPut::new, thread);
  }

  /** Follows the game no more. */
  public void unwatch(final GameThread thread) throws IOException {
    follow(HttpDelete::new, thread);
  }

  /** The game's follow, by the method given, with the session and its CSRF token as replies. */
  private void follow(final Function<URI, HttpRequestBase> method, final GameThread thread)
      throws IOException {
    try (CloseableHttpClient client = newClient()) {
      final HttpRequestBase request =
          limited(method, forum.resolve("api/v3/topics/" + thread.topicId() + "/follow"));
      request.setHeader(CSRF_HEADER, csrfToken(client));
      try (CloseableHttpResponse response = client.execute(request)) {
        checkOk(response);
      }
    }
  }

  /**
   * Uploads a picture as the signed-in player, as the forum's editor does when one is attached to a
   * post; its address on the forum, eg "/assets/uploads/files/1-map.png", to link in a post.
   */
  public String uploadPicture(final Path picture) throws IOException {
    try (CloseableHttpClient client = newClient()) {
      return upload(client, csrfToken(client), picture, pictureType(picture));
    }
  }

  /** The type the forum takes the file as a picture by, as it does not for any other. */
  private static ContentType pictureType(final Path picture) {
    final String name = String.valueOf(picture.getFileName()).toLowerCase(Locale.ROOT);
    if (name.endsWith(".png")) {
      return ContentType.IMAGE_PNG;
    }
    if (name.endsWith(".gif")) {
      return ContentType.IMAGE_GIF;
    }
    return name.endsWith(".webp") ? ContentType.create("image/webp") : ContentType.IMAGE_JPEG;
  }

  /**
   * The game threads among the topics the player started and the posts the player wrote: topics of
   * the Play by Forum category, or any topic where the player posted a TripleA save game or turn;
   * and the games of others of that category the player follows, marked as only watched.
   */
  @VisibleForTesting
  static List<GameThread> gameThreads(
      final JsonObject started,
      final JsonObject watched,
      final List<JsonObject> posted,
      final URI forum,
      final int playByForumCategory) {
    final List<GameThread> startedThreads =
        objectsIn(started, "topics").stream()
            // the forum still lists a deleted topic to the player who started it
            .filter(topic -> !isSet(topic, "deleted") && isPlayByForum(topic, playByForumCategory))
            .map(NodeBbGameThreads::topicThread)
            .toList();
    final List<GameThread> postedThreads =
        posted.stream()
            .flatMap(posts -> objectsIn(posts, "posts").stream())
            .flatMap(post -> postedThread(post, forum, playByForumCategory).stream())
            .toList();
    final Set<Integer> playersTopics =
        Stream.concat(startedThreads.stream(), postedThreads.stream())
            .map(GameThread::topicId)
            .collect(Collectors.toSet());
    // the followed games tell the newest post, which the player's own posts do not; a followed game
    // of others counts only in the Play by Forum category, as the player follows other topics too
    final Stream<GameThread> watchedThreads =
        objectsIn(watched, "topics").stream()
            .filter(topic -> !isSet(topic, "deleted"))
            .flatMap(
                topic -> {
                  final boolean players = playersTopics.contains(intOf(topic, "tid"));
                  return players || isPlayByForum(topic, playByForumCategory)
                      ? Stream.of(topicThread(topic).withOnlyWatched(!players))
                      : Stream.empty();
                });
    final Map<Integer, GameThread> threads =
        Stream.of(startedThreads.stream(), watchedThreads, postedThreads.stream())
            .flatMap(Function.identity())
            .collect(
                Collectors.toMap(
                    GameThread::topicId,
                    Function.identity(),
                    NodeBbGameThreads::newerOf,
                    LinkedHashMap::new));
    return threads.values().stream()
        .sorted(Comparator.comparing(GameThread::lastPost).reversed())
        .toList();
  }

  /**
   * Whether the topic or post lies in the Play by Forum category, one below it, or one named so.
   */
  private static boolean isPlayByForum(final JsonObject topicOrPost, final int category) {
    final boolean known = category > 0;
    return (known && intOf(topicOrPost, "cid") == category)
        || objectOf(topicOrPost, "category")
            .filter(
                found ->
                    (known
                            && (intOf(found, "cid") == category
                                || intOf(found, "parentCid") == category))
                        || PLAY_BY_FORUM_NAME.matcher(stringOf(found, "name")).find())
            .isPresent();
  }

  private static GameThread toThread(
      final JsonObject topic, final long lastPost, final String lastTurn, final String lastPoster) {
    return new GameThread(
        intOf(topic, "tid"),
        stringOf(topic, "slug"),
        StringEscapeUtils.unescapeHtml4(stringOf(topic, "title")),
        Instant.ofEpochMilli(Math.max(lastPost, longOf(topic, "lastposttime"))),
        intOf(topic, "postcount"),
        lastTurn,
        lastPoster,
        isSet(topic, "unread"),
        false);
  }

  /** The thread of a post that is a game's: in the Play by Forum category, or a turn or save. */
  private static Optional<GameThread> postedThread(
      final JsonObject post, final URI forum, final int playByForumCategory) {
    final String content = stringOf(post, "content");
    final String turn = turnOf(content);
    final boolean gamePost = !turn.isEmpty() || !saveGamesIn(content, forum).isEmpty();
    return objectOf(post, "topic")
        // a deleted game would come back through its starter's own first post
        .filter(
            topic ->
                !isSet(topic, "deleted") && (gamePost || isPlayByForum(post, playByForumCategory)))
        // the player's own post: its news are the player's
        .map(
            topic ->
                toThread(
                    topic,
                    longOf(post, "timestamp"),
                    turn,
                    objectOf(post, "user").map(user -> stringOf(user, "userslug")).orElse("")));
  }

  /** A thread found twice, eg started by the player and posted in, with the newer news of both. */
  private static GameThread newerOf(final GameThread known, final GameThread other) {
    final GameThread newer = other.lastPost().isAfter(known.lastPost()) ? other : known;
    final GameThread older = newer == other ? known : other;
    return new GameThread(
        known.topicId(),
        known.slug().isEmpty() ? other.slug() : known.slug(),
        known.title().isEmpty() ? other.title() : known.title(),
        newer.lastPost(),
        Math.max(known.posts(), other.posts()),
        newer.lastTurn().isEmpty() ? older.lastTurn() : newer.lastTurn(),
        newer.lastPosterSlug().isEmpty() ? older.lastPosterSlug() : newer.lastPosterSlug(),
        known.unread() || other.unread(),
        // found also as the player's own: the player's
        known.onlyWatched() && other.onlyWatched());
  }

  /** A page of a thread from the forum's answer, its links resolved against the forum. */
  @VisibleForTesting
  static ThreadPage threadPage(final JsonObject topic, final URI forum) {
    final List<ThreadPost> posts =
        objectsIn(topic, "posts").stream()
            .filter(post -> !isSet(post, "deleted"))
            .map(post -> toPost(post, forum))
            .toList();
    final boolean watched = isSet(topic, "isFollowing");
    return objectOf(topic, "pagination")
        .map(
            pagination ->
                new ThreadPage(
                    posts,
                    Math.max(1, intOf(pagination, "currentPage")),
                    Math.max(1, intOf(pagination, "pageCount")),
                    watched))
        .orElseGet(() -> new ThreadPage(posts, 1, 1, watched));
  }

  private static ThreadPost toPost(final JsonObject post, final URI forum) {
    final String html = stringOf(post, "content");
    final Optional<JsonObject> user = objectOf(post, "user");
    final String author =
        user.map(
                it ->
                    stringOf(it, "displayname").isEmpty()
                        ? stringOf(it, "username")
                        : stringOf(it, "displayname"))
            .map(StringEscapeUtils::unescapeHtml4)
            .orElse("");
    return new ThreadPost(
        author,
        user.map(it -> stringOf(it, "userslug")).orElse(""),
        user.map(it -> stringOf(it, "icon:text")).map(StringEscapeUtils::unescapeHtml4).orElse(""),
        user.map(it -> stringOf(it, "icon:bgColor")).orElse(""),
        Instant.ofEpochMilli(longOf(post, "timestamp")),
        plainText(html),
        html,
        saveGamesIn(html, forum));
  }

  /**
   * The save games linked in a post's HTML, each with the turn the post names; only files the forum
   * itself keeps, as anyone can link any server in a post.
   */
  @VisibleForTesting
  static List<SaveGame> saveGamesIn(final String html, final URI forum) {
    final Matcher links = SAVE_GAME_LINK.matcher(html);
    final List<URI> urls = new ArrayList<>();
    while (links.find()) {
      resolved(StringEscapeUtils.unescapeHtml4(links.group(1)), forum)
          .filter(url -> isOnForum(url, forum))
          .filter(url -> url.getPath().toLowerCase(Locale.ROOT).endsWith(".tsvg"))
          .ifPresent(urls::add);
    }
    if (urls.isEmpty()) {
      return List.of();
    }
    final String turn = turnOf(html);
    // the path is decoded already, eg "100%.tsvg" for "100%25.tsvg"
    return urls.stream()
        .map(
            url ->
                new SaveGame(
                    url, url.getPath().substring(url.getPath().lastIndexOf('/') + 1), turn))
        .toList();
  }

  /**
   * The link as an address, a link from the forum's root (eg "/assets/...") on the forum; its "..",
   * which the download would follow, already gone.
   */
  private static Optional<URI> resolved(final String link, final URI forum) {
    try {
      return Optional.of(forum.resolve(link).normalize()).filter(url -> url.getPath() != null);
    } catch (final IllegalArgumentException e) {
      return Optional.empty();
    }
  }

  /**
   * Whether the address is on the forum: the same scheme, host and port, and below the forum's
   * path, eg "/forums/" on axisandallies.org, where other applications share the host.
   */
  private static boolean isOnForum(final URI url, final URI forum) {
    // decoded: "%2e%2e" is ".." to the server, though normalizing the raw path keeps it
    final String path = Objects.toString(url.getPath(), "");
    return forum.getScheme().equalsIgnoreCase(Objects.toString(url.getScheme(), ""))
        && forum.getHost().equalsIgnoreCase(Objects.toString(url.getHost(), ""))
        && forum.getPort() == url.getPort()
        && path.startsWith(forum.getPath())
        && !path.contains("/../")
        && !path.endsWith("/..");
  }

  /**
   * The turn a post of TripleA names in its HTML, eg "Germans, round 5"; an empty text for other
   * posts.
   */
  @VisibleForTesting
  static String turnOf(final String html) {
    final Matcher turn = TURN.matcher(plainText(html));
    return turn.find() ? turn.group(1).strip() + ", round " + turn.group(2) : "";
  }

  /** The text of a post's HTML: its paragraphs and lines kept, its tags and entities gone. */
  @VisibleForTesting
  static String plainText(final String html) {
    final String lines =
        html.replaceAll("(?i)<br\\s*/?>", "\n")
            .replaceAll("(?i)</(p|h[1-6]|li|div|blockquote|pre|tr)>", "\n")
            .replaceAll("<[^>]*>", "");
    return StringEscapeUtils.unescapeHtml4(lines)
        .replace(NO_BREAK_SPACE, ' ')
        .replaceAll("[ \\t]+\\n", "\n")
        .replaceAll("\\n{3,}", "\n\n")
        .strip();
  }

  /** The object under the key; empty when missing, null or no object, as the forum may send. */
  private static Optional<JsonObject> objectOf(final JsonObject parent, final String key) {
    final JsonElement value = parent.get(key);
    return value != null && value.isJsonObject()
        ? Optional.of(value.getAsJsonObject())
        : Optional.empty();
  }

  private static List<JsonObject> objectsIn(final JsonObject parent, final String key) {
    final JsonElement array = parent.get(key);
    if (array == null || !array.isJsonArray()) {
      return List.of();
    }
    return StreamSupport.stream(array.getAsJsonArray().spliterator(), false)
        .filter(JsonElement::isJsonObject)
        .map(JsonElement::getAsJsonObject)
        .toList();
  }

  private static String stringOf(final JsonObject object, final String key) {
    final JsonElement value = object.get(key);
    return value == null || !value.isJsonPrimitive() ? "" : value.getAsString();
  }

  /** A number the forum sends as a number or a string, eg "6"; 0 when missing or broken. */
  private static long longOf(final JsonObject object, final String key) {
    try {
      return stringOf(object, key).isEmpty() ? 0 : Long.parseLong(stringOf(object, key));
    } catch (final NumberFormatException e) {
      return 0;
    }
  }

  private static int intOf(final JsonObject object, final String key) {
    return (int) longOf(object, key);
  }

  private static boolean isSet(final JsonObject object, final String key) {
    final String value = stringOf(object, key);
    return "1".equals(value) || "true".equals(value);
  }
}
