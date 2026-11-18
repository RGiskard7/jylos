# Vault format

Español: [es/VAULT_FORMAT.md](es/VAULT_FORMAT.md)

A filesystem vault is a folder of Markdown files that Obsidian can open as well. The rule:
**a note's file holds only what the user wrote**. Jylos' own state lives in `.jylos/`, as
Obsidian's lives in `.obsidian/`, and Jylos never writes in `.obsidian/`.
User guide: [site/guides/obsidian-compatibility.md](../site/guides/obsidian-compatibility.md).

## A note's file

Written by `FrontmatterHandler.generate(note, previousFile)`, called from
`NoteDAOFileSystem.updateNote` and `createNote` with the file as it is on disk now:

- **Properties that did not change are copied byte for byte** (comments, quotes, key order,
  CRLF), and so are properties that do not parse as YAML, which Jylos cannot know.
- **When they changed**, the YAML is written again with the keys in their previous order,
  new ones last, and the same line ends; the blank lines before the text are kept.
- **A note without properties is written without a block.** A new note has none.
- **When the result equals the file on disk, nothing is written**, so a mark changed in
  `.jylos/` does not touch the note or its modification time.
- **`tags:`** keeps the tags it had that the note still has, in their order and spelling,
  plus those added from the app. A `#tag` of the text (now or before the edit) stays in the
  text; the text of a private note is encrypted, so its tags all stay in `tags:`.
- **The first line of the text is never taken as the title**: the title is the file name.

### Keys Jylos reads

| Key | Meaning |
|-----|---------|
| `tags`, `aliases`/`alias` | Tags and link aliases, as in Obsidian |
| `private: true` | The text is encrypted (`JENC1:…`); written only when true |
| `status`, `author`, `source_url`, `is_todo`, `todo_due`, `todo_completed` | Written only when set |
| `created` | Shown as the creation date when present; never added (the file's date is used otherwise) |
| anything else | User properties, kept with their YAML types |

### Fields older versions wrote

Older versions wrote `id`, `title`, `created`, `modified`, `favorite`, `pinned`, `deleted` and
`deleted_date` in every note. They are no longer written. A frontmatter is recognised as
written by Jylos when it has `id`, `favorite`, `pinned` and `deleted` together
(`FrontmatterHandler.isJylosFrontmatter`; an Obsidian note practically never does, and "`id`
equals the path" would fail after a rename). Then:

- **on read**, `favorite`/`pinned` give the note's marks when `.jylos/` has none for it;
- **on save**, those fields leave the file except `created`;
- **Tools → Clean Up Jylos Metadata…** (`VaultCleanupSupport` →
  `NoteService.cleanJylosFields`) does the whole vault at once: it lists the notes
  (`notesWithJylosFields`, reading only; hidden folders other than `.trash` skipped), asks,
  copies each one to `.jylos/backups/cleanup-<yyyyMMdd-HHmmss>/` at the same path, moves the
  marks to `.jylos/` unless it already has marks for the note, and takes the fields out as
  lines (`withoutJylosFields`), so the rest of the YAML stays as written. A field spanning
  several lines falls back to writing the note again.

Without that signature the same keys are the user's (`id: 202401011200`, `modified: yesterday`,
`favorite: true`) and stay untouched. The command is meant to be retired in 3.0; the rules
on read and save stay.

## `.jylos/`

| File | Content |
|------|---------|
| `document-metadata.json` | Per path: favorite and pinned marks of notes; favorite, pinned, private, status and trash data of canvases and attachments |
| `encryption.json` | Salt and verifier of the master password (see [ARCHITECTURE.md](ARCHITECTURE.md#private-notes-encryption)) |
| `backups/cleanup-<date>/` | Copies made by the clean-up command |

`document-metadata.json` (`FileSystemDocumentMetadataStore`):

- **Moves with the document:** the entry moves when the note is renamed or moved, sent to the
  trash and restored, when its folder is renamed or moved, and it goes when the note is deleted
  for good.
- **New notes start clean:** creating a note clears any entry left at its path, for example by
  a note deleted outside Jylos.
- **Empty entries:** an entry with no marks is removed. The exception is a note whose file has
  a user-written `favorite`/`pinned` property: then "not marked" is kept, so the property
  does not bring the mark back.
- **Corrupt file:** a corrupt sidecar is an explicit error, never read as empty.

## Limits

- Renaming a note outside Jylos loses its marks: they are found by path.
- `document-metadata.json` is one file for the whole vault; marking notes on two devices and
  syncing with Git can make it conflict.
- Favorites and pinned notes are not shared with Obsidian's bookmarks.
