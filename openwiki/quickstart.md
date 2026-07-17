---
type: Contributor Guide
title: OpenWiki quickstart
description: First-stop guide for contributors and coding agents working inside the Jylos repository.
tags: [quickstart, contributors, agents]
---

# OpenWiki quickstart

Jylos is a local-first desktop knowledge-management app built with Java 21, JavaFX 23, Maven, and SQLite, with an alternative filesystem Markdown vault mode. It combines note editing and preview, wiki-style links and backlinks, a knowledge graph, Kanban notes, canvas files, plugins, themes, workspace management, Git-aware vault workflows, and packaging/build scripts for Windows, macOS, and Linux.

Start here if you are new to the repository or you are a coding agent preparing a change. The main app entry point is `jylos/src/main/java/com/example/jylos/Main.java`; most runtime behavior is coordinated by `jylos/src/main/java/com/example/jylos/ui/controller/MainController.java`.

## What to read first

- [Repository overview](overview.md) — where the app starts, how modules fit together, and what the core user experience is.
- [Storage and data model](storage.md) — SQLite vs filesystem vaults, note/folder/tag persistence, and schema/runtime rules.
- [UI, workflows, and extension points](ui-and-workflows.md) — major controllers, note workflows, search, workspaces, plugins, graph, and packaging flows.

## Useful existing docs

The repository already has detailed canonical docs under `docs/` and `README.md` / `README.es.md`. OpenWiki is a synthesis layer that points you to the right source files and explains how the pieces relate.

- User-facing overview: [README.md](../README.md)
- Technical docs index: [docs/README.md](../docs/README.md)
- Architecture: [docs/ARCHITECTURE.md](../docs/ARCHITECTURE.md)
- Architecture guidelines: [docs/ARCHITECTURE_GUIDELINES.md](../docs/ARCHITECTURE_GUIDELINES.md)
- Build and run: [docs/BUILD.md](../docs/BUILD.md)
- Testing: [docs/TESTING.md](../docs/TESTING.md)
- Plugins: [docs/PLUGINS.md](../docs/PLUGINS.md)
- Packaging: [docs/PACKAGING.md](../docs/PACKAGING.md)
- CI/CD: [docs/CICD.md](../docs/CICD.md)
- Git vault workflows: [docs/GIT.md](../docs/GIT.md)
- Search: [docs/SEARCH.md](../docs/SEARCH.md)
- Workspaces: [docs/WORKSPACES.md](../docs/WORKSPACES.md)
- Event bus contract: [docs/EVENT_BUS_CONTRACT.md](../docs/EVENT_BUS_CONTRACT.md)
- Internationalization: [docs/I18N.md](../docs/I18N.md)
- Graph: [docs/GRAPH.md](../docs/GRAPH.md)
- Launching the app: [docs/LAUNCH_APP.md](../docs/LAUNCH_APP.md)

## Repository shape at a glance

- `jylos/` — the application module and primary source tree
- `scripts/` — build, test, launch, and packaging scripts
- `plugins-source/` — source workspace for bundled/external plugins
- `themes/` — external theme packs and theme build inputs
- `resources/` and `docs/assets/` — images and documentation assets
- `docs/` — canonical technical documentation, including Spanish translations in `docs/es/`
- `openwiki/` — this synthesized map for humans and agents

## Change guidance

- For UI changes, start with `MainController` and the relevant `ui/controller/*Support` or `*Controller` class instead of growing `MainController` with feature logic.
- For storage changes, check both DAO families: `data/dao/sqlite/` and `data/dao/filesystem/`.
- For plugin-related changes, inspect `plugin/`, `ui/components/PluginManagerDialog.java`, and the plugin docs before editing behavior.
- For workflows that affect search, workspaces, Git integration, graph, or packaging, follow the links above and verify the corresponding tests in `jylos/src/test/java`.

## Source map

- App startup: `jylos/src/main/java/com/example/jylos/Main.java`, `Launcher.java`, `AppConfig.java`
- UI shell: `jylos/src/main/java/com/example/jylos/ui/controller/MainController.java`
- Storage: `jylos/src/main/java/com/example/jylos/data/database/SQLiteDB.java`
- Filesystem vaults: `jylos/src/main/java/com/example/jylos/data/dao/filesystem/NoteDAOFileSystem.java`
- Workspace persistence: `jylos/src/main/java/com/example/jylos/workspace/WorkspaceService.java`
- Update checks: `jylos/src/main/java/com/example/jylos/service/UpdateChecker.java`
