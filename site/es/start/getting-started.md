# Primeros pasos

Un recorrido rápido por los flujos principales para que empieces a escribir ya.

## La disposición

Jylos tiene dos diseños. Se cambia entre ellos con el botón de diseño, a la derecha de la barra superior, o en **Herramientas → Preferencias**; la elección se recuerda.

- **Explorador** — la barra lateral muestra un solo árbol con tus carpetas y notas juntas. Un clic en una carpeta la despliega y un clic en una nota la abre. El panel de notas solo aparece al buscar o al elegir una etiqueta, para mostrar los resultados, y se cierra con su **✕**. Las instalaciones nuevas empiezan en este diseño.
- **Clásico** — carpetas, lista de notas y editor lado a lado (**columnas**), o las carpetas encima de la lista de notas (**apilado**). La lista de notas añade orden, vista de cuadrícula y la opción de incluir las notas de las subcarpetas.

En los dos diseños:

- **Barra lateral izquierda** — carpetas, etiquetas, recientes, favoritos y papelera.
- **Centro** — el editor Markdown con vista previa en vivo y pestañas (ver [Pestañas](/es/guides/creating-notes#pestanas-y-estado-de-guardado)).
- **Panel derecho** — backlinks e información de la nota.
- **Barra de estado** — la bóveda, si la nota está guardada ("Guardado" / "Sin guardar") y el modo del editor. Un clic en el modo permite cambiar entre vista previa en vivo, fuente, lectura y lectura enlazada.

## Crea y organiza

1. Crea una nota con **Ctrl/Cmd+N**.
2. Mete las notas en carpetas desde la barra lateral (arrastra o usa el menú de la carpeta).
3. Asigna etiquetas con el flujo de etiquetas, disponible en modo SQLite y en modo vault.
4. Marca notas como favoritas o encuéntralas en **Recientes**.

## Enlaza notas entre sí

Escribe `[[` para autocompletar un wiki-link al título de otra nota, o escribe un enlace Markdown a `nota.md`. Haz clic en un enlace en la vista de lectura para abrir el destino.

Consulta [Wiki-links](/es/guides/wiki-links) para ver el panorama completo, incluidos los backlinks.

## Explora el grafo

Abre **Ver → Vista de grafo** (o **Ctrl/Cmd+G**) para ver tus notas como un grafo por fuerzas. Haz clic en un nodo para abrir su nota. Alterna entre el grafo global y un vecindario local alrededor de la nota actual.

Consulta [Grafo de conocimiento](/es/guides/knowledge-graph).

## Planifica con Kanban y Canvas

- **Kanban** — una nota normal cuyos encabezados son columnas y sus elementos de lista son tarjetas. Ábrela con **Ver → Tablero Kanban** o **Ctrl/Cmd+K**.
- **Canvas** — un fichero `.canvas` compatible con Obsidian sobre una superficie infinita. Crea uno y añade nodos de texto, enlaces y grupos.

Consulta [Kanban](/es/guides/kanban) y [Canvas](/es/guides/canvas).

## Siguientes pasos

- [Búsqueda](/es/guides/search) — búsqueda de texto completo y operadores avanzados.
- [Plugins](/es/guides/plugins) — Dataview, MCP Server, Publish y JARs externos.
- [Notas privadas](/es/guides/encryption) — cifra el cuerpo de las notas con AES-256.
