# Knowledge graph & insights

Jylos turns your notes, wiki-links, backlinks and tags into a knowledge graph — and,
with **Knowledge Insights**, into an analytical tool.

> **Knowledge Insights** helps you detect orphan notes, broken links, highly connected
> notes, tag usage and overall graph health.

It works in both storage modes (SQLite and Markdown vault), since both expose the same
notes/links/tags the analysis needs.

## Knowledge Insights

Open it from **View → Knowledge Insights** (or the command palette: *Knowledge
Insights*, shortcut **Ctrl/Cmd+Shift+K**). The report is computed off the UI thread and
shown in a tabbed dialog. Every row is clickable: **double-click a note to open it**
(for a broken link, its source note opens); the dialog then closes.

| Tab | Shows |
|-----|-------|
| Summary | Totals (notes, links, backlinks, tags), average links per note, and the graph health score with its breakdown. |
| Most connected | Top 10 notes by total connections (inbound + outbound). |
| Orphan notes | Notes with no resolved links in or out. |
| Broken links | Wiki-links / internal links pointing to notes that don't exist. |
| Notes without tags | Notes that carry no tags. |
| Tag usage | Most-used tags with their note counts. |

### Definitions

- **Resolved link** — a link whose source and target are both existing notes.
  Connectivity counts only these.
- **Broken link** — a link from a note to a non-existent note (a "ghost" in the graph).
- **Orphan** — a note with no resolved links. A note that only links to non-existent
  notes surfaces under *Broken links*; it is an orphan only when it has no resolved
  connections.

### Graph health score

A deliberately **simple, explainable** number in `[0, 100]` — guidance, not an absolute
metric. The formula (in `KnowledgeInsightsService.healthScore`):

```
start at 100
− up to 40, proportional to the share of orphan notes
− up to 20, proportional to the share of untagged notes
− 5 per broken link, capped at 25
clamp to [0, 100]
```

An empty vault scores 100. The Summary tab shows the exact deduction for each factor so
the number is never a black box.

## Graph filters

The Graph View's settings panel (gear icon) includes filters that re-render the graph
**without rebuilding the model** (cheap, even while typing):

- **Filter by text** — keeps nodes whose label matches.
- **Tag** — restricts the graph to notes carrying the selected tag.
- **Folder** — restricts the graph to a folder group.
- **Show orphans** — hide/show unconnected notes.
- **Show unresolved** — hide/show broken-link ("ghost") nodes.
- **Show tags** (toolbar) — include/exclude `#tag` nodes.

Structural toggles (orphans / unresolved / tags / local scope) rebuild the model — but
only re-read notes that actually changed (cached link extraction). Text/tag/folder
filters operate purely on the already-built model.

## Architecture

Analytical logic lives in services, never in the JavaFX controllers, and reuses the
existing graph/link machinery — there is **no second link-resolution path**.

| Layer | Type | Role |
|-------|------|------|
| Service | `insights/GraphAnalysisService` | Structural pass: connectivity, orphans, broken links. Pure `analyze(GraphData)` overload is unit-tested. |
| Service | `insights/KnowledgeInsightsService` | Composes the full report (+ tags, health score) from the analysis and `NoteService`/`TagService`. |
| Model / DTO | `insights/KnowledgeHealthReport`, `NoteConnectivityInfo`, `BrokenLinkInfo` | Immutable report snapshots. |
| Builder | `graph/GraphBuilder` (reused) | Derives note→note edges via `WikiLinkResolver` — the single source of link truth. |
| View | `ui/components/KnowledgeInsightsPanel` | Read-only tabbed dialog. |
| View | `ui/controller/GraphController` | Graph overlay + the view filters (post-filtering of the cached `GraphData`). |
