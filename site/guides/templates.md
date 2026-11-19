# Templates and Daily Note

::: v-pre

Jylos writes templates as Obsidian does: with the syntax of its Templates plugin and, if you turn it on, with Templater's. The same template gives the same result in both apps. Everything is set in **Preferences → Vault**, and each vault has its own settings.

## Where templates are

Templates are the notes of the **templates folder**, its subfolders included. If you choose none, they are those of a folder named `Templates`. The vault's root cannot be the templates folder.

## Using a template

- **Edit → Insert Template…** (in the palette, "Templates: Insert template") finds a template and writes it where the cursor is. Its properties are added to the note's: new ones go at the end, in the template's order; two lists are joined without repeats; a template's value takes the place of the note's, unless the template's is empty.
- **Edit → Insert Current Date** and **Insert Current Time** write the date or the time where the cursor is, in the vault's formats.
- **File → New from Template…** creates a note named as the template, with the template written for it, as it is in its file.

## Variables

| Variable | Writes |
|---|---|
| `{{title}}` | The note's name |
| `{{date}}` | Today's date, in the vault's date format (`YYYY-MM-DD` by default) |
| `{{time}}` | The time, in the vault's time format (`HH:mm` by default) |
| `{{date:FORMAT}}`, `{{time:FORMAT}}` | The date or time in that format, for example `{{date:dddd, MMMM D}}` |
| `{{datetime}}` | Date and time, `YYYY-MM-DDTHH:mm:ss` (Jylos only) |

Formats are Obsidian's: `YYYY` (year), `MM` and `MMMM` (month as a number and by name), `DD` (day), `dddd` (weekday), `W` (week of the year), `HH:mm` (time), `[text]` to write text as it is… Day and month names are in the interface's language. In Preferences, each format shows what it writes today.

## Daily note

**File → Daily Note** (**Ctrl/Cmd+Shift+D**) opens today's note, or creates it if it is not there:

- **Its name** is today in the daily note's format. A format with slashes, such as `YYYY/MM/DD`, keeps it in folders, made if missing.
- **Its folder** is the daily notes folder; without one, the new notes folder when that is a given folder; else the vault's root.
- **Its template** is written when it is created. In it, `{{title}}` is the note's name, and `{{date}}` and `{{time}}` use the fixed formats `YYYY-MM-DD` and `HH:mm`, as in Obsidian. With no template chosen, the `daily` note of the templates folder is used if there is one; otherwise the note is created empty.

If the chosen folder or template is not in the vault, Jylos says so and creates nothing.

## Where new notes go

In the folder in view (as always), in the vault's root, or in a given folder. It applies to new notes, notes created from a link to a note that does not exist yet, and "New from Template". If the chosen folder is not there, the note goes to the root.

## Templater

With **Understand Templater syntax** on, templates also write Templater's `<% %>` tags, as Templater does:

| Function | Writes |
|---|---|
| `tp.date.now(format, offset, reference, reference_format)` | Today, or the reference date, plus an offset in days (`7`, `-1`) or as a duration (`"P1M"`, `"P-1W"`, `"PT2H"`) |
| `tp.date.tomorrow(format)`, `tp.date.yesterday(format)` | Tomorrow, yesterday |
| `tp.date.weekday(format, day, reference, reference_format)` | A day of this week (0 is the first, as the language counts) |
| `tp.file.title`, `tp.file.tags`, `tp.file.content` | The note's name, tags and text |
| `tp.file.folder(path)`, `tp.file.path(relative)` | Its folder (the name, or the path with `true`) and its path (on disk, or in the vault with `true`) |
| `tp.file.creation_date(format)`, `tp.file.last_modified_date(format)` | When its file was created and changed |
| `tp.file.selection()` | What is selected in the editor when inserting |
| `tp.file.cursor()` | Leaves the cursor there when inserting |
| `tp.file.exists("path")`, `tp.file.include("[[Note]]")` | Whether a note or folder exists; another note's text, its tags written |
| `tp.frontmatter.field` | A property of the note |
| `tp.system.prompt("…?")`, `tp.system.suggester([…], […])`, `tp.system.clipboard()` | What you answer in a dialog, what you pick from a list, the clipboard |

`<%-` and `-%>` take away the line break before or after the tag; `<%_` and `_%>`, all the whitespace. `await` before a function is allowed.

**What runs code is left as it is**: `<%* … %>`, `tp.user`, `tp.web`, `tp.app`, `tp.obsidian`, `tp.hooks`, `tp.config` and any other JavaScript. The status bar says which tags were left. What would make Templater stop a template stops it too, writing nothing: a reference date not in its format, an included note that is not there, or an `include` more than ten deep.

With **Trigger on new note creation**:

- a new empty note gets its **folder's template**, or that of the nearest folder up with one (notes in the templates folder do not);
- the Templater tags of the **daily note's template** are written too, as when Templater is installed in Obsidian.

Differences from Templater:

- **One command for both syntaxes:** "Insert Template" writes Obsidian's variables and Templater's at once. In Obsidian, each plugin writes only its own.
- **Several `tp.file.cursor()`:** the cursor goes to the first in order and all are taken out; Jylos has no command to jump to the next, as Templater does.

## Taking the settings from Obsidian

**Tools → Import Obsidian Settings…** takes these settings from the vault's `.obsidian` folder. See [Obsidian Compatibility](/guides/obsidian-compatibility#obsidian-s-settings).

:::
