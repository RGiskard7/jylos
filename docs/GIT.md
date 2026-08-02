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
  the local branch is ahead/behind its upstream (`↑n ↓n`). The panel also makes it
  explicit when a remote exists but the current branch has no upstream yet.
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

  Each non-conflicted row has an explicit **Stage** / **Unstage** action. Conflicts
  are ordered first and show a `conflict` tag instead of a toggle.
  If a file is changed again after staging, its index and working-tree states are
  shown separately and labelled **staged** / **unstaged** so neither change is hidden.
  A tracked nested Git repository with uncommitted internal changes is shown as a
  blocking `nested repository` row, not as a stageable file. Commit those changes
  from that repository first; the parent vault can only stage its gitlink commit.
- **Branches** — the branch selector lists local branches and provides **New branch…**.
  Jylos only switches branches from a clean working tree and only when the vault is
  itself the repository root.
- **Commit message** field.
- **Operations** — *Refresh, Stage All, Unstage All, Commit, Pull, Push, Sync* and
  *Set Remote…*.
- **Activity log** — a timestamped, read-only transcript of each operation's outcome.
  It grows with the resizable dialog. The active operation and the latest native Git
  transfer progress are visible above the workspace while Git is running; **Cancel**
  stops the Git process and its transfer helpers.

If the vault is not a repository yet, the panel shows an **Initialize Git** prompt
instead. If `git` is not installed/on `PATH`, it shows a clear message and disables
the actions.

## Operations

| Action       | What it does                                                            |
|--------------|-------------------------------------------------------------------------|
| Refresh      | Fetches/prunes `origin` once, then re-reads status + change list.       |
| Stage All    | `git add -A -- .` beneath the vault root only.                          |
| Unstage All  | `git reset -q HEAD -- .` beneath the vault root only.                   |
| Commit       | Commits exactly the files currently staged in the vault.                |
| Pull         | `git pull --no-rebase`; requires an upstream.                           |
| Push         | Normal `git push`; first push configures the current branch upstream.    |
| Sync         | Commit staged changes → pull → push, stopping at the first error.       |
| Set Remote…  | Validates/fetches `origin` and configures tracking when safe.            |
| New branch…  | Creates and switches to a validated local branch from a clean tree.      |
| Branch menu  | Switches to an existing local branch from a clean tree.                  |

The status bar is a set of shortcuts, not a second Git client: remote opens its setup,
changes and commit open the matching area in the workspace, the branch name opens the
branch selector, and history opens the commit history.

## Safety guarantees

These are enforced and will not change without an explicit, documented decision:

- **Nothing destructive runs automatically.** Every action is an explicit click.
- **No force push, ever.** Push uses a normal fast-forward push.
- **No implicit staging.** A commit never overrides the file-level staging choices
  shown in the panel.
- **Nested repositories stay isolated.** Jylos never stages or commits files inside
  a Git submodule or another tracked nested repository. It reports that state clearly
  instead of claiming a parent `git add` included work it cannot own.
- **GitHub size limits are checked before upload.** For GitHub remotes, Jylos inspects
  reachable history before pushing and reports blobs over GitHub's 100 MiB limit. Remove
  those blobs from history or use Git LFS; deleting them only from the working tree is
  not enough for a first push.
- **Conflicts are never auto-resolved.** Unmerged paths (`DD/AU/UD/UA/DU/AA/UU`) are
  surfaced as `conflict`, never reported as staged, and the panel asks you to resolve
  them on disk before committing.
- **The UI never blocks.** Every Git call runs off the JavaFX Application Thread on a
  short-lived daemon `Task`; while one runs the buttons are disabled, an indeterminate
  progress bar and the latest Git transfer line are shown, and the operation can be
  cancelled explicitly. Remote transfers have no arbitrary fixed timeout: a large vault
  may legitimately take time to pack and upload.
- **Git locks are never removed by Jylos.** If another Git client owns
  `index.lock`, Jylos reports the condition and leaves the repository untouched.
- **Vault boundary is respected.** When a vault lives inside a parent repository,
  status, staging, history and commits are scoped to the vault. A commit is refused
  if the parent repository already has staged changes outside the vault, preventing
  Jylos from committing unrelated work.
- **Branch boundary is respected.** Branch changes are disabled for a vault nested in
  a parent repository, because `git switch` would affect that parent workspace too.
- **No dirty branch switches.** Jylos refuses to create or switch a branch until the
  repository is clean; it never carries unsaved working-tree changes across branches.

## Error reporting

Failures are classified by `GitService` into `GitResult.Status` and logged in the
panel's activity log: Git unavailable, not a repository, no remote, merge conflict
during pull, push rejected (non-fast-forward — pull first), authentication failure,
and network errors.

Authentication remains the responsibility of the system Git installation. Use an SSH
agent, Git credential helper, or an already authenticated GitHub CLI setup; Jylos does
not collect credentials or implement a separate account system.

## SSH with GitHub

Jylos invokes your installed Git executable, so SSH works exactly as it does in a
terminal. Configure it once in the operating system, then use an SSH remote in the
Git Sync panel.

1. Check whether you already have a public key:

   ```bash
   ls ~/.ssh/id_ed25519.pub
   ```

2. If not, create one and accept the default path unless you intentionally use a
   different key name:

   ```bash
   ssh-keygen -t ed25519 -C "you@example.com"
   ```

3. Start the agent and load the private key. On macOS:

   ```bash
   eval "$(ssh-agent -s)"
   ssh-add --apple-use-keychain ~/.ssh/id_ed25519
   ```

   On Linux or Git Bash, use `ssh-add ~/.ssh/id_ed25519`. Replace `id_ed25519` with
   the filename you chose, for example `key_github`.

4. Copy the **public** key and add it in GitHub: **Settings → SSH and GPG keys → New
   SSH key**. On macOS:

   ```bash
   pbcopy < ~/.ssh/id_ed25519.pub
   ```

   Elsewhere, print it with `cat ~/.ssh/id_ed25519.pub` and copy the complete line.
   Never upload or share the private key file without the `.pub` suffix.

5. Verify the account connection:

   ```bash
   ssh -T git@github.com
   ```

6. In **Set Remote…**, use the SSH URL supplied by GitHub, for example:

   ```text
   git@github.com:OWNER/REPOSITORY.git
   ```

For a first push, the branch has no upstream by design. Jylos configures it with the
normal `git push --set-upstream origin <branch>` operation after the transfer succeeds.

Jylos provides basic local branch creation and switching for standalone vault
repositories. It does not provide visual conflict resolution, remote branch browsing,
rebases, merges, resets, or force operations; use a full Git client for those advanced
repository-wide tasks.

## Architecture

Git logic stays out of the JavaFX controllers; the panel is a pure view.

| Layer            | Type                                  | Role                                            |
|------------------|---------------------------------------|-------------------------------------------------|
| Service (logic)  | `git/GitService`                      | Drives the system `git` CLI; serialized, off-FX.|
| Model / DTO      | `git/GitStatus`, `GitChange`, `GitResult`, `GitCommit` | Immutable snapshots of Git state.|
| Controller (UI)  | `ui/controller/GitController`         | Resolves the vault, owns the status-bar strip, opens the panel. |
| View component   | `ui/components/GitSyncPanel`          | The consolidated dialog and all its async wiring.|

`GitService` shells out to `git` (no JGit dependency), so there is no native library
to bundle and it behaves like the user's own command line. Local status-bar reads do
not perform network access; the panel's explicit **Refresh** does. All invocations are
serialized app-wide, while locks held by external Git clients are reported safely.
