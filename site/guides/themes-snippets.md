# Themes & CSS Snippets

Jylos ships with light, dark and system themes, and you can extend or restyle the whole app with external themes and CSS snippets.

## Built-in themes

- **Light**, **Dark** and **System** — *System* follows your OS light/dark setting (polled live).
- Switch from **View → Theme**.

## External themes

An external theme is a folder containing `theme.properties` and `theme.css`:

```properties
id=my-theme
name=My Theme
css=theme.css
darkLike=false
base=dark
```

- `darkLike=true` — dark Markdown preview and dark-aware dialogs.
- `base=dark|light|system|auto` — stacks the built-in stylesheet under your CSS. Set it to `dark` or `light` for full UI coverage (graph, Git panel, command palette, dialogs, plugin manager, …).

The repository's `themes/` folder has **Gruvbox Dark**, **Gruvbox Light** and **Retro Phosphor** (`base=dark` plus a green overlay), ready to copy and to start from; download them from [Themes](/guides/themes). Optional `author`, `description` and `description.es` keys are shown there. The colours of the base theme's generic controls (labels, fields, buttons, toggles, tooltips) are variables too (`-fx-label-text`, `-fx-input-bg`, `-fx-control-bg`, `-fx-control-border`, …), so a theme recolours them by setting those variables in `.root`.

### Install a theme

In **Tools → Preferences → Appearance**, *Open themes folder* opens the folder where themes go (creating it if needed). Copy the theme's folder there, press *Reload themes* and choose it; no restart needed. The folder is `themes/` under Jylos' app data:

| System | Path |
| --- | --- |
| macOS | `~/Library/Application Support/Jylos/themes/<id>/` |
| Windows | `%APPDATA%\Jylos\themes\<id>\` |
| Linux | `~/.local/share/jylos/themes/<id>/` |

Its colours also reach the editor, the reading view and the graph.

From the repository you can also build themes into `jylos/themes/`:

```bash
./scripts/build-themes.sh
# or, to copy them into your app data:
./scripts/build-themes.sh --appdata
```

## CSS snippets

A snippet is a plain `.css` file layered **on top of** the active theme — you tweak colours without writing a whole theme.

1. Open **Preferences → CSS snippets → Open folder** (this is `<appData>/snippets`).
2. Drop `.css` files in, then **Reload** and tick the ones you want.

Snippets are layered after the theme, so their rules win. Names must be simple `.css` filenames. Ready-made adaptive examples (Atom One, Nord, Solarized — each with a dark and a light variant) live in [`snippets-examples/`](https://github.com/RGiskard7/jylos/tree/main/snippets-examples).

### Writing a snippet

Jylos tags the scene root with `theme-dark` or `theme-light`, so you can support both modes in one file. Snippets use **JavaFX CSS** (a subset of web CSS): properties are prefixed `-fx-` and there are no media queries — branch on the root class instead:

```css
.root.theme-dark {
    -fx-accent: #61afef;
    -fx-text-main: #abb2bf;
    -fx-sidebar-bg: #21252b;
}

.root.theme-light {
    -fx-accent: #4078f2;
}
```

The same layered styles apply to themed dialogs and command overlays such as the command palette and quick switcher.

## Related

- [Plugins](/guides/plugins)
- [Install Jylos](/start/install)
