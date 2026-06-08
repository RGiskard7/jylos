# Architecture

Desktop monolith: JavaFX UI, domain services, pluggable storage, in-process `EventBus`. Offline, single-user.

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
| `ui.controller` | `MainController`, `SidebarController`, `NotesListController`, `EditorController`, `ToolbarController`, `GraphController`; shell helpers (`ThemeCommand`, `CommandUI`, `UiDialog`, `UiLayout`, …) |
| `ui.graph` | `GraphCanvas` — native JavaFX force-directed graph renderer |
| `ui.components` | Command palette, quick switcher, plugin manager, file viewers |
| `graph` | `GraphBuilder`, `GraphData` — vault graph from notes, wiki-links, tags |
| `git` | `GitService` — status, stage, commit, sync when vault is a Git repo |
| `service` | Business rules (`BacklinkService`, `DatabaseBackupService`, …) |
| `data.dao` | SQLite and filesystem implementations |
| `data.models` | `Note`, `Folder`, `Tag`, `ToDoNote` |
| `event` | `EventBus`, typed events under `event.events` (including `SystemActionEvent`) |
| `plugin` | `PluginLoader`, `PluginManager`, `AbstractPlugin`, `PluginIds`; built-in Mermaid under `plugin/mermaid/` |
| `util` | `WikiLinkResolver`, `MarkdownProcessor`, `MarkdownPreview` (CommonMark + KaTeX + emoji), `NoteExporter` |
| `config` | `AppContext`, `LoggerConfig` |

## UI composition

- `MainView.fxml` — `BorderPane`: toolbar | center `StackPane` (main `SplitPane` + **graph overlay**) | collapsible right panel | status bar (optional **Git** strip in vault mode).
- Center split — sidebar | (notes list | editor/preview).
- **Graph** — `GraphView.fxml` + `GraphController`; `GraphCanvas` embedded in overlay; toggled via `SystemActionEvent.GRAPH_VIEW` (`Ctrl+G`, View menu, toolbar).
- Sidebar — icon nav bar + `TabPane` (folders, tags, recent, favorites, trash).
- Notes list — custom `ListCell` (title, preview lines, dates, pin/favorite icons).
- Editor — `TextArea` + `WebView` preview (`MarkdownPreview`, wiki-link clicks via `jylos://` protocol).
- Right panel — note metadata, **backlinks** (`BacklinkService`), plugin side panels.

## Knowledge graph

1. `GraphBuilder` loads notes via `NoteService` / `TagService`.
2. Note→note edges from `WikiLinkResolver.extractLinkTargets()` (same rules as preview).
3. Optional tag nodes and note→tag edges.
4. `GraphController` builds JSON/model off the FX thread; `GraphCanvas` runs Barnes–Hut simulation on the JavaFX thread (alpha cooling → idle = no CPU).

Local graph: BFS neighbourhood around the open note id (configurable depth in controller).

## Wiki-links and backlinks

- `WikiLinkResolver` — `[[Title]]`, `[[path/Note#heading|alias]]`, `[label](Note.md)`; resolves to HTML anchors for preview.
- `BacklinkService` — scans note bodies (full content, cached) for incoming links to the current note.

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

No automatic schema migration: SQL schema changes need a documented manual step.

Preferences key `storage_type`: `sqlite` vs `filesystem` (restart to switch).

## Git (filesystem vault)

When the vault directory is inside a Git repository, `GitService` drives the status-bar Git UI and changes dialog (stage/unstage, commit, pull/push). Attachments (PDF, images) appear in change lists alongside `.md` notes.

## Plugins

Core does not reference plugin classes by name. `PluginLoader` scans `plugins/` under the app base directory. Sample plugins compile with **`--release 21`** (`scripts/build-plugins.sh`). Use `AbstractPlugin` + stable ids from `PluginIds` for palette commands.

## Events

- Domain events (`NoteEvents`, `FolderEvents`, …) for refresh coordination.
- `SystemActionEvent` — toolbar/menu/command palette actions; `MainController` dispatches via `EnumMap` (avoid republishing mutating actions from handlers).

Keep subscriptions typed; unsubscribe on teardown; no refresh loops.

## Conventions

- JDK 21, no wildcard imports.
- `LoggerConfig.getLogger(Class)` for logging.
- Long I/O on background threads / `Task`; UI updates on `Platform.runLater`.
- i18n: `com/example/jylos/i18n/messages*.properties` (EN + ES).
