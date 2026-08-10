# Dataview plugin

Español: [es/DATAVIEW.md](es/DATAVIEW.md)

Query your notes' metadata from inside a note. Write a fenced <code>```dataview</code>
block and the **note preview** replaces it with the query result.

````markdown
```dataview
TABLE rating AS "Score", file.mtime AS "Updated"
FROM #book AND -"archive"
WHERE rating >= 4
SORT rating DESC
LIMIT 10
```
````

The plugin ships in `plugins-source/com/example/jylos/plugin/builtin/dataview/` and is built by
`scripts/build-plugins.sh` into `jylos/plugins/DataviewPlugin.jar`.

## Where results appear

Results render in the **reading-mode preview**. While editing, the query stays visible as
source: the editor renders through CodeMirror, and the plugin API reaches the preview
pipeline only (`PreviewEnhancer.transformHtml`). This is the main behavioural difference
from Obsidian's Dataview, which also renders inside Live Preview.

`dataviewjs` blocks are **not** supported — arbitrary JavaScript queries would need a
scripting surface the host does not expose. Such a block renders an explanatory notice
rather than failing silently.

## Metadata sources

| Source | Syntax |
|--------|--------|
| YAML frontmatter | `rating: 5`, `tags: [book, scifi]`, block lists |
| Inline field (own line) | `status:: read` |
| Inline field (bracketed) | `… [due:: 2026-03-01]` or `(due:: 2026-03-01)` |
| Tags | `#book` in the body, or a frontmatter `tags`/`tag` field |
| Tasks | `- [ ] text` / `- [x] text` |

Field names match case-insensitively, and `-`, `_` and spaces are equivalent: `due-date`,
`Due Date` and `due_date` are the same field. Values are typed automatically — numbers,
booleans, `yyyy-MM-dd` dates, `[[links]]` and comma-separated lists.

Fenced code blocks are skipped when scanning a note, so a `#comment` or a `::` inside a
code sample is not mistaken for metadata.

### Implicit `file` fields

`file.name` · `file.path` · `file.folder` · `file.link` · `file.size` · `file.ctime` ·
`file.cday` · `file.mtime` · `file.mday` · `file.tags` · `file.etags` · `file.outlinks` ·
`file.inlinks` · `file.tasks` · `file.starred` · `file.pinned`

`this` refers to the note holding the query, e.g. `this.file.name`.

## Query types

| Form | Result |
|------|--------|
| `TABLE expr, expr AS "Name"` | A table, with an implicit link column first |
| `LIST [expr]` | A bullet list of links, or of the given expression |
| `TASK` | Checklist items, grouped by their note |

`WITHOUT ID` drops the implicit link column: `TABLE WITHOUT ID rating`.

In a `TASK` query the task's own fields resolve first: `text`, `completed`, `status`,
`line`, plus any inline field declared on the task line.

## FROM

| Source | Selects |
|--------|---------|
| `#tag` | Pages with that tag — hierarchical, so `#project` also matches `#project/active` |
| `"folder"` | Pages in that folder (including nested paths) |
| `[[Note]]` | Pages that link **to** `Note` |
| `outgoing([[Note]])` | Pages `Note` links to |
| `incoming([[Note]])` | Same as `[[Note]]` |

Combine with `AND` / `OR`, negate with `-` or `NOT`, group with parentheses:

```
FROM (#book OR #article) AND -"archive"
```

`FROM [[]]` means "this note".

## Clauses

| Clause | Notes |
|--------|-------|
| `WHERE expr` | May be repeated; all conditions must hold |
| `SORT expr [ASC\|DESC]` | Several keys, comma-separated |
| `GROUP BY expr [AS name]` | Renders one section per group |
| `FLATTEN list AS name` | One row per element; applied **before** `WHERE` |
| `LIMIT n` | Caps rows, or groups when grouped |

Clauses may appear in any order after the projection.

## Expressions

Operators: `+ - * / %`, `= != > >= < <=`, `AND OR NOT` (`&& || !`).

`AND` and `OR` short-circuit, so `field AND length(field) > 0` is safe on pages that lack
the field. Comparisons are total: comparing a string to a date, or a missing field to a
number, yields a defined result rather than dropping the row with an error.

Literals: numbers, `"strings"`, `true`/`false`/`null`, `[[links]]`, `#tags`, lists
(`[1, 2]`) and objects (`{a: 1}`). `today`, `now`, `tomorrow` and `yesterday` are built in.

### Dates and durations

Durations are day counts: `date(today) - 7` and `date(today) - dur("1 week")` both mean
seven days ago, and subtracting two dates gives the number of days between them.

```
WHERE file.mtime >= date(today) - dur("30 days")
```

### Functions

`length` `contains` `icontains` `econtains` `typeof`
`lower` `upper` `replace` `split` `join` `truncate` `startswith` `endswith`
`regexmatch` `regexreplace`
`number` `string` `round` `floor` `ceil` `abs` `min` `max` `sum` `average`
`date` `dateformat` `striptime` `dur`
`default` `ifnull` `choice` `nonnull`
`link` `elink`
`sort` `reverse` `unique` `flat` `first` `last` `any` `all` `none`

## Inline queries

Write `` `= expression` `` in prose to show a computed value:

```markdown
This note is called `= this.file.name` and links to `= length(this.file.outlinks)` others.
```

Anything that is not a valid expression is left exactly as written, so `` `= 1 + 1` ``
used as prose is never mangled.

## Indexing and performance

Queries need each note's **full** body — an inline field or task can sit anywhere in the
file, while the notes list only holds a truncated head. The plugin therefore keeps a warm
index: each note is read and parsed once, cached against its modified timestamp, and
re-read only when it changes. The first query after startup pays the full read; later ones
are map lookups. Note events invalidate only the affected note.

**Private notes are excluded** from the index by design: a query renders into a preview
that may be exported or shared, so surfacing the contents of a note marked private would
leak it. Attachments and canvas files are excluded too — they carry no Markdown metadata.

## Errors

A query that cannot be parsed or evaluated renders an error box in place, naming the
problem and echoing the query. The rest of the note still renders.

## Tests

```bash
./scripts/test-plugins.sh
```

Compiles `plugins-source/` together with `plugins-test/` and runs the behavioural checks in
`plugins-test/com/example/jylos/plugin/builtin/dataview/DataviewPluginTest.java` (metadata
extraction, every clause, functions, HTML escaping and the end-to-end preview transform).
The host-side hook is covered by `PreviewEnhancerTransformTest` in the Maven test suite.
