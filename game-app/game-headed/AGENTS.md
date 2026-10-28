# game-headed

The desktop GUI client for TripleA. This is the main user-facing application — a Swing-based client that provides the full game experience including lobby, game setup, gameplay UI, and battle calculator.

## Entry Point

**Main class**: `org.triplea.game.client.HeadedGameRunner`

The `main()` method initializes logging, look-and-feel, client settings, and desktop integrations (macOS URI handlers, file associations), then launches the game setup UI.

### CLI Properties / Startup Modes

Startup behavior is controlled by system properties (set via CLI arguments):

- **`triplea.game`** — Load a save file or game map XML directly
- **`triplea.server`** — Start as a network game server
- **`triplea.client`** — Start as a network game client
- **`triplea.map.download`** — Open the map download window for a specific map name
- **`triplea.start`** — Startup mode selector. Values: `"lobby"` (lobby login), `"local"` (single-player), `"pbem"` (Play-By-Email), `"pbf"` (Play-By-Forum)

macOS-specific: opening a file or a `triplea://` URI is translated to the corresponding property.

## Package Structure

- **`org.triplea.game.client`** — Client bootstrap: `HeadedGameRunner` (entry point), `HeadedApplicationContext` (provides main class reference for `ApplicationContext`)
- **`org.triplea.game.client.ui.swing.laf`** — Swing L&F setup (`DefaultSubstanceLookAndFeelManager`) and the TripleA themes `TripleaDarkLaf` (default, except on macOS where the system look and feel stays default; near-black) and `TripleaLightLaf`, both without colored accents: the only color is `TripleA.highlightBackground` (light blue in the dark theme, indigo in the light one), the text of Multiplayer in the main menu. FlatLaf reads their colors from `TripleaDarkLaf.properties`/`TripleaLightLaf.properties` in `src/main/resources` under the same package; `FlatLaf.properties` there adds shapes, the highlight color, the `primary` button style and the `card` panel style to every FlatLaf theme
- **`org.triplea.sound`** — Audio playback (`DefaultSoundChannel` — routes server-side sound events to local `ClipPlayer` based on which players are local)
- **`org.triplea.lobby.common`** — `LobbyGameUpdateListener` interface (notifies of game additions/removals via WebSocket)
- **`games.strategy.engine.framework.ui`** — `MainFrame` — main game frame and window management; while it is showing, Settings and Maps open as its pages (`MainFramePages`, registered as the `PageHost` of game-core's `MainWindowPages`, shows them below a `BackBar` whose link names the setup screen they replaced, eg "Back to Main menu"), so the client stays one window; `WindowPlacement` starts it maximized (or in full screen, a setting) and remembers a size the player gave it
- **`games.strategy.engine.framework.startup.ui`** — Game setup panels (local, server, client, meta-setup, player selector)
- **`games.strategy.engine.framework.startup.ui.panels.main`** — `MainPanel`/`MainPanelBuilder`, `HeadedServerSetupModel`
- **`games.strategy.engine.framework.startup.ui.posted.game`** — PBEM/PBF setup panels (`PbemSetupPanel`, `PbfSetupPanel`)
- **`games.strategy.engine.framework.startup.mc`** — `HeadedLaunchAction` (creates `TripleAFrame` and `TripleADisplay` at game start), `HeadedPlayerTypes` (maps player type enums to implementations — human, client, DoesNothing, FlowFieldAi), `HeadedServerStartupRemote`
- **`games.strategy.engine.auto.update`** — Startup update checks: engine version (every 2 days via `EngineVersionCheck`), tutorial map presence (`TutorialMapCheck`), outdated downloaded maps (`UpdatedMapsCheck`). Orchestrated by `UpdateChecks`.
- **`games.strategy.engine.framework`** — `GameProcess` (external process launching), `ProcessRunnerUtil`
- **`games.strategy.engine.framework.map.download`** — `MapDownloadController` — controls tutorial map download prompting (checks preference + user map folder)
- **`games.strategy.engine.lobby.client.login`** — Login flow (8 files, see [Login & Registration](#login--registration-lobbyclientlogin-8-files))
- **`games.strategy.engine.lobby.client.ui`** — Lobby UI: `LobbyPanel`, `LobbyGamePanel` (the games as a list of cards, open games first), `LobbyGameTableModel`
- **`games.strategy.engine.lobby.client.ui.action`** — Player context-menu actions: `ShowPlayersAction`, moderator actions (`BanPlayerModeratorAction`, `DisconnectPlayerModeratorAction`, `MutePlayerAction`), `ActionConfirmation`/`ActionDuration`/`ActionDurationDialog` helpers
- **`games.strategy.engine.lobby.client.ui.action.player.info`** — Player information popup with tabs: `PlayerAliasesTab`, `PlayerBansTab`, `PlayerGamesTab`, `PlayerInfoSummaryTextArea`
- **`games.strategy.engine.lobby.moderator.toolbox`** — Moderator administration tools (see [Moderator Toolbox](#moderator-toolbox-lobbymoderatortoolbox-21-files))
- **`games.strategy.engine.posted.game`** — PBEM/PBF turn-posting panels: `AbstractForumPosterPanel` (extends `ActionPanel`), `EndTurnPanel`, `MoveForumPosterPanel`, `ForumPosterComponent`
- **`games.strategy.triplea`** — `TripleAPlayer` (human player controller — extends `AbstractBasePlayer`, manages all turn phases by delegating to `TripleAFrame` UI methods)
- **`games.strategy.triplea.odds.calculator`** — `BattleCalculatorDialog` (battle outcome odds calculator, extends `JDialog`)
- **`games.strategy.triplea.ui`** — Gameplay UI components (see [Gameplay UI](#gameplay-ui))
- **`games.strategy.triplea.ui.menubar`** — Main menu bar (File, View, Game, Export, Network, Lobby, Debug menus — 8 files)
- **`games.strategy.triplea.ui.display`** — `TripleADisplay` (implements `IDisplay`, routes engine display events to `TripleAFrame` methods)
- **`games.strategy.triplea.ui.export`** — `ScreenshotExporter` (renders map to PNG with optional title overlay)
- **`games.strategy.triplea.ui.panel.move`** — `MovePanel` (movement phase UI), `DoneMoveAction`

## Running

```bash
./gradlew :game-headed:run
```

Game assets are downloaded to `build/downloads/assets-zip` by the `downloadAssets` task, unzipped to `build/assets`, and bundled as classpath resources.

## Gameplay UI

The headed module provides ~20 gameplay UI files in `games.strategy.triplea.ui` plus subpackages for menus, display, export, and movement.

### TripleAFrame — Main Game Window

`TripleAFrame` (extends `JFrame`, implements `QuitHandler`) is the main game window. Layout:

- **Map area**: `MapPanel` (interactive game map from game-core) + `ImageScrollerSmallView` (minimap at the top of the right-hand side panel, which is at least 360 px wide)
- **Turn guide**: `TurnGuidePanel` as a bar across the top of the window, over map and side panel — current player, round and phase, plus a plain-language hint for new players of what to do in the phase (`PhaseHints`, chosen from the step like `TripleAPlayer` chooses the action panel: by step name, with the step properties deciding non-combat moves and bids)
- **Tabbed pane** (`JTabbedPane`) with keyboard shortcuts:
  - "Actions" (Ctrl+C) — `ActionButtonsPanel` (phase-specific action panels)
  - "Players" (Ctrl+P) — `StatPanel` (player statistics)
  - "Technology" (Ctrl+Y) — `TechnologyPanel` (conditional: only if tech is in the game)
  - "Resources" (Ctrl+R) — `EconomyPanel`
  - Custom objective tab (Ctrl+O) — `ObjectivePanel` (conditional: only if objectives exist in game data)
  - "Territory" (Ctrl+T) — `TerritoryDetailPanel`
- **Conditional panels**: `EditPanel` (shown as tab when edit mode is active), `HistoryPanel` (game history navigation)
- **Other components**: `ChatPanel` (multiplayer chat, nullable), `CommentPanel`, `BottomBar`
- **Menu bar**: `TripleAMenuBar`

### ActionPanel Hierarchy

`ActionPanel` (abstract, extends `JPanel`) is the base class for all turn-phase action panels. Each panel controls one phase of a player's turn, using `waitForRelease()`/`release()` with `CountDownLatch` for thread synchronization between the game engine and the UI.

```
ActionPanel (abstract base)
├── BattlePanel          — Battle display, casualty selection, retreat decisions
├── PurchasePanel        — Unit purchasing phase
├── TechPanel            — Technology research selection
├── RepairPanel          — Unit repair
├── PoliticsPanel        — Political actions and relationship changes
├── UserActionPanel      — Custom user-defined actions
├── EditPanel            — Admin game editing (add/remove units, change ownership, etc.)
├── PickTerritoryAndUnitsPanel — Territory and unit picker
├── AbstractMovePanel (abstract)
│   ├── MovePanel        — Movement phase (in ui/panel/move/)
│   └── PlacePanel       — Unit placement phase
└── AbstractForumPosterPanel (abstract, in engine/posted/game/)
    ├── EndTurnPanel         — End-of-turn summary posting
    └── MoveForumPosterPanel — Move summary posting
```

### Undo Support

- **`AbstractUndoableMovesPanel`** — Base panel for displaying undoable actions
  - **`UndoableMovesPanel`** — Undo support for movement
  - **`UndoablePlacementsPanel`** — Undo support for placements

### ActionButtonsPanel

Manages the set of `ActionPanel` instances using `CardLayout`, showing the appropriate panel for the current game phase. Holds references to: `BattlePanel`, `MovePanel`, `PurchasePanel`, `RepairPanel`, `PlacePanel`, `TechPanel`, `EndTurnPanel`, `MoveForumPosterPanel`, `PoliticsPanel`, `UserActionPanel`, `PickTerritoryAndUnitsPanel`.

### Other Gameplay UI

- **`CommentPanel`** — Game comment display/entry.
- **`FindTerritoryDialog`** / **`FindTerritoryAction`** — Search for territories by name.
- **`TerritoryDetailPanel`** — Displays detailed territory information (units, production, owner).

### Menu Bar (`ui/menubar/`, 8 files)

- **`TripleAMenuBar`** — Main menu bar builder, assembles all submenus.
- **`FileMenu`** — Save, load, post PBEM/PBF turns.
- **`ViewMenu`** — Map display options (zoom, unit display flags, map skin selection).
- **`GameMenu`** — Game options and settings.
- **`ExportMenu`** — Export setup/stats/unit stats as text or images.
- **`NetworkMenu`** — Network game management (change map, password, boot/ban players).
- **`LobbyMenu`** — The lobby's menu, behind the Menu button of `LobbyPanel`: account, moderator toolbox, sound and chat, help.
- **`DebugMenu`** — Developer debug options.

Note: Help menu content (`HelpMenu`, `MoveHelpMenu`, `UnitHelpMenu`, etc.) lives in **game-core** `ui/menubar/help/`.

### Display Channel (`ui/display/`)

- **`TripleADisplay`** — Implements `IDisplay` interface. Routes engine display events (battle notifications, casualty selection, step changes) to `TripleAFrame` methods on the Swing EDT.

## Lobby UI

The lobby system (~24 files across `games.strategy.engine.lobby`) provides:

### Login & Registration (`lobby/client/login/`, 8 files)

- **`LobbyLogin`** — Orchestrates the full login flow (login, account creation, password recovery, forced password change)
- **`LoginPanel`** — Login dialog with username/password fields, "Login Without An Account" (anonymous) checkbox, "Remember Password" option
- **`CreateAccountPanel`** — New account registration (username, email, password with confirmation)
- **`ForgotPasswordPanel`** — Password recovery (username + email)
- **`ChangePasswordPanel`** — Forced password change after login with a temporary password
- **`ChangeEmailPanel`** — Email update dialog
- **`LoginMode`** — Enum: `REGISTRATION_REQUIRED` / `REGISTRATION_NOT_REQUIRED` (controls anonymous login availability)
- **`LoginResult`** — Login response data: apiKey, username, anonymousLogin flag, moderator flag, passwordChangeRequired flag, optional loginMessage

### Game Browser (`lobby/client/ui/`)

- **`LobbyPanel`** — The lobby as a screen of the main window (a `SetupPanel` that fills it, `isFullWindow`): the `BackBar` of every screen with its link back to the main menu, which leaves the lobby (also Escape and the mouse's back button) and the title, on its right the signed-in name, the lobby menu (`LobbyMenu`: account, admin toolbox, sound and chat, help), Join game and Host game; the games on the left; on the right the players online above a compact chat (`ChatPlayerPanel`, `ChatMessagePanel`). Hosting or joining starts the game in a process of its own (`GameProcess`), the lobby stays; leaving or a lost connection goes back to the main menu
- **`LobbyGamePanel`** — The games as a list of cards (map, host, round, password, comments; status and players on the right), games waiting for players first; the selection survives the frequent updates. Right-click context menu: Join Game, Host Game, Show Players, Boot Game (moderator), Shutdown (moderator). Bot comments in italic. Double-click to join.
- **`LobbyGameTableModel`** — 9 table columns (Host, Name, Round, Players, Password indicator, Status, Comments, Started, UUID). Real-time updates via WebSocket. The Started column only for moderators; `LobbyGamePanel` shows it on their cards.

### Player Actions (`lobby/client/ui/action/`, 7 files + `player/info/`, 6 files)

Right-click actions on players in the lobby chat panel:
- **All users**: "Show Player Info" (`ShowPlayerInformationAction` — tabbed popup with aliases, bans, games history, summary)
- **Moderators only**: "Mute Player" (`MutePlayerAction`), "Disconnect Player" (`DisconnectPlayerModeratorAction`), "Ban Player" (`BanPlayerModeratorAction`)
- **Helper classes**: `ActionConfirmation`, `ActionDuration`, `ActionDurationDialog`, `ShowPlayersAction`

### Moderator Toolbox (`lobby/moderator/toolbox/`, 21 files)

`ToolBoxWindow` opens a `JDialog` with tabbed administration panels (created by `TabFactory`):

| Tab | Package | Description |
|-----|---------|-------------|
| Access Log | `tabs/access/log/` | View player access logs, with ban action |
| Bad Words | `tabs/bad/words/` | Manage chat filter word list (add/remove) |
| Banned Names | `tabs/banned/names/` | Manage banned username patterns |
| Banned Users | `tabs/banned/users/` | View/manage user bans |
| Event Log | `tabs/event/log/` | View moderator action audit log |
| Maps | `tabs/maps/` | Manage game maps listing |
| Moderators | `tabs/moderators/` | Manage moderator accounts (add/remove, show API keys) |

Each tab follows a consistent MVC pattern: `*Tab` (view), `*TabModel` (data), `*TabActions` (user interactions). Common utilities: `Pager` (pagination), `ShowApiKeyDialog`, `MessagePopup`.

## Game Setup UI

Setup panels in `games.strategy.engine.framework.startup.ui/`:
- **`MetaSetupPanel`** — Main menu, as strategy games show one: on a quiet gradient, a left-aligned column with the TripleA logo, the name and a tagline above `MenuEntry` entries (large text without a box, lit with a bar on hover and focus): Multiplayer (the one entry in the accent color) and Play locally with an inviting line each, then Load game, Maps, Settings, How to play and Quit (the way out also in full screen, where the window has no frame) with only their names; no entry has a tool tip. At the top left `LobbyStatusPanel`, one line with players online and open games (example numbers, `MainMenuSources.LobbyStats.EXAMPLE`, until the lobby server tells them without a login). On the right one column, from top to bottom: `CommunityPanel` (a newer version if there is one, the three newest forum topics from the forum's public `api/recent`, read by `ForumTopics`, each with its replies and last answer and marked new when answered within a day, and a link to the forum), `UpdatesPanel` (the newest TripleA release from GitHub and the maps updated last, by `UpdatesFeed`) and `MapOfTheDayPanel` (a map chosen by the date from the installed ones and those of the server's map listing, never the tutorial, large with its preview, name, whether it is installed and one line of its description; Play starts a local game on an installed map, Install downloads another; arrows show the others, it never changes on its own). At the bottom left a tip and the version. The menu never scrolls (`MainPanel` shows it without a scroll pane): `MainMenuFit` hides what does not fit into the window, first updates, map of the day and forum when the height is too small (the right column first narrows to the width of the map picture, and goes as a whole when even that does not fit), then the lobby status, the tip and logo, name and tagline; the entries always stay, and the first focus is Multiplayer. Maps, forum and version are read in the background from `MainMenuSources`, which tests replace so that they stay off the disk and the network; offline the forum part shows a short message and the link to the forum. Multiplayer, Load game and How to play open as pages in place of the menu, each below a `BackBar` back to the main menu (also Escape and the mouse's back button): the ways to play with others (lobby, host and join in the local network, play by email and by forum), the saved games (`SavedGamesPanel`) and the tutorial (`TutorialsPanel`) with links to guides, forum and bug reports. The map creator is in the Maps window. Load game goes through `HeadedServerSetupModel.continueSavedGame`, which opens an email or forum save on its own setup screen, so it keeps its posting settings; a save opened from outside (macOS file handler, `MainFrame.loadSaveFile`) does the same on the main menu and in the lobby (`isFullWindow`) and loads into the current setup screen otherwise (`openSavedGame`)
- **`SavedGamesPanel`** — The saves of the saved games folder and its auto saves, newest first, read in the background, with a search; the chosen one is shown on the right with Continue (also a double click or Enter), "Other file..." picks one elsewhere
- **`TutorialsPanel`** — The tutorial of How to play: chapters on the left, the chosen one on the right; the chapters are HTML resources under `startup/ui/tutorials/<language>/`, the folder named by `startup.Tutorial.Folder` of the resource bundle, English as fallback
- **`LocalSetupPanel`** — Single-player game setup
- **`ServerSetupPanel`** — Host a network game
- **`ClientSetupPanel`** — Join a network game
- **`SetupPanel`** — Abstract base for setup panels; lays out the player table, for local games (`layoutPlayerComponentsWithQuickSetup`) with quick setup buttons above it ("Play <alliance>" against the AI for every alliance of several nations with one a person can play, "Everyone human"); nations the map gives to the computer or to nobody by default, eg neutrals, keep their default
- **`PlayerSelectorRow`** — Player type selection (Human, AI variant)
- **`MainPanel`** / **`MainPanelBuilder`** — Main setup window container: a `BackBar` across the top with the link back to the main menu (cancels the screen and shows the main menu, also Escape and the mouse's back button) and the screen's title (`SetupPanel.getScreenTitle`), the game selection on the left, the setup panel on the right (in a scroll pane; the main menu without one), Quit and the ways forward (Network..., Start Game) in the footer; the main menu and the lobby (`SetupPanel.isFullWindow`) hide the bar, the game selection and the footer
- **`HeadedServerSetupModel`** — Server setup state management

PBEM/PBF setup in `startup/ui/posted/game/`:
- **`PbemSetupPanel`** — Play-by-email configuration
- **`PbfSetupPanel`** — Play-by-forum configuration
