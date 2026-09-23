# Grafo de conocimiento

El grafo de conocimiento dibuja tus notas y sus wiki-links como una red interactiva por fuerzas y, con **Knowledge Insights**, los convierte en una herramienta analítica.

## Abrir el grafo

- **Ver → Vista de grafo**
- Botón de la barra de herramientas
- **Ctrl/Cmd+G**
- Paleta de comandos

## Vistas global y local

- **Grafo global** — todas las notas y aristas de wiki-links resueltos, con nodos de etiquetas opcionales y aristas nota→etiqueta.
- **Grafo local** — la nota actual más sus vecinos dentro de una profundidad configurable.

## Interactúa

- Zoom y paneo.
- Arrastra nodos.
- Pasa el cursor para resaltar vecinos.
- Haz clic en un nodo de nota para abrirlo.

## Ajustes y filtros

El panel de ajustes controla la repulsión, la fuerza y distancia de los enlaces, la gravedad central, los huérfanos y enlaces sin resolver, las flechas, el color por carpeta y las opciones de etiqueta/tamaño/línea.

El panel de ajustes también incluye filtros que redibujan el grafo sin reconstruirlo:

- **Filtrar por texto** — conserva los nodos cuya etiqueta coincide.
- **Etiqueta** — restringe el grafo a las notas con la etiqueta seleccionada.
- **Carpeta** — restringe el grafo a un grupo de carpetas.
- **Mostrar huérfanos** — oculta/muestra notas sin conexiones.
- **Mostrar sin resolver** — oculta/muestra nodos de enlaces rotos ("fantasmas").
- **Mostrar etiquetas** — incluye/excluye nodos `#etiqueta`.

## Knowledge Insights

Abre **Ver → Knowledge Insights** (o **Ctrl/Cmd+Shift+K**) para analizar tu vault. El informe se abre en un diálogo con pestañas y cada fila es clicable: haz doble clic en una nota para abrirla.

| Pestaña | Muestra |
| --- | --- |
| Resumen | Totales (notas, enlaces, backlinks, etiquetas), media de enlaces por nota y una puntuación de salud del grafo con su desglose. |
| Más conectadas | Las 10 notas con más conexiones. |
| Notas huérfanas | Notas sin enlaces resueltos de entrada ni de salida. |
| Enlaces rotos | Enlaces que apuntan a notas que no existen. |
| Notas sin etiquetas | Notas que no llevan etiquetas. |
| Uso de etiquetas | Etiquetas más usadas con su recuento. |

La puntuación de salud es un número simple y explicable en `[0, 100]` que parte de 100 y resta por notas huérfanas, notas sin etiquetas y enlaces rotos.

## Relacionado

- [Wiki-links](/es/guides/wiki-links) — cómo se crean las aristas.
- Detalles técnicos: [GRAPH.md](https://github.com/RGiskard7/jylos/blob/main/docs/GRAPH.md)
