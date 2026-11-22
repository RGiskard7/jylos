# Arquitectura

English: [../ARCHITECTURE.md](../ARCHITECTURE.md)

Monolito desktop: UI JavaFX, servicios de dominio, almacenamiento intercambiable y `EventBus` en proceso. Offline y de un solo usuario.

Reglas normativas: [ARCHITECTURE_GUIDELINES.md](ARCHITECTURE_GUIDELINES.md).

## Entradas

- `com.example.jylos.Launcher` — delega en `Main`; usado por `exec:java` y packaging.
- `com.example.jylos.Main` — `Application` JavaFX; carga FXML, inicializa storage y plugins.

## Capas

```text
ui/ (FXML, controllers, components, GraphCanvas)
  -> service/ (Note, Folder, Tag, Backlink, backup, ...)
    -> data/dao/ (sqlite + filesystem)
```

| Paquete | Rol |
|---------|-----|
| `ui.controller` | Shell y controladores: `MainController`, sidebar, lista, editor, toolbar, grafo y helpers (`GitController`, `PrivacySupport`, `TabsSupport`, etc.); piezas compartidas por los árboles y la lista clásicos y el árbol Explorador: `NoteContextMenuSupport`, `FolderContextMenuSupport` y `FolderDropSupport` |
| `ui.theme` | Aplicación/detección de temas y catálogos de temas/snippets |
| `ui.preferences` | Persistencia de preferencias UI serializadas y del diseño de la ventana (`LayoutMode`: Explorador, clásico en columnas, clásico apilado) |
| `ui.graph` | `GraphCanvas`, render nativo del grafo |
| `ui.components` | Paleta, quick switcher, diálogos, visores, canvas, pestañas (`EditorTabs` sobre `EditorTabsModel`), árbol Explorador (`ExplorerTree`, `ExplorerOrdering`), Kanban |
| `graph` | Construcción del grafo desde notas, wiki-links y tags |
| `insights` | Métricas y DTOs de salud del grafo |
| `git` | `GitService`, integración Git acotada a la bóveda |
| `search` | Parser/modelo y servicio de búsqueda avanzada |
| `service` | Reglas de aplicación: notas, carpetas, tags, backlinks, títulos, cifrado, backups |
| `data.dao` | Implementaciones SQLite y filesystem |
| `data.models` | `Note`, `Folder`, `Tag`, `ToDoNote` |
| `event` | `EventBus` y eventos tipados |
| `plugin` | Loader, manager y API de plugins |
| `util` | Markdown, wiki-links, preview, exportación y Kanban |
| `workspace` | Captura y restauración de workspaces |
| `config` | Logging |

## Patrón `MainController`

`MainController` coordina shell, wiring, apertura/guardado/cierre de notas y callbacks entre features. No debe absorber cuerpos de features nuevas. Cada feature autocontenida vive en `*Controller` o `*Support` con `wire(...)`.

## Composición UI

