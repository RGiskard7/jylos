# Architecture

Español: [es/ARCHITECTURE.md](es/ARCHITECTURE.md)

Desktop monolith: JavaFX UI, domain services, pluggable storage, in-process `EventBus`. Offline, single-user.

For normative growth rules and cleanup boundaries, see [ARCHITECTURE_GUIDELINES.md](ARCHITECTURE_GUIDELINES.md).

## Entry points

- `com.example.jylos.Launcher` — delegates to `Main` (used by `exec:java` and packaging).
- `com.example.jylos.Main` — JavaFX `Application`; loads FXML, initializes storage and plugins.

## Layers

```
ui/ (FXML, controllers, components, GraphCanvas)
  → service/ (Note, Folder, Tag, Backlink, backup, …)
    → data/dao/ (sqlite + filesystem)
```

| Package | Role |
|---------|------|
| `ui.controller` | `MainController` (shell coordinator), `SidebarController`, `NotesListController`, `EditorController`, `ToolbarController`, `GraphController`; **feature helpers** that `MainController` delegates to (`GitController`, `PrivacySupport`, `FocusModeSupport`, `OverlaySupport`, `StatusBarSupport`, `BacklinksSupport`) and shell helpers such as `DialogSupport`, `DocumentSupport`, `DocumentWorkflowSupport`, `NoteCreationSupport`, `UiLayout`, `UiInitialization`, `CommandRouting`, `CommandRegistry`, `CommandUI`, `PluginLifecycle`, `PluginUi`, `AppSettings`, `TagManagement`, `NavigationCommand`, `FolderOperations`, `NoteOperations` |
| `ui.theme` | Theme application/detection plus read-only theme and CSS snippet catalogs (`ThemeCommand`, `ThemeCatalog`, `CssSnippetCatalog`, `SystemThemeMonitor`) |
| `ui.preferences` | Persistence of serialized UI preference state (`UiPreferencesStore`) |
| `ui.graph` | `GraphCanvas` — native JavaFX force-directed graph renderer |
| `ui.components` | `CommandPalette`, `QuickSwitcher`, `PluginManagerDialog`, `GitSyncPanel`, `KnowledgeInsightsPanel`, `FileViewer`, `CanvasView`, `EditorTabs` (open-note tab strip), `KanbanBoard` (Kanban overlay) |
| `graph` | `GraphBuilder`, `GraphData` — vault graph from notes, wiki-links, tags |
| `insights` | `KnowledgeInsightsService`, `GraphAnalysisService`, immutable graph-health DTOs |
| `git` | `GitService` — vault-scoped status, stage, commit and sync when a vault is a Git repo |
| `search` | Search query parser/model plus `AdvancedSearchService` |
| `service` | Business rules (`NoteService`, `FolderService`, `TagService` for note-tag relationships, `BacklinkService`, `NoteTitleIndex`, `EncryptionService`, `DatabaseBackupService`, …) |
| `data.dao` | SQLite and filesystem implementations |
| `data.models` | `Note` (incl. `status`, `isPrivate`), `Folder`, `Tag`, `ToDoNote` |
| `event` | `EventBus`, typed events under `event.events` (including `SystemActionEvent`) |
| `plugin` | `PluginLoader`, `PluginManager`, `AbstractPlugin`, `PluginIds`; built-in Mermaid under `plugin/mermaid/` |
| `util` | `WikiLinkResolver`, `MarkdownProcessor`, `MarkdownPreview` (CommonMark + KaTeX + emoji), `KanbanModel`, `NoteExporter` |
| `workspace` | Workspace capture/persistence (`Workspace`, `WorkspaceRepository`, `WorkspaceService`) |
| `config` | `LoggerConfig`, `VersionConfig` |
| `exceptions` | `DataAccessException`, `InvalidParameterException`, `NoteException`, `NoteNotFoundException` — all unchecked, thrown by the DAO/service layers instead of returning a sentinel `null`/`false` on failure |

> **MainController pattern.** `MainController` is the FXML shell coordinator and must stay thin: each self-contained feature lives in its own `ui/controller/*Controller`/`*Support` class with a `wire(...)` method (FXML nodes + small callbacks). `MainController` remains the owner of shell wiring, note-open flows, and cross-feature callbacks. New features follow this — no feature bodies inside `MainController`. See `AGENTS.md`.

## UI composition

