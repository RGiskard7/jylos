# Testing

Español: [es/TESTING.md](es/TESTING.md)

Jylos uses a focused JUnit 5 test suite as a release gate. The goal is not to
inflate test count; every test should protect behaviour, storage compatibility,
or an explicit architecture rule.

Run everything:

```bash
mvn -f jylos/pom.xml test
```

Compile only:

```bash
mvn -f jylos/pom.xml -DskipTests compile
```

## Test Types

### Unit Tests

Use for pure logic with no filesystem, database, JavaFX runtime, or Git process.

Examples:

- parsers and search query handling
- Markdown/link transformation
- graph/canvas model logic
- encryption helpers
- template expansion

Rules:

- prefer direct assertions over source-code inspection
- keep inputs small and named
- test boundaries, not every obvious branch

### Integration and Contract Tests

Use when behaviour depends on storage or external process semantics.

Examples:

- SQLite DAO persistence
- filesystem vault read/write/rename/move/delete
- Obsidian-compatible `.canvas` and Markdown frontmatter behaviour
- document metadata integrity for Markdown, canvas and binary attachments
- folder and document move parity between SQLite and filesystem
- Git repository state
- import/export flows

Rules:

- use `@TempDir`
- never write to user data paths
- assert persisted state after reopening/reloading where relevant
- prefer storage contracts over implementation details
- for filesystem documents, assert that name conflicts preserve the original
  extension and that corrupt sidecar metadata fails without being overwritten
- for SQLite/filesystem parity, assert the same visible behaviour even when the
  internal persistence mechanism differs

### Architecture Guard Tests

Use sparingly for project rules that normal unit tests cannot express well.

Valid guard examples:

- `service/*` must not depend on JavaFX or UI packages
- `data/*` must not depend on UI packages
- removed global locators/events must not return
- UI/service ownership boundaries must not regress

Rules:

- guard tests may inspect source text, but only for architecture boundaries
- avoid adding guard tests for style preferences or incidental formatting
- if a behaviour test can express the rule, write the behaviour test instead
- do not add source-text guards for exact private method names, comments, logger
  calls, or one-off UI implementation details

### UI Smoke Tests

Use for JavaFX wiring and layout regressions that are cheap to check.

Examples:

- FXML loads
- required `fx:id` nodes exist
- critical toolbar/panel remains visible after loading a note
- attachment viewers expose essential controls such as PDF page navigation

Rules:

- keep UI smoke tests small
- do not try to fully automate visual QA in unit tests
- use manual smoke checks for complex interactions such as Canvas editing and PDF scrolling

## What Not To Add

Do not add tests that only prove implementation trivia:

- logger factory string checks
- exact private method names
- exact whitespace or comment text
- broad `source.contains(...)` assertions for non-architecture concerns
- sleeps to force ordering when a clock or deterministic input can be used

## Current Policy

The test suite intentionally keeps a few architecture guards because they
protect decisions that are otherwise hard to express in runtime tests: layer
boundaries, removed global locators/events, UI/service ownership boundaries,
and i18n coverage.

Guard tests are not a dumping ground. Source-text tests that only checked
sidebar private implementation details or logging style were removed. When a
rule can be tested as behaviour, prefer that. For example, folder drag/drop
safety is covered through `FolderService.canMoveFolder(...)` behaviour instead
of checking the exact source code in `SidebarController`.

## Current Gaps

Known areas that still need stronger behavioural coverage:

- `FileViewer` PDF scroll-position restoration under real tab switching
- `CanvasView` interactions: edge creation, embedded notes/files, text editing, groups
- Kanban UI behaviour beyond model-level coverage
- broader JavaFX controller interaction tests

These gaps should be closed with focused tests, not broad snapshot tests or more
source-text guards.