- `MainView.fxml`: toolbar, centro con split principal, área del editor (`editorArea`: la nota o el grafo), panel derecho y status bar.
- Split central según `LayoutMode` (guardado en `ui.layout.mode`; se decide una sola vez en `Main.start`: instalación nueva, Explorador; actualización, clásico en columnas). `UiLayout` guarda la distribución y si el panel de resultados está abierto, coloca los paneles (`arrange`) y pliega o muestra la barra lateral, la lista y el panel derecho desde sus botones. Disposiciones: columnas `sidebar | lista | editor`; apilado `(sidebar sobre lista) | editor`; Explorador `sidebar | editor`, con la lista delante del editor solo mientras muestra resultados de búsqueda o etiqueta.
- Barra lateral: en el diseño Explorador la pestaña de carpetas muestra `ExplorerTree` en lugar del árbol clásico. Cada carpeta lee su contenido (`FolderService.getSubfolders`, `NoteService.getNotesByFolder`) en el hilo de carga de la barra lateral al desplegarse por primera vez, y descarta resultados más antiguos que la última lectura. El filtro y "revelar la nota abierta" leen la bóveda entera una vez y la guardan hasta el siguiente refresco.
- Lista de notas: en el diseño Explorador es un panel de resultados (botón de cerrar, sin cuadrícula ni "notas de subcarpetas").
- El grafo es una pestaña, como en Obsidian: `GraphView.fxml` está con el editor en `editorArea` y se ve mientras su pestaña (`ViewTabs.GRAPH`, un id reservado en la misma lista que los de las notas) es la activa. Como en Obsidian, todas las pestañas siguen una regla (lo que se abre ocupa la pestaña activa, o una nueva con Cmd/Ctrl; "pestaña nueva" llena la activa mientras está vacía) y cada una guarda su historial atrás/adelante de lo que mostró, notas, grafo y pestañas nuevas incluidos (`EditorTabsModel`, 20 pasos). El + tras las pestañas y `NEW_TAB` (`Ctrl/Cmd+T`) abren una pestaña vacía (`ViewTabs.newEmptyTab()`) al final, con el estado vacío del editor. `TabsSupport` lo coordina todo (abrir, elegir, cerrar, atrás/adelante, restaurar desde un espacio de trabajo) y termina en un único sitio que muestra lo que tiene la pestaña activa.
- Editor: `EditorTabs` (reglas en `EditorTabsModel`: abrir en la pestaña actual sustituye su nota, una nota ya abierta activa su pestaña, una pestaña nueva va justo después de la activa), `MarkdownEditorView` como frontera JavaFX para CodeMirror 6 offline en modo fuente/vista previa en vivo, `WebView` de lectura separado y autocompletado `[[`.
- Panel derecho: un `SideDock`, una fila de pestañas con icono (37 px, alineada con la cabecera del editor; se desplaza en horizontal con la rueda si no caben) y debajo la vista elegida, con su propio scroll. `RightPanelSupport` pone sus vistas, **enlaces entrantes** y **enlaces salientes** (solo se calculan mientras una de las dos está a la vista), y recuerda la elegida (`ui.right_panel.view`); cada panel de plugin es una vista más (`PluginUi`). El recuento de nodos y conexiones del grafo está arriba del panel de ajustes del propio grafo.
- Focus mode: oculta chrome de UI y deja solo el área del editor. Sin botón: atajo, menú Ver o paleta, y al entrar un aviso con el atajo, porque la barra superior y sus menús quedan ocultos.

### Edición Markdown y vista previa

`MarkdownEditorView` aloja la página del editor; la vista de lectura y las notas de versión son otras dos páginas en el mismo motor `WebView` de JavaFX, y las tres se muestran a través de `HtmlView` (`ui/web`); las limitaciones del motor y el contrato del puente están en [WEBVIEW.md](WEBVIEW.md). CodeMirror gestiona transacciones, selección, resaltado, decoraciones de vista previa en vivo, historial, atajos, búsqueda/reemplazo y autocompletado; `EditorController` conserva el estado de nota, la coordinación de guardado/preview y los hooks de plugins. Fuente y vista previa en vivo son dos presentaciones del mismo `EditorState`: las decoraciones se derivan del árbol Lezer solo para el viewport (más patrones para la sintaxis de Obsidian que Lezer no conoce: enlaces wiki, notas al pie, etiquetas, ids de bloque, fórmulas en línea, comentarios), muestran el Markdown del elemento que toca la selección (las marcas de línea, por línea) solo mientras el editor tiene el foco y nunca reescriben el documento. El foco se sigue con los eventos focus/blur del contenido, porque `hasFocus` de CodeMirror también consulta `document.hasFocus()`, que el WebView de JavaFX no responde como un navegador. Las actualizaciones solo de selección reconstruyen las decoraciones únicamente si cambia lo que la selección revela, y nunca durante un arrastre del ratón. Los widgets de bloque no llevan márgenes verticales (CodeMirror los mide sin márgenes y descolocaría todos los clics de debajo) y los pesos de fuente son múltiplos de 100 (el WebKit del WebView descarta 750 y similares). Los callouts y las notas incrustadas en su propia línea los renderiza Java en segundo plano (`EditorBlockRenderSupport.hostBlocks`, claves `callout\n…` y `embed\n…`, que el editor encuentra igual con `scanHostBlocks`), y KaTeX se inyecta en la página del editor la primera vez que una nota tiene fórmulas. Al cambiar de nota se instala un estado nuevo para aislar el historial.

El shell expone dos estados semánticos mediante una única acción libro/lápiz: edición (vista previa en vivo por defecto, o fuente según la preferencia de UI) y lectura. La vista de lectura enlazada lado a lado es una acción de layout independiente disponible en Ver y en la paleta de comandos. Internamente, `EditorController.ViewMode` guarda la disposición: el editor es dueño de los paneles que coloca, el shell le pide el cambio (barra de herramientas, paleta, barra de estado) y lo escucha para la barra de estado, y los workspaces lo guardan. No crea otro documento ni otro estado de editor.

