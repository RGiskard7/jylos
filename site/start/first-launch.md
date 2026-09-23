# First Launch

When you open Jylos for the first time, you choose where your notes live. This choice is the foundation of everything else.

## Choose your storage

Jylos supports two storage modes:

- **SQLite** (default) — a single `jylos/data/database.db` file that holds your notes, folders and tags.
- **Filesystem Markdown vault** — a folder of plain `.md` notes with YAML frontmatter, so you can read and edit them with any editor.

You can switch between the two from **Tools → Switch storage**. Changing from one filesystem vault to another reloads the session without restarting; switching between SQLite and filesystem requires a restart.

## Runtime directories

Jylos creates a few directories next to its working directory:

```text
data/       # the SQLite database or your vault
logs/       # application logs
backups/    # automatic SQLite backups
plugins/    # external plugin JARs
themes/     # installed external themes
snippets/   # user CSS snippets
```

## Language and theme

Jylos ships with English and Spanish UI strings. The theme can be light, dark, or follow your system. Both are configured from **Preferences**.

## Create your first note

Use **Ctrl/Cmd+N** to create a note, or open the command palette with **Ctrl/Cmd+P** and type "new note". Notes are plain Markdown with support for GFM tables, KaTeX math, emoji and `[[wiki-links]]`.

See [Getting started](/start/getting-started) for a guided tour.
