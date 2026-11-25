# Getting Started

A quick tour of the core workflows so you can start writing immediately.

## The layout

Jylos has two layouts. Switch between them with the layout button at the right of the toolbar, or in **Tools → Preferences**; your choice is remembered.

- **Explorer** — the left sidebar shows one tree with your folders and notes together. Click a folder to expand it and a note to open it. The notes panel only appears when you search or pick a tag, to show the results, and closes with its **✕**. New installations start in this layout.
- **Classic** — folders, the notes list and the editor side by side (**columns**), or the folders above the notes list (**stacked**). The notes list adds sorting, a grid view and the option to include notes from subfolders.

In both layouts:

- **Left sidebar** — folders, tags, recent notes, favorites and trash.
- **Center** — the Markdown editor with Live Preview and tabs (see [Tabs](/guides/creating-notes#tabs-and-save-state)).
- **Right panel** — backlinks and note information.
- **Status bar** — the vault, whether the note is saved ("Saved" / "Unsaved") and the editor mode. Click the mode to switch between Live Preview, source, reading and linked reading.

## Create and organize

1. Create a note with **Ctrl/Cmd+N**.
2. Put notes into folders from the sidebar (drag or use the folder menu).
3. Assign tags with the tag workflow, available in both SQLite and vault modes.
4. Mark notes as favorites or find them under **Recent**.

## Link notes together

Type `[[` to autocomplete a wiki-link to another note title, or write a Markdown link to `note.md`. Click a link in the reading view to open the target.

See [Wiki-links](/guides/wiki-links) for the full picture, including backlinks.

## Explore the graph

Open **View → Graph View** (or **Ctrl/Cmd+G**) to see your notes as a force-directed graph. Click a node to open its note. Toggle between the global graph and a local neighbourhood around the current note.

See [Knowledge graph](/guides/knowledge-graph).

## Plan with Kanban and Canvas

- **Kanban** — a normal note whose headings are columns and list items are cards. Open with **View → Kanban Board** or **Ctrl/Cmd+K**.
- **Canvas** — an Obsidian-compatible `.canvas` file on an infinite surface. Create one and add text, link and group nodes.

See [Kanban](/guides/kanban) and [Canvas](/guides/canvas).

## Next steps

- [Search](/guides/search) — full-text search and advanced operators.
- [Plugins](/guides/plugins) — Dataview, MCP Server, Publish and external JARs.
- [Private notes](/guides/encryption) — encrypt note bodies with AES-256.
