# Wiki-links

Los wiki-links conectan tus notas en una red, y son lo que alimenta el grafo y el panel de backlinks.

## Crea un enlace

Escribe `[[` en el editor y Jylos te sugiere notas mientras escribes:

- Las tildes y las mayúsculas dan igual (`reunion` encuentra "Reunión"). Salen primero las notas cuyo nombre empieza por lo escrito, luego las que tienen una palabra que empieza así, luego las que lo contienen y por último las que tienen sus letras en orden (`pj` encuentra "Proyecto Jylos").
- Cada nota aparece con su carpeta, también dos con el mismo nombre. Jylos escribe el enlace más corto que nombra la que eliges: su nombre, o su ruta si otra nota se llama igual. Con una `/` se busca por ruta (`personal/reu`).
- También salen los `aliases` de las notas, y se escriben como `[[Nota|alias]]`.
- Tras `#` salen los encabezados de esa nota (`[[Nota#`) o de la nota abierta (`[[#`). Tras `|` no se sugiere nada: el texto que muestra el enlace es tuyo.

Jylos también admite enlaces Markdown internos:

```markdown
[[Mi Nota]]
[etiqueta](mi-nota.md)
```

Haz clic en un enlace en la vista de lectura para abrir la nota de destino.

## Backlinks

El panel derecho lista los enlaces entrantes: cada nota que enlaza a la nota actual, ya sea mediante `[[wiki-links]]` o enlaces Markdown.

## Resolución

La resolución de wiki-links se comparte entre la vista previa, el grafo y el panel de backlinks a través de un único resolutor (`WikiLinkResolver`), de modo que los tres siempre coinciden en a qué apunta un enlace.

## Transclusión

Incrusta el contenido renderizado de otra nota con `![[Nota]]`, o una sección con `![[Nota#Encabezado]]`. La transclusión está acotada con detección de ciclos para que los embebidos recursivos no puedan quedarse en bucle.

## Enlaces en Kanban y Canvas

- Una tarjeta Kanban puede enlazar a una nota con `[[Título]]` o convertirse en nota.
- Los nodos de Canvas pueden enlazar a notas y conectarse entre sí con aristas.

Consulta [Grafo de conocimiento](/es/guides/knowledge-graph) para visualizar la red que forman estos enlaces.
