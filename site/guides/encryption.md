# Private Notes

Mark a note as private to encrypt **only its body** at rest with AES-256-GCM, behind a single master password.

## Make a note private

- **Tools → Make Note Private/Public** (`Ctrl/Cmd+Shift+L`)
- Right-click the note.

## Unlock and lock

- Opening a locked note prompts to unlock just that note.
- **Tools → Unlock Private Notes** reveals all of them.
- **Lock Private Notes** locks them again.

A lock badge marks private notes in the list and editor: closed = locked, open = readable this session.

## How it works

- The master password is PBKDF2-derived; the password itself is never stored.
- In SQLite, privacy is a dedicated column; in a vault, a `private:` frontmatter flag.
- Metadata stays readable, so a locked note still shows as locked without the key.

## Protection

Private notes are protected from deletion and export. Turn a note normal first.
