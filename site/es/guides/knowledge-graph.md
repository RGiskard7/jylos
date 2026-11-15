# Grafo de conocimiento

El grafo de conocimiento dibuja tus notas y sus wiki-links como una red interactiva por fuerzas y, con **Knowledge Insights**, los convierte en una herramienta analítica.

## Abrir el grafo

- **Ver → Vista de grafo**
- Botón de la barra de herramientas
- **Ctrl/Cmd+G**
- Paleta de comandos

El grafo se abre en una pestaña, como en Obsidian, con la regla de las notas: ocupa la pestaña activa, o una nueva con Cmd/Ctrl. Si ya está abierto, su pestaña pasa al frente, donde está como lo dejaste; la × de la pestaña lo cierra. Un espacio de trabajo guardado lo vuelve a abrir con sus notas. Una pestaña nueva (el **+** tras las pestañas, o **Ctrl/Cmd+T**) también lo ofrece.

Los enlaces cuentan tanto en el cuerpo como en las propiedades de la nota (`up: "[[Índice]]"`), como en Obsidian.

## Vistas global y local

- **Grafo global** — todas las notas y aristas de wiki-links resueltos, con nodos de etiquetas opcionales y aristas nota→etiqueta.
- **Grafo local** — la nota actual más las notas a las que llega en hasta **Profundidad** enlaces (de 1 a 5). Las casillas **Enlaces entrantes** y **Enlaces salientes** eligen qué enlaces seguir, y **Enlaces adyacentes** muestra también los enlaces entre esas notas. Por defecto, como en Obsidian: profundidad 1, entrantes y salientes, sin adyacentes.

## Interactúa

- Zoom y paneo. El grafo abre con el zoom que dejaste, como en Obsidian (el grafo local guarda el suyo). Hasta que eliges uno, o tras **Restablecer vista**, muestra el grafo entero mientras se despliega.
- Arrastra nodos.
- Pasa el cursor para resaltar vecinos.
- Haz clic en un nodo de nota para abrirla en la pestaña del grafo; Cmd/Ctrl+clic o el botón central la abren en otra pestaña y el grafo se queda.

## Ajustes y filtros

El panel de ajustes controla la repulsión, la fuerza y distancia de los enlaces, la gravedad central, los huérfanos y enlaces sin resolver, las flechas, el color por carpeta y las opciones de etiqueta/tamaño/línea.

El panel de ajustes también incluye filtros que redibujan el grafo sin reconstruirlo:

- **Filtrar por texto** — conserva los nodos cuya etiqueta coincide.
- **Etiqueta** — restringe el grafo a las notas con la etiqueta seleccionada.
- **Carpeta** — restringe el grafo a un grupo de carpetas.
- **Mostrar huérfanos** — oculta/muestra notas sin conexiones.
- **Mostrar sin resolver** — oculta/muestra nodos de enlaces rotos ("fantasmas").
- **Mostrar adjuntos** — desactivado por defecto, como en Obsidian. Activado, aparecen las imágenes, PDF, audios y demás ficheros, cada uno unido a las notas que lo incrustan o enlazan. Los canvas aparecen siempre.
- **Mostrar etiquetas** — incluye/excluye nodos `#etiqueta`.

### Grupos de color

En **Grupos**, **Nuevo grupo** añade una fila con una consulta y un color. Los grupos se prueban en orden y cada nota toma el color del primero con el que coincide; los demás nodos no cambian. La consulta puede ser:

- texto, que se busca en el nombre o en la ruta de la nota;
- `path:texto`, en la ruta (`path:proyectos/`);
- `file:texto`, en el nombre;
- `tag:etiqueta`, las notas con esa etiqueta (y su nodo de etiqueta).

Un color de grupo tiene prioridad sobre **Color por carpeta**. La X de la fila borra el grupo.

Todos los ajustes del grafo (fuerzas, tamaños, casillas, profundidad local y grupos) se guardan y siguen igual la próxima vez.

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