- `MainView.fxml` — `BorderPane`: toolbar | center content | status bar (optional **Git** strip in vault mode).
- Center content — `centerRightSplitPane` (`SplitPane`): the mutually-exclusive main content (`centerStack`) on the left, the right panel on the right. The panel is a sibling of `centerStack`, not nested inside the editor's own layout, so it stays reachable — same toggle, same width — no matter which center view is active (editor, graph, or Kanban); this is also the seam a future plugin-hosted panel (e.g. an AI chat) would use.
- `centerStack` — main `SplitPane` (sidebar | notes list | editor) plus the **graph and Kanban overlays**, mutually exclusive, both managed by `OverlaySupport`. Toggled via `SystemActionEvent.GRAPH_VIEW` (`Ctrl/Cmd+G`) and `KANBAN_VIEW` (`Ctrl/Cmd+K`).
- Sidebar — icon nav bar + `TabPane` (folders, tags, recent, favorites, trash).
- Notes list — custom `ListCell` (title, preview lines, dates, pin/favorite icons).
- Editor — `EditorTabs` strip (one tab per open note) above `MarkdownEditorView`, the JavaFX boundary for an offline **CodeMirror 6** source/Live Preview editor, plus a separate reading `WebView` (`MarkdownPreview`, wiki-link clicks via `jylos://` protocol). Inline save indicator; `[[` autocomplete.
- Right panel — baseline section swapped per active center view (`MainController.updateRightPanelContext()`, driven by `OverlaySupport`'s visibility callback): note metadata + **backlinks** (`BacklinksSupport` + `BacklinkService`) for the editor, a live node/connection count for the graph (fed by `GraphController`'s `onDataBuilt` callback), nothing yet for Kanban. Plus plugin side panels (`pluginPanelsContainer`).
- **Focus / writing mode** (`FocusModeSupport`, `Ctrl/Cmd+Shift+F`) — removes sidebar, notes list, right panel, toolbar and status bar, leaving only the editor; restores the prior layout on exit.

### Markdown editing and preview

`MarkdownEditorView` is the only Java-to-JavaScript boundary. CodeMirror owns document transactions, selection, syntax highlighting, Live Preview decorations, history, platform shortcuts, search/replace and autocomplete; `EditorController` owns note state, save/preview orchestration and plugin hooks. Source and Live Preview are two presentations of the same `EditorState`: Live Preview derives viewport decorations from the Lezer syntax tree, reveals Markdown for the active block and never rewrites the document. A fresh state is installed when switching documents so undo history never crosses tabs.

Plugins can claim a fenced-block language (`EditorBlockRenderer`) and have it shown as
generated HTML inside Live Preview. Results are computed on a background thread by
`EditorBlockRenderSupport` and pushed to the editor as a lookup table — never fetched by
it — because `WebView` JavaScript runs on the JavaFX Application Thread. The replacement
comes from a CodeMirror `StateField`, not the Live Preview `ViewPlugin`: block-level
decorations, and any replacement spanning a line break, are rejected when they come from a
plugin (the same constraint is why Markdown tables are replaced row by row).

The shell exposes two semantic states through one book/pencil action: editing (Live Preview by default, or source according to the UI preference) and reading. The linked side-by-side reading view is an independent layout action available from View and the command palette. Internally, `UiLayout.ViewMode` preserves these arrangements for workspace persistence; it does not create another document or editor state.

The pinned npm dependencies are bundled with esbuild and committed as a local resource, so editing never requires network access. `MarkdownPreview` remains a separate CommonMark reading pipeline rendered off the FX thread and loaded in JavaFX `WebView`; it is the full-render owner for recursive transclusion, highlight.js, KaTeX and preview plugin enhancers. It is deliberately not editable, avoiding HTML-to-Markdown round trips and duplicate document state.

Notes may contain raw HTML, which `MarkdownProcessor` passes through unescaped for parity with other Markdown vault apps. Because notes can also come from outside the user's own hands (imported vaults, `git pull`, an MCP client), the reading preview and HTML export are served under a Content-Security-Policy with a fresh nonce per render: only the scripts Jylos emits and those preview enhancers inject carry the nonce, so a `<script>` or inline event handler written in a note does not run, and the page cannot open connections, frames or forms. Relative image references are inlined only when they resolve inside the vault root (`PathContainment`); a reference that climbs out of the vault is left unresolved.

## Knowledge graph

1. `GraphBuilder` loads notes via `NoteService` / `TagService`.
2. Note→note edges from `WikiLinkResolver.extractLinkTargets()` (same rules as preview).
3. Optional tag nodes and note→tag edges.
4. `GraphController` builds JSON/model off the FX thread; `GraphCanvas` runs Barnes–Hut simulation on the JavaFX thread (alpha cooling → idle = no CPU).

Local graph: BFS neighbourhood around the open note id (configurable depth in controller).

## Wiki-links and backlinks

- `WikiLinkResolver` — `[[Title]]`, `[[path/Note#heading|alias]]`, `[label](Note.md)`; resolves to HTML anchors for preview.
- `BacklinkService` — bidirectional warm index (forward `noteId → targets`, inverse `title → noteIds`) kept current by note events; after the initial warm-up, `backlinksFor` resolves from the inverse index instead of re-scanning the whole vault on each call.

