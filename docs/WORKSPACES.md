# Workspaces

Español: [es/WORKSPACES.md](es/WORKSPACES.md)

> A workspace is a named working context (writing, research, a project): its open tabs,
> each tab's back and forward, the active tab, the view mode and the panel layout.
> Opening one replaces the open tabs with its own.

Workspaces are stored in Jylos' local app data (never inside your notes) and work in both
SQLite and Markdown-vault modes. User guide: [site/guides/workspaces.md](../site/guides/workspaces.md).

## Using workspaces

The **status bar** names the workspace in use ("No workspace" when there is none). Its menu
lists "No workspace" and the 8 workspaces used last (by name; preference `workspace.recent`
keeps the ids), saves the one in use, saves as a new one, opens the manage dialog ("All
workspaces (n)…" when there are more than 8) and turns *Save the workspace when switching
to another* on or off. The manage dialog has a search field that ignores case and accents;
Enter opens the first workspace found.

**No workspace** is a context of its own: opening a workspace from it keeps its tabs, layout
and history (in `session.dat`, under the reserved id `jylos:no-workspace`), and picking it
again saves the workspace in use (when that is on) and brings them back.

**File → Workspaces** and the command palette (*Workspace:*) have the same actions:

| Action | What it does |
|--------|--------------|
| Save Current Workspace | Updates the workspace in use; with none, behaves like *Save As*. |
| Save Current Workspace As… | Prompts for a name and saves the current state (same name overwrites); that workspace is then in use. |
| Manage Workspaces… | Open (also with a double click), rename or delete saved workspaces. The palette's *Workspace: Open…* opens it too. |

## What a workspace stores

- **name**, plus an id and created/updated timestamps
- **open tabs** (note ids and the graph, in order) and the **active tab**
- **each tab's back and forward**, up to 20 steps each, and **whether it is pinned** (in `workspace-history.dat`)
- **view mode** — editor / split / preview
- **basic layout** — focus mode on/off, sidebar visible/hidden, and the two split-pane
  divider positions (sidebar|content and notes-list|editor)
- **storage mode** at save time (to warn on mismatch)

New, empty tabs are not stored: their ids do not last. Graph filters and the folder tree's
selection are not stored either.

## Behaviour & edge cases

- **Opening replaces the tabs.** When *Save the workspace when switching to another* is on
  (the default, preference `workspace.save_on_switch`), the workspace in use is saved first.
  Then every tab closes, after the open note's unsaved edits are dealt with (cancelling keeps
  everything), and the workspace's tabs open with their history.
- **The last session comes back.** On closing, the open tabs, their history and the workspace
  in use are kept in `session.dat` and `session-history.dat`; the next start reopens them.
  What it reopens is a vault setting (Preferences > Vault > *When the vault opens*,
  `VaultSettings.startup`): all the tabs of the last time or only the pinned ones, and in
  front the tab that was active, a new tab, the home page or today's daily note
  (`StartupSupport`). Opening another vault applies only what goes in front.
- **Pinned tabs.** A tab's menu (right click) pins it: pinned tabs go first, show a pin instead
  of the close cross (a click on it unpins), and keep what they show: what is opened from one
  goes to a new tab, back and forward bring a pinned tab forward instead of taking its note,
  and *Close others* leaves them (`EditorTabsModel`).
- **Home page.** A note or a canvas of the vault (Preferences > Vault), opened with *Go to
  home page*: the house in the top bar (Cmd/Ctrl or middle click: in a new tab), the View menu
  or the command palette. The house and its menu item show only when the vault has a home page.
- **Missing notes don't fail.** A note that no longer exists is skipped, in the tabs and in
  their history; when opening a workspace, a status message says how many were skipped.
- **Storage mismatch.** If a workspace was saved in a different storage mode, a
  non-blocking warning is shown, but Jylos still opens whatever it can.
- **Empty workspaces are allowed.**
- **Corrupt entries are ignored.** A malformed line in any of these files is skipped on
  load, never crashing the rest.

## Files

All in `<appData>/data/`:

| File | Content |
|------|---------|
| `workspaces.dat` | One workspace per line (`Workspace.serialize()`). Its shape is unchanged, so older versions still read it. |
| `workspace-history.dat` | One line per workspace id: each tab's back and forward, in the order of its tabs, and a third part `pinned` for a pinned tab (older versions read the first two and leave it). |
| `session.dat`, `session-history.dat` | The last session, in the same shapes; its id and name are the workspace in use. Also the tabs of "No workspace", under the id `jylos:no-workspace`. |

## Architecture

| Layer | Type | Role |
|-------|------|------|
| Model | `workspace/Workspace`, `workspace/TabHistory` | Immutable records; `Workspace` has a line `serialize()`/`parse()` (control-char separated, no JSON dependency). |
| Persistence | `workspace/WorkspaceRepository`, `workspace/WorkspaceHistoryRepository`, `workspace/SessionStore` | The files above, skipping corrupt lines. |
| Service | `workspace/WorkspaceService` | List / save (create or overwrite by name, with history) / rename / delete; assigns id + timestamps. |
| Controller | `ui/controller/WorkspaceController` | Status bar indicator and menu, dialogs, switching and the last session; reaches the window through its `Host`. |
| Tabs | `ui/controller/TabsSupport`, `ui/components/EditorTabsModel` | Close every tab, reopen tabs with their history, read each tab's history. |
| Host | `ui/controller/MainController` | `captureLiveWorkspace()` snapshots tabs + layout; `applyWorkspaceLayout()` reapplies the layout. |
