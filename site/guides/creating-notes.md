# Creating Notes

Notes are at the heart of Jylos. Here is everything around creating, editing and organizing them.

## Create

- **Ctrl/Cmd+N** creates a new note in the current folder.
- **Daily note** creates a note for today.
- **New note from template** creates a note from a template — a note in a "Templates" folder — replacing the placeholders <span v-pre>{{title}}</span>, <span v-pre>{{date}}</span>, <span v-pre>{{time}}</span> and <span v-pre>{{datetime}}</span>.

## Edit

The editor is CodeMirror 6 and WYSIWYG by default: you write Markdown and it renders live as you type — GFM tables, KaTeX math, emoji and embeds appear in place.

- **Live Preview** (default) — the WYSIWYG view. Punctuation hides while you type, keeping one document and one undo history.
- **Source mode** — plain Markdown, when you want the raw text.
- **Reading view** — the full render for a clean read; a side-by-side split is still available as a separate **View** command if you want it.

Switch between them by clicking the editor mode at the right of the status bar.

**Focus / writing mode** (**Ctrl/Cmd+Shift+F**, or **View → Focus mode** and the command palette) hides everything but the editor. The same shortcut leaves it; a hint says so when you enter it.

## Markdown features

- GFM tables, autolinks and strikethrough.
- Task lists (`- [ ]` / `- [x]`) render as checkboxes.
- Code-block highlighting (highlight.js).
- KaTeX math with `$…$`, `$$…$$` and LaTeX delimiters.
- Emoji in preview.
- Transclusion with `![[Note]]` or `![[Note#Heading]]`, with cycle detection.
- Rich links: paste a URL to insert it as a visual card (metadata fetched in the background).

## Tabs and save state

Tabs work as in VS Code:

- A click on a note opens it in the **current tab**, replacing the note that was there. If the note is already open in another tab, that tab is activated instead of opening it twice.
- To open a note in a **new tab** (right after the current one): **Cmd+click** on macOS or **Ctrl+click** on Windows and Linux, a **middle click**, or **Open in new tab** in the note's context menu. This works in the Explorer tree, the notes list, the grid, Recent and Favorites.
- New notes and the notes of a workspace each open in their own tab.
- A middle click on a tab closes it.

If you prefer one tab per note, turn on **Always open notes in a new tab** in **Tools → Preferences**.

Links between notes open in the current tab: in source mode, Cmd/Ctrl+click is the gesture that follows a link.

The status bar shows whether the open note is **Saved** or **Unsaved**; its tooltip says how often autosave runs, or how to turn it on. Each tab also shows a dot: amber means unsaved, green means saved.

## Version history

**Tools → Note History** (**Ctrl/Cmd+Shift+H**) opens local snapshots taken before each save (coalesced, capped at 50 per note), with a line diff viewer and one-click restore. Private notes' snapshots stay encrypted.

## Import and export

- Export a note, or the whole vault, to HTML or PDF.
- Import an individual note.
- Import an **Obsidian vault** (folder hierarchy, frontmatter and tags preserved; `.obsidian/` skipped) or an **Evernote `.enex`** export (ENML converted to Markdown, tags kept) from the File menu.

## Organize

Move notes between folders, add tags, and mark favorites from the sidebar. Notes and folders have a context menu (right click) with the same entries in both layouts, including **Move to Folder...**; you can also drag a note or a folder onto another folder. Deleting moves notes to the trash, where they can be restored.

In the **Explorer** layout, the header above the tree has:

- a **filter** that searches names and paths (`work/minutes`) across the whole vault (case and accents are ignored) and shows the matches inside their folders;
- the **note order** (name, last edited or created);
- **Auto-reveal current file**: when on, the tree opens the folders of the note you open and selects it;
- one button to **expand all** or **collapse all**.

Folders you leave open stay open next time. In the tree, **F2** (or **Rename** in the menu) renames in the row itself: Enter applies, Escape cancels. A new folder is created as "Untitled", ready for its name, and a new note as "Untitled". Holding a dragged note over a closed folder opens it. The note menu also has **Make a copy**, **Copy path** and **Open in default app**.

**Show note count next to each folder** (Preferences) applies to both layouts.

## Related

- [Wiki-links](/guides/wiki-links)
- [Search](/guides/search)
- [Workspaces](/guides/workspaces)
