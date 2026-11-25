# Crear notas

Las notas son el corazón de Jylos. Aquí está todo lo relacionado con crearlas, editarlas y organizarlas.

## Crear

- **Ctrl/Cmd+N** crea una nota nueva en la carpeta actual, o donde diga Preferencias → Bóveda.
- **Nota diaria** abre o crea la nota de hoy, y **Nueva desde plantilla** e **Insertar plantilla** usan las plantillas de la bóveda: consulta [Plantillas y nota diaria](/es/guides/templates).

## Editar

El editor es CodeMirror 6 y WYSIWYG por defecto: escribes Markdown y se renderiza en vivo mientras tecleas — tablas GFM, fórmulas KaTeX, emoji y embebidos aparecen en su sitio.

- **Live Preview** (por defecto) — la vista WYSIWYG. La puntuación se oculta mientras escribes, manteniendo un único documento y un único historial de deshacer.
- **Modo fuente** — Markdown plano, cuando quieres el texto sin formato.
- **Vista de lectura** — el render completo para leer con comodidad; si lo prefieres, sigue existiendo una vista dividida lado a lado como comando **Ver** aparte.

Se cambia entre ellos con un clic en el modo del editor, a la derecha de la barra de estado.

El **modo concentración / escritura** (**Ctrl/Cmd+Shift+F**, o **Ver → Modo concentración** y la paleta de comandos) oculta todo salvo el editor. El mismo atajo lo cierra; al entrar, un aviso lo recuerda.

## Funciones de Markdown

- Tablas GFM, autolinks y tachado.
- Las listas de tareas (`- [ ]` / `- [x]`) se muestran como casillas.
- Resaltado de bloques de código (highlight.js).
- Fórmulas KaTeX con `$…$`, `$$…$$` y delimitadores LaTeX.
- Emoji en la vista previa.
- Transclusión con `![[Nota]]` o `![[Nota#Encabezado]]`, con detección de ciclos.
- Enlaces enriquecidos: pega una URL para insertarla como tarjeta visual (los metadatos se obtienen en segundo plano).

## Pestañas y estado de guardado

Las pestañas funcionan como en VS Code:

- Un clic en una nota la abre en la **pestaña actual**, sustituyendo la nota que había. Si la nota ya está abierta en otra pestaña, se activa esa pestaña en lugar de abrirla dos veces.
- Para abrir una nota en una **pestaña nueva** (justo a la derecha de la actual): **Cmd+clic** en macOS o **Ctrl+clic** en Windows y Linux, **clic central**, o **Abrir en pestaña nueva** en el menú contextual de la nota. Funciona en el árbol Explorador, la lista de notas, la cuadrícula, Recientes y Favoritas, en las tarjetas enlazadas del Kanban, en los enlaces de la vista de lectura y en los del panel de propiedades.
- En el editor el clic sirve para escribir, así que **Cmd/Ctrl+clic** sigue un enlace en la pestaña actual, y **Cmd/Ctrl+Mayús+clic** o el **clic central** lo abren en una pestaña nueva.
- Las notas nuevas y las de un espacio de trabajo se abren cada una en su pestaña.
- El clic central sobre una pestaña la cierra.

Si prefieres una pestaña por nota, activa **Abrir siempre las notas en una pestaña nueva** en **Herramientas → Preferencias**.

Los enlaces entre notas se abren en la pestaña actual: en modo fuente, Cmd/Ctrl+clic es el gesto para seguir un enlace.

La barra de estado muestra **Guardado** o **Sin guardar** según el estado de la nota abierta; su tooltip dice cada cuánto se autoguarda, o cómo activarlo. Cada pestaña muestra además un punto: ámbar significa sin guardar, verde significa guardado.

## Historial de versiones

**Herramientas → Historial de notas** (**Ctrl/Cmd+Shift+H**) abre instantáneas locales tomadas antes de cada guardado (coalescidas, con tope de 50 por nota), con un visor de diferencias línea a línea y restauración con un clic. Las instantáneas de notas privadas permanecen cifradas.

## Importar y exportar

- Exporta una nota, o todo el vault, a HTML o PDF.
- Importa una nota individual.
- Importa una **bóveda**, cualquier carpeta de notas Markdown, de Obsidian, de Jylos o de otra app (jerarquía de carpetas, propiedades y etiquetas preservadas; las carpetas que empiezan por punto, como `.obsidian/`, se omiten; las imágenes y demás adjuntos no se copian), o una exportación **`.enex` de Evernote** (ENML convertido a Markdown, etiquetas conservadas) desde el menú Archivo. Para trabajar con una bóveda tal como está, con sus adjuntos, abre su carpeta como bóveda en lugar de importarla: consulta [Compatibilidad con Obsidian](/es/guides/obsidian-compatibility).

## Organiza

Mueve notas entre carpetas, añade etiquetas y marca favoritas desde la barra lateral. Las notas y las carpetas tienen un menú contextual (clic derecho) con las mismas opciones en los dos diseños, incluido **Mover a Carpeta...**; también puedes arrastrar una nota o una carpeta sobre otra carpeta. Borrar mueve las notas a la papelera, desde donde se pueden restaurar.

En el diseño **Explorador**, la cabecera sobre el árbol tiene:

- un **filtro** que busca en el nombre y en la ruta (`trabajo/acta`) entre todas las notas y carpetas de la bóveda (sin distinguir mayúsculas ni acentos) y muestra las coincidencias dentro de sus carpetas;
- el **orden de las notas** (nombre, última edición o creación);
- **Mostrar automáticamente el archivo actual**: activado, el árbol despliega las carpetas de la nota que abres y la selecciona;
- un solo botón para **expandir todo** o **colapsar todo**.

Las carpetas que dejas abiertas siguen abiertas la próxima vez. En el árbol, **F2** (o **Renombrar** en el menú) cambia el nombre en la propia fila: Enter lo aplica y Escape lo cancela. Una carpeta nueva se crea como "Sin nombre", lista para escribir su nombre, y una nota nueva como "Sin título". Si mantienes una nota arrastrada sobre una carpeta cerrada, la carpeta se abre. El menú de las notas también tiene **Duplicar**, **Copiar ruta** y **Abrir en la app por defecto**.

**Mostrar el número de notas junto a cada carpeta** (Preferencias) se aplica a los dos diseños.

## Relacionado

- [Wiki-links](/es/guides/wiki-links)
- [Búsqueda](/es/guides/search)
- [Workspaces](/es/guides/workspaces)
