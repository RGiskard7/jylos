# Creating Notes

Notes are at the heart of Jylos. Here is everything around creating, editing and organizing them.

## Create

- **Ctrl/Cmd+N** creates a new note in the current folder.
- **Daily note** creates a note for today.
- **New note from template** creates a note from a template — a note in a "Templates" folder — replacing the placeholders <span v-pre>{{title}}</span>, <span v-pre>{{date}}</span>, <span v-pre>{{time}}</span> and <span v-pre>{{datetime}}</span>.

## Edit

The editor is CodeMirror 6 and WYSIWYG by default: you write Markdown and it renders live as you type — GFM tables, KaTeX math, emoji and embeds appear in place.

- **Live Preview** (default) — the WYSIWYG view. Punctuation hides while you type, keeping one document and one undo history.
- **Source mode** — plain Markdown, in Preferences, when you want the raw text.
- **Reading view** — the full render for a clean read; a side-by-side split is still available as a separate **View** command if you want it.

**Focus / writing mode** (**Ctrl/Cmd+Shift+F**) hides everything but the editor.

## Markdown features

- GFM tables, autolinks and strikethrough.
- Task lists (`- [ ]` / `- [x]`) render as checkboxes.
- Code-block highlighting (highlight.js).
- KaTeX math with `$…$`, `$$…$$` and LaTeX delimiters.
- Emoji in preview.
- Transclusion with `![[Note]]` or `![[Note#Heading]]`, with cycle detection.
- Rich links: paste a URL to insert it as a visual card (metadata fetched in the background).

## Tabs and save indicator

Open multiple notes in tabs. Each tab shows an inline save indicator: amber means unsaved, green means saved.

## Version history

**Tools → Note History** (**Ctrl/Cmd+Shift+H**) opens local snapshots taken before each save (coalesced, capped at 50 per note), with a line diff viewer and one-click restore. Private notes' snapshots stay encrypted.

## Import and export

- Export a note, or the whole vault, to HTML or PDF.
- Import an individual note.
- Import an **Obsidian vault** (folder hierarchy, frontmatter and tags preserved; `.obsidian/` skipped) or an **Evernote `.enex`** export (ENML converted to Markdown, tags kept) from the File menu.

## Organize

Move notes between folders, add tags, and mark favorites from the sidebar. Deleting moves notes to the trash, where they can be restored.

## Related

- [Wiki-links](/guides/wiki-links)
- [Search](/guides/search)
- [Workspaces](/guides/workspaces)
