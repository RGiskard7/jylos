# Primer arranque

La primera vez que abres Jylos eliges dónde vivirán tus notas. Esa decisión es la base de todo lo demás.

## Elige tu almacenamiento

Jylos admite dos modos de almacenamiento:

- **SQLite** (por defecto) — un único fichero `jylos/data/database.db` que guarda notas, carpetas y etiquetas.
- **Vault de archivos Markdown** — una carpeta de notas `.md` con frontmatter YAML, para leerlas y editarlas con cualquier editor.

Puedes cambiar entre ambos desde **Herramientas → Cambiar almacenamiento**. Cambiar de un vault de archivos a otro recarga la sesión sin reiniciar; cambiar entre SQLite y archivos requiere reiniciar.

## Directorios de runtime

Jylos guarda sus ficheros en la carpeta estándar de cada sistema: `%APPDATA%\Jylos` en Windows, `~/Library/Application Support/Jylos` en macOS y `~/.local/share/jylos` en Linux. Los logs van a `%LOCALAPPDATA%\Jylos\logs`, `~/Library/Logs/Jylos` y `~/.local/state/jylos/logs`. El ZIP portable de Windows lo guarda todo junto a `Jylos.exe`. Dentro de la carpeta de datos:

```text
data/       # la base de datos SQLite o tu vault
backups/    # copias de seguridad automáticas de SQLite
plugins/    # JARs de plugins externos
themes/     # temas externos instalados
snippets/   # snippets CSS del usuario
```

## Idioma y tema

Jylos incluye textos de interfaz en inglés y español. El tema puede ser claro, oscuro o seguir tu sistema. Ambos se configuran desde **Preferencias**.

## Crea tu primera nota

Usa **Ctrl/Cmd+N** para crear una nota, o abre la paleta de comandos con **Ctrl/Cmd+P** y escribe "nueva nota". Las notas son Markdown plano con soporte para tablas GFM, fórmulas KaTeX, emoji y `[[wiki-links]]`.

Consulta [Primeros pasos](/es/start/getting-started) para un recorrido guiado.
