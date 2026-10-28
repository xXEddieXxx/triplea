package games.strategy.engine.framework.startup.ui;

import static org.assertj.core.api.Assertions.assertThat;

import games.strategy.engine.framework.I18nEngineFramework;
import games.strategy.engine.framework.I18nResourceBundle;
import games.strategy.engine.framework.startup.ui.SavedGamesPanel.SaveKind;
import games.strategy.engine.framework.startup.ui.SavedGamesPanel.SavedGame;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class SavedGamesPanelTest {
  @TempDir Path savedGames;

  @DisplayName("Saves and auto saves are listed together, newest first")
  @Test
  void newestFirst() throws IOException {
    givenSave(savedGames.resolve("older.tsvg"), 1000);
    givenSave(savedGames.resolve("autoSave").resolve("autosave_round_2.tsvg"), 3000);
    givenSave(savedGames.resolve("Global 1940 round 3.tsvg"), 2000);

    assertThat(
            SavedGamesPanel.findSavedGames(savedGames, savedGames.resolve("autoSave")).stream()
                .map(savedGame -> savedGame.name() + " " + savedGame.kind()))
        .as("the game played last is the one most often continued")
        .containsExactly(
            "autosave_round_2 " + SaveKind.AUTO_SAVE,
            "Global 1940 round 3 " + SaveKind.SAVED,
            "older " + SaveKind.SAVED);
  }

  @DisplayName("Files that this version does not save are left out, eg old .svg saves")
  @Test
  void onlySavesOfThisVersion() throws IOException {
    givenSave(savedGames.resolve("Global 1940 round 3.tsvg"), 1000);
    givenSave(savedGames.resolve("map picture.svg"), 3000);
    givenSave(savedGames.resolve("old game.tsvg.gz"), 5000);
    givenSave(savedGames.resolve("notes.txt"), 9000);

    assertThat(
            SavedGamesPanel.findSavedGames(savedGames, savedGames.resolve("autoSave")).stream()
                .map(SavedGame::name))
        .as("loading the other files would fail; a missing auto save folder lists nothing")
        .containsExactly("Global 1940 round 3");
  }

  @DisplayName("A save with the ending in capitals is listed too, as Windows does not care")
  @Test
  void endingInCapitals() throws IOException {
    givenSave(savedGames.resolve("Game.TSVG"), 1000);

    assertThat(
            SavedGamesPanel.findSavedGames(savedGames, savedGames.resolve("autoSave")).stream()
                .map(SavedGame::name))
        .as("Other file... opens it, the list must show it")
        .containsExactly("Game");
  }

  @DisplayName("A save of today or yesterday says so, older ones show their date")
  @Test
  void whenSavedReadsLikeAPerson() {
    final ZoneId zone = ZoneId.of("Europe/Berlin");
    final LocalDate today = LocalDate.of(2026, 10, 27);
    final I18nResourceBundle bundle = I18nEngineFramework.get();

    final String todays =
        SavedGamesPanel.whenSaved(bundle, Instant.parse("2026-10-27T16:33:00Z"), today, zone);
    final String yesterdays =
        SavedGamesPanel.whenSaved(bundle, Instant.parse("2026-10-26T08:05:00Z"), today, zone);
    final String older =
        SavedGamesPanel.whenSaved(bundle, Instant.parse("2026-10-12T16:33:00Z"), today, zone);

    assertThat(List.of(todays, yesterdays, older))
        .as("the newest saves are found by when, not by a date to decode")
        .satisfiesExactly(
            text -> assertThat(text).startsWith(bundle.getText("startup.LoadGamePanel.Today", "")),
            text ->
                assertThat(text).startsWith(bundle.getText("startup.LoadGamePanel.Yesterday", "")),
            text ->
                assertThat(text)
                    .doesNotStartWith(bundle.getText("startup.LoadGamePanel.Today", "")));
  }

  @DisplayName("An auto save shows when it was saved in words, not its file name")
  @Test
  void autoSaveNamesReadable() {
    assertThat(
            List.of(
                SavedGamesPanel.readableAutoSaveName("autosaveAfterGermansCombatMove"),
                SavedGamesPanel.readableAutoSaveName("autosave_round_even"),
                SavedGamesPanel.readableAutoSaveName("autosave")))
        .as("a name without words beyond 'autosave' stays as it is")
        .containsExactly("After germans combat move", "Round even", "autosave");
  }

  @DisplayName("The search finds saves by a part of their name, ignoring case")
  @Test
  void searchFindsByName() throws IOException {
    givenSave(savedGames.resolve("Global 1940 round 3.tsvg"), 3000);
    givenSave(savedGames.resolve("Pacific round 1.tsvg"), 2000);
    givenSave(savedGames.resolve("global practice.tsvg"), 1000);
    final List<SavedGame> all =
        SavedGamesPanel.findSavedGames(savedGames, savedGames.resolve("autoSave"));

    assertThat(SavedGamesPanel.matching(all, "  GLOBAL ").stream().map(SavedGame::name))
        .as("both Global saves, newest first; the spaces of the search do not count")
        .containsExactly("Global 1940 round 3", "global practice");
  }

  private static void givenSave(final Path file, final long lastModifiedMillis) throws IOException {
    Files.createDirectories(file.getParent());
    Files.writeString(file, "save");
    Files.setLastModifiedTime(file, FileTime.fromMillis(lastModifiedMillis));
  }
}
