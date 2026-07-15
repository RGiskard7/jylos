# Workspaces

Español: [es/WORKSPACES.md](es/WORKSPACES.md)

> Workspaces let you save and restore groups of open notes and layout state, useful for
> switching between writing, research, programming or personal contexts.

Workspaces are stored in Jylos' local app data (never inside your notes) and work in both
SQLite and Markdown-vault modes.

## Using workspaces

**File → Workspaces** (also in the command palette under *Workspace:*):

| Action | What it does |
|--------|--------------|
| Save Current Workspace | Updates the active workspace; if none is active, behaves like *Save As*. |
| Save Current Workspace As… | Prompts for a name and saves the current state (same name overwrites). |
| Open Workspace… | Pick a saved workspace and restore it. |
| Manage Workspaces… | Open or **delete** saved workspaces. |

Typical flow: open the notes you want → *Save Current Workspace As…* → name it
("Programming") → later, *Open Workspace…* → "Programming" to bring those notes back.

## What a workspace stores

Each workspace records:

- **name**, plus an id and created/updated timestamps
- **open note tabs** (ids, in order) and the **active note**
- **view mode** — editor / split / preview
- **basic layout** — focus mode on/off, sidebar visible/hidden, and the two split-pane
  divider positions (sidebar|content and notes-list|editor)
- **storage mode** at save time (to warn on mismatch)

### Not stored (yet)

- **Graph filters** (text/tag/folder) — these are transient view state on the graph
  overlay and are not persisted with the workspace.
- **Folder tree selection/expansion** — left out in this first version to keep restore
  predictable.

## Behaviour & edge cases

- **Restore is additive.** Opening a workspace reopens its notes and activates the saved
  active note; it does **not** close tabs you already had open, so you never lose work.
- **Missing notes don't fail.** If a note saved in a workspace no longer exists, it is
  skipped and a non-blocking status message reports how many were skipped; the rest open
  normally.
- **Storage mismatch.** If a workspace was saved in a different storage mode, a
  non-blocking warning is shown (note ids may not resolve across modes), but Jylos still
  opens whatever it can.
- **Empty workspaces are allowed.** You can save a workspace with no open notes.
- **Corrupt entries are ignored.** A malformed line in the workspaces file is skipped on
  load, never crashing the rest.

## Architecture

| Layer | Type | Role |
|-------|------|------|
| Model | `workspace/Workspace` | Immutable record + line `serialize()`/`parse()` (control-char separated, no JSON dependency). |
| Persistence | `workspace/WorkspaceRepository` | Reads/writes `<appData>/data/workspaces.dat`, one workspace per line, skipping corrupt lines. |
| Service | `workspace/WorkspaceService` | List / save (create or overwrite by name) / update / delete; assigns id + timestamps. |
| Controller | `ui/controller/WorkspaceController` | Dialogs and actions; delegates state capture/restore to the host via callbacks. |
| Host | `ui/controller/MainController` | `captureLiveWorkspace()` snapshots tabs + layout; `applyWorkspace()` restores them. |
