package games.strategy.engine.framework.map.file.system.loader;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import games.strategy.engine.ClientFileSystemHelper;
import games.strategy.engine.framework.GameRunner;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NonNls;
import org.triplea.io.FileUtils;
import org.triplea.io.ZipExtractor;
import org.triplea.io.ZipExtractor.FileSystemException;
import org.triplea.io.ZipExtractor.ZipReadException;
import org.triplea.map.description.file.MapDescriptionYaml;

/**
 * Responsible to find downloaded maps and unzip any that are zipped. Any 'bad' map zips that we
 * fail to unzip will be moved into a bad-zip folder.
 */
@Builder
@Slf4j
public class ZippedMapsExtractor {
  @NonNls private static final String ZIP_EXTENSION = ".zip";

  /**
   * Callback to be invoked if we find any zip files. The task passed to the progress indicator will
   * be the unzip task.
   */
  private final Consumer<Runnable> progressIndicator;

  /** Path to where downloaded maps can be found. */
  private final Path downloadedMapsFolder;

  /**
   * Finds all map zips, extracts them and then removes the original zip. If any zipped files are
   * found, then the progressIndicator is invoked with a callback that will execute the unzip task.
   */
  public void unzipMapFiles() {
    final Collection<Path> zippedMaps = findAllZippedMapFiles();
    if (zippedMaps.isEmpty()) {
      return;
    }
    progressIndicator.accept(
        () ->
            zippedMaps.forEach(
                mapZip -> {
                  try {
                    unzipMapWithMapYaml(mapZip);
                  } catch (final ZipReadException zipReadException) {
                    if (GameRunner.headless()) {
                      log.warn(
                          "Error reading zip file: {}, deleting the file.",
                          mapZip,
                          zipReadException);
                      FileUtils.delete(mapZip);
                    } else {
                      // Problem reading the zip, move it to a folder so that the user does
                      // not repeatedly see an error trying to read this zip.
                      moveBadZip(mapZip)
                          .ifPresent(
                              newLocation ->
                                  log.warn(
                                      "Error extracting map zip: {}, zip has been moved to: {}",
                                      mapZip.toAbsolutePath(),
                                      newLocation.toAbsolutePath(),
                                      zipReadException));
                    }
                  } catch (final FileSystemException e) {
                    // Thrown if we are out of disk space or have file system access issues.
                    // Do not move the zip file to a bad-zip folder as that operation could also
                    // fail.
                    log.warn("Error extracting map zip: {}, {}", mapZip, e.getMessage(), e);
                  } catch (final ZipExtractor.ZipSecurityException e) {
                    log.error(
                        "Malicious zip file detected: {}, please report this to TripleA and delete the zip file",
                        mapZip.toAbsolutePath(),
                        e);
                  }
                }));
  }

  /** Unzips the map and gives it a 'map.yml' if it has none, as maps before 2.6. */
  private static Optional<Path> unzipMapWithMapYaml(final Path mapZip) {
    final Optional<Path> mapFolder = unzipMap(mapZip);
    mapFolder
        .filter(installPath -> MapDescriptionYaml.fromMap(installPath).isEmpty())
        .ifPresent(MapDescriptionYaml::generateForMap);
    return mapFolder;
  }

  private Collection<Path> findAllZippedMapFiles() {
    return FileUtils.listFiles(downloadedMapsFolder).stream()
        .filter(Predicate.not(Files::isDirectory))
        .filter(
            file -> file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(ZIP_EXTENSION))
        .collect(Collectors.toList());
  }

  /**
   * Unzips are target map file into the downloaded maps folder, deletes the zip file after
   * extraction. Extracted files are first extracted to a temporary location before being moved into
   * the downloaded map's folder. This temporary location is to help avoid intermediate results if
   * for example we run out of disk space while extracting.
   *
   * @param mapZip The map zip file to be extracted to the downloaded map's folder.
   * @return Returns extracted location (if successful, otherwise empty)
   */
  public static Optional<Path> unzipMap(final Path mapZip) {
    if (!Files.exists(mapZip)) {
      String msg =
          "Unexpected, cannot extract map zip, no file exists at: " + mapZip.toAbsolutePath();
      if (GameRunner.headless()) {
        log.warn(msg);
      } else {
        log.info(msg);
      }
      return Optional.empty();
    }
    Preconditions.checkArgument(!Files.isDirectory(mapZip), mapZip.toAbsolutePath());
    Preconditions.checkArgument(
        mapZip.getFileName().toString().endsWith(".zip"), mapZip.toAbsolutePath());

    try {
      // extraction target is important, it is the folder path we seek to create with
      // extracted map contents.
      final Path mapsFolder = ClientFileSystemHelper.getUserMapsFolder();
      final Path extractionTarget =
          mapsFolder.resolve(computeExtractionFolderName(mapZip.getFileName().toString()));
      log.info(
          "Extracting map zip: {} -> {}",
          mapZip.toAbsolutePath(),
          extractionTarget.toAbsolutePath());
      FileUtils.deleteDirectory(extractionTarget);
      ZipExtractor.unzipFile(mapZip, extractionTarget);

      // delete old properties file if they exists
      final Path propertiesFile =
          mapZip.resolveSibling(mapZip.getFileName().toString() + ".properties");
      if (Files.exists(propertiesFile)) {
        FileUtils.delete(propertiesFile);
      }

      final boolean successfullyExtracted = Files.exists(extractionTarget);
      if (successfullyExtracted) {
        Files.delete(mapZip);
        return Optional.of(extractionTarget);
      } else {
        return Optional.empty();
      }
    } catch (final IOException e) {
      log.warn("Error extracting file: {}, {}", mapZip.toAbsolutePath(), e.getMessage(), e);
      return Optional.empty();
    }
  }

