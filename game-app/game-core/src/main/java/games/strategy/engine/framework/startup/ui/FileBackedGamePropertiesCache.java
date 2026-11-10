package games.strategy.engine.framework.startup.ui;

import com.google.common.annotations.VisibleForTesting;
import games.strategy.engine.ClientFileSystemHelper;
import games.strategy.engine.data.GameData;
import games.strategy.engine.data.properties.IEditableProperty;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;

/**
 * Keeps the rule sets of a game as files "Name.ruleSet" in the folder "optionCache/Game.ruleSets";
 * the options that are serializable (which they should all be) are kept.
 */
@Slf4j
public class FileBackedGamePropertiesCache implements IGamePropertiesCache {
  // chars illegal on windows (on linux/mac anything that is allowed on windows works fine), and
  // "/", so a rule set's name never leads into another folder; sorted for the binary search
  private static final char[] ILLEGAL_CHARS = {
    0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25,
    26, 27, 28, 29, 30, 31, 34, 42, 47, 58, 60, 62, 63, 92, 124
  };
  private static final String RULE_SET_SUFFIX = ".ruleSet";

  // read when used, as the user's folder is set up after this cache is made
  private final Supplier<Path> folder;

  public FileBackedGamePropertiesCache() {
    this(() -> ClientFileSystemHelper.getUserRootFolder().resolve("optionCache"));
  }

  @VisibleForTesting
  FileBackedGamePropertiesCache(final Supplier<Path> folder) {
    this.folder = folder;
  }

  @Override
  public void saveRuleSet(final GameData gameData, final String name) {
    write(gameData, getRuleSetFile(gameData, name));
  }

  @Override
  public List<String> getRuleSetNames(final GameData gameData) {
    final Path gameFolder = getGameFolder(gameData);
    if (!Files.isDirectory(gameFolder)) {
      return List.of();
    }
    try (Stream<Path> files = Files.list(gameFolder)) {
      return files
          .map(file -> file.getFileName().toString())
          .filter(file -> file.endsWith(RULE_SET_SUFFIX))
          .map(file -> file.substring(0, file.length() - RULE_SET_SUFFIX.length()))
          .sorted(String.CASE_INSENSITIVE_ORDER)
          .toList();
    } catch (final IOException e) {
      log.warn("Could not list the rule sets in " + gameFolder.toAbsolutePath(), e);
      return List.of();
    }
  }

  @Override
  public void loadRuleSetInto(final GameData gameData, final String name) {
    readInto(gameData, getRuleSetFile(gameData, name));
  }

  @Override
  public void deleteRuleSet(final GameData gameData, final String name) {
    final Path file = getRuleSetFile(gameData, name);
    try {
      Files.deleteIfExists(file);
    } catch (final IOException e) {
      log.error("Failed to delete the rule set " + file.toAbsolutePath(), e);
    }
  }

  private Path getRuleSetFile(final GameData gameData, final String name) {
    final String fileName = cleanName(name);
    // a name of only characters a file cannot have still gets a file, and shows in the list
    return getGameFolder(gameData)
        .resolve((fileName.isBlank() ? "Unnamed" : fileName) + RULE_SET_SUFFIX);
  }

  /** A folder per game, so games whose names start alike never see each other's rule sets. */
  private Path getGameFolder(final GameData gameData) {
    return folder.get().resolve(cleanName(gameData.getGameName()) + ".ruleSets");
  }

  private static void write(final GameData gameData, final Path file) {
    // only serializable values can be written, which all options should be
    final Map<String, Object> serializableMap = new HashMap<>();
    for (final IEditableProperty<?> property : gameData.getProperties().getEditableProperties()) {
      if (property.getValue() instanceof Serializable propertyValue) {
        serializableMap.put(property.getName(), propertyValue);
      }
    }
    try {
      Files.createDirectories(file.getParent());
      try (OutputStream os = Files.newOutputStream(file);
          ObjectOutputStream out = new ObjectOutputStream(os)) {
        out.writeObject(serializableMap);
      }
    } catch (final IOException e) {
      log.error("Failed to write game properties to cache: " + file.toAbsolutePath(), e);
    }
  }

  @SuppressWarnings("unchecked")
  // generics are compile time only, and lost during serialization
  private static void readInto(final GameData gameData, final Path file) {
    try {
      if (Files.exists(file)) {
        try (InputStream is = Files.newInputStream(file);
            ObjectInputStream in = new ObjectInputStream(is)) {
          final Map<String, Serializable> serializedMap =
              (Map<String, Serializable>) in.readObject();
          for (final IEditableProperty<?> property :
              gameData.getProperties().getEditableProperties()) {
            final Serializable ser = serializedMap.get(property.getName());
            if (ser != null) {
              property.validateAndSet(ser);
            }
          }
        }
      }
    } catch (final IOException | ClassNotFoundException e) {
      log.error("Failed to load game properties from cache: " + file.toAbsolutePath(), e);
    }
  }

  /** Removes the characters a file name cannot have, eg on Windows. */
  private static String cleanName(final String name) {
    final StringBuilder sb = new StringBuilder();
    for (int i = 0, charArrayLength = name.length(); i < charArrayLength; i++) {
      final char c = name.charAt(i);
      if (Arrays.binarySearch(ILLEGAL_CHARS, c) < 0) {
        sb.append(c);
      }
    }
    return sb.toString();
  }
}
