# Search

Jylos search stays simple by default: type words and it matches note **titles and
bodies**, exactly as before. Power users can add Gmail/Obsidian-style operators on top —
mix freely, the rest of the query still works.

## Advanced Search Syntax

Filters are combined with **AND**. Prefix any clause with `-` to negate it. Operator
values may be quoted (`body:"java virtual machine"`).

| Operator | Matches |
|----------|---------|
| `word`, `another` | free text in title or body |
| `"exact phrase"` | the phrase as written (order matters) |
| `tag:java` | notes carrying the tag *java* |
| `folder:backend` | notes in the *backend* folder |
| `title:draft` | title contains *draft* |
| `body:"unit test"` | body contains the phrase |
| `created:<date>` | creation date (see date tokens) |
| `modified:<date>` | modified date (see date tokens) |
| `favorite:true` / `favorite:false` | favorite flag |
| `private:true` / `encrypted:true` | encrypted/private notes |
| `has:tag` | note has at least one tag |
| `has:links` | note links to at least one existing note |
| `has:backlinks` | at least one note links to it |
| `is:orphan` | no links in or out |
| `-tag:archive`, `-title:draft` | negation (must NOT match) |

### Date tokens

`today`, `yesterday`, `last-week`, `last-month`, `YYYY`, `YYYY-MM`, `YYYY-MM-DD`.

```
modified:today
created:2026
created:2026-06
modified:2026-06-13
modified:last-week
```

### Examples

```
tag:java modified:last-week
"design patterns" -tag:archive
is:orphan
has:backlinks
private:true
java tag:spring
body:"java virtual machine"
-title:draft favorite:true
```

## Behaviour & robustness

- **Simple search is unchanged** — a query with no operators is plain title/body text.
- **Forgiving parser** — it never throws. An unknown operator (e.g. `foo:bar`) is
  searched as literal text with a quiet warning; an invalid value (bad date, non-boolean,
  unknown `has:`/`is:` target) is dropped with a warning, and the rest of the query runs.
- **Both storage modes** — works in SQLite and Markdown vault. Metadata a mode can't
  provide simply yields no match for that filter rather than an error.
- **Performance** — runs off the UI thread; full note content is read through the list's
  existing cache, and expensive metadata (tag membership, links/backlinks, orphan status)
  is computed lazily and once per search, only when the query uses it.
- **Tip** — hover the search box for a syntax reminder.

## Architecture

| Layer | Type | Role |
|-------|------|------|
| Parser | `search/SearchQueryParser` | Raw string → `SearchQuery` (pure, never throws). |
| Model | `search/SearchQuery`, `search/SearchFilter` | Parsed clauses + non-fatal warnings. |
| Dates | `search/SearchDates` | Lenient date-token matching. |
| Service | `search/AdvancedSearchService` | Applies a query to notes; reuses `TagService` and `GraphAnalysisService` (no second link-resolution path). |
| Result | `search/SearchResult` | A hit: note + snippet + folder + tags. |
| UI | `ui/controller/NotesListController` | `performSearch` delegates to the service; results render in the existing notes list. |
