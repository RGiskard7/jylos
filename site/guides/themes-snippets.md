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

The sample theme **Retro Phosphor** uses `base=dark` plus a green overlay.

### Install a theme

Copy the theme folder into `themes/` under Jylos' app data:

| System | Path |
| --- | --- |
| macOS | `~/Library/Application Support/Jylos/themes/<id>/` |
| Windows | `%APPDATA%\Jylos\themes\<id>\` |
| Linux | `~/.config/Jylos/themes/<id>/` |

Then activate it in **Tools → Preferences → External theme** (restart if it does not appear in the list).

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
