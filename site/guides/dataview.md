# Dataview

Query your notes' metadata from inside a note. Write a fenced ```` ```dataview ```` block and the note preview replaces it with the query result.

````markdown
```dataview
TABLE rating AS "Score", file.mtime AS "Updated"
FROM #book AND -"archive"
WHERE rating >= 4
SORT rating DESC
LIMIT 10
```
````

Results render in both the reading-mode preview and the editor's Live Preview. `dataviewjs` blocks are not supported.

## Query types

| Form | Result |
| --- | --- |
| `TABLE expr, expr AS "Name"` | A table, with an implicit link column first |
| `LIST [expr]` | A bullet list of links, or of the given expression |
| `TASK` | Checklist items, grouped by their note |

`WITHOUT ID` drops the implicit link column.

## Metadata sources

| Source | Syntax |
| --- | --- |
| YAML frontmatter | `rating: 5`, `tags: [book, scifi]` |
| Inline field (own line) | `status:: read` |
| Inline field (bracketed) | `… [due:: 2026-03-01]` |
| Tags | `#book` in the body, or a frontmatter `tags` field |
| Tasks | `- [ ] text` / `- [x] text` |

Field names match case-insensitively, and `-`, `_` and spaces are equivalent. Values are typed automatically (numbers, booleans, dates, links, lists).

## Implicit `file` fields

`file.name` · `file.path` · `file.folder` · `file.link` · `file.size` · `file.ctime` · `file.cday` · `file.mtime` · `file.mday` · `file.tags` · `file.etags` · `file.outlinks` · `file.inlinks` · `file.tasks` · `file.starred` · `file.pinned`

`this` refers to the note holding the query, e.g. `this.file.name`.

## FROM

| Source | Selects |
| --- | --- |
| `#tag` | Pages with that tag (hierarchical) |
| `"folder"` | Pages in that folder |
| `[[Note]]` | Pages that link **to** `Note` |
| `outgoing([[Note]])` | Pages `Note` links to |

Combine with `AND` / `OR`, negate with `-` or `NOT`, group with parentheses.

## Clauses

`WHERE` (repeatable), `SORT expr [ASC|DESC]`, `GROUP BY expr [AS name]`, `FLATTEN list AS name`, `LIMIT n`.

## Inline queries

Write `` `= expression` `` in prose to show a computed value:

```markdown
This note is called `= this.file.name` and links to `= length(this.file.outlinks)` others.
```

## Private notes

Private notes are excluded from the index by design: a query renders into a preview that may be exported or shared.

## Related

- Technical details: [DATAVIEW.md](https://github.com/RGiskard7/jylos/blob/main/docs/DATAVIEW.md)
- [Plugins](/guides/plugins)
