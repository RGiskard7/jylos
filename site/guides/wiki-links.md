# Wiki-links

Wiki-links connect your notes into a network, and they are what powers the graph and the backlinks panel.

## Create a link

Type `[[` in the editor and Jylos suggests notes as you type:

- Accents and case do not matter (`reunion` finds "Reunión"). Notes whose name starts with what you typed come first, then those with a word that starts with it, then those that contain it, then those with its letters in order (`pj` finds "Proyecto Jylos").
- Every note is listed with its folder, also two with the same name. Jylos writes the shortest link that names the one you pick: its name, or its path when another note has that name. Typing a `/` searches by path (`personal/reu`).
- A note's `aliases` are listed too, and are written as `[[Note|alias]]`.
- After `#` it lists the headings of that note (`[[Note#`), or of the open note (`[[#`). After `|` nothing is suggested: the text the link shows is yours.

Jylos also supports internal Markdown links:

```markdown
[[My Note]]
[label](my-note.md)
```

Click a link in the reading view to open the target note.

## Backlinks

The right panel lists incoming links: every note that links to the current note, whether via `[[wiki-links]]` or Markdown links.

## Resolution

Wiki-link resolution is shared between the preview, the graph and the backlinks panel through a single resolver (`WikiLinkResolver`), so the three always agree on what a link points to.

## Transclusion

Embed another note's rendered content inline with `![[Note]]` or a section with `![[Note#Heading]]`. Transclusion is bounded with cycle detection so recursive embeds cannot loop forever.

## Links in Kanban and Canvas

- A Kanban card can link to a note with `[[Title]]` or be converted into a note.
- Canvas nodes can link to notes and connect to each other with edges.

See [Knowledge graph](/guides/knowledge-graph) to visualize the network these links form.
