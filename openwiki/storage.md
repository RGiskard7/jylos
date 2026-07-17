---
type: Storage Architecture
title: Storage and data model
description: Explains SQLite and filesystem vault persistence, shared domain concepts, and storage rules.
tags: [storage, sqlite, filesystem, data-model]
---

# Storage and data model

Jylos supports two persistence strategies that share the same user-facing concepts:

1. **SQLite-backed storage** — the default application database and schema.
2. **Filesystem Markdown vault storage** — notes as `.md` files with YAML frontmatter, plus attachments and optional Git workflows.

These two modes exist because the app serves both app-managed and vault-managed workflows. The codebase is structured so UI actions talk to services, and services talk to the selected DAO family.

## Core model concepts

The main persisted entities are:

- **Notes** — Markdown content plus metadata such as title, dates, favorite/pinned state, deleted state, privacy, author, source URL, and todo-specific fields.
- **Folders** — hierarchical containers for notes and other folders.
- **Tags** — note labels and the many-to-many note/tag relationship.
- **Workspaces** — saved UI state snapshots that restore open notes, layout, view mode, and storage mode.
- **Attachments** — viewable files that the filesystem vault treats alongside notes.

The app keeps content state and UI state separate on purpose: workspace snapshots do not replace note storage, they just remember what the user had open and how the shell was arranged.

## SQLite mode

`jylos/src/main/java/com/example/jylos/data/database/SQLiteDB.java` defines the schema and initializes or migrates the database on startup.

Important details:

- Tables include `notes`, `folders`, `tags`, and `tagsNotes`.
- The database uses foreign keys and indexes for folder trees, deleted state, favorites, modified dates, and tag joins.
- Startup code enables SQLite pragmas such as foreign keys, WAL, and busy timeout.
- The schema file is not separate; initialization logic lives in code, so database changes should be reviewed carefully and tested against migration behavior.

The SQLite DAO implementation for notes is `jylos/src/main/java/com/example/jylos/data/dao/sqlite/NoteDAOSQLite.java`. It is synchronized on the shared connection and handles create, read, update, soft delete, restore, hard delete, and tag relation operations.

## Filesystem vault mode

`jylos/src/main/java/com/example/jylos/data/dao/filesystem/NoteDAOFileSystem.java` implements note storage on top of a directory tree.

What it does:

- Stores notes as Markdown files with YAML frontmatter.
- Builds caches for fast list and preview access.
- Supports deferred/background content loading so large or cloud-backed vaults do not block startup.
- Treats attachments as vault items when they are viewable file types.
- Uses a separate metadata store to preserve additional document metadata.

This mode is sensitive to I/O behavior and path handling. The code uses locks and cache invalidation to avoid concurrent corruption during refreshes and background loading.

## Workspace persistence

`jylos/src/main/java/com/example/jylos/workspace/WorkspaceService.java` stores named workspace snapshots. It is separate from note persistence because it captures UI state rather than content state.

A workspace snapshot includes:

- open note ids
- active note id
- view mode
- sidebar visibility
- focus mode
- split pane positions
- storage mode

Workspace names are sanitized and are treated case-insensitively when saving.

## What to watch out for

- When changing a note field, update both SQLite and filesystem DAO families.
- When changing search, backlinks, graph, or tag behavior, verify the storage layer still supplies the data those features expect.
- When modifying schema initialization, review migration behavior in `SQLiteDB` and any tests that guard it.
- When touching vault code, watch for locale-neutral behavior in DAOs and for hidden folders or cloud-synced content loading edge cases.
