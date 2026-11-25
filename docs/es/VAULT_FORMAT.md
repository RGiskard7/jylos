# Formato de la bóveda

English: [../VAULT_FORMAT.md](../VAULT_FORMAT.md)

Una bóveda de carpetas es una carpeta de ficheros Markdown que también puede abrir Obsidian. La
regla: **el fichero de una nota contiene solo lo que escribió el usuario**. El estado propio de
Jylos va en `.jylos/`, y Jylos nunca escribe en `.obsidian/`, que es de
Obsidian. Guía de uso: [site/es/guides/obsidian-compatibility.md](../../site/es/guides/obsidian-compatibility.md).

## El fichero de una nota

Lo escribe `FrontmatterHandler.generate(nota, ficheroAnterior)`, llamado desde
`NoteDAOFileSystem.updateNote` y `createNote` con el fichero tal como está en disco:

- **Las propiedades que no cambiaron se copian byte a byte** (comentarios, comillas, orden de
  las claves, CRLF), y también las que no se pueden leer como YAML, que Jylos no puede conocer.
- **Si cambiaron**, se vuelve a escribir el YAML con las claves en su orden anterior, las
  nuevas al final, y con los mismos saltos de línea; se conservan las líneas en blanco antes
  del texto.
- **Una nota sin propiedades se escribe sin bloque.** Una nota nueva no tiene ninguno.
- **Si el resultado es igual que el fichero en disco, no se escribe nada**: una marca cambiada
  en `.jylos/` no toca la nota ni su fecha de modificación.
- **`tags:`** conserva las etiquetas que tenía y la nota sigue teniendo, en su orden y como
  estaban escritas, más las añadidas desde la app. Una `#etiqueta` del texto (la de ahora o la
  de antes de editar) se queda en el texto; el texto de una nota privada va cifrado, así que
  todas sus etiquetas se quedan en `tags:`.
- **La primera línea del texto nunca se toma como título**: el título es el nombre del fichero.
- **El YAML se lee y se escribe como YAML 1.2 núcleo, el que usa también Obsidian** (`FrontmatterResolver`,
  `FrontmatterConstructor`): solo `true`/`false` son booleanos; `no`, `yes`, `on`, `10:30` o
  `1_000` son texto; `0123` es 123, `0x1F` y `0o17` llevan su prefijo. Al volver a escribir:
  listas con la sangría de Obsidian (`  - x`), una propiedad vacía como `clave:`, una fecha sin
  comillas, y unas propiedades nuevas van justo antes del texto, sin línea en blanco.
- **Las propiedades que añade una plantilla** (`FrontmatterHandler.mergeProperties`:
  las nuevas al final en su orden, listas unidas sin repetir, un valor vacío de la
  plantilla no pisa) se guardan en `Note.addedFrontmatter` hasta el siguiente guardado: así un
  `title`, `created` o `favorite` de la plantilla es una propiedad del fichero y no cambia el
  nombre, las fechas ni las marcas de la nota.

### Claves que lee Jylos

| Clave | Significado |
|-------|-------------|
| `tags`, `aliases`/`alias` | Etiquetas y alias de enlace (las claves que lee también Obsidian) |
| `private: true` | El texto va cifrado (`JENC1:…`); solo se escribe si es verdadero |
| `author`, `status`, `source_url` | Propiedades como cualquier otra: se ven, se editan y se quitan en el panel de propiedades, y Dataview las lee; de ellas salen el autor, el estado y la fuente de la nota |
| `is_todo`, `todo_due`, `todo_completed` | Solo si tienen valor |
| `created` | Se muestra como fecha de creación si está; nunca se añade (si no, se usa la del fichero) |
| cualquier otra | Propiedades del usuario, con sus tipos YAML |

### Campos de versiones anteriores

Las versiones anteriores escribían `id`, `title`, `created`, `modified`, `favorite`, `pinned`,
`deleted` y `deleted_date` en cada nota. Ya no se escriben. Un frontmatter se reconoce como de
Jylos cuando tiene a la vez `id`, `favorite`, `pinned` y `deleted`
(`FrontmatterHandler.isJylosFrontmatter`; una nota de Obsidian prácticamente nunca los tiene, y
"`id` igual a la ruta" fallaría tras renombrar). Entonces:

- **al leer**, `favorite`/`pinned` dan las marcas de la nota si `.jylos/` no tiene ninguna para ella;
- **al guardar**, esos campos salen del fichero, salvo `created`;
- **Herramientas → Limpiar metadatos de Jylos…** (`VaultCleanupSupport` →
  `NoteService.cleanJylosFields`) limpia toda la bóveda de una vez: lista las notas
  (`notesWithJylosFields`, solo lectura; salta las carpetas ocultas salvo `.trash`), pregunta,
  copia cada una a `.jylos/backups/cleanup-<aaaaMMdd-HHmmss>/` con la misma ruta, pasa las
  marcas a `.jylos/` salvo que ya tenga marcas para esa nota, y quita los campos como líneas
  (`withoutJylosFields`), así que el resto del YAML se queda como estaba. Un campo de varias
  líneas obliga a volver a escribir la nota.

