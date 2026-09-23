# Workspaces

Workspaces let you save and restore groups of open notes and layout state, useful for switching between writing, research or personal contexts.

Workspaces are stored in Jylos' local app data (never inside your notes) and work in both SQLite and Markdown-vault modes.

## Using workspaces

**File → Workspaces** (also in the command palette under *Workspace:*):

| Action | What it does |
| --- | --- |
| Save Current Workspace | Updates the active workspace, or behaves like *Save As* if none is active. |
| Save Current Workspace As… | Prompts for a name and saves the current state. |
| Open Workspace… | Pick a saved workspace and restore it. |
| Manage Workspaces… | Open or delete saved workspaces. |

Typical flow: open the notes you want → *Save Current Workspace As…* → name it → later, *Open Workspace…* to bring those notes back.

## What a workspace stores

- **Name**, id and timestamps.
- **Open note tabs** (in order) and the active note.
- **View mode** — editor / split / preview.
- **Basic layout** — focus mode, sidebar visibility, and split-pane divider positions.
- **Storage mode** at save time (to warn on mismatch).

Not stored (yet): graph filters, and folder-tree selection/expansion.

## Behaviour

- **Restore is additive** — reopening a workspace does not close tabs you already had open.
- **Missing notes don't fail** — a note that no longer exists is skipped with a status message.
- **Storage mismatch** — a non-blocking warning is shown if the workspace was saved under a different storage mode.

## Related

- Technical details: [WORKSPACES.md](https://github.com/RGiskard7/jylos/blob/main/docs/WORKSPACES.md)
