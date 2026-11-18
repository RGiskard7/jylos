# Knowledge Graph

The knowledge graph renders your notes and their wiki-links as an interactive force-directed network, and, with **Knowledge Insights**, turns them into an analytical tool.

## Open the graph

- **View → Graph View**
- Toolbar button
- **Ctrl/Cmd+G**
- Command palette

The graph opens in a tab, as in Obsidian, following the rule of notes: it takes the active tab, or a new one with Cmd/Ctrl+click or a middle click on its toolbar button. If it is already open, its tab comes to the front, where it is as you left it; the tab's × closes it. A saved workspace reopens it with its notes. A new tab (the **+** after the tabs, or **Ctrl/Cmd+T**) also offers it.

Links count both in the note's body and in its properties (`up: "[[Index]]"`), as in Obsidian.

## Global and local views

- **Global graph** — every note and resolved wiki-link edge, with optional tag nodes and note→tag edges.
- **Local graph** — the current note plus the notes it reaches within **Depth** links (1 to 5). **Incoming links** and **Outgoing links** choose which links to follow, and **Neighbor links** also shows the links between those notes. Defaults, as in Obsidian: depth 1, incoming and outgoing, no neighbor links.

## Interact

- Zoom and pan. The graph opens at the zoom you left it at, as in Obsidian (the local graph keeps its own). Until you choose one, or after **Reset view**, it keeps the whole graph in view while it spreads out.
- Drag nodes.
- Hover to highlight neighbours.
- Click a note node to open it in the graph's tab; Cmd/Ctrl+click or the middle button opens it in a new tab and the graph stays.

## Settings and filters

The settings panel controls repulsion, link force and distance, center gravity, orphans and unresolved links, arrows, color-by-folder, and label/size/line options.

The settings panel also includes filters that re-render the graph without rebuilding it:

- **Filter by text** — keeps nodes whose label matches.
- **Tag** — restricts the graph to notes carrying the selected tag.
- **Folder** — restricts the graph to a folder group.
- **Show orphans** — hide/show unconnected notes.
- **Show unresolved** — hide/show broken-link ("ghost") nodes.
- **Show attachments** — off by default, as in Obsidian. When on, images, PDFs, audio and other files appear, each joined to the notes that embed or link it. Canvases always appear.
- **Show tags** — include/exclude `#tag` nodes.

### Colour groups

Under **Groups**, **New group** adds a row with a query and a colour. Groups are tried in order and each note takes the colour of the first one it matches; other nodes keep theirs. A query can be:

- text, looked for in the note's name or path;
- `path:text`, in the path (`path:projects/`);
- `file:text`, in the name;
- `tag:name`, the notes with that tag (and its tag node).

A group colour wins over **Colour by folder**. The row's X removes the group.

Every graph setting (forces, sizes, switches, local depth and groups) is saved and stays the same next time.

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
