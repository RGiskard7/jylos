# Dataview

Consulta los metadatos de tus notas desde dentro de una nota. Escribe un bloque cercado ```` ```dataview ```` y la vista previa lo sustituye por el resultado de la consulta.

````markdown
```dataview
TABLE rating AS "Puntuación", file.mtime AS "Actualizado"
FROM #libro AND -"archivo"
WHERE rating >= 4
SORT rating DESC
LIMIT 10
```
````

Los resultados se renderizan tanto en la vista de lectura como en el Live Preview del editor. Los bloques `dataviewjs` no están soportados.

## Tipos de consulta

| Forma | Resultado |
| --- | --- |
| `TABLE expr, expr AS "Nombre"` | Una tabla, con una columna de enlace implícita al principio |
| `LIST [expr]` | Una lista de enlaces, o de la expresión dada |
| `TASK` | Elementos de checklist, agrupados por su nota |

`WITHOUT ID` elimina la columna de enlace implícita.

## Fuentes de metadatos

| Fuente | Sintaxis |
| --- | --- |
| Frontmatter YAML | `rating: 5`, `tags: [libro, scifi]` |
| Campo inline (línea propia) | `estado:: leído` |
| Campo inline (entre corchetes) | `… [vencimiento:: 2026-03-01]` |
| Etiquetas | `#libro` en el cuerpo, o un campo `tags` del frontmatter |
| Tareas | `- [ ] texto` / `- [x] texto` |

Los nombres de campo coinciden sin distinguir mayúsculas, y `-`, `_` y espacios son equivalentes. Los valores se tipan automáticamente (números, booleanos, fechas, enlaces, listas).

## Campos `file` implícitos

`file.name` · `file.path` · `file.folder` · `file.link` · `file.size` · `file.ctime` · `file.cday` · `file.mtime` · `file.mday` · `file.tags` · `file.etags` · `file.outlinks` · `file.inlinks` · `file.tasks` · `file.starred` · `file.pinned`

`this` se refiere a la nota que contiene la consulta, p. ej. `this.file.name`.

## FROM

| Fuente | Selecciona |
| --- | --- |
| `#etiqueta` | Páginas con esa etiqueta (jerárquica) |
| `"carpeta"` | Páginas de esa carpeta |
| `[[Nota]]` | Páginas que enlazan **a** `Nota` |
| `outgoing([[Nota]])` | Páginas a las que `Nota` enlaza |

Combina con `AND` / `OR`, niega con `-` o `NOT`, agrupa con paréntesis.

## Cláusulas

`WHERE` (repetible), `SORT expr [ASC|DESC]`, `GROUP BY expr [AS nombre]`, `FLATTEN lista AS nombre`, `LIMIT n`.

## Consultas inline

Escribe `` `= expresión` `` en prosa para mostrar un valor calculado:

```markdown
Esta nota se llama `= this.file.name` y enlaza a `= length(this.file.outlinks)` otras.
```

## Notas privadas

Las notas privadas se excluyen del índice por diseño: una consulta se renderiza en una vista previa que puede exportarse o compartirse.

## Relacionado

- Detalles técnicos: [DATAVIEW.md](https://github.com/RGiskard7/jylos/blob/main/docs/DATAVIEW.md)
- [Plugins](/es/guides/plugins)
