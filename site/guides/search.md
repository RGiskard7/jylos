# Search

Jylos search stays simple by default: type words and it matches note **titles and bodies**. Power users can add Gmail/Obsidian-style operators on top, and mix them freely.

## Quick access

- **Ctrl/Cmd+P** — command palette.
- **Ctrl/Cmd+O** — quick switcher for notes.

## Advanced search syntax

Filters are combined with **AND**. Prefix any clause with `-` to negate it. Operator values may be quoted (`body:"java virtual machine"`).

| Operator | Matches |
| --- | --- |
| `word`, `another` | free text in title or body |
| `"exact phrase"` | the phrase as written (order matters) |
| `tag:java` | notes carrying the tag *java* |
| `folder:backend` | notes in the *backend* folder |
| `title:draft` | title contains *draft* |
| `body:"unit test"` | body contains the phrase |
| `created:<date>` | creation date |
| `modified:<date>` | modified date |
| `favorite:true` / `favorite:false` | favorite flag |
| `private:true` / `encrypted:true` | encrypted/private notes |
| `has:tag` | note has at least one tag |
| `has:links` | note links to at least one existing note |
| `has:backlinks` | at least one note links to it |
| `is:orphan` | no links in or out |
| `-tag:archive`, `-title:draft` | negation (must NOT match) |

### Date tokens

`today`, `yesterday`, `last-week`, `last-month`, `YYYY`, `YYYY-MM`, `YYYY-MM-DD`.

```text
modified:today
created:2026
created:2026-06
modified:2026-06-13
modified:last-week
```

### Examples

```text
tag:java modified:last-week
"design patterns" -tag:archive
is:orphan
has:backlinks
private:true
java tag:spring
body:"java virtual machine"
-title:draft favorite:true
```

## Behaviour

- A query with no operators is plain title/body text.
- The parser never throws: an unknown operator is searched as literal text, and an invalid value is dropped with a warning while the rest of the query runs.
- Works in both SQLite and Markdown vault modes.

## Related

- Technical details: [SEARCH.md](https://github.com/RGiskard7/jylos/blob/main/docs/SEARCH.md)
