# Git integration

Español: [es/GIT.md](es/GIT.md)

> **Your notes, your repository, your control.**
> Jylos versions your Markdown vault with plain Git. There is no Jylos cloud, no
> backend, and no account — just your own repository, managed visually from the app.

Git support is available **only in Markdown vault mode**. In SQLite mode the Git UI
is hidden and every Git entry point is a no-op (it reports "no vault" and returns).

## The Git Sync panel

`Tools → Git → Git Sync panel…` (shortcut **Ctrl/Cmd+Shift+G**, also in the command
palette as *Git: Sync Panel*) opens a single, IDE-style window that consolidates the
whole workflow:

- **Repository state** — current branch, configured remote URL, and how many commits
  the local branch is ahead/behind its upstream (`↑n ↓n`).
- **Changes** — one unified list of working-tree changes, each prefixed with its VCS
  status:

  | Prefix | Meaning   |
  |--------|-----------|
  | `M`    | modified  |
  | `A`    | added     |
  | `D`    | deleted   |
  | `R`    | renamed   |
  | `??`   | untracked |
  | `UU`   | conflict  |

  Each non-conflicted row has an inline `+ / −` toggle to stage/unstage that single
  file. Conflicts are ordered first and show a `conflict` tag instead of a toggle.
- **Commit message** field.
- **Operations** — *Refresh, Stage All, Unstage All, Commit, Pull, Push, Sync* and
  *Set Remote…*.
- **Activity log** — a timestamped, read-only transcript of each operation's outcome.

If the vault is not a repository yet, the panel shows an **Initialize Git** prompt
instead. If `git` is not installed/on `PATH`, it shows a clear message and disables
the actions.

## Operations

| Action       | What it does                                                            |
|--------------|-------------------------------------------------------------------------|
| Refresh      | Re-reads status + change list (fetches when a remote exists).           |
| Stage All    | `git add -A`.                                                           |
| Unstage All  | `git reset -q` (leaves working-tree edits untouched).                   |
| Commit       | Stages everything and commits with your message (auto-message if blank).|
| Pull         | `git pull --no-rebase`.                                                 |
| Push         | `git push` (sets upstream on first push of a new branch).               |
| Sync         | Commit (if there's anything) → pull → push, stopping at the first error.|
| Set Remote…  | Adds or updates the `origin` URL.                                       |

The legacy status-bar Git strip (remote · changes · commit · sync · history) remains
for quick glances; the panel is the consolidated, full-featured surface.

## Safety guarantees

These are enforced and will not change without an explicit, documented decision:

- **Nothing destructive runs automatically.** Every action is an explicit click.
- **No force push, ever.** Push uses a normal fast-forward push.
- **Conflicts are never auto-resolved.** Unmerged paths (`DD/AU/UD/UA/DU/AA/UU`) are
  surfaced as `conflict`, never reported as staged, and the panel asks you to resolve
  them on disk before committing.
- **The UI never blocks.** Every Git call runs off the JavaFX Application Thread on a
  short-lived daemon `Task`; while one runs the buttons are disabled and an
  indeterminate progress bar is shown.

## Error reporting

Failures are classified by `GitService` into `GitResult.Status` and logged in the
panel's activity log: Git unavailable, not a repository, no remote, merge conflict
during pull, push rejected (non-fast-forward — pull first), authentication failure,
and network errors.

## Architecture

Git logic stays out of the JavaFX controllers; the panel is a pure view.

| Layer            | Type                                  | Role                                            |
|------------------|---------------------------------------|-------------------------------------------------|
| Service (logic)  | `git/GitService`                      | Drives the system `git` CLI; serialized, off-FX.|
| Model / DTO      | `git/GitStatus`, `GitChange`, `GitResult`, `GitCommit` | Immutable snapshots of Git state.|
| Controller (UI)  | `ui/controller/GitController`         | Resolves the vault, owns the status-bar strip, opens the panel. |
| View component   | `ui/components/GitSyncPanel`          | The consolidated dialog and all its async wiring.|

`GitService` shells out to `git` (no JGit dependency), so there is no native library
to bundle and it behaves exactly like the user's own command line. All invocations are
serialized app-wide and recover from a stale `.git/index.lock`.
