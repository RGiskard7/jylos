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
- **YAML is read and written as Obsidian does, YAML 1.2 core** (`FrontmatterResolver`,
  `FrontmatterConstructor`): only `true`/`false` are booleans; `no`, `yes`, `on`, `10:30` or
  `1_000` are text; `0123` is 123, `0x1F` and `0o17` have their prefix. When written again:
  lists with Obsidian's indentation (`  - x`), an empty property as `key:`, a date unquoted, a
  value Jylos' field (`author`, `status`, `source_url`) reads as written is copied as it is (a
  list stays a list), and new properties go right before the text, with no blank line.
- **Properties a template adds** (`FrontmatterHandler.mergeProperties`, Obsidian's rule: new
  ones at the end in their order, lists joined without repeats, a template's empty value does
  not overwrite) are kept in `Note.addedFrontmatter` until the next save: a template's `title`,
  `created` or `favorite` is a property of the file and does not change the note's name, dates
  or marks.

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
| `settings.json` | The vault's settings (`VaultSettings`, `VaultSettingsStore`): templates and their formats, daily note, new notes, Templater, and whether importing Obsidian's was offered. With the database it is in the data folder (`vault-settings.json`). The paths it names follow a folder renamed or moved in Jylos |
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

## Obsidian's bookmarks

**Tools → Import Obsidian Bookmarks** (`ObsidianBookmarksSupport`) reads
`.obsidian/bookmarks.json` (`ObsidianBookmarks.read`) and never writes it. Its format is
Obsidian's own and undocumented; a note seen in a real vault is
`{"items": [{"type": "file", "ctime": …, "path": "Folder/Note.md", "title": "…"}]}`. A group
is any item with its own `items` and is walked into. A `file` item with a `subpath` (a heading
or block of the file) and items of other types (search, folder…) are counted as ignored. A file
in another shape is reported as not understood, never read as empty. The files bookmarked that
exist in the vault become favorites; the result says how many were marked, already were, were
not found and were ignored.

## Obsidian's settings

**Tools → Import Obsidian Settings…** (`ObsidianSettingsSupport`, offered once when the vault is
opened, from `ObsidianImportSupport`) reads `.obsidian` (`compat/obsidian/ObsidianSettings`, the
only code that knows those files) and never writes it:

| File | Jylos' settings |
|------|-----------------|
| `templates.json` (`folder`, `dateFormat`, `timeFormat`) | `templates`, if the Templates plugin is on |
| `daily-notes.json` (`folder`, `format`, `template`) | `daily`, if Daily notes is on |
| `app.json` (`newFileLocation`, `newFileFolderPath`) | `newNotes` (`root` by default) |
| `plugins/templater-obsidian/data.json` (`templates_folder`, `trigger_on_file_creation`, `enable_folder_templates`, `folder_templates`) | `templater`, if Templater is in `community-plugins.json` |

The plugins on come from `core-plugins.json` (an object `{id: true}` or, in older versions, a
list; with no file, Obsidian's defaults). A missing key takes Obsidian's default. A file in
another shape is reported as not understood and nothing is applied. What would change is shown,
and applied only when the user accepts it.

## Limits

- Renaming a note outside Jylos loses its marks: they are found by path.
- `document-metadata.json` is one file for the whole vault; marking notes on two devices and
  syncing with Git can make it conflict.
- Favorites and pinned notes are not shared with Obsidian's bookmarks.
