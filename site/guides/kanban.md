# Kanban

A Kanban board is a normal note whose Markdown body holds columns and cards. A board note opens as its board, in its own tab like any note.

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

Open the board's note as any note (the file tree, the quick switcher, a link); boards have their own icon in the file tree, the notes list and the tab. **View → Kanban Board**, **Ctrl/Cmd+K** or **Kanban board** in a new tab list the vault's boards, the one changed last first, to pick one or make a new one.

## New board

**File → New Kanban Board**, the command palette, **New Kanban Board** on a folder in the file tree, or **+ New board** in the board list. It is created as a new note is, with three columns (to do, doing, done), its title ready to rename.

The board button in the note's header shows the board as Markdown, and back. Changes made on the board are saved as any edit is.

## Edit

- Add, rename and delete columns.
- Create, edit and delete cards.
- Drag cards between columns.

## Cards and notes

A card can link to a note (`[[Title]]`, `[[Folder/Title]]`) or be converted into a note. Clicking a linked card opens that note in the board's tab; back returns to the board. Cmd/Ctrl+click, a middle click or **Open in new tab** in the card's menu open it in a new tab and the board stays.

## Column options

From the column menu you can set, in the heading line:

- **WIP limit** — `[wip=N]`; the count badge turns red when exceeded.
- **Color** — `[color=#rrggbb]`.

## Attachments

Cards referencing an image or PDF (`![…](file.png)`, `[[scan.pdf]]`) show an embedded thumbnail (first PDF page via PDFBox).

## Related

- [Canvas](/guides/canvas)
