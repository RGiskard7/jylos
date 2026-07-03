# Architecture

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
| `ui.components` | `CommandPalette`, `QuickSwitcher`, `PluginManagerDialog`, `FileViewer`, `EditorTabs` (open-note tab strip), `KanbanBoard` (Kanban overlay) |
| `graph` | `GraphBuilder`, `GraphData` — vault graph from notes, wiki-links, tags |
| `git` | `GitService` — status, stage, commit, sync when vault is a Git repo |
| `service` | Business rules (`NoteService`, `FolderService`, `TagService` for note-tag relationships, `BacklinkService`, `NoteTitleIndex`, `EncryptionService`, `DatabaseBackupService`, …) |
| `data.dao` | SQLite and filesystem implementations |
| `data.models` | `Note` (incl. `status`, `isPrivate`), `Folder`, `Tag`, `ToDoNote` |
| `event` | `EventBus`, typed events under `event.events` (including `SystemActionEvent`) |
| `plugin` | `PluginLoader`, `PluginManager`, `AbstractPlugin`, `PluginIds`; built-in Mermaid under `plugin/mermaid/` |
| `util` | `WikiLinkResolver`, `MarkdownProcessor`, `MarkdownPreview` (CommonMark + KaTeX + emoji), `MarkdownHighlighter` (editor syntax highlighting), `KanbanModel`, `NoteExporter` |
| `config` | `LoggerConfig` |

> **MainController pattern.** `MainController` is the FXML shell coordinator and must stay thin: each self-contained feature lives in its own `ui/controller/*Controller`/`*Support` class with a `wire(...)` method (FXML nodes + small callbacks). `MainController` remains the owner of shell wiring, note-open flows, and cross-feature callbacks. New features follow this — no feature bodies inside `MainController`. See `AGENTS.md`.

## UI composition

- `MainView.fxml` — `BorderPane`: toolbar | center `StackPane` (main `SplitPane` + **graph and Kanban overlays**) | collapsible right panel | status bar (optional **Git** strip in vault mode).
- Center split — sidebar | (notes list | editor/preview).
- **Overlays** — graph (`GraphView.fxml` + `GraphController` + `GraphCanvas`) and Kanban (`KanbanBoard`) share the center `StackPane` and are mutually exclusive; both managed by `OverlaySupport`. Toggled via `SystemActionEvent.GRAPH_VIEW` (`Ctrl+G`) and `KANBAN_VIEW` (`Ctrl/Cmd+Shift+K`).
- Sidebar — icon nav bar + `TabPane` (folders, tags, recent, favorites, trash).
- Notes list — custom `ListCell` (title, preview lines, dates, pin/favorite icons).
- Editor — `EditorTabs` strip (one tab per open note) above a **RichTextFX `CodeArea`** (live Markdown highlighting via `MarkdownHighlighter`) + `WebView` preview (`MarkdownPreview`, wiki-link clicks via `jylos://` protocol). Inline save indicator; `[[` autocomplete.
- Right panel — note metadata, **backlinks** (`BacklinksSupport` + `BacklinkService`), plugin side panels.
- **Focus / writing mode** (`FocusModeSupport`, `Ctrl/Cmd+Shift+F`) — removes sidebar, notes list, right panel, toolbar and status bar, leaving only the editor; restores the prior layout on exit.

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

Resolved by `AppDataDirectory` (typically `jylos/` when launched via project scripts):

| Path | Use |
|------|-----|
| `data/` | SQLite `database.db` or filesystem vault root |
| `logs/` | Application logs |
| `plugins/` | External plugin JARs |
| `themes/` | Installed external themes (`theme.properties` + `theme.css`) |
| `backups/` | SQLite auto-backups on startup (gitignored; `DatabaseBackupService`) |

External theme sources: repo `themes/` → `scripts/build-themes.sh` → `jylos/themes/`. Catalog also scans AppData and cwd.

App icons: `src/main/resources/icons/` — see [icons README](../jylos/src/main/resources/icons/README.md).

## Storage

- **SQLite** (default) — `SQLiteDB.initDatabase()`; DAOs in `data.dao.sqlite`.
- **Filesystem vault** — Markdown + YAML frontmatter (`FrontmatterHandler`); lightweight list cache (`parseLightweight`); full body on open/export/graph/backlinks.

Notes carry a `status` column (Kanban legacy/free use) and an `is_private` column (SQLite) / `private:` frontmatter (vault). `SQLiteDB.initDatabase()` performs **idempotent `ALTER TABLE` migrations** (checks `PRAGMA table_info` before adding a column); other SQL schema changes still need a documented manual step.

Preferences key `storage_type`: `sqlite` vs `filesystem` (restart to switch).

## Private notes (encryption)

`EncryptionService` (singleton) encrypts the **body only** of notes flagged `isPrivate`, behind a single **master password**: an AES-256 key is derived with PBKDF2-HMAC-SHA256 over a random salt; only the salt and a verifier are stored (never the password). Bodies are AES-GCM (random IV per note) and persisted as `JENC1:base64(iv‖ciphertext)`. `NoteService` encrypts on write and decrypts on read when the session is unlocked; while locked, list previews and the editor show a 🔒 placeholder and private-note saves are blocked (so ciphertext is never overwritten with plaintext). Vault frontmatter (title, dates) stays readable so locked notes still list.

## Git (filesystem vault)

When the vault directory is inside a Git repository, `GitService` drives the status-bar Git UI and changes dialog (stage/unstage, commit, pull/push). Attachments (PDF, images) appear in change lists alongside `.md` notes.

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
