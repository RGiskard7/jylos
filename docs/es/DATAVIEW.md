# Plugin Dataview

English: [../DATAVIEW.md](../DATAVIEW.md)

Consulta los metadatos de tus notas desde dentro de una nota. Escribe un bloque
<code>```dataview</code> y la **vista previa** lo sustituye por el resultado.

````markdown
```dataview
TABLE rating AS "Puntuación", file.mtime AS "Actualizada"
FROM #libro AND -"archivo"
WHERE rating >= 4
SORT rating DESC
LIMIT 10
```
````

El plugin vive en `plugins-source/com/example/jylos/plugin/builtin/dataview/` y `scripts/build-plugins.sh`
lo compila a `jylos/plugins/DataviewPlugin.jar`.

## Dónde aparecen los resultados

Los resultados se renderizan **tanto** en la vista previa (modo lectura) **como** en el
Live Preview del editor. Mientras el cursor está dentro de un bloque, este vuelve a
mostrar su código fuente para poder editar la consulta — la misma regla de
revelar-al-editar que Live Preview aplica a tablas e imágenes.

Ambas superficies usan el mismo parser, motor y renderizador (`DataviewRunner`), así que
una consulta no puede dar una tabla distinta según se esté leyendo o editando la nota.

Los bloques `dataviewjs` **no** están soportados: requerirían una superficie de scripting
que la aplicación no expone, y supondría ejecutar código procedente del contenido de las
notas. Un bloque así muestra un aviso explicativo en vez de fallar en silencio.

## Fuentes de metadatos

| Fuente | Sintaxis |
|--------|----------|
| Frontmatter YAML | `rating: 5`, `tags: [libro, scifi]`, listas por bloque |
| Campo inline (línea propia) | `estado:: leído` |
| Campo inline (entre corchetes) | `… [vence:: 2026-03-01]` o `(vence:: 2026-03-01)` |
| Etiquetas | `#libro` en el cuerpo, o campo `tags`/`tag` del frontmatter |
| Tareas | `- [ ] texto` / `- [x] texto` |

Los nombres de campo se comparan sin distinguir mayúsculas, y `-`, `_` y espacios son
equivalentes: `fecha-limite`, `Fecha Limite` y `fecha_limite` son el mismo campo. Los
valores se tipan automáticamente: números, booleanos, fechas `yyyy-MM-dd`, `[[enlaces]]`
y listas separadas por comas.

Los bloques de código se omiten al escanear, así que un `#comentario` o un `::` dentro de
un ejemplo de código no se confunde con metadatos.

### Campos implícitos `file`

`file.name` · `file.path` · `file.folder` · `file.link` · `file.size` · `file.ctime` ·
`file.cday` · `file.mtime` · `file.mday` · `file.tags` · `file.etags` · `file.outlinks` ·
`file.inlinks` · `file.tasks` · `file.starred` · `file.pinned`

`this` es la nota que contiene la consulta, por ejemplo `this.file.name`.

## Tipos de consulta

| Forma | Resultado |
|-------|-----------|
| `TABLE expr, expr AS "Nombre"` | Tabla, con una columna de enlace implícita al principio |
| `LIST [expr]` | Lista de enlaces, o de la expresión indicada |
| `TASK` | Tareas, agrupadas por su nota |

`WITHOUT ID` elimina la columna de enlace implícita: `TABLE WITHOUT ID rating`.

En una consulta `TASK` los campos de la propia tarea se resuelven primero: `text`,
`completed`, `status`, `line`, más cualquier campo inline declarado en su línea.

## FROM

| Fuente | Selecciona |
|--------|------------|
| `#tag` | Páginas con esa etiqueta — jerárquico: `#proyecto` también capta `#proyecto/activo` |
| `"carpeta"` | Páginas de esa carpeta (incluidas rutas anidadas) |
| `[[Nota]]` | Páginas que enlazan **a** `Nota` |
| `outgoing([[Nota]])` | Páginas a las que `Nota` enlaza |
| `incoming([[Nota]])` | Igual que `[[Nota]]` |

Combina con `AND` / `OR`, niega con `-` o `NOT`, agrupa con paréntesis:

