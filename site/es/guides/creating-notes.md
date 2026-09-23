# Crear notas

Las notas son el corazón de Jylos. Aquí está todo lo relacionado con crearlas, editarlas y organizarlas.

## Crear

- **Ctrl/Cmd+N** crea una nota nueva en la carpeta actual.
- **Nota diaria** crea la nota de hoy.
- **Nueva nota desde plantilla** crea una nota a partir de una plantilla — una nota de una carpeta "Templates" — sustituyendo los marcadores <span v-pre>{{title}}</span>, <span v-pre>{{date}}</span>, <span v-pre>{{time}}</span> y <span v-pre>{{datetime}}</span>.

## Editar

El editor es CodeMirror 6 y WYSIWYG por defecto: escribes Markdown y se renderiza en vivo mientras tecleas — tablas GFM, fórmulas KaTeX, emoji y embebidos aparecen en su sitio.

- **Live Preview** (por defecto) — la vista WYSIWYG. La puntuación se oculta mientras escribes, manteniendo un único documento y un único historial de deshacer.
- **Modo fuente** — Markdown plano, en Preferencias, cuando quieres el texto sin formato.
- **Vista de lectura** — el render completo para leer con comodidad; si lo prefieres, sigue existiendo una vista dividida lado a lado como comando **Ver** aparte.

El **modo concentración / escritura** (**Ctrl/Cmd+Shift+F**) oculta todo salvo el editor.

## Funciones de Markdown

- Tablas GFM, autolinks y tachado.
- Las listas de tareas (`- [ ]` / `- [x]`) se muestran como casillas.
- Resaltado de bloques de código (highlight.js).
- Fórmulas KaTeX con `$…$`, `$$…$$` y delimitadores LaTeX.
- Emoji en la vista previa.
- Transclusión con `![[Nota]]` o `![[Nota#Encabezado]]`, con detección de ciclos.
- Enlaces enriquecidos: pega una URL para insertarla como tarjeta visual (los metadatos se obtienen en segundo plano).

## Pestañas e indicador de guardado

Abre varias notas en pestañas. Cada pestaña muestra un indicador inline: ámbar significa sin guardar, verde significa guardado.

## Historial de versiones

**Herramientas → Historial de notas** (**Ctrl/Cmd+Shift+H**) abre instantáneas locales tomadas antes de cada guardado (coalescidas, con tope de 50 por nota), con un visor de diferencias línea a línea y restauración con un clic. Las instantáneas de notas privadas permanecen cifradas.

## Importar y exportar

- Exporta una nota, o todo el vault, a HTML o PDF.
- Importa una nota individual.
- Importa un **vault de Obsidian** (jerarquía de carpetas, frontmatter y etiquetas preservados; `.obsidian/` se omite) o una exportación **`.enex` de Evernote** (ENML convertido a Markdown, etiquetas conservadas) desde el menú Archivo.

## Organiza

Mueve notas entre carpetas, añade etiquetas y marca favoritas desde la barra lateral. Borrar mueve las notas a la papelera, desde donde se pueden restaurar.

## Relacionado

- [Wiki-links](/es/guides/wiki-links)
- [Búsqueda](/es/guides/search)
- [Workspaces](/es/guides/workspaces)