  /**
   * Installs a map from a zip the player picked. Extracts a copy, as unzipMap deletes the zip it
   * extracted, so the player keeps the file. A zip in the maps folder itself goes, as TripleA would
   * extract it at its next start.
   *
   * @return The extracted map folder, empty if the file is no zip, a map of its name is installed
   *     (unzipMap would delete it) or it could not be extracted.
   */
  public static Optional<Path> importMapZip(final Path zip) {
    final String fileName = zip.getFileName().toString();
    if (!fileName.toLowerCase(Locale.ROOT).endsWith(ZIP_EXTENSION) || !Files.isRegularFile(zip)) {
      return Optional.empty();
    }
    // unzipMap only takes a lower case ".zip"
    final String copyName =
        fileName.substring(0, fileName.length() - ZIP_EXTENSION.length()) + ZIP_EXTENSION;
    final Path mapsFolder = ClientFileSystemHelper.getUserMapsFolder();
    if (Files.exists(mapsFolder.resolve(computeExtractionFolderName(copyName)))) {
      return Optional.empty();
    }
    final Path copy = mapsFolder.resolve(copyName);
    try {
      Files.copy(zip, copy, StandardCopyOption.REPLACE_EXISTING);
    } catch (final IOException e) {
      log.warn("Could not copy {} to {}", zip, copy, e);
      return Optional.empty();
    }
    try {
      final Optional<Path> mapFolder = unzipMapWithMapYaml(copy);
      if (mapFolder.isEmpty()) {
        cleanUpFailedImport(zip, copy);
      }
      return mapFolder;
    } catch (final ZipReadException | FileSystemException | ZipExtractor.ZipSecurityException e) {
      log.warn("Could not import the map zip {}", zip, e);
      cleanUpFailedImport(zip, copy);
      return Optional.empty();
    }
  }

  /**
   * Removes what a failed import left: the half extracted folder and the copy, but not the player's
   * own zip in the maps folder.
   */
  private static void cleanUpFailedImport(final Path zip, final Path copy) {
    try {
      FileUtils.deleteDirectory(
          copy.resolveSibling(computeExtractionFolderName(copy.getFileName().toString())));
      if (Files.exists(copy) && !Files.isSameFile(zip, copy)) {
        Files.delete(copy);
      }
    } catch (final IOException e) {
      log.warn("Could not delete {}", copy, e);
    }
  }

  /**
   * Removes the '.zip' or '-master.zip' suffix from map names if present. <br>
   * EG: 'map-name-master.zip' -> 'map-name'
   */
  @VisibleForTesting
  static String computeExtractionFolderName(final String mapZipName) {
    String newName = mapZipName;
    if (newName.endsWith(".zip")) {
      newName = newName.substring(0, newName.length() - ".zip".length());
    }
    if (newName.endsWith("-master")) {
      newName = newName.substring(0, newName.length() - "-master".length());
    }
    return newName.toLowerCase(Locale.ROOT);
  }

  /**
   * Moves a target zip file into a 'bad-zip' folder. This is to prevent the file from being picked
   * up in future unzip operations and cause repeated warning messages to users.
   *
   * @return Returns the new location of the file, returns an empty if the file move operation
   *     failed.
   */
  private Optional<Path> moveBadZip(final Path mapZip) {
    final Path badZipFolder = downloadedMapsFolder.resolve("bad-zips");
    if (!Files.exists(badZipFolder)) {
      try {
        Files.createDirectories(badZipFolder);
      } catch (final IOException e) {
        log.error(
            "Unable to create folder: "
                + badZipFolder.toAbsolutePath()
                + ", please report this to TripleA and create the folder manually.",
            e);
        return Optional.empty();
      }
    }
    try {
      final Path newLocation = badZipFolder.resolve(mapZip.getFileName());
      Files.move(mapZip, newLocation, StandardCopyOption.REPLACE_EXISTING);
      return Optional.of(newLocation);
    } catch (final IOException e) {
      log.error(
          "Failed to move file: "
              + mapZip.toAbsolutePath()
              + ", to: "
              + badZipFolder.toAbsolutePath(),
          e);
      return Optional.empty();
    }
  }
}
