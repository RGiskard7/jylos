# Architecture

Jylos is a single-process JavaFX desktop monolith. Its UI, domain services, storage adapters, plugin runtime, graph renderer, and knowledge utilities all live in the same JVM and communicate in-process.

Primary source references:
- `docs/ARCHITECTURE.md`
- `docs/ARCHITECTURE_GUIDELINES.md`
- `AGENTS.md`
- `jylos/pom.xml`

## Entry points

The app starts through `com.example.jylos.Launcher`, which delegates to the JavaFX `Application` class `com.example.jylos.Main`.

That separation exists so the same launch target works across:
- IDE runs,
- `exec:java`,
- the packaged application,
- and the shaded/uber JAR.

## Layering

The documented flow is:

`ui/` → `service/` → `data/dao/`

The architecture doc maps the major packages as follows:
- `ui.controller` — shell coordinator, feature controllers, and support classes
- `ui.components` — reusable UI widgets and overlays
- `ui.graph` — the native force-directed graph canvas
- `graph` — graph data construction
- `insights` — graph-health and knowledge insights services
- `git` — Git integration for filesystem vault mode
- `search` — advanced search query parsing and evaluation
- `service` — note, folder, tag, backlink, encryption, and backup rules
- `data.dao` — SQLite and filesystem persistence implementations
- `data.models` — note/folder/tag models
- `event` — typed in-process event bus
- `plugin` — plugin loading and lifecycle
- `util` — Markdown processing, wiki-link resolution, export, and Kanban helpers
- `workspace` — workspace capture and restoration
- `config` — logger setup

## UI composition

The main window is assembled as a border layout with a central split view and overlays:
- toolbar at the top
- a central `StackPane` that hosts the normal editor shell plus graph/Kanban overlays
- a collapsible right panel
- a status bar at the bottom

The main editor area combines:
- tabbed note editing,
- RichTextFX syntax highlighting,
- a WebView preview,
- and `[[` autocomplete.

The right panel is used for note metadata, backlinks, and plugin side panels.

## Feature boundaries to preserve

`AGENTS.md` and `docs/ARCHITECTURE.md` both stress the same rule: keep `MainController` thin.

Good change pattern:
- create a focused `*Controller` or `*Support` class,
- give it a `wire(...)` method,
- keep feature-specific state and logic there,
- let `MainController` only coordinate the shell and high-level note flow.

This matters because the app already has many cross-cutting features: graph overlay, Kanban overlay, Git panel, privacy unlock flows, backlinks, status bar updates, and focus mode.

## Storage and runtime

The app supports two storage backends:
- **SQLite** — the default persistent store
- **filesystem vault** — Markdown plus YAML frontmatter

The architecture docs note that switching between two filesystem vaults can be done in-process, while switching between SQLite and filesystem mode still requires restart.

Runtime directories resolved from the app base directory include:
- `data/`
- `logs/`
- `plugins/`
- `themes/`
- `snippets/`
- `backups/`

## Graph, backlinks, and Kanban

The graph and backlinks use the same wiki-link resolution rules as preview rendering. That keeps navigation, backlinks, and visualization consistent.

The Kanban board is stored as a normal note whose Markdown body is parsed and serialized by `KanbanModel`. The board overlay edits that note rather than using a special database schema.

## Plugins

Plugins are loaded from JAR files in the app’s plugin directory. The core app avoids hardcoded plugin class references. The plugin docs show extension points for:
- palette commands,
- menu items,
- side panels,
- preview enhancers,
- toolbar buttons,
- editor hooks,
- and note-open / refresh requests back to the shell.

## What to watch out for when changing architecture

- Do not push feature bodies into `MainController`.
- Keep storage migrations explicit; the docs note that `SQLiteDB.initDatabase()` only performs limited idempotent schema adjustments.
- Keep event-bus use typed and bounded to refresh-style coordination.
- Preserve the current preview/wiki-link resolution rules so graph, backlinks, and rendered links stay aligned.
- Use the launch scripts or shaded JAR path when working on JavaFX runtime behavior, since `javafx.web` is required for the preview.