Sin esa firma, las mismas claves son del usuario (`id: 202401011200`, `modified: ayer`,
`favorite: true`) y no se tocan. El comando se retirará en la 3.0; las reglas al leer y al
guardar se quedan.

## `.jylos/`

| Fichero | Contenido |
|---------|-----------|
| `document-metadata.json` | Por ruta: favorito y fijado de las notas; favorito, fijado, privado, estado y papelera de lienzos y adjuntos |
| `encryption.json` | Sal y verificador de la contraseña maestra (ver [ARCHITECTURE.md](ARCHITECTURE.md)) |
| `settings.json` | Ajustes de la bóveda (`VaultSettings`, `VaultSettingsStore`): plantillas y sus formatos, nota diaria, notas nuevas, Templater y si ya se ofreció importar los de Obsidian. Con la base de datos va en la carpeta de datos (`vault-settings.json`). Las rutas que nombra siguen a una carpeta renombrada o movida en Jylos |
| `backups/cleanup-<fecha>/` | Copias que hace el comando de limpieza |

`document-metadata.json` (`FileSystemDocumentMetadataStore`):

- **Se mueve con el documento:** la entrada sigue a la nota al renombrarla o moverla, al
  enviarla a la papelera y restaurarla y al renombrar o mover su carpeta, y desaparece al
  borrarla del todo.
- **Las notas nuevas empiezan limpias:** crear una nota borra la entrada que quedara en su
  ruta, por ejemplo de una nota borrada fuera de Jylos.
- **Entradas vacías:** una entrada sin marcas se borra. La excepción es una nota cuyo fichero
  tiene una propiedad `favorite`/`pinned` escrita por el usuario: entonces se guarda "sin
  marcar", para que la propiedad no devuelva la marca.
- **Fichero corrupto:** es un error explícito, nunca se lee como vacío.

## Marcadores de Obsidian

**Herramientas → Importar marcadores de Obsidian** (`ObsidianBookmarksSupport`) lee
`.obsidian/bookmarks.json` (`ObsidianBookmarks.read`) y nunca lo escribe. El formato es propio
de Obsidian y no está documentado; una nota, vista en una bóveda real, es
`{"items": [{"type": "file", "ctime": …, "path": "Carpeta/Nota.md", "title": "…"}]}`. Un grupo es
cualquier elemento con su propio `items`, y se recorre. Un `file` con `subpath` (un encabezado o
bloque del fichero) y los elementos de otros tipos (búsqueda, carpeta…) se cuentan como
ignorados. Un fichero con otra forma se da por no entendido, nunca se lee como vacío. Los
ficheros marcados que existen en la bóveda pasan a favoritos; el resultado dice cuántos se
marcaron, cuántos ya lo eran, cuántos no se encontraron y cuántos se ignoraron.

## Ajustes de Obsidian

**Herramientas → Importar ajustes de Obsidian…** (`ObsidianSettingsSupport`, ofrecido una vez al
abrir la bóveda desde `ObsidianImportSupport`) lee `.obsidian` (`compat/obsidian/ObsidianSettings`,
el único código que conoce esos ficheros) y nunca lo escribe:

| Fichero | Ajustes de Jylos |
|---------|------------------|
| `templates.json` (`folder`, `dateFormat`, `timeFormat`) | `templates`, si el plugin Plantillas está activo |
| `daily-notes.json` (`folder`, `format`, `template`) | `daily`, si Notas diarias está activo |
| `app.json` (`newFileLocation`, `newFileFolderPath`) | `newNotes` (`root` por defecto) |
| `plugins/templater-obsidian/data.json` (`templates_folder`, `trigger_on_file_creation`, `enable_folder_templates`, `folder_templates`) | `templater`, si Templater está en `community-plugins.json` |

Los plugins activos salen de `core-plugins.json` (un objeto `{id: true}` o, en versiones
antiguas, una lista; sin fichero, los de Obsidian por defecto). Una clave que falta toma el
valor por defecto de Obsidian. Un fichero con otra forma se da por no entendido y no se aplica
nada. Se muestra qué cambiaría y se aplica solo si el usuario lo acepta.

## Límites

- Renombrar una nota fuera de Jylos le hace perder sus marcas: se buscan por ruta.
- `document-metadata.json` es un solo fichero para toda la bóveda; marcar notas en dos equipos
  y sincronizar con Git puede darle conflictos.
- Los favoritos y las fijadas no se comparten con los marcadores de Obsidian.
