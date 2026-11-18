# Obsidian Compatibility

A Jylos vault is a plain folder of Markdown files, the same as an Obsidian vault: you can open one folder with both apps. Jylos writes nothing of its own in your notes. A note keeps only what you wrote in it.

## What both apps share

- **The notes and their text**, including headings, lists, tasks, callouts, code and Mermaid diagrams.
- **Properties** (the YAML at the top of a note). When you don't change them, Jylos saves them exactly as they were, with your comments, quotes and order.
- **Links**: `[[Note]]`, `[[Folder/Note]]`, `[[Note#Heading]]`, `[[Note|text]]`, embeds `![[…]]`, `aliases`, and links inside properties (`up: "[[Index]]"`).
- **Tags**, written in the text (`#idea`) or in the `tags` property.
- **Canvas files** (`.canvas`) and attachments.
- **The trash**, the `.trash` folder.

## What each app keeps to itself

Each app keeps its own state in a hidden folder of the vault, which the other ignores:

| | Obsidian | Jylos |
|---|---|---|
| Folder | `.obsidian/` | `.jylos/` |
| Favorites | Bookmarks | Favorites |
| Pinned notes | — (it pins tabs) | Pinned |
| Workspaces | `.obsidian/` | Jylos' app data (outside the vault) |

So **favorites and pinned notes are not shared with Obsidian**: a bookmark in Obsidian is not a favorite in Jylos, nor the other way round. Jylos never writes in `.obsidian/`.

To bring Obsidian's bookmarks over once, use **Tools → Import Obsidian Bookmarks**: the notes bookmarked in Obsidian (also inside groups) become favorites in Jylos. It only reads Obsidian's bookmarks; bookmarks of searches, folders, headings or blocks have no equivalent and are skipped, and the result says how many.

Bookmarks are not tags: a tag (`#idea`, `tags:`) is written in the note and classifies it; a bookmark is a shortcut kept in the app's settings, outside the note.

## Good to know

- **Renaming a note outside Jylos** (in Obsidian, Finder or with Git) loses its favorite and pinned marks, because Jylos finds them by the note's path. Obsidian bookmarks have the same limit.
- **`.jylos/` is part of the vault** and is synced with it (Git, iCloud…). Favorites and pinned marks are kept in one file, `.jylos/document-metadata.json`. If you mark notes on two devices at the same time and sync with Git, that file can conflict.
- **Private notes** keep `private: true` in their properties: their text is encrypted, and Jylos needs the flag to open them.

## Notes saved by older versions of Jylos

Older versions of Jylos wrote `id`, `title`, `modified`, `favorite`, `pinned` and `deleted` in every note they saved, and Obsidian showed them as properties. Jylos now takes them out:

- **When a note is saved**, it loses those fields. Its favorite and pinned marks, its `created` date and its other properties are kept.
- **Tools → Clean Up Jylos Metadata…** cleans the whole vault at once. It says first how many notes it will change and asks; it copies each one to `.jylos/backups/` before changing it.

Only notes whose properties Jylos wrote are touched. If you wrote one of those fields yourself (an `id`, a `modified` date…), it is yours and stays.
