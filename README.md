# Jylos

<div align="center">
  <a href="README.es.md">Español</a> |
  <strong>English</strong>
</div>

<div align="center">
  <img src="resources/images/banner.png" alt="Jylos Banner" style="width: 100%; max-width: 100%;">
</div>

<div align="center">

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Version](https://img.shields.io/badge/version-1.0.0-success.svg)](changelog.md)
[![Java](https://img.shields.io/badge/Java-21+-orange.svg)](https://www.oracle.com/java/)
[![JavaFX](https://img.shields.io/badge/JavaFX-23-blue.svg)](https://openjfx.io/)
[![SQLite](https://img.shields.io/badge/SQLite-3-lightgrey.svg)](https://www.sqlite.org/)
[![Maven](https://img.shields.io/badge/Maven-3.9+-red.svg)](https://maven.apache.org/)
[![Platform](https://img.shields.io/badge/Platform-Windows%20%7C%20macOS%20%7C%20Linux-lightgrey.svg)]()

</div>

<div align="center">
  <strong>Local-first desktop notes with Markdown preview, wiki-links, an interactive knowledge graph, plugins, themes, and SQLite or Markdown vault storage.</strong>
</div>

## Table of Contents

- [Overview](#overview)
- [Features](#features)
- [Screenshots](#screenshots)
- [Technology Stack](#technology-stack)
- [Prerequisites](#prerequisites)
- [Quick Start](#quick-start)
- [Scripts and Commands (All OS)](#scripts-and-commands-all-os)
- [Project Structure](#project-structure)
- [Configuration](#configuration)
- [Documentation](#documentation)
- [Troubleshooting](#troubleshooting)
- [Contributing](#contributing)
- [License](#license)

## Overview

Jylos is a Java 21 + JavaFX 23 desktop application inspired by Obsidian-like workflows:

- Fast note writing/editing with live Markdown preview (GFM, KaTeX math, emoji)
- Folder hierarchy + tags + favorites + recent + trash
- **Obsidian-compatible internal links** (`[[wiki-links]]`, `[label](note.md)`) with click-to-open in preview
- **Knowledge graph** (global vault view or local neighbourhood around the open note)
- **Backlinks** panel listing notes that link to the current note
- Command palette (`Ctrl+P`) and quick switcher (`Ctrl+O`)
- External plugins (JARs in `jylos/plugins/`, built from `plugins-source/`) and themes (`themes/` → `jylos/themes/`)
- Storage: **SQLite** (default) or **filesystem Markdown vault** (`.md` + YAML frontmatter; optional **Git** menu for commit/stage/sync)

## Features

### Core

- Create, edit, save, delete, and restore notes
- Hierarchical folders and subfolders
- Tags with assignment/removal workflows (SQLite and vault modes)
- Favorites and recent notes
- Trash with restore for notes and nested folders
- **Full-text search** across note titles and bodies (with navigation from results)
- Sorting and list/grid note views (title, preview lines, dates)

### Editor & Preview

- Markdown rendering with GFM tables, autolinks, strikethrough
- Live preview and split / editor-only / preview-only modes
- Syntax highlighting for fenced code blocks (highlight.js)
- **KaTeX** for `$…$`, `$$…$$`, and LaTeX delimiters (offline assets bundled in the JAR)
- Emoji in preview via rasterized glyphs (reliable in the JavaFX WebView)
- **Wiki-link resolution** shared with the graph and backlinks (`WikiLinkResolver`)

### Knowledge graph

- Full-screen overlay: **View → Graph View**, toolbar button, or **`Ctrl+G`** / command palette
- **Global graph**: all notes and resolved wiki-link edges; optional **tag nodes** and note→tag edges
- **Local graph**: current note plus neighbours within a configurable hop depth
- Native **JavaFX Canvas** force simulation (Barnes–Hut repulsion, link springs, alpha cooling — idle graph uses no CPU)
- Zoom/pan, drag nodes, hover highlights neighbours, **click a note node to open it**
- Settings panel: repulsion, link force/distance, center gravity, orphans/unresolved links, arrows, color-by-folder, label/size/line tuning

### Vault, Git & attachments (filesystem mode)

- Markdown vault with optional folder layout; non-`.md` files (PDF, images) open in built-in viewers
- **Git** integration when the vault is a repository: status, stage/unstage, commit, sync (see **Git** menu)
- IDE-style staged/unstaged changes dialog (notes and attachments)

### Productivity

- **Backlinks** in the right info panel (incoming wiki-links and internal Markdown links)
- **Daily note** and **new note from template** (`{{title}}`, `{{date}}`, …)
- Per-note and **bulk vault export** to HTML/PDF
- Import/export of individual notes

### UI/UX

- Light, dark, and **system** themes (OS theme polling when “System” is selected) + external CSS themes
- Sample external theme: Retro Phosphor (`themes/retro-phosphor/`)
- Configurable sidebar/editor button presentation (text/icons/auto)
- Centered sidebar navigation (folders, tags, recent, favorites, trash)
- UI strings in **English** and **Spanish** (`i18n/messages*.properties`)
- Toolbar uses **Feather** icons via Ikonli (`fth-*` in FXML — not separate image files)

### Extensibility

- External plugin JARs loaded from `jylos/plugins/` (see `scripts/build-plugins.sh`; bytecode **Java 21**)
- Plugin manager UI with stable command IDs and safe load/disable lifecycle
- Built-in **Mermaid** diagram support in preview (plugin source under `plugins-source/`)
- Theme catalog with external theme discovery and safe fallback

## Screenshots

<div align="center">
  <img src="resources/images/interfaz-1.png" alt="" style="width: 100%; max-width: 100%; margin-bottom: 1.5em; display: block;">
  <img src="resources/images/interfaz-2.png" alt="" style="width: 100%; max-width: 100%; margin-bottom: 1.5em; display: block;">
  <img src="resources/images/interfaz-3.png" alt="" style="width: 100%; max-width: 100%; margin-bottom: 1.5em; display: block;">
  <img src="resources/images/interfaz-4.png" alt="" style="width: 100%; max-width: 100%; margin-bottom: 1.5em; display: block;">
  <img src="resources/images/interfaz-5.png" alt="" style="width: 100%; max-width: 100%; margin-bottom: 1.5em; display: block;">
  <img src="resources/images/interfaz-6.png" alt="" style="width: 100%; max-width: 100%; margin-bottom: 1.5em; display: block;">
  <img src="resources/images/interfaz-7.png" alt="" style="width: 100%; max-width: 100%; margin-bottom: 1.5em; display: block;">
  <img src="resources/images/interfaz-8.png" alt="" style="width: 100%; max-width: 100%; margin-bottom: 1.5em; display: block;">
  <img src="resources/images/interfaz-9.png" alt="" style="width: 100%; max-width: 100%; margin-bottom: 1.5em; display: block;">
  <img src="resources/images/interfaz-10.png" alt="" style="width: 100%; max-width: 100%; margin-bottom: 1.5em; display: block;">
  <img src="resources/images/interfaz-11.png" alt="" style="width: 100%; max-width: 100%; margin-bottom: 1.5em; display: block;">
  <img src="resources/images/interfaz-12.png" alt="" style="width: 100%; max-width: 100%; margin-bottom: 1.5em; display: block;">
  <img src="resources/images/interfaz-13.png" alt="" style="width: 100%; max-width: 100%; margin-bottom: 1.5em; display: block;">
  <img src="resources/images/interfaz-14.png" alt="" style="width: 100%; max-width: 100%; margin-bottom: 1.5em; display: block;">
  <img src="resources/images/interfaz-15.png" alt="" style="width: 100%; max-width: 100%; display: block;">
</div>

## Technology Stack

- Java 21
- JavaFX 23
- Maven 3.9+
- SQLite JDBC
- CommonMark
- Ikonli (Feather icons)
- JUnit 5 + H2 (tests)

## Prerequisites

1. Java JDK 21
2. Maven 3.9+

Check installation:

```bash
java -version
mvn -version
```

## Quick Start

### 1) Clone

```bash
git clone https://github.com/RGiskard7/jylos.git
cd jylos
```

### 2) Build

From the repository root (produces `jylos/target/jylos-1.0.0-uber.jar`):

```bash
./scripts/build_all.sh
```

```powershell
.\scripts\build_all.ps1
```

Equivalent Maven command:

```bash
mvn -f jylos/pom.xml clean package -DskipTests
```

### 3) Run

Use a launcher (sets JavaFX `--module-path`). Requires the uber-JAR from step 2:

```bash
./scripts/launch-jylos.sh
```

```powershell
.\scripts\launch-jylos.bat
# or
.\scripts\launch-jylos.ps1
```

`run_all.*` is an alternative dev runner. Plain `java -jar` without module-path often fails on JavaFX.

## Scripts and Commands (All OS)

All commands assume the **repository root** (the folder that contains `jylos/` and `scripts/`).

### Build / Run Matrix

| Purpose | Linux/macOS | Windows PowerShell | Windows CMD |
|---|---|---|---|
| Build app | `./scripts/build_all.sh` | `.\scripts\build_all.ps1` | N/A |
| Run app (dev runner) | `./scripts/run_all.sh` | `.\scripts\run_all.ps1` | N/A |
| Run app (launcher, recommended) | `./scripts/launch-jylos.sh` | `.\scripts\launch-jylos.ps1` | `.\scripts\launch-jylos.bat` |

### Tests and Quality Gates

```bash
mvn -f jylos/pom.xml test
mvn -f jylos/pom.xml clean test
```

```bash
./scripts/smoke-phase-gate.sh
./scripts/hardening-storage-matrix.sh
```

```powershell
.\scripts\smoke-phase-gate.ps1
.\scripts\hardening-storage-matrix.ps1
```

### Plugins (external JARs)

```bash
./scripts/build-plugins.sh
./scripts/build-plugins.sh --clean
```

```powershell
.\scripts\build-plugins.ps1
.\scripts\build-plugins.ps1 -Clean
```

### Themes (external)

```bash
./scripts/build-themes.sh
./scripts/build-themes.sh --clean
./scripts/build-themes.sh --appdata
```

```powershell
.\scripts\build-themes.ps1
.\scripts\build-themes.ps1 -Clean
.\scripts\build-themes.ps1 -AppData
```

### Packaging (native installers)

**Requirements:** full **JDK 21+** (not JRE) with `jpackage` on `PATH`. Run from the **repository root**.

Each `package-*` script builds the uber-JAR, optionally runs `build-plugins.sh`, then invokes `jpackage`. Main class: `com.example.jylos.Launcher`.

| Platform | Command | Typical output |
|---|---|---|
| macOS (DMG) | `./scripts/package-macos.sh` | `jylos/target/installers/Jylos-1.0.0.dmg` |
| Linux (deb/rpm) | `./scripts/package-linux.sh` | `jylos/target/installers/` |
| Windows | `.\scripts\package-windows.ps1` | `Jylos\target\installers\` |

```bash
./scripts/package-macos.sh
./scripts/package-linux.sh
```

```powershell
.\scripts\package-windows.ps1
```

Icons: window + About dialog use `jylos/src/main/resources/icons/app-icon.png`; installers use `icon.{icns,ico,png}` (see `app.properties` and [jylos/src/main/resources/icons/README.md](jylos/src/main/resources/icons/README.md)). Details: [doc/PACKAGING.md](doc/PACKAGING.md).

### Maven development run

Prefer launchers for JavaFX. If using Maven directly:

```bash
mvn -f jylos/pom.xml javafx:run
```

Or:

```bash
mvn -f jylos/pom.xml clean compile exec:java -Dexec.mainClass="com.example.jylos.Launcher"
```

## Project Structure

Repository root (contains the Maven module `jylos/` and `scripts/`):

```text
<repo-root>/
├── jylos/                              # Maven module (app)
│   ├── pom.xml
│   ├── src/main/java/com/example/jylos/
│   │   ├── config/                     # AppContext, LoggerConfig
│   │   ├── data/                       # models; DAOs (sqlite/, filesystem/)
│   │   ├── event/                      # EventBus + domain events
│   │   ├── exceptions/
│   │   ├── git/                        # GitService (vault repositories)
│   │   ├── graph/                      # GraphBuilder, GraphData, nodes/edges
│   │   ├── plugin/                     # loader, manager, registries; mermaid/
│   │   ├── service/                    # Note, Folder, Tag, Backlink, backup, …
│   │   ├── ui/
│   │   │   ├── controller/             # Main, Editor, Sidebar, Graph, Toolbar, …
│   │   │   ├── components/             # CommandPalette, QuickSwitcher, FileViewer
│   │   │   └── graph/                  # GraphCanvas (force-directed renderer)
│   │   └── util/                       # WikiLinkResolver, MarkdownPreview, NoteExporter
│   ├── src/main/resources/
│   │   ├── app.properties              # app name, icon paths, window title
│   │   ├── icons/                      # app-icon.png + icon.{ico,icns,png}
│   │   └── com/example/jylos/
│   │       ├── i18n/                   # messages.properties, messages_en/es
│   │       ├── ui/css/                 # modern-theme.css, dark-theme.css
│   │       ├── ui/view/                # FXML (MainView, EditorView, GraphView, …)
│   │       └── ui/preview/             # KaTeX, highlight.js (bundled offline)
│   ├── src/test/java/com/example/jylos/
│   ├── plugins/                        # runtime plugin JARs (often gitignored)
│   ├── themes/                         # installed external themes
│   ├── data/                           # runtime DB or vault (gitignored)
│   ├── logs/
│   └── backups/
├── plugins-source/                     # plugin sources → build-plugins → jylos/plugins/
├── themes/                             # theme sources → build-themes → jylos/themes/
├── resources/images/                   # README banner and screenshots
├── scripts/                            # build, launch, package, smoke tests
├── doc/                                # technical docs (see doc/README.md)
├── AGENTS.md
├── changelog.md
├── README.md
└── README.es.md
```

Not part of the app: `replica-grafo/` (optional Typst/graph experiment; see [doc/README.md](doc/README.md)).

## Configuration

### Storage

- **SQLite** (default): `jylos/data/database.db`
- **Filesystem vault**: folder of `.md` notes with YAML frontmatter; switch in **Tools → Switch storage** (restart required)
- Other runtime dirs (auto-created under `jylos/`): `logs/`, `backups/`, `plugins/`, `themes/`

### App icons

| Asset | Path | Used for |
|-------|------|----------|
| In-app window + About | `jylos/src/main/resources/icons/app-icon.png` | `app.icon.window` in `app.properties` |
| Windows installer | `icons/icon.ico` | `app.icon.windows` |
| macOS installer | `icons/icon.icns` | `app.icon.macos` |
| Linux installer | `icons/icon.png` | `app.icon.linux` |

Toolbar/sidebar icons are **Feather** glyphs (`fth-*` in FXML), not files in `icons/`.

### Themes

Source packs live in `themes/<id>/` (`theme.properties` + `theme.css`). Install with `./scripts/build-themes.sh` (copies to `jylos/themes/`).

### Plugins

- Build: `./scripts/build-plugins.sh` → `jylos/plugins/*.jar` (compile target **Java 21**)
- Enable/disable in **Tools → Manage plugins**

## Documentation

- [doc/README.md](doc/README.md) — index
- [doc/BUILD.md](doc/BUILD.md)
- [doc/LAUNCH_APP.md](doc/LAUNCH_APP.md)
- [doc/ARCHITECTURE.md](doc/ARCHITECTURE.md)
- [doc/PLUGINS.md](doc/PLUGINS.md)
- [doc/PACKAGING.md](doc/PACKAGING.md)
- [doc/EVENT_BUS_CONTRACT.md](doc/EVENT_BUS_CONTRACT.md)
- [AGENTS.md](AGENTS.md)
- [changelog.md](changelog.md)

## Troubleshooting

### JavaFX runtime errors

Use `launch-jylos.*` (module-path included). Run `build_all` first if the JAR is missing.

### JAR not found

```bash
./scripts/build_all.sh
```

### Maven/Java Missing

Ensure both are available in `PATH`:

```bash
java -version
mvn -version
```

### JavaFX Parent-POM Warnings

Warnings such as `Failed to build parent project for org.openjfx:javafx-*` are known and non-blocking.

## Contributing

- Keep changes focused and incremental.
- Run tests before opening PR.
- Preserve SQLite/FileSystem and plugin compatibility.
- Update documentation when behavior changes.

## License

[MIT License](LICENSE) — Copyright © 2025–2026 **Eduardo Díaz Sánchez**.

You may use, modify, and distribute this software under the MIT terms; keep the copyright and license notice in copies or substantial portions. Contact: ed.dzsn@protonmail.com