## Kanban board

A board is a normal **note** whose Markdown body is parsed/serialised by `KanbanModel`: a hidden first-line marker (`%% jylos-kanban %%`) flags it as a board, `## Heading` lines are columns, `- card` lines are text cards. `KanbanBoard` (overlay) renders columns/cards, supports add/rename/delete columns, create/edit/delete cards, drag between columns, and per-card open-linked-note / convert-to-note. Each change is serialised back to the board note via `NoteService`; shell-level note-created / note-updated events are published by the overlay owner (`MainController` through `OverlaySupport`), not by the widget itself — works in both storage modes with no schema change.

## Runtime directories

Resolved once by `AppDataDirectory`, which applies `AppDirectories.resolve` to the real
machine. Where they live depends on how Jylos runs:

| Mode (detected by) | User data | Logs | Cache (`updates/`) |
|---|---|---|---|
| Override (`-Djylos.data.dir=X`) | `X` | `X/logs` | `X/updates` |
| Development / uber-JAR (no `jpackage.app-path`) | working directory (`jylos/` via project scripts); if read-only, the installed paths | same | same |
| Windows portable ZIP (`jylos.portable` next to `Jylos.exe`) | the `Jylos.exe` folder | `…/logs` | `…/updates` |
| Installed, Windows | `%APPDATA%\Jylos` | `%LOCALAPPDATA%\Jylos\logs` | `%LOCALAPPDATA%\Jylos\cache` |
| Installed, macOS | `~/Library/Application Support/Jylos` | `~/Library/Logs/Jylos` | `~/Library/Caches/Jylos` |
| Installed, Linux | `$XDG_DATA_HOME/jylos` (`~/.local/share/jylos`) | `$XDG_STATE_HOME/jylos/logs` | `$XDG_CACHE_HOME/jylos` |

"Installed" means jpackage's launcher set `jpackage.app-path`. An installed build never uses
the working directory, so uninstalling or upgrading never touches the data. (The Windows
installers keep jpackage's install folder `%LOCALAPPDATA%\Jylos`: `%LOCALAPPDATA%\Programs\Jylos`
fails WiX validation, ICE64. Only logs and the update cache sit next to the program there.) Older installed builds used the working
directory (the install folder on Windows, usually `~` on Linux): on startup `LegacyDataMigration`
moves what they left there, only files Jylos creates, and logs what it did. When the new folder already holds data from an older run, the copy used most recently wins and the other is renamed `<name>.before-migration-<date>`, never deleted; a SQLite database always moves or stays together with its `-wal`/`-shm` files.

Inside the user-data folder:

| Path | Use |
|------|-----|
| `data/` | SQLite `database.db` or filesystem vault root |
| `logs/` | Application logs |
| `plugins/` | External plugin JARs |
| `themes/` | Installed external themes (`theme.properties` + `theme.css`) |
| `snippets/` | User CSS snippets layered after the active theme |
| `backups/` | SQLite auto-backups on startup (gitignored; `DatabaseBackupService`) |

External theme sources: repo `themes/` → `scripts/build-themes.sh` → `jylos/themes/`. When developing, the catalog also scans the working directory.

App icons: `src/main/resources/icons/` — see [icons README](../jylos/src/main/resources/icons/README.md).

## Storage

- **SQLite** (default) — `SQLiteDB.initDatabase()`; DAOs in `data.dao.sqlite`.
- **Filesystem vault** — Markdown + YAML frontmatter (`FrontmatterHandler`); lightweight list cache (`parseLightweight`); full body on open/export/graph/backlinks.
- In filesystem mode, **external edits are not reconciled continuously** while navigating. Global sync with out-of-process changes remains **explicit refresh**; heavyweight viewers that are temporarily reused in the UI (for example `.canvas`) must invalidate themselves against the backing file before being reused.
- Saving a Markdown note never silently overwrites an edit made outside Jylos. `EditorController` keeps a `FileFingerprint` (modification time, size and SHA-256) of the note's file from when it was loaded or last saved; before writing, `SaveConflictSupport` compares it with the file on disk. Content decides, so a file merely touched by a sync client is not a conflict. On a real change a manual save asks whether to overwrite, reload the disk version, or save the editor's text as a separate conflict copy; an autosave does not write and reports the conflict instead. Canvas files and SQLite notes are not covered.
- If the configured vault directory cannot be found at startup (unplugged drive, unmounted cloud folder, moved directory), `VaultLocationSupport` asks the user to retry, pick another folder, open the default data directory for that session only, or quit — it never silently falls back to the default directory.
- Document and folder moves are owned by `FolderService`. The UI only asks for a target and refreshes visible state; the active DAO adapts the operation to its backend.
- In filesystem mode, moving a document is a `Files.move` of the real vault file; moving a folder is a directory move. Name conflicts preserve document extensions, and binary attachment metadata is moved with the document through the private sidecar.
- In SQLite mode, moving a document updates the note-folder relationship and moving a folder updates the parent relationship. Moving to root clears the relationship according to the SQLite schema instead of creating filesystem paths.
- Vault writes for Markdown, canvas and the binary metadata sidecar use a same-directory temporary file followed by atomic replace where the platform supports it. If atomic move is unavailable, the DAO falls back to a controlled replace and cleans temporary files on error.
- Binary attachment metadata lives in `.jylos/document-metadata.json`. Corrupt sidecar JSON is treated as an explicit persistence error, never as an empty index, so Jylos does not silently overwrite real metadata.

Notes carry a `status` column (Kanban legacy/free use) and an `is_private` column (SQLite) / `private:` frontmatter (vault). `SQLiteDB.initDatabase()` performs **idempotent `ALTER TABLE` migrations** (checks `PRAGMA table_info` before adding a column); other SQL schema changes still need a documented manual step.

Preferences keys `storage_type` / `filesystem_path` define the active backend. Switching
between two filesystem vaults reloads the backend session in place (same UI shell, new
DAOs/services/controllers wiring, cleared tabs/editor state, stale-callback guard by
session generation). Switching between `sqlite` and `filesystem` still requires restart.

## Private notes (encryption)

`EncryptionService` (singleton) encrypts the **body only** of notes flagged `isPrivate`, behind a single **master password**: an AES-256 key is derived with PBKDF2-HMAC-SHA256 over a random salt; only the salt and a verifier are stored (never the password). Bodies are AES-GCM (random IV per note) and persisted as `JENC1:base64(iv‖ciphertext)`. `NoteService` encrypts on write and decrypts on read when the session is unlocked; while locked, list previews and the editor show a 🔒 placeholder and private-note saves are blocked (so ciphertext is never overwritten with plaintext). Vault frontmatter (title, dates) stays readable so locked notes still list.

The salt and verifier (`KeyMaterial`) live with the notes they protect, through `EncryptionKeyDAO`: `.jylos/encryption.json` in a filesystem vault, the `app_settings` table in SQLite. A vault copied or synced to another machine therefore unlocks there with the same password. `MainController` wires the shared `EncryptionService` to the session's storage on every vault/database open (which also locks it); until then it keeps key material in memory only, so tests never touch the real user preferences. In earlier versions the material lived in the user preferences; it is migrated into the vault/database only if that storage has none and already contains private notes — notes that can only have been encrypted with it. The preferences themselves are left untouched.

**Security note.** The verifier allows an offline password-guessing attack, as it did from the preferences. Now that it travels with the vault, anyone with a copy of the vault (a Git remote, a cloud folder) can attempt one. PBKDF2 with 210,000 iterations slows each guess down, but only a long, unique master password makes the attack impractical.

## Git (filesystem vault)

When the vault directory is inside a Git repository, `GitService` owns system-Git execution and `GitSyncPanel` owns asynchronous presentation (stage/unstage, commit, pull/push, local branch creation and switching). Every operation is scoped to the vault path, which prevents a nested vault from staging or committing unrelated files in a parent repository. Branch changes are available only when the vault is the repository root, because switching a parent repository from a nested vault would affect unrelated files. Remote work reports coalesced native Git progress, is explicitly cancellable, and triggers the shell's existing explicit vault-refresh flow after a successful pull. Attachments (PDF, images) and vault support files appear in change lists alongside `.md` notes.

## Plugins

Core does not reference plugin classes by name. `PluginLoader` scans `plugins/` under the app base directory. Sample plugins compile with **`--release 21`** (`scripts/build-plugins.sh`). Use `AbstractPlugin` + stable ids from `PluginIds` for palette commands. `PluginContext.requestOpenNote(...)` delegates to a direct callback owned by `MainController`; plugin note-open requests do not travel through ad-hoc UI events.

## Events

- Domain events (`NoteEvents`, `FolderEvents`, …) for refresh coordination.
- `SystemActionEvent` — toolbar/menu/command palette actions; `MainController` dispatches via `EnumMap` (avoid republishing mutating actions from handlers).
- One-to-one UI flows such as theme changes, internal note opening, editor modified notifications, and status messages use explicit wiring/callbacks instead of the bus.
- Plugin/extensibility fan-out remains event-based where appropriate; for example the shell publishes note-selection updates for plugins that track the active note.

Keep subscriptions typed; unsubscribe on teardown; no refresh loops.

## Conventions

- JDK 21, no wildcard imports.
- `LoggerConfig.getLogger(Class)` for logging.
- Long I/O on background threads / `Task`; UI updates on `Platform.runLater`.
- i18n: `com/example/jylos/i18n/messages*.properties` (EN + ES).
