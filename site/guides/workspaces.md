# Workspaces

A workspace is a working context with a name ("Writing", "Research", a project): its open tabs, what each tab went through (back and forward), the active tab, the view mode and the layout of the panels. Opening a workspace replaces the open tabs with its own, so you switch from one context to another without tabs piling up.

Workspaces are stored in Jylos' local app data (never inside your notes) and work in both SQLite and Markdown-vault modes.

## The workspace in use

The status bar shows the workspace in use, next to the storage switcher ("No workspace" when there is none). Click it to:

- **switch** to another workspace (the one in use is ticked). The menu lists the eight used last; with more, **All workspaces…** opens the list, with a search field;
- go back to **No workspace**: the tabs you had before opening a workspace come back;
- **save** the one in use, or **save as a new workspace**;
- **manage** them: open, rename or delete;
- turn **Save the workspace when switching to another** on or off (on by default).

The same actions are in **File → Workspaces** and in the command palette under *Workspace:*.

Typical flow: open the notes you want → *Save as New Workspace…* → name it → later, pick it in the status bar to bring those tabs back.

## Switching

- Opening a workspace first saves the one in use, when *Save the workspace when switching to another* is on, so you find it as you left it. With it off, a workspace only changes when you save it.
- The open note's unsaved edits are asked about before the tabs close; cancelling keeps everything as it was.
- Each tab gets back its back and forward, so going back works as before switching.
- **No workspace** is a context of its own: when you open a workspace from it, its tabs are kept, and picking it again brings them back.

## When Jylos starts

Jylos reopens the tabs that were open when it closed, with their back and forward, and the workspace that was in use. Notes deleted in the meantime are left out.

## What a workspace stores

- Its **name**.
- The **open tabs** in order (notes and the graph), the **active tab**, and the **back and forward of each tab**.
- The **view mode**: editor, split or reading.
- The **layout**: focus mode, sidebar shown or hidden, and the panel dividers.
- The **storage mode** when it was saved, to warn when it is opened in another one.

## Behaviour

- **Missing notes don't fail**: a note that no longer exists is skipped, with a message in the status bar.
- **Storage mismatch**: a non-blocking warning appears if the workspace was saved in another storage mode.
- New, empty tabs are not stored.

## Related

- Technical details: [WORKSPACES.md](https://github.com/RGiskard7/jylos/blob/main/docs/WORKSPACES.md)
