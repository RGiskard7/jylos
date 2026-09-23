# Kanban

A Kanban board is a normal note whose Markdown body holds columns and cards, in the spirit of Obsidian's Kanban plugin.

## Structure

- `## Heading` — a column.
- `- card` — a card in that column.

```markdown
## Backlog
- Research topic
- Write outline

## Doing
- Draft the note
```

## Open a board

Use **View → Kanban Board** or **Ctrl/Cmd+K**, then pick or create a board from the toolbar.

## Edit

- Add, rename and delete columns.
- Create, edit and delete cards.
- Drag cards between columns.

## Cards and notes

A card can link to a note (`[[Title]]`) or be converted into a note.

## Column options

From the column menu you can set, in the heading line:

- **WIP limit** — `[wip=N]`; the count badge turns red when exceeded.
- **Color** — `[color=#rrggbb]`.

## Attachments

Cards referencing an image or PDF (`![…](file.png)`, `[[scan.pdf]]`) show an embedded thumbnail (first PDF page via PDFBox).

## Related

- [Canvas](/guides/canvas)
