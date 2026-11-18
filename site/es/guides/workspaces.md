# Espacios de trabajo

Un espacio de trabajo es un contexto con nombre ("Escritura", "Investigación", un proyecto): sus pestañas abiertas, lo que recorrió cada pestaña (atrás y adelante), la pestaña activa, el modo de vista y la disposición de los paneles. Abrir un espacio sustituye las pestañas abiertas por las suyas, así que pasas de un contexto a otro sin que se acumulen pestañas.

Los espacios se guardan en los datos locales de Jylos (nunca dentro de tus notas) y funcionan en modo SQLite y en modo bóveda Markdown.

## El espacio en uso

La barra de estado muestra el espacio en uso, junto al selector de almacenamiento ("Sin espacio" si no hay ninguno). Al pulsarlo puedes:

- **cambiar** a otro espacio (el que está en uso aparece marcado). El menú muestra los ocho usados más recientemente; si hay más, **Todos los espacios…** abre la lista, con un campo de búsqueda;
- volver a **Sin espacio**: vuelven las pestañas que tenías antes de abrir un espacio;
- **guardar** el que está en uso, o **guardar como espacio nuevo**;
- **gestionarlos**: abrir, renombrar o eliminar;
- activar o desactivar **Guardar el espacio al cambiar a otro** (activado por defecto).

Las mismas acciones están en **Archivo → Espacios de trabajo** y en la paleta de comandos, bajo *Espacio:*.

Uso típico: abre las notas que quieras → *Guardar como espacio nuevo…* → ponle nombre → más tarde, elígelo en la barra de estado para recuperar esas pestañas.

## Cambiar de espacio

- Al abrir un espacio, primero se guarda el que está en uso, si *Guardar el espacio al cambiar a otro* está activado, y así lo encuentras como lo dejaste. Si está desactivado, un espacio solo cambia cuando lo guardas.
- Antes de cerrar las pestañas se pregunta por los cambios sin guardar de la nota abierta; si cancelas, todo queda como estaba.
- Cada pestaña recupera su atrás y adelante, así que volver atrás funciona igual que antes de cambiar.
- **Sin espacio** es un contexto más: al abrir un espacio desde él se guardan sus pestañas, y al elegirlo de nuevo vuelven.

## Al abrir Jylos

Jylos vuelve a abrir las pestañas que estaban abiertas al cerrarlo, con su atrás y adelante, y el espacio que estaba en uso. Las notas borradas mientras tanto se omiten.

## Qué guarda un espacio

- Su **nombre**.
- Las **pestañas abiertas** en orden (notas y el grafo), la **pestaña activa** y el **atrás y adelante de cada pestaña**.
- El **modo de vista**: edición, dividido o lectura.
- La **disposición**: modo concentración, barra lateral visible u oculta y los divisores de los paneles.
- El **modo de almacenamiento** al guardarlo, para avisar si se abre en otro.

## Comportamiento

- **Las notas que faltan no dan error**: una nota que ya no existe se omite, con un mensaje en la barra de estado.
- **Otro modo de almacenamiento**: se muestra un aviso, sin bloquear, si el espacio se guardó en otro modo.
- Las pestañas nuevas vacías no se guardan.

## Relacionado

- Detalles técnicos: [WORKSPACES.md](https://github.com/RGiskard7/jylos/blob/main/docs/WORKSPACES.md)