Las dependencias npm están fijadas, se empaquetan con esbuild y el bundle se incluye como recurso local, por lo que el editor no necesita red. `MarkdownPreview` sigue siendo el pipeline CommonMark de lectura separado y renderizado fuera del hilo FX; conserva la responsabilidad del render completo de transclusiones recursivas, highlight.js, KaTeX y enhancers de plugins. No es editable deliberadamente, evitando conversiones HTML-a-Markdown y estados de documento duplicados.

Sintaxis de Obsidian en la vista de lectura: los callouts y el `==resaltado==` son extensiones de CommonMark (`CalloutsAndHighlights`: un procesador de delimitadores para `==` y un postproceso que convierte una cita que empieza por `[!tipo]` en un nodo callout, como `<details>` si es plegable), así que el código queda intacto y todos los renders los tienen (incrustaciones, exportación). `ReadingSyntax` trabaja sobre el texto antes de CommonMark y fuera del código: oculta los `%%comentarios%%`, numera las notas al pie y añade su lista, convierte `^id` en un ancla invisible (`blockref-id`) y las `#etiquetas` en enlaces `jylos://tag/`. Los enlaces llevan su encabezado o bloque en `data-subpath`; `NoteAnchors` calcula su posición (editor) y su id (lectura), y el salto espera a que cargue la página de la nota abierta. Los enlaces `[[#Encabezado]]` son anclas dentro de la página.

Las notas pueden contener HTML crudo, que `MarkdownProcessor` deja pasar sin escapar por compatibilidad con otras apps de bóvedas Markdown. Como las notas también pueden venir de fuera (bóvedas importadas, `git pull`, un cliente MCP), la vista de lectura y la exportación HTML se sirven con una Content-Security-Policy con un nonce nuevo en cada render: solo los scripts que emite Jylos y los que inyectan los preview enhancers llevan el nonce, así que un `<script>` o un manejador de eventos inline escrito en una nota no se ejecuta, y la página no puede abrir conexiones, frames ni formularios. Las referencias relativas a imágenes solo se incrustan si resuelven dentro de la raíz de la bóveda (`PathContainment`); una referencia que sale de la bóveda se deja sin resolver.

## Grafo y backlinks

Un enlace nombra una nota por su ruta, no por su título: dos notas "Reunión" en carpetas distintas son destinos distintos.

`GraphBuilder` carga notas y tags por servicios y resuelve cada enlace con `LinkIndex`, igual que los backlinks y la vista de lectura.

