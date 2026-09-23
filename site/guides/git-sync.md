# Git Sync

When your vault is a Git repository, Jylos versions it with plain Git — no Jylos cloud, no backend, no account, just your own repository managed visually from the app.

Git support is available **only in Markdown vault mode**. In SQLite mode the Git UI is hidden.

## The Git Sync panel

Open **Tools → Git → Git Sync panel…** (or **Ctrl/Cmd+Shift+G**) for a single IDE-style window that consolidates the whole workflow:

- **Repository state** — current branch, remote URL, and how many commits you are ahead/behind your upstream (`↑n ↓n`).
- **Changes** — one unified list of working-tree changes, each prefixed with its status (`M` modified, `A` added, `D` deleted, `R` renamed, `??` untracked, `UU` conflict). Each non-conflicted row has an explicit **Stage** / **Unstage** action.
- **Branches** — list local branches and create new ones.
- **Commit message** field.
- **Activity log** — a timestamped transcript of each operation.

## Operations

| Action | What it does |
| --- | --- |
| Refresh | Fetches/prunes `origin`, then re-reads status and changes. |
| Stage All | `git add -A -- .` beneath the vault root. |
| Unstage All | `git reset -q HEAD -- .` beneath the vault root. |
| Commit | Commits exactly the files currently staged. |
| Pull | `git pull --no-rebase`; requires an upstream. |
| Push | Normal `git push`; the first push configures the upstream. |
| Sync | Commit staged changes → pull → push, stopping at the first error. |
| Set Remote… | Validates/fetches `origin` and configures tracking. |
| New branch… | Creates and switches to a local branch from a clean tree. |

If the vault is not a repository yet, the panel shows an **Initialize Git** prompt. If `git` is not installed, it shows a clear message and disables the actions.

## Safety guarantees

- Nothing destructive runs automatically; every action is an explicit click.
- No force push, ever.
- No implicit staging — a commit never overrides your file-level staging choices.
- Nested repositories stay isolated — Jylos never stages or commits files inside a submodule.
- GitHub size limits are checked before upload (blobs over 100 MiB are reported).
- Conflicts are never auto-resolved.
- The UI never blocks — Git calls run off the JavaFX Application Thread and can be cancelled.

## Authentication

Authentication is handled by your system Git installation: an SSH agent, a Git credential helper, or an already-authenticated GitHub CLI. Jylos does not collect credentials.

## Related

- Technical details: [GIT.md](https://github.com/RGiskard7/jylos/blob/main/docs/GIT.md)
