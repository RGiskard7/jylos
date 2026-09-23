# Temas y snippets CSS

Jylos incluye temas claro, oscuro y de sistema, y puedes ampliar o cambiar el aspecto de toda la aplicación con temas externos y snippets CSS.

## Temas integrados

- **Claro**, **Oscuro** y **Sistema** — *Sistema* sigue la configuración claro/oscuro de tu SO (consultada en vivo).
- Se cambia desde **Ver → Tema**.

## Temas externos

Un tema externo es una carpeta que contiene `theme.properties` y `theme.css`:

```properties
id=mi-tema
name=Mi Tema
css=theme.css
darkLike=false
base=dark
```

- `darkLike=true` — vista previa Markdown oscura y diálogos conscientes del tema oscuro.
- `base=dark|light|system|auto` — apila la hoja de estilos integrada debajo de tu CSS. Pon `dark` o `light` para una cobertura completa de la UI (grafo, panel Git, paleta de comandos, diálogos, gestor de plugins, …).

El tema de ejemplo **Retro Phosphor** usa `base=dark` más una capa verde.

### Instalar un tema

Copia la carpeta del tema en `themes/` dentro de los datos de Jylos:

| Sistema | Ruta |
| --- | --- |
| macOS | `~/Library/Application Support/Jylos/themes/<id>/` |
| Windows | `%APPDATA%\Jylos\themes\<id>\` |
| Linux | `~/.config/Jylos/themes/<id>/` |

Luego actívalo en **Herramientas → Preferencias → Tema externo** (reinicia si no aparece en la lista).

Desde el repositorio también puedes compilar los temas a `jylos/themes/`:

```bash
./scripts/build-themes.sh
# o, para copiarlos a tus datos de la app:
./scripts/build-themes.sh --appdata
```

## Snippets CSS

Un snippet es un fichero `.css` plano que se aplica **encima** del tema activo — ajustas colores sin escribir un tema completo.

1. Abre **Preferencias → Snippets CSS → Abrir carpeta** (es `<appData>/snippets`).
2. Deja los ficheros `.css` ahí, pulsa **Recargar** y marca los que quieras.

Los snippets se aplican después del tema, así que sus reglas ganan. Los nombres deben ser ficheros `.css` sencillos. Hay ejemplos adaptativos listos para usar (Atom One, Nord, Solarized — cada uno con variante oscura y clara) en [`snippets-examples/`](https://github.com/RGiskard7/jylos/tree/main/snippets-examples).

### Escribir un snippet

Jylos marca la raíz de la escena con `theme-dark` o `theme-light`, así que puedes soportar ambos modos en un solo fichero. Los snippets usan **CSS de JavaFX** (un subconjunto del CSS web): las propiedades llevan prefijo `-fx-` y no hay media queries — usa la clase raíz en su lugar:

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

Los mismos estilos en capas se aplican a los diálogos con tema y a las superposiciones de comandos como la paleta de comandos y el selector rápido.

## Relacionado

- [Plugins](/es/guides/plugins)
- [Instalar Jylos](/es/start/install)