- `WikiLinkResolver` encuentra los enlaces de un texto Markdown con su posición (`[[Título]]`, `[[ruta/Nota#sección|alias]]`, `[texto](Nota.md)`), ignorando el código, los enlaces externos, los anclajes y los adjuntos; los enlaces se conservan tal como están escritos, con su carpeta.
- `LinkResolver` resuelve un enlace a un fichero de la bóveda: sin distinguir mayúsculas ni la forma Unicode, sin sección ni texto, con `./` y `../` relativos a la nota donde está escrito, ruta exacta o parcial y, si no, el nombre del fichero (primero la carpeta de la nota de origen, luego la ruta más corta); los alias van al final.
- `LinkIndex` es el resolvedor de la bóveda por id de nota: la ruta de cada nota (la del fichero en una bóveda de carpetas; la cadena de carpetas más el título en SQLite) y sus alias (propiedad `aliases` o `alias`). Vive en la instantánea de `NoteTitleIndex`; `NoteService.resolveLink(enlace, notaOrigen)` es lo que usan los clics.
- `BacklinkService` mantiene un índice de enlaces por nota y otro inverso (destino → notas que enlazan) que se reconstruye en memoria cuando cambia alguno de los dos, porque a dónde apunta un enlace depende de todas las notas. También prepara lo que muestra el panel: `backlinkGroups`, `unlinkedMentions` (el título y los alias como texto), `outgoingLinks` y `linkMention` (convierte una mención en enlace). El trabajo sobre el texto, con la línea y la posición, está en `LinkOccurrences`.
- Caché de metadatos: `NoteMetadataCache` guarda de cada nota lo que la bóveda necesita sin abrirla: las propiedades tal como están escritas, los primeros 900 caracteres del texto (la vista previa de la lista), las `#etiquetas` y los enlaces a notas de todo el fichero, y los enlaces a adjuntos. Un fichero por bóveda en la carpeta de caché de la app (`metadata/<hash>.json`, fuera de la bóveda); una entrada solo vale para la fecha de modificación y el tamaño del fichero del que se leyó. Al cargar la bóveda, `NoteDAOFileSystem` consulta la fecha y el tamaño de cada nota y solo lee, enteras y una vez, aquellas cuya entrada no coincide; una nota sin cambios no se lee (con iCloud, leer una nota descargada solo en la nube la descarga). Las notas listadas así son `metadataComplete`: el grafo, Insights y las consultas de etiquetas toman sus enlaces y etiquetas de la lista, y `GraphController` espera a la carga en vez de leer todas las notas. Los plugins guardan sus propios valores por nota en las mismas entradas mediante `NoteService.derived` (Dataview guarda ahí un resumen de los campos, tareas y etiquetas de cada nota). El texto de una nota privada está cifrado en el disco, así que nada de lo que dice se guarda en la caché: se lee y se descifra en memoria cuando hace falta. Lo que escribe la app (crear, guardar, renombrar, limpiar) actualiza su entrada con el texto escrito, así que una nota editada en la sesión no se vuelve a leer en el siguiente arranque; `NoteDAO.close()` escribe lo pendiente al terminar la sesión de la bóveda.
- Cambios hechos fuera de la app: `VaultWatcher` vigila una bóveda de carpetas con `io.methvin:directory-watcher` (FSEvents mediante JNA en macOS, la vigilancia de Java en el resto), sin calcular hashes ni leer ficheros y sin recorrer carpetas ocultas. Avisa de las rutas cambiadas por tandas (400 ms sin cambios, 2 s como mucho); `NoteService.applyExternalChanges` → `NoteDAOFileSystem.applyExternalChanges` vuelve a listar solo esos ficheros: uno cuya fecha y tamaño son los listados o escritos por la app no se toca (las escrituras propias, iCloud quitando contenido del disco), uno cambiado se lee una vez, uno que falta sale de la lista, y una carpeta se pone al día en los dos sentidos. Una nota que se va y otra que llega en la misma tanda con la misma fecha y tamaño (mover un fichero conserva ambos) se dan por movidas (`ExternalChanges.renamed`), y la nueva toma lo que la caché sabía de ella, sin leerla; si dos coinciden en ambos, no se adivina nada. Un segundo después de arrancar, el vigilante avisa una vez de la propia carpeta de la bóveda, lo que concilia lo que cambió mientras arrancaba. En el hilo de JavaFX, `VaultWatchSupport` publica `NotesRefreshRequestedEvent` y la nota abierta solo reacciona si el cambio la tocó (`ExternalChangeSupport`): se recarga si no tiene cambios sin guardar; si los tiene, se conserva con un aviso (al guardar pasa por la comprobación de conflicto de siempre); si se borró, se trata como nota que falta; si se movió, se sigue a su nueva ruta (`EditorTabs.rebindId`, `EditorController.syncCurrentNoteIdentity`, como al renombrar desde la app). El guardado automático se pausa en lugar de volver a crear un fichero que ha desaparecido (`SaveConflictSupport.decide`); el guardado manual sí lo crea. Si el sistema pierde avisos, la bóveda se refresca como con el botón Refrescar.
- `LinksPanel` dibuja las dos vistas de enlaces del panel derecho: los entrantes (*Menciones con enlaces*, *Menciones sin enlace*, que se buscan al abrir la sección) y, en `outgoingView()`, los *Enlaces* salientes; `BacklinksSupport` pide los datos fuera del hilo de JavaFX y descarta las respuestas tardías.

## Kanban

Un tablero es una nota normal con cuerpo Markdown serializado por `KanbanModel`. Como en el plugin Kanban de Obsidian, una nota-tablero se abre como tablero en su pestaña: `EditorKanbanSupport` dibuja un `KanbanBoard` sobre los paneles de la nota y el botón de tablero de la cabecera la cambia a Markdown y vuelta. `KanbanBoard` no escribe la nota: cada cambio devuelve el Markdown del tablero, que el editor pone en el texto y guarda como cualquier edición. El comando Kanban (`TabsSupport.openKanban`) muestra la lista de tableros, el cambiado más recientemente primero, en `KanbanPickerSupport` (el selector rápido con "+ Nuevo tablero" al pie); el elegido se abre como una nota. Un tablero nuevo (`NEW_KANBAN_BOARD`, menú Archivo, paleta, menú contextual de carpeta) lo crea `NoteCreationSupport` como una nota nueva. `NoteIcons` da a los tableros (y a lienzos y adjuntos) su icono en el árbol, la lista de notas y la pestaña.

