# Kanban

Un tablero Kanban es una nota normal cuyo cuerpo Markdown guarda columnas y tarjetas, en el espíritu del plugin Kanban de Obsidian.

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

Usa **Ver → Tablero Kanban** o **Ctrl/Cmd+K**, y luego elige o crea un tablero desde la barra de herramientas.

## Editar

- Añade, renombra y borra columnas.
- Crea, edita y borra tarjetas.
- Arrastra tarjetas entre columnas.

## Tarjetas y notas

Una tarjeta puede enlazar a una nota (`[[Título]]`) o convertirse en nota.

## Opciones de columna

Desde el menú de la columna puedes definir, en la línea del encabezado:

- **Límite WIP** — `[wip=N]`; la insignia de recuento se pone roja al superarse.
- **Color** — `[color=#rrggbb]`.

## Adjuntos

Las tarjetas que referencian una imagen o un PDF (`![…](archivo.png)`, `[[escaneo.pdf]]`) muestran una miniatura embebida (primera página del PDF vía PDFBox).

## Relacionado

- [Canvas](/es/guides/canvas)
