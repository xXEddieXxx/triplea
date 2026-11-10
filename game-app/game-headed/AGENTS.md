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
- **`games.strategy.engine.framework.startup.ui`** — Game setup panels (local, server, client, meta-setup, player selector); the play by forum page `ForumGamesPanel` (games, posts with quote and, for the player's own, edit and delete, a "new posts" line after the newest post seen here, `ForumPostsSeen`, the unsent answer kept per game while TripleA runs, and in a thread of one page without a save "Start the game in this thread", `HeadedServerSetupModel.showPbf(forum, topic)`); its `ForumMessageEditor` also mentions the thread's players ("@"), as does the end of a turn (`ForumPosterComponent`, with pictures where the player is signed in) and `ForumTurnNotifier`, started with the client, which asks the signed-in forums every 30 minutes and shows a system tray message for a new turn, one for several (`ClientSetting.notifyForumTurns`)
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
- **`games.strategy.triplea.ui.menubar`** — The game window's quick bar of icons (`GameToolBar`) with the menus behind them (files, statistics and exports, network, help), and the map's settings that were the View menu (`MapViewSettings`)
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
- **Turn guide**: `TurnGuidePanel` as a slim bar across the top of the window, over map and side panel — current player and round with the phase beside them on one line, plus a plain-language hint for new players of what to do in the phase (`PhaseHints`, chosen from the step like `TripleAPlayer` chooses the action panel: by step name, with the step properties deciding non-combat moves and bids)
- **Units to place and unit scroller**: at the bottom of the side panel, below the tabs (both closed until the player opens them, which `ClientSetting.placementsCollapsed` and `unitScrollerCollapsed` keep); the unit scroller's buttons (previous, wake all, skip, next, each with a tool tip) float apart from it over the bottom right corner of the map in the move phases (`UnitScroller.buildButtons`, placed by `TripleAFrame.floatOverMap` in the window's layered pane, since the map paints over its own children)
- **Tabbed pane** (`JTabbedPane`, below the minimap; all tabs in view in as many rows as needed; FlatLaf's `rotateTabRuns: false` keeps each row in its place when a tab is chosen) with keyboard shortcuts:
  - "Actions" (Ctrl+C) — `ActionButtonsPanel` (phase-specific action panels, eg the units to buy or the moves made, each with its title and below it its buttons side by side, `ActionPanel.newButtonRow`), first and chosen when the game shows; while the history shows, a note with the "Back to the game" button (the Show current game action) instead
  - "History" — `HistoryDetailsPanel`, after Actions and chosen while the history shows
  - "Territory" (Ctrl+T) — `TerritoryDetailPanel`
  - "Players" (Ctrl+P) — `StatPanel` (player statistics)
  - "Technology" (Ctrl+Y) — `TechnologyPanel` (conditional: only if tech is in the game)
  - "Resources" (Ctrl+R) — `EconomyPanel`
  - Custom objective tab (Ctrl+O) — `ObjectivePanel` (conditional: only if objectives exist in game data)
- **Conditional panels**: `EditPanel` (shown as tab when edit mode is active), `HistoryPanel` (game history navigation; on the first turn of a local human, eg after loading a save, the history opens at the end of that player's last turn, `History.getFirstStepSinceLastTurnOf`, while `ClientSetting.replayOthersTurnsOnLoad` is on)
- **Other components**: `ChatPanel` (multiplayer chat, nullable), `CommentPanel`, `BottomBar`
- **Quick bar**: `GameToolBar`'s game menus (history, save, settings, network) at the left end of the turn guide, its tools (battle calculator, find, politics, edit mode, statistics, help) at the right end; the bar shows from the start and also in the history, in place of a menu bar (see [Menu Bar](#menu-bar-uimenubar-10-files))

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

Manages the set of `ActionPanel` instances using `CardLayout`, showing the appropriate panel for the current game phase; each panel lays out its own buttons, eg as a row from `ActionPanel.newButtonRow`. Holds references to: `BattlePanel`, `MovePanel`, `PurchasePanel`, `RepairPanel`, `PlacePanel`, `TechPanel`, `EndTurnPanel`, `MoveForumPosterPanel`, `PoliticsPanel`, `UserActionPanel`, `PickTerritoryAndUnitsPanel`.

### Other Gameplay UI

- **`CommentPanel`** — Game comment display/entry.
- **`FindTerritoryDialog`** / **`FindTerritoryAction`** — Search for territories by name.
- **`TerritoryDetailPanel`** — Displays detailed territory information (units, production, owner).

### Menu Bar (`ui/menubar/`, 10 files)

- **`GameToolBar`** — The game window has no menu bar and no ☰: every entry is behind an icon (22 px `ToolIcon`s whose tool tips say what they do, shown at once on hover). `newGameMenus` is a `JMenuBar` of icons at the left end of the turn guide (the bar across the top): the history book (the game's own Show history, again Show current game), the save icon with the File menu, the gear for the settings on their In game section (`GameMenu.showSettings`, which redraws the map and shows or hides the comment log as the settings changed), and the network icon (only in network games). At the right end, the bar itself, a `JMenuBar` of tools: buttons for the battle calculator, find a territory, politics (`GameMenu.showPoliticsPanel`) and edit mode (a toggle on the frame's edit mode model), the statistics icon (statistics, dice, exports) and, at the very right, the help icon. Being menu bars, the icon menus keep their accelerators (eg Ctrl+S) and Alt and a menu's letter opens them (Alt+F, Alt+S, Alt+N, Alt+H); the shortcuts of the former Game and View menus are bound on the window: Ctrl+B battle calculator, Ctrl+F find, Ctrl+W (Cmd+O on macOS) politics, Ctrl+E edit mode, Ctrl+G current game, Ctrl+H (Cmd+Y) history, Ctrl+L lock map. The same bar on every system, macOS included.
- **`TripleAMenuBar`** — Builds the icon menus: files, statistics (with the entries of `ExportMenu`), network (the lobby's and `NetworkMenu`'s entries, only online) and help (with the game's options to look at and `DebugMenu` at the end).
- **`FileMenu`** — Save, post PBEM/PBF turns, leave the game, exit; behind the save icon.
- **`MapViewSettings`** — There is no View menu any more: its entries are settings. Map zoom (with fit width, fit height and 100%), the unit size and skin of this map and the units hidden for a moment are `MapSetting`s, which `GameMenu.showSettings` passes to the settings window as This map at the top of In game; they change the map at once, and the map keeps zoom, unit size and skin per map. How the map looks for good (flags, relief, blends, map text, territory effects, comment log, lock map, the status bar's units and zoom) is in the In game settings too. Ctrl+F finds a territory and Ctrl+L locks the map (`GameToolBar`).
- **`GameMenu`** — No Game menu any more: the statistics and dice entries for the statistics icon, the game's options to look at for the help icon, the politics panel and `showSettings`, which applies what the settings changed to the running game. Sound is in the settings (Audio).
- **`ExportMenu`** — Export setup/stats/unit stats as text or images; its entries are in the statistics icon's menu.
- **`NetworkMenu`** — Network game management (change map, password, boot/ban players); its entries are in the network icon's menu.
- **`LobbyMenu`** — The lobby's menu, behind the Menu button of `LobbyPanel`: account, moderator toolbox, sound and chat, help.
- **`DebugMenu`** — Developer debug options (AI), at the end of the help icon's menu.

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
- **`SavedGamesPanel`** — The saves of the saved games folder and its auto saves, newest first, read in the background, with a search; the chosen one is shown on the right with Continue (also a double click or Enter), "Other file..." picks one elsewhere. `SavedGamesPanel.choose` shows it as a page of the main window for Load game of the setup screens (file chooser while the main window is not showing)
- **`TutorialsPanel`** — The tutorial of How to play: chapters on the left, the chosen one on the right; the chapters are HTML resources under `startup/ui/tutorials/<language>/`, the folder named by `startup.Tutorial.Folder` of the resource bundle, English as fallback
- **`LocalSetupPanel`** — Single-player game setup
- **`ServerSetupPanel`** — Host a network game
- **`ClientSetupPanel`** — Join a network game
- **`SetupPanel`** — Abstract base for setup panels; lays out the player table, for local games (`layoutPlayerComponentsWithQuickSetup`) at the top, centered across the width, with quick setup buttons above it ("Play <alliance>" against the AI for every alliance of several nations with one a person can play, as a list instead of buttons for more than five; "Everyone human"); the computer's nations keep the AI the player already chose for them, and nations that were human get the AI chosen in the table, eg Fast AI by "Set all to" (the hard AI while none is chosen), also those the map gives to the computer; nations the map gives to nobody, eg neutrals, keep doing nothing. Each nation's income fields (percent, bonus PUs) sit in its row and show while `ClientSetting.showResourceModifiers` is on, which the Resource Modifiers button in the game selection's column (`GameSelectorPanel`) switches; `MainPanel` shows that button only for screens whose table has the fields (`hasResourceModifiers`: local, email and forum games, not the network screens with players of their own)
- **`PlayerSelectorRow`** — Player type selection (Human, AI variant)
- **`MainPanel`** / **`MainPanelBuilder`** — Main setup window container: a `BackBar` across the top with the link back to the main menu (cancels the screen and shows the main menu, also Escape and the mouse's back button) and the screen's title (`SetupPanel.getScreenTitle`), the setup panel in the middle (in a scroll pane; the main menu without one), the game selection (`GameSelectorPanel`) in a column of fixed width on the left, the players and the game's options on two tabs ("Game options · 14" tells their number), Quit and the ways forward (Network..., Start Game) in the footer; the main menu and the lobby (`SetupPanel.isFullWindow`) hide the bar, the game selection and the footer
- **`HeadedServerSetupModel`** — Server setup state management

PBEM/PBF setup in `startup/ui/posted/game/`:
- **`PbemSetupPanel`** — Play-by-email configuration
- **`PbfSetupPanel`** — Play-by-forum configuration