## Directorios runtime

Los resuelve una vez `AppDataDirectory`, que aplica `AppDirectories.resolve` a la máquina real.
Dónde están depende de cómo se ejecute Jylos:

| Modo (cómo se detecta) | Datos del usuario | Logs | Caché (`updates/`) |
|---|---|---|---|
| Override (`-Djylos.data.dir=X`) | `X` | `X/logs` | `X/updates` |
| Desarrollo / uber-JAR (sin `jpackage.app-path`) | directorio de trabajo (`jylos/` con los scripts del proyecto); si no se puede escribir, las rutas de instalado | igual | igual |
| ZIP portable de Windows (`jylos.portable` junto a `Jylos.exe`) | la carpeta de `Jylos.exe` | `…/logs` | `…/updates` |
| Instalado, Windows | `%APPDATA%\Jylos` | `%LOCALAPPDATA%\Jylos\logs` | `%LOCALAPPDATA%\Jylos\cache` |
| Instalado, macOS | `~/Library/Application Support/Jylos` | `~/Library/Logs/Jylos` | `~/Library/Caches/Jylos` |
| Instalado, Linux | `$XDG_DATA_HOME/jylos` (`~/.local/share/jylos`) | `$XDG_STATE_HOME/jylos/logs` | `$XDG_CACHE_HOME/jylos` |

"Instalado" significa que el lanzador de jpackage definió `jpackage.app-path`. Una versión
instalada nunca usa el directorio de trabajo, así que desinstalar o actualizar no toca los datos.
(Los instaladores de Windows mantienen la carpeta de instalación de jpackage, `%LOCALAPPDATA%\Jylos`:
`%LOCALAPPDATA%\Programs\Jylos` no pasa la validación de WiX, ICE64. Ahí solo quedan, junto al
programa, los logs y la caché de actualizaciones.) Las versiones
instaladas anteriores usaban el directorio de trabajo. En Linux (normalmente `~`, o
`~/.config/Jylos`), al arrancar, `LegacyDataMigration` mueve lo que dejaron allí, solo ficheros
que crea Jylos, y lo registra en el log. En Windows era la carpeta de instalación por usuario,
que el desinstalador viejo borra al actualizar, antes de que arranque la versión nueva; las
notas de la 2.5.8 piden copiarla antes (ver `docs/PACKAGING.md`). Si la carpeta nueva ya tiene datos de una ejecución anterior, gana la copia usada más recientemente y la otra se renombra `<nombre>.before-migration-<fecha>`, nunca se borra; una base de datos SQLite se mueve o se queda siempre junto a sus `-wal`/`-shm`.

Dentro de la carpeta de datos del usuario:

| Ruta | Uso |
|------|-----|
| `data/` | SQLite `database.db` o raíz de vault filesystem |
| `logs/` | Logs |
| `plugins/` | JARs de plugins externos |
| `themes/` | Temas externos instalados |
| `snippets/` | Snippets CSS de usuario |
| `backups/` | Backups SQLite de arranque |

## Storage

