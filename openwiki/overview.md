---
type: Repository Overview
title: Repository overview
description: High-level map of Jylos domains, runtime shell, storage backends, and source entry points.
tags: [repository, overview, architecture]
---

# Repository overview

Jylos is a Java 21 / JavaFX 23 desktop application for local-first knowledge management. The codebase is organized around a single desktop client with two storage backends:

- **SQLite mode** for app-managed persistence.
- **Filesystem vault mode** for plain Markdown notes, folders, frontmatter, and attachments.

The product is intentionally offline-first and single-user. There is no REST backend; instead, the app coordinates local services, DAOs, and UI controllers through an event bus and a plugin system. The workspace subsystem stores UI state separately from note content so users can return to a familiar layout and open-note set.

## Major domains

### App startup and runtime shell

`Main` bootstraps directories, backup creation, database initialization, locale selection, and the JavaFX scene graph. `Launcher` exists so JavaFX can be launched correctly from packaged builds and IDEs.

### Content model and storage

Core concepts are notes, folders, tags, backlinks, trash, favorites, pinned notes, private notes, and workspace state. Those concepts are stored either in SQLite tables or mirrored from a filesystem vault.

### UI and user workflows

`MainController` coordinates the primary shell, while smaller controllers and support classes own focused workflows like editing, sidebar navigation, graph rendering, workspaces, Git sync, plugins, and update notifications.

### Extensibility and integrations

Plugins can contribute menu items, side panels, preview enhancements, editor hooks, and toolbar buttons. The app also integrates with Git for vault workflows, GitHub Releases for update checks, and theme/snippet packaging scripts.

## Key source entry points

- `jylos/src/main/java/com/example/jylos/Main.java`
- `jylos/src/main/java/com/example/jylos/Launcher.java`
- `jylos/src/main/java/com/example/jylos/AppConfig.java`
- `jylos/src/main/java/com/example/jylos/ui/controller/MainController.java`
- `jylos/src/main/java/com/example/jylos/data/database/SQLiteDB.java`
- `jylos/src/main/java/com/example/jylos/workspace/WorkspaceService.java`
- `jylos/src/main/java/com/example/jylos/plugin/PluginManager.java`
- `jylos/src/main/java/com/example/jylos/service/UpdateChecker.java`

## What changed recently

Recent commits emphasize packaging stability, CI workflows, UI refinements, update checking, workspace management, graph/canvas features, and filesystem vault performance. When modifying behavior, prefer the most recent source and tests over older doc patterns.
