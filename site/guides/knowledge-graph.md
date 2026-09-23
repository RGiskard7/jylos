# Knowledge Graph

The knowledge graph renders your notes and their wiki-links as an interactive force-directed network, and, with **Knowledge Insights**, turns them into an analytical tool.

## Open the graph

- **View → Graph View**
- Toolbar button
- **Ctrl/Cmd+G**
- Command palette

## Global and local views

- **Global graph** — every note and resolved wiki-link edge, with optional tag nodes and note→tag edges.
- **Local graph** — the current note plus neighbours within a configurable hop depth.

## Interact

- Zoom and pan.
- Drag nodes.
- Hover to highlight neighbours.
- Click a note node to open it.

## Settings and filters

The settings panel controls repulsion, link force and distance, center gravity, orphans and unresolved links, arrows, color-by-folder, and label/size/line options.

The settings panel also includes filters that re-render the graph without rebuilding it:

- **Filter by text** — keeps nodes whose label matches.
- **Tag** — restricts the graph to notes carrying the selected tag.
- **Folder** — restricts the graph to a folder group.
- **Show orphans** — hide/show unconnected notes.
- **Show unresolved** — hide/show broken-link ("ghost") nodes.
- **Show tags** — include/exclude `#tag` nodes.

## Knowledge Insights

Open **View → Knowledge Insights** (or **Ctrl/Cmd+Shift+K**) to analyze your vault. The report opens in a tabbed dialog and every row is clickable — double-click a note to open it.

| Tab | Shows |
| --- | --- |
| Summary | Totals (notes, links, backlinks, tags), average links per note, and a graph health score with its breakdown. |
| Most connected | Top 10 notes by total connections. |
| Orphan notes | Notes with no resolved links in or out. |
| Broken links | Links pointing to notes that don't exist. |
| Notes without tags | Notes that carry no tags. |
| Tag usage | Most-used tags with their note counts. |

The health score is a simple, explainable number in `[0, 100]` that starts at 100 and subtracts for orphan notes, untagged notes and broken links.

## Related

- [Wiki-links](/guides/wiki-links) — how the edges are created.
- Technical details: [GRAPH.md](https://github.com/RGiskard7/jylos/blob/main/docs/GRAPH.md)