```
FROM (#libro OR #articulo) AND -"archivo"
```

`FROM [[]]` significa «esta nota».

## Cláusulas

| Cláusula | Notas |
|----------|-------|
| `WHERE expr` | Puede repetirse; deben cumplirse todas |
| `SORT expr [ASC\|DESC]` | Varias claves separadas por comas |
| `GROUP BY expr [AS nombre]` | Renderiza una sección por grupo |
| `FLATTEN lista AS nombre` | Una fila por elemento; se aplica **antes** de `WHERE` |
| `LIMIT n` | Limita filas, o grupos si hay agrupación |

Las cláusulas pueden ir en cualquier orden tras la proyección.

## Expresiones

Operadores: `+ - * / %`, `= != > >= < <=`, `AND OR NOT` (`&& || !`).

`AND` y `OR` cortocircuitan, así que `campo AND length(campo) > 0` es seguro en páginas que
no tienen el campo. Las comparaciones son totales: comparar un texto con una fecha, o un
campo ausente con un número, da un resultado definido en vez de descartar la fila con error.

Literales: números, `"textos"`, `true`/`false`/`null`, `[[enlaces]]`, `#etiquetas`, listas
(`[1, 2]`) y objetos (`{a: 1}`). `today`, `now`, `tomorrow` y `yesterday` están integrados.

### Fechas y duraciones

Las duraciones son días: `date(today) - 7` y `date(today) - dur("1 week")` significan lo
mismo, y restar dos fechas da los días entre ellas.

```
WHERE file.mtime >= date(today) - dur("30 days")
```

### Funciones

`length` `contains` `icontains` `econtains` `typeof`
`lower` `upper` `replace` `split` `join` `truncate` `startswith` `endswith`
`regexmatch` `regexreplace`
`number` `string` `round` `floor` `ceil` `abs` `min` `max` `sum` `average`
`date` `dateformat` `striptime` `dur`
`default` `ifnull` `choice` `nonnull`
`link` `elink`
`sort` `reverse` `unique` `flat` `first` `last` `any` `all` `none`

## Consultas inline

Escribe `` `= expresión` `` en el texto para mostrar un valor calculado:

```markdown
Esta nota se llama `= this.file.name` y enlaza a `= length(this.file.outlinks)` más.
```

Lo que no sea una expresión válida se deja tal cual, así que un `` `= 1 + 1` `` usado como
texto normal nunca se altera.

## Índice y rendimiento

Las consultas necesitan el cuerpo **completo** de cada nota: un campo inline o una tarea
pueden estar en cualquier parte del fichero, mientras que la lista de notas solo guarda una
cabecera truncada. Por eso el plugin mantiene un índice caliente: cada nota se lee y parsea
una vez, se cachea contra su fecha de modificación y solo se relee cuando cambia. La
primera consulta tras arrancar paga la lectura completa; las siguientes son búsquedas en
memoria. Los eventos de nota invalidan solo la nota afectada.

**Las notas privadas se excluyen** deliberadamente del índice: una consulta se renderiza en
una vista previa que puede exportarse o compartirse, así que mostrar el contenido de una
nota marcada como privada lo filtraría. Los adjuntos y ficheros canvas también se excluyen
porque no llevan metadatos Markdown.

## Errores

Una consulta que no se puede parsear o evaluar muestra un recuadro de error en su lugar,
con el problema y la consulta. El resto de la nota se sigue renderizando.

## Pruebas

```bash
./scripts/test-plugins.sh
```

Compila `plugins-source/` junto a `plugins-test/` y ejecuta las comprobaciones de
`plugins-test/com/example/jylos/plugin/builtin/dataview/DataviewPluginTest.java` (extracción
de metadatos, todas las cláusulas, funciones, escapado HTML, la transformación end-to-end de
la vista previa y que las superficies de editor y vista previa coincidan en la misma consulta).

Los hooks del lado de la aplicación los cubren `PreviewEnhancerTransformTest` y
`EditorBlockRenderSupportTest` en la suite de Maven, y el lado del editor
`editor-web/src/__tests__/block-render.test.js`.
