# External themes

Each theme is a folder with `theme.properties` and `theme.css`. Start from [`_template/`](_template/).

This folder has **Gruvbox Dark** (`gruvbox-dark/`), **Gruvbox Light** (`gruvbox-light/`) and **Retro Phosphor** (`retro-phosphor/`): copy one to use it, or start from it.

By default the app loads **only** your CSS. Set `base=dark` or `base=light` in `theme.properties` to stack the built-in stylesheet underneath (recommended for full UI coverage — graph, git panel, command palette, quick switcher, themed dialogs, plugin manager, etc.).

Spanish notes below; see also [README.md](../README.md) (Configuration → Themes).

## Instalar un tema

En **Herramientas → Preferencias → Apariencia**, con *Tema* en *Otro tema*:

1. **Abrir carpeta de temas** abre la carpeta donde van (la crea si no existe).
2. Copia ahí la carpeta del tema (`<id>/` con `theme.properties` y `theme.css`).
3. **Recargar temas** y elígelo en la lista. No hace falta reiniciar.

La carpeta es `themes/` dentro de los datos de Jylos:

| Sistema | Ruta |
|---------|------|
| **macOS** | `~/Library/Application Support/Jylos/themes/<id>/` |
| **Windows** | `%APPDATA%\Jylos\themes\<id>\` |
| **Linux** | `~/.local/share/jylos/themes/<id>/` (o `$XDG_DATA_HOME/jylos/themes/<id>/`) |

Los ids `light`, `dark` y `system` son los del menú Ver y se ignoran.

### Desarrollo (desde el repo)

- `./jylos/themes/` — tras `./scripts/build-themes.sh`
- `./themes/` junto al cwd si ejecutas la app desde el proyecto
- `./scripts/build-themes.sh --appdata` (macOS/Linux) o `.\scripts\build-themes.ps1 -AppData` (Windows) los copia a los datos de la app.

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

## Colores

Un tema cambia sobre todo variables de `.root`:

- **Superficies y texto:** `-fx-base-bg`, `-fx-sidebar-bg`, `-fx-header-bg`, `-fx-text-main`, `-fx-text-muted`, `-fx-text-faint`, `-fx-fn-border-color`, `-fx-hover-bg`, `-fx-selected-bg`.
- **Acento y estados:** `-fx-accent`, `-fx-accent-hover`, `-fx-accent-contrast`, `-fx-success`, `-fx-warning`, `-fx-danger`.
- **Controles genéricos del tema oscuro** (etiquetas, campos, botones, interruptores, tooltips): `-fx-label-text`, `-fx-input-bg`, `-fx-input-prompt`, `-fx-control-bg`, `-fx-control-hover-bg`, `-fx-control-pressed-bg`, `-fx-control-border`, `-fx-control-text`, `-fx-toggle-text`, `-fx-tooltip-bg`, `-fx-tooltip-text`. Es mejor redefinirlas que repetir reglas como `.button` o `.label`: una regla genérica en el tema gana también a las específicas del tema base, como la del botón principal.
- **Iconos por tipo:** `-fx-icon-folder`, `-fx-icon-pdf`, `-fx-icon-image`, `-fx-icon-canvas`, `-fx-icon-board`.

El editor, la vista de lectura y el grafo toman el fondo, el texto, el acento y los bordes de esas variables.
