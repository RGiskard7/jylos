# AGENTS.md — Jylos

Guide for contributors and automated agents. Human overview: [README.md](README.md).

## Project

- Desktop notes app: folders, tags, trash, Markdown preview.
- Stack: Java 21, JavaFX 23 (including `javafx.web`), Maven, SQLite or filesystem Markdown vault.
- Offline, single-user, no REST backend.

## Commands

```bash
./scripts/build_all.sh
./scripts/launch-jylos.sh
mvn -f jylos/pom.xml test
```

```powershell
.\scripts\build_all.ps1
.\scripts\launch-jylos.bat
.\scripts\launch-jylos.ps1
```

```bash
mvn -f jylos/pom.xml clean compile exec:java -Dexec.mainClass="com.example.jylos.Launcher"
```

Uber-JAR: `jylos/target/jylos-2.2.0-uber.jar`. Use `launch-*` scripts for JavaFX modules.

## Layout

- Module root: `jylos/`
- UI: `ui/controller/` (Main, Editor, Sidebar, NotesList, Graph, Toolbar + shell helpers), `ui/graph/GraphCanvas.java`, FXML in `ui/view/` (incl. `GraphView.fxml`)
- Graph model: `graph/` (`GraphBuilder` uses `WikiLinkResolver` for edges)
- Git (vault): `git/GitService.java`
- DAOs: `data/dao/sqlite/`, `data/dao/filesystem/`
- Runtime (gitignored, cwd usually `jylos/`): `data/`, `logs/`, `backups/`, `plugins/`, `themes/`
- Icons: `src/main/resources/icons/` (`app-icon.png` for window/About; `icon.*` for jpackage)
- Plugin sources: `plugins-source/` → build to `jylos/plugins/`
- Theme sources: `themes/` → `scripts/build-themes.sh` → `jylos/themes/`

## Code rules

- JDK 21, package `com.example.jylos.*`
- No wildcard imports
- `LoggerConfig.getLogger(Class)` — no `System.out` for app logs
- Persistence via services/DAOs only
- Commits: `feat:`, `fix:`, `chore:`, `refactor:`

## UI feature pattern (keep `MainController` thin)

`MainController` is the FXML shell coordinator — do **not** grow it with feature logic.
Each self-contained feature lives in its own `ui/controller/*Controller` or `*Support`
class that:

- exposes a `wire(...)` method taking the FXML nodes it needs plus small callbacks
  (`Function<String,String> i18n`, `Consumer<String> status`, `Supplier<Scene>`, …);
- owns that feature's state and logic;
- is called from thin `MainController` handlers (FXML-bound methods just delegate).

Examples: `GitController` (status-bar Git + dialogs), `PrivacySupport` (master-password
prompts for encrypted notes), `FocusModeSupport` (writing mode), `OverlaySupport`
(graph/Kanban center-stack overlays), `StatusBarSupport` (word/char counts + storage
label), `BacklinksSupport` (right-panel backlinks). New features must follow this — no
new feature bodies inside `MainController`. What remains in `MainController` is its
legitimate coordinator core (note open/save/close/tabs/navigation flow); do not
fragment that across helpers just to shrink line count.

## Gotchas

- JavaFX preview needs `javafx.web` on module-path when not using uber-JAR launch path.
- `SQLiteDB.initDatabase()` performs only limited/idempotent schema adjustments (e.g. guarded `ALTER TABLE` additions); non-trivial SQL changes still need an explicit migration plan.
- Plugins: no hardcoded plugin classes in core; JARs in `plugins/` under app base dir.
- Warnings on `org.openjfx:javafx-*` parent POM during Maven build are harmless.

## Common tasks

**New `Note` field:** model → `SQLiteDB` / both DAO families → UI → tests.

**New FXML view:** `ui/view/*.fxml` + controller + wire in `MainController`.

**Docs:** keep [README.md](README.md), [doc/](doc/), especially [doc/ARCHITECTURE.md](doc/ARCHITECTURE.md) and [doc/ARCHITECTURE_GUIDELINES.md](doc/ARCHITECTURE_GUIDELINES.md), and this file aligned with code — no outdated paths (e.g. plugins live under `jylos/plugins/`, not repo-root `plugins/` only).
