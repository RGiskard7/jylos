# External themes

Each theme is a folder with `theme.properties` and `theme.css`.

By default the app loads **only** your CSS. Set `base=dark` or `base=light` in `theme.properties` to stack the built-in stylesheet underneath (recommended for full UI coverage — graph, git panel, command palette, quick switcher, themed dialogs, plugin manager, etc.). **Retro Phosphor** uses `base=dark` plus a green overlay in `theme.css`.

Spanish notes below; see also [README.md](../README.md) (Configuration → Themes).

## Ubicación

Cada tema es una carpeta bajo `themes/<id>/` (p. ej. `themes/retro-phosphor/` con `theme.properties` + `theme.css`). Jylos escanea esas carpetas al arrancar; **basta con copiar la carpeta del tema al directorio de datos**.

### Desarrollo (desde el repo)

- `./jylos/themes/` — tras `./scripts/build-themes.sh`
- `./themes/` junto al cwd si ejecutas la app desde el proyecto

### App instalada (DMG, deb/rpm, exe/msi)

Los instaladores **no incluyen** temas externos. Copia la carpeta del tema (desde este repo o un zip) dentro de `themes/` bajo los datos de Jylos:

| Sistema | Ruta (crea `themes/` si no existe) |
|---------|-------------------------------------|
| **macOS** | `~/Library/Application Support/Jylos/themes/<id>/` |
| **Windows** | `%APPDATA%\Jylos\themes\<id>\` |
| **Linux** | `~/.config/Jylos/themes/<id>/` (o `$XDG_CONFIG_HOME/Jylos/themes/<id>/`) |

Ejemplo (Retro Phosphor en macOS):

```bash
mkdir -p ~/Library/Application\ Support/Jylos/themes
cp -R themes/retro-phosphor ~/Library/Application\ Support/Jylos/themes/
```

Desde el repo también: `./scripts/build-themes.sh --appdata` (macOS/Linux) o `.\scripts\build-themes.ps1 -AppData` (Windows).

Actívalo en **Herramientas → Preferencias → Tema externo** y reinicia si no aparece en la lista.

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
