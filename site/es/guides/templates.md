# Plantillas y nota diaria

::: v-pre

Jylos entiende las plantillas escritas para Obsidian: la sintaxis de su plugin Plantillas y, si la activas, la de Templater. Una misma plantilla da el mismo resultado en las dos apps. Todo se configura en **Preferencias → Bóveda**, y cada bóveda tiene sus propios ajustes.

## Dónde están las plantillas

Las plantillas son las notas de la **carpeta de plantillas**, también las de sus subcarpetas. Si no eliges ninguna, son las de una carpeta llamada `Templates`. La raíz de la bóveda no puede ser la carpeta de plantillas.

## Usar una plantilla

- **Edición → Insertar plantilla…** (en la paleta, «Plantillas: Insertar plantilla») busca una plantilla y la escribe donde está el cursor. Sus propiedades se añaden a las de la nota: las nuevas van al final, en el orden de la plantilla; dos listas se juntan sin repetir; un valor de la plantilla sustituye al de la nota, salvo que el de la plantilla esté vacío.
- **Edición → Insertar fecha actual** e **Insertar hora actual** escriben la fecha o la hora donde está el cursor, con los formatos de la bóveda.
- **Archivo → Nueva desde plantilla…** crea una nota con el nombre de la plantilla y la plantilla escrita para ella, tal como está en su fichero.

## Variables

| Variable | Escribe |
|---|---|
| `{{title}}` | El nombre de la nota |
| `{{date}}` | La fecha de hoy, con el formato de fecha de la bóveda (por defecto `YYYY-MM-DD`) |
| `{{time}}` | La hora, con el formato de hora de la bóveda (por defecto `HH:mm`) |
| `{{date:FORMATO}}`, `{{time:FORMATO}}` | La fecha o la hora con ese formato, por ejemplo `{{date:dddd D [de] MMMM}}` |
| `{{datetime}}` | Fecha y hora, `YYYY-MM-DDTHH:mm:ss` (solo en Jylos) |

Los formatos son los de Obsidian: `YYYY` (año), `MM` y `MMMM` (mes en número y en nombre), `DD` (día), `dddd` (día de la semana), `W` (semana del año), `HH:mm` (hora), `[texto]` para escribir texto tal cual… Los nombres de días y meses van en el idioma de la interfaz. En Preferencias, cada formato muestra lo que escribe hoy.

## Nota diaria

**Archivo → Nota diaria** (**Ctrl/Cmd+Shift+D**) abre la nota de hoy, o la crea si no existe:

- **Su nombre** es la fecha de hoy con el formato de la nota diaria. Un formato con barras, como `YYYY/MM/DD`, la guarda en carpetas, que se crean si faltan.
- **Su carpeta** es la de las notas diarias; si no hay, la de las notas nuevas cuando es una carpeta concreta; si no, la raíz de la bóveda.
- **Su plantilla** se escribe al crearla. En ella, `{{title}}` es el nombre de la nota, y `{{date}}` y `{{time}}` usan los formatos fijos `YYYY-MM-DD` y `HH:mm`. Sin plantilla elegida se usa la nota `daily` de la carpeta de plantillas, si existe; si no, la nota se crea vacía.

Si la carpeta o la plantilla elegidas no están en la bóveda, Jylos lo dice y no crea nada.

## Dónde se crean las notas nuevas

En la carpeta que estás viendo (lo de siempre), en la raíz de la bóveda o en una carpeta concreta. Vale para las notas nuevas, las que se crean desde un enlace a una nota que aún no existe y las de «Nueva desde plantilla». Si la carpeta elegida no está, la nota va a la raíz.

## Templater

Con **Entender la sintaxis de Templater** activado, las plantillas también escriben las etiquetas `<% %>` de Templater, como Templater:

| Función | Escribe |
|---|---|
| `tp.date.now(formato, desplazamiento, referencia, formato_ref)` | Hoy, o la fecha de referencia, más un desplazamiento en días (`7`, `-1`) o como duración (`"P1M"`, `"P-1W"`, `"PT2H"`) |
| `tp.date.tomorrow(formato)`, `tp.date.yesterday(formato)` | Mañana, ayer |
| `tp.date.weekday(formato, día, referencia, formato_ref)` | Un día de esta semana (0 es el primero, según el idioma) |
| `tp.file.title`, `tp.file.tags`, `tp.file.content` | El nombre, las etiquetas y el texto de la nota |
| `tp.file.folder(ruta)`, `tp.file.path(relativa)` | Su carpeta (el nombre, o la ruta con `true`) y su ruta (la del disco, o la de la bóveda con `true`) |
| `tp.file.creation_date(formato)`, `tp.file.last_modified_date(formato)` | Cuándo se creó y se cambió su fichero |
| `tp.file.selection()` | Lo seleccionado en el editor al insertar |
| `tp.file.cursor()` | Deja ahí el cursor al insertar |
| `tp.file.exists("ruta")`, `tp.file.include("[[Nota]]")` | Si existe una nota o carpeta; el texto de otra nota, con sus etiquetas escritas |
| `tp.frontmatter.campo` | Una propiedad de la nota |
| `tp.system.prompt("¿…?")`, `tp.system.suggester([…], […])`, `tp.system.clipboard()` | Lo que respondes en un diálogo, lo que eliges de una lista, el portapapeles |

`<%-` y `-%>` quitan el salto de línea de antes o de después de la etiqueta; `<%_` y `_%>`, todos los espacios. `await` delante de una función se admite.

**Lo que ejecuta código se deja tal cual**: `<%* … %>`, `tp.user`, `tp.web`, `tp.app`, `tp.obsidian`, `tp.hooks`, `tp.config` y cualquier otro JavaScript. La barra de estado dice qué etiquetas se han dejado. Lo que haría que Templater detuviera la plantilla la detiene también, sin escribir nada: una fecha de referencia que no sigue su formato, una nota incluida que no existe o un `include` de más de diez niveles.

Con **Aplicar al crear una nota**:

- una nota nueva vacía recibe la **plantilla de su carpeta**, o la de la carpeta más cercana hacia arriba que tenga una (las notas de la carpeta de plantillas no);
- las etiquetas de Templater de la **plantilla de la nota diaria** también se escriben, como cuando Templater está instalado en Obsidian.

Diferencias con Templater:

- **Una sola orden para las dos sintaxis:** «Insertar plantilla» escribe a la vez las variables de Obsidian y las de Templater. En Obsidian, cada plugin escribe solo las suyas.
- **Varios `tp.file.cursor()`:** el cursor va al primero en orden y se quitan todos; Jylos no tiene la orden de Templater para saltar al siguiente.

## Traer los ajustes desde Obsidian

**Herramientas → Importar ajustes de Obsidian…** toma estos ajustes de la carpeta `.obsidian` de la bóveda. Consulta [Compatibilidad con Obsidian](/es/guides/obsidian-compatibility#los-ajustes-de-obsidian).

:::