- SQLite por defecto, DAOs en `data.dao.sqlite`.
- Vault filesystem con Markdown + frontmatter YAML. El fichero de una nota contiene solo lo que escribió el usuario (compatible con Obsidian): ver [VAULT_FORMAT.md](VAULT_FORMAT.md).
- Cambios externos del vault no se reconcilian continuamente; se usan refresh explícitos y validaciones al reabrir documentos pesados como `.canvas`.
- Guardar una nota Markdown nunca sobrescribe en silencio un cambio hecho fuera de Jylos. `EditorController` guarda un `FileFingerprint` (fecha de modificación, tamaño y SHA-256) del fichero de la nota al cargarla o guardarla; antes de escribir, `SaveConflictSupport` lo compara con el fichero en disco. Decide el contenido, así que un fichero que un cliente de sincronización solo ha tocado no es un conflicto. Ante un cambio real, un guardado manual pregunta si sobrescribir, recargar la versión del disco o guardar el texto del editor como una copia aparte; un autoguardado no escribe y avisa del conflicto. No cubre ficheros canvas ni notas SQLite.
- Si al arrancar no se encuentra la carpeta del vault configurado (disco desconectado, carpeta en la nube sin montar, carpeta movida), `VaultLocationSupport` pide reintentar, elegir otra carpeta, abrir la carpeta de datos por defecto solo durante esa sesión o salir; nunca recurre en silencio a la carpeta por defecto.
- El movimiento de documentos y carpetas lo coordina `FolderService`. La UI solo pide destino y refresca estado visible; el DAO activo adapta la operación a su backend.
- En filesystem, mover un documento es mover el fichero real del vault con `Files.move`; mover una carpeta es mover el directorio completo. Las colisiones conservan la extensión del documento y la metadata de adjuntos binarios se mueve mediante el sidecar privado.
- En SQLite, mover un documento actualiza la relación nota-carpeta y mover una carpeta actualiza la relación padre-hijo. Mover a raíz limpia la relación según el esquema SQLite, sin simular rutas de filesystem.
- Las escrituras del vault para Markdown, canvas y sidecar de metadata usan temporal en el mismo directorio y reemplazo atómico cuando la plataforma lo soporta. Si no hay soporte para `ATOMIC_MOVE`, el DAO usa reemplazo controlado y limpia temporales en error.
- El favorito y el fijado de las notas, y la metadata de lienzos y adjuntos binarios, viven en `.jylos/document-metadata.json` y se mueven con el documento. Si el JSON del sidecar está corrupto, se trata como error explícito de persistencia y nunca como índice vacío para evitar sobrescrituras silenciosas.
- Cambio filesystem -> filesystem recarga sesión sin reiniciar. Cambio sqlite <-> filesystem requiere reinicio.

## Notas privadas

`EncryptionService` cifra solo cuerpo de nota con AES-256-GCM y clave derivada por PBKDF2. Metadata sigue legible para listar. Guardar nota privada bloqueada está protegido para no sobrescribir ciphertext.

El salt y el verificador (`KeyMaterial`) viven con las notas que protegen, a través de `EncryptionKeyDAO`: `.jylos/encryption.json` en una bóveda de ficheros y la tabla `app_settings` en SQLite. Una bóveda copiada o sincronizada a otro equipo se desbloquea allí con la misma contraseña. `MainController` conecta el `EncryptionService` compartido al almacenamiento de la sesión cada vez que se abre una bóveda o base de datos (y de paso lo bloquea); hasta entonces guarda el material solo en memoria, así que los tests nunca tocan las preferencias reales del usuario. En versiones anteriores el material vivía en las preferencias del usuario; se migra a la bóveda o base de datos solo si esta no tiene material propio y ya contiene notas privadas, que solo pudieron cifrarse con él. Las preferencias no se modifican.

**Nota de seguridad.** El verificador permite un ataque offline de adivinación de la contraseña, igual que cuando estaba en las preferencias. Ahora que viaja con la bóveda, cualquiera con una copia de ella (un remoto Git, una carpeta en la nube) puede intentarlo. PBKDF2 con 210.000 iteraciones encarece cada intento, pero solo una contraseña maestra larga y única hace el ataque impracticable.

## Plugins y eventos

Core no importa clases concretas de plugins. `PluginLoader` escanea `plugins/`. `PluginContext.requestOpenNote(...)` usa callback directo del shell.

`EventBus` queda para fan-out, dominio y extensibilidad. Flujos UI uno-a-uno usan callbacks.

## Git (filesystem vault)

Cuando una bóveda está dentro de un repositorio Git, `GitService` es dueño de ejecutar
el Git del sistema y `GitSyncPanel` de su presentación asíncrona: estado, staging,
commits, historial, pull/push y ramas se limitan a la ruta de la bóveda. Esto evita que
una bóveda anidada incluya trabajo ajeno de un repositorio padre. Crear o cambiar rama
solo se habilita cuando la bóveda es raíz del repositorio, porque una rama del
repositorio padre afectaría archivos ajenos. Las operaciones remotas muestran progreso
nativo coalescido y se pueden cancelar explícitamente. Tras un `pull` correcto, el
shell usa su flujo canónico de refresco explícito de bóveda para actualizar las vistas
visibles.

## Convenciones

- JDK 21.
- Sin wildcard imports.
- Logging con `LoggerConfig.getLogger(Class)`.
- I/O largo fuera del FX thread; UI con `Platform.runLater`.
- i18n en `messages*.properties`.
