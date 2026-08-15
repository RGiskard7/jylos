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

## Methodology: New vs Existing Code

New code and existing (legacy) code call for different discipline.

### New Code: TDD

Write the test before the implementation. The initial failure is what proves
the test can fail: write the test, run it, confirm it fails for the right
reason (missing implementation, not a typo), then write the minimum code to
make it pass.

### Existing Code: Characterization

Existing code doesn't get red-green-refactor — it gets characterized: pin down
its current behaviour as a safety net before refactoring, without judging
whether that behaviour is correct.

1. Write the test. It should pass on the first run, because it describes
   behaviour that already exists.
2. Break the production code on purpose — the exact thing the test claims to
   protect.
3. Confirm the test fails, and that it fails with the expected message, not
   just any failure.
4. Restore the code.
5. Confirm the test passes again.

A test that has never been seen failing has not proven anything, no matter how
green the suite looks. When reporting characterization work, say explicitly
what you broke and what the failure message was — "the suite is green" is not
evidence on its own.

### Setting a Mutation Threshold

After characterizing a module, decide whether it is worth adding to the
mutation-testing gate (see [Mutation Testing](#mutation-testing) below).
Measure the actual mutation score first, then set the threshold at, or just
below, that measured number — never at an aspirational one, and never lower it
later to make a PR pass.

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
- the embedded CodeMirror bridge loads and preserves document/undo semantics, platform clipboard and context-menu behavior, read-only protections, link routing, fenced-language highlighting and source/Live Preview switching without mutating text
- critical toolbar/panel remains visible after loading a note
- attachment viewers expose essential controls such as PDF page navigation

Rules:

- keep UI smoke tests small
- do not try to fully automate visual QA in unit tests
- use manual smoke checks for complex interactions such as Canvas editing and PDF scrolling

## Mutation Testing

Line coverage only proves a line ran, not that a test would notice if it broke.
The highest-risk classes — the note/folder/tag DAOs on both the SQLite and
filesystem backends, where a bug means silent data loss — are additionally
checked with mutation testing: an automated pass injects small faults into the
production code and confirms the suite actually fails for each one.

CI enforces this as a gate (`.github/workflows/mutacion.yml`, PIT via
`pitest-maven`) on those classes. The threshold only ever moves up as real
coverage improves; it is never lowered to make a PR pass. Run it locally with:

```bash
mvn -f jylos/pom.xml org.pitest:pitest-maven:mutationCoverage \
  -DtargetClasses=<fully.qualified.ClassName> \
  -DtargetTests=com.example.jylos.tests.*
```

This complements, and does not replace, sabotage-verifying tests by hand when
writing them: break the code on purpose, confirm the test goes red with the
expected message, restore it, confirm green again. A "green suite" alone is
not evidence a test protects anything — say explicitly what you verified and
how.

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
