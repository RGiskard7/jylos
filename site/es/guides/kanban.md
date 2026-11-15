# Kanban

Un tablero Kanban es una nota normal cuyo cuerpo Markdown guarda columnas y tarjetas, en el espíritu del plugin Kanban de Obsidian. Una nota-tablero se abre como tablero, en su propia pestaña como cualquier nota.

## Estructura

- `## Encabezado` — una columna.
- `- tarjeta` — una tarjeta de esa columna.

```markdown
## Pendiente
- Investigar el tema
- Escribir el esquema

## En curso
- Redactar la nota
```

## Abrir un tablero

Abre la nota del tablero como cualquier nota (el árbol, el selector rápido, un enlace); los tableros tienen su propio icono en el árbol, la lista de notas y la pestaña. **Ver → Tablero Kanban**, **Ctrl/Cmd+K** o **Tablero Kanban** en una pestaña nueva muestran la lista de tableros, el cambiado más recientemente primero, para elegir uno o crear otro.

## Nuevo tablero

**Archivo → Nuevo tablero Kanban**, la paleta de comandos, **Nuevo tablero Kanban** en una carpeta del árbol, o **+ Nuevo tablero** en la lista de tableros. Se crea como una nota nueva, con tres columnas (por hacer, en curso, hecho) y el título listo para renombrarlo.

El botón de tablero de la cabecera de la nota la muestra como Markdown, y vuelta. Lo que cambias en el tablero se guarda como cualquier edición.

## Editar

- Añade, renombra y borra columnas.
- Crea, edita y borra tarjetas.
- Arrastra tarjetas entre columnas.

## Tarjetas y notas

Una tarjeta puede enlazar a una nota (`[[Título]]`, `[[Carpeta/Título]]`) o convertirse en nota. Al pulsar una tarjeta enlazada, la nota se abre en la pestaña del tablero; atrás vuelve al tablero.

## Opciones de columna

Desde el menú de la columna puedes definir, en la línea del encabezado:

- **Límite WIP** — `[wip=N]`; la insignia de recuento se pone roja al superarse.
- **Color** — `[color=#rrggbb]`.

## Adjuntos

Las tarjetas que referencian una imagen o un PDF (`![…](archivo.png)`, `[[escaneo.pdf]]`) muestran una miniatura embebida (primera página del PDF vía PDFBox).

## Relacionado

- [Canvas](/es/guides/canvas)
