# AGENTS.md — Jylos

Guide for contributors and automated agents. Human overview: [README.md](README.md).

<!-- OPENWIKI:START -->

## OpenWiki

This repository uses OpenWiki for recurring code documentation. Start with `openwiki/quickstart.md`, then follow its links to architecture, workflows, domain concepts, operations, integrations, testing guidance, and source maps.

The scheduled OpenWiki GitHub Actions workflow refreshes the repository wiki. Do not hand-edit generated OpenWiki pages unless explicitly asked; prefer updating source code/docs and letting OpenWiki regenerate.

<!-- OPENWIKI:END -->

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

Uber-JAR: `jylos/target/jylos-<version>-uber.jar`. Use `launch-*` scripts for JavaFX modules.

## Layout

- Module root: `jylos/`
- UI: `ui/controller/` (Main, Editor, Sidebar, NotesList, Graph, Toolbar + shell helpers), `ui/graph/GraphCanvas.java`, FXML in `ui/view/` (incl. `GraphView.fxml`)
- Graph model: `graph/` (`GraphBuilder` uses `WikiLinkResolver` for edges)
- Git (vault): `git/GitService.java`
- DAOs: `data/dao/sqlite/`, `data/dao/filesystem/`
- Runtime (gitignored, cwd usually `jylos/`): `data/`, `logs/`, `backups/`, `plugins/`, `themes/`, `snippets/`
- Icons: `src/main/resources/icons/` (`app-icon.png` for window/About; `icon.*` for jpackage)
- Plugin sources: `plugins-source/` → build to `jylos/plugins/`
- Theme sources: `themes/` → `scripts/build-themes.sh` → `jylos/themes/`

## Code rules

- JDK 21, package `com.example.jylos.*`
- No wildcard imports
- `LoggerConfig.getLogger(Class)` — no `System.out` for app logs
- Persistence via services/DAOs only
- Commits: `feat:`, `fix:`, `chore:`, `refactor:`
- Changelog: keep `## [Unreleased]` undated at the top. When preparing a release,
  move bullets into one `## [x.y.z] - YYYY-MM-DD` section using the release date;
  do not create duplicate sections for the same version. Put `Fixes #123` in PR
  descriptions, not ordinary changelog bullets.

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

**Docs:** keep [README.md](README.md), [README.es.md](README.es.md), [docs/](docs/), especially [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) and [docs/ARCHITECTURE_GUIDELINES.md](docs/ARCHITECTURE_GUIDELINES.md), and this file aligned with code — no outdated paths (e.g. plugins live under `jylos/plugins/`, not repo-root `plugins/` only).

Canonical technical docs live in English under `docs/*.md`. Spanish translations live
under `docs/es/*.md` with the same filename. When changing a doc in `docs/`, update its
Spanish counterpart in `docs/es/` in the same change, and keep language cross-links at
the top of both files. `README.md` must link English docs; `README.es.md` must link
Spanish docs. Exception: `openwiki/`, `AGENTS.md`, and `CLAUDE.md` are managed by the
OpenWiki workflow and do not need manual translation mirroring in `docs/es/`.

## Tests

- **Un test no vale hasta haberlo visto fallar.** Código nuevo: falla porque no existe la
  implementación. Código existente: rompe el código a propósito, comprueba el rojo,
  restaura. Al informar, incluye el mensaje de fallo real. Sin ese rojo, no afirmes que un
  test protege nada.
- **Nunca afirmes solo una cota superior.** `assertTrue(n < 300)` pasa con `n == 0`, es
  decir, con la funcionalidad completamente rota. Acota por los dos lados y pregúntate qué
  valores degenerados (0, vacío, null) satisfacen la aserción.
- **Comprueba efectos observables, no llamadas.** Que se invocara un método no demuestra
  que hiciera algo. Verifica el resultado: el fichero en disco, el valor devuelto, el
  estado final.
- **Los tests que leen el código fuente como texto no son cobertura.** Sirven para vigilar
  invariantes de arquitectura, no para demostrar comportamiento. No los presentes como red
  de seguridad.
- **"Suite en verde" no es una garantía.** Al reportar, di qué has verificado y cómo. Si no
  has roto el código para comprobarlo, dilo explícitamente.
- **TDD solo para código nuevo.** Sobre código existente se hace caracterización: los tests
  pasan en verde desde el primer run y el anti-fraude es el sabotaje posterior.

Procedimiento y protocolo de sabotaje detallados: `docs/TESTING.md`.