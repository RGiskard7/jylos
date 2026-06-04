# External themes

Each theme is a folder with `theme.properties` and `theme.css`.

By default the app loads **only** your CSS. Set `base=dark` or `base=light` in `theme.properties` to stack the built-in stylesheet underneath (recommended for full UI coverage — graph, git panel, plugin manager, etc.). **Retro Phosphor** uses `base=dark` plus a green overlay in `theme.css`.

Spanish notes below; see also [README.md](../README.md) (Configuration → Themes).

## Ubicación

- `{AppData}/themes/`
- `./themes/` (directorio de trabajo)

## `theme.properties`

```properties
id=mi-tema
name=Mi Tema
css=theme.css
darkLike=true
base=dark
```

- `darkLike=true` — dark Markdown preview and themed dialogs (`UiDialogs`).
- `base=dark|light|system|auto` — built-in layer under your CSS (`auto` = follow menu light/dark/system, or `darkLike` when menu is external-only).

Built-in **System** theme (menu View → Theme) follows the OS light/dark setting when selected; external themes are independent of that mode.

## Retro Phosphor

`base=dark` + phosphor palette overlay: sidebar nav, graph, git/backlinks panel, plugin manager, attachments, note cards, scrollbars, and editor/preview surfaces aligned with the current Jylos UI.
