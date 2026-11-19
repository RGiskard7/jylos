# Compatibilidad con Obsidian

Una bóveda de Jylos es una carpeta de ficheros Markdown, igual que una de Obsidian: puedes abrir la misma carpeta con las dos apps. Jylos no escribe nada suyo en tus notas. Una nota contiene solo lo que escribiste tú.

## Lo que comparten las dos apps

- **Las notas y su texto**, con encabezados, listas, tareas, callouts, código y diagramas Mermaid.
- **Las propiedades** (el YAML del principio de la nota). Si no las cambias, Jylos las guarda exactamente como estaban, con tus comentarios, comillas y orden. Las lee como Obsidian (YAML 1.2): `no`, `yes`, `10:30` o `1_000` son texto, no un booleano ni un número; una lista sigue siendo lista y una propiedad vacía sigue vacía.
- **Las plantillas**, con las variables de Obsidian (<span v-pre>`{{date:YYYY-MM-DD}}`</span>…) y, si lo activas, la sintaxis de Templater (`<% tp.date.now() %>`). La nota diaria sigue las reglas de Obsidian. Consulta [Plantillas y nota diaria](/es/guides/templates).
- **Los enlaces**: `[[Nota]]`, `[[Carpeta/Nota]]`, `[[Nota#Encabezado]]`, `[[Nota|texto]]`, incrustaciones `![[…]]`, `aliases` y enlaces dentro de propiedades (`up: "[[Índice]]"`).
- **Las etiquetas**, escritas en el texto (`#idea`) o en la propiedad `tags`.
- **Los lienzos** (`.canvas`) y los adjuntos.
- **La papelera**, la carpeta `.trash`.

## Lo que cada app guarda para sí

Cada app guarda su estado en una carpeta oculta de la bóveda, que la otra ignora:

| | Obsidian | Jylos |
|---|---|---|
| Carpeta | `.obsidian/` | `.jylos/` |
| Favoritos | Marcadores | Favoritos |
| Notas fijadas | — (fija pestañas) | Fijadas |
| Espacios de trabajo | `.obsidian/` | Datos de la app (fuera de la bóveda) |

Así que **los favoritos y las notas fijadas no se comparten con Obsidian**: un marcador de Obsidian no es un favorito en Jylos, ni al revés. Jylos nunca escribe en `.obsidian/`.

Para traer los marcadores de Obsidian de una vez, usa **Herramientas → Importar marcadores de Obsidian**: las notas con marcador en Obsidian (también las de dentro de grupos) pasan a ser favoritas en Jylos. Solo lee los marcadores de Obsidian; los de búsquedas, carpetas, encabezados o bloques no tienen equivalente y se saltan, y el resultado dice cuántos.

Los marcadores no son etiquetas: una etiqueta (`#idea`, `tags:`) va escrita en la nota y la clasifica; un marcador es un atajo guardado en la configuración de la app, fuera de la nota.

## Los ajustes de Obsidian

Los ajustes de Jylos para una bóveda (plantillas, nota diaria, notas nuevas, Templater) son suyos y están en Preferencias → Bóveda. **Herramientas → Importar ajustes de Obsidian…** los toma de `.obsidian` de una vez:

- **Qué toma:** la carpeta de plantillas y sus formatos (plugin Plantillas), la carpeta, el formato y la plantilla de la nota diaria (plugin Notas diarias), dónde se crean las notas nuevas, y de Templater su carpeta de plantillas, si aplica la plantilla al crear una nota y sus plantillas por carpeta.
- **Solo de los plugins que tienes activos en Obsidian**, porque un plugin desactivado deja sus ajustes viejos en `.obsidian`. Donde Obsidian no tiene un ajuste, se toma su valor por defecto. Templater cuenta como activo si aparece en `.obsidian/community-plugins.json`.
- **Antes de cambiar nada** muestra qué cambiaría y pide confirmación.
- **Al abrir por primera vez una bóveda con `.obsidian`**, Jylos lo ofrece una vez, aunque digas «Ahora no»; el comando sigue en Herramientas.

Después cada app sigue con sus ajustes: si los cambias en Obsidian, vuelve a importarlos. Los ficheros de Obsidian solo se leen.

## Conviene saber

- **Si renombras una nota fuera de Jylos** (en Obsidian, en el Finder o con Git), pierde su favorito y su fijado, porque Jylos los encuentra por la ruta de la nota. A los marcadores de Obsidian les pasa lo mismo.
- **`.jylos/` es parte de la bóveda** y se sincroniza con ella (Git, iCloud…). Los favoritos y las fijadas van en un solo fichero, `.jylos/document-metadata.json`. Si marcas notas en dos equipos a la vez y sincronizas con Git, ese fichero puede dar conflicto.
- **Las notas privadas** conservan `private: true` en sus propiedades: su texto va cifrado y Jylos necesita esa marca para abrirlas.

## Notas guardadas por versiones anteriores de Jylos

Las versiones anteriores de Jylos escribían `id`, `title`, `modified`, `favorite`, `pinned` y `deleted` en cada nota que guardaban, y Obsidian los mostraba como propiedades. Ahora Jylos los quita:

- **Al guardar una nota**, pierde esos campos. Se conservan su favorito, su fijado, su fecha `created` y el resto de sus propiedades.
- **Herramientas → Limpiar metadatos de Jylos…** limpia toda la bóveda de una vez. Dice antes cuántas notas va a cambiar y pregunta; copia cada una a `.jylos/backups/` antes de cambiarla.

Solo se tocan las notas cuyas propiedades escribió Jylos. Si escribiste tú alguno de esos campos (un `id`, una fecha `modified`…), es tuyo y se queda.
