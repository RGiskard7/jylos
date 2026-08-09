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
| `ui.controller` | Shell y controladores: `MainController`, sidebar, lista, editor, toolbar, grafo y helpers (`GitController`, `PrivacySupport`, `OverlaySupport`, etc.) |
| `ui.theme` | Aplicación/detección de temas y catálogos de temas/snippets |
| `ui.preferences` | Persistencia de preferencias UI serializadas |
| `ui.graph` | `GraphCanvas`, render nativo del grafo |
| `ui.components` | Paleta, quick switcher, diálogos, visores, canvas, tabs, Kanban |
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

- `MainView.fxml`: toolbar, centro con split principal, overlays de grafo/Kanban, panel derecho y status bar.
- Split central: sidebar, lista de notas, editor/preview.
- Overlays: grafo (`GraphView.fxml`) y Kanban (`KanbanBoard`) se gestionan por `OverlaySupport`.
- Editor: `EditorTabs`, `MarkdownEditorView` como frontera JavaFX para CodeMirror 6 offline en modo fuente/vista previa en vivo, `WebView` de lectura separado y autocompletado `[[`.
- Panel derecho: metadata, backlinks y paneles de plugins.
- Focus mode: oculta chrome de UI y deja solo editor.

### Edición Markdown y vista previa

`MarkdownEditorView` es la única frontera entre Java y JavaScript. CodeMirror gestiona transacciones, selección, resaltado, decoraciones de vista previa en vivo, historial, atajos, búsqueda/reemplazo y autocompletado; `EditorController` conserva el estado de nota, la coordinación de guardado/preview y los hooks de plugins. Fuente y vista previa en vivo son dos presentaciones del mismo `EditorState`: las decoraciones se derivan del árbol Lezer solo para el viewport, muestran el Markdown del bloque activo y nunca reescriben el documento. Al cambiar de nota se instala un estado nuevo para aislar el historial.

El shell expone dos estados semánticos mediante una única acción libro/lápiz: edición (vista previa en vivo por defecto, o fuente según la preferencia de UI) y lectura. La vista de lectura enlazada lado a lado es una acción de layout independiente disponible en Ver y en la paleta de comandos. Internamente, `UiLayout.ViewMode` conserva estas disposiciones en los workspaces; no crea otro documento ni otro estado de editor.

Las dependencias npm están fijadas, se empaquetan con esbuild y el bundle se incluye como recurso local, por lo que el editor no necesita red. `MarkdownPreview` sigue siendo el pipeline CommonMark de lectura separado y renderizado fuera del hilo FX; conserva la responsabilidad del render completo de transclusiones recursivas, highlight.js, KaTeX y enhancers de plugins. No es editable deliberadamente, evitando conversiones HTML-a-Markdown y estados de documento duplicados.

## Grafo y backlinks

`GraphBuilder` carga notas y tags por servicios. Los enlaces se resuelven con `WikiLinkResolver`. `BacklinkService` mantiene índices forward/inverse para no reescanear todo el vault en cada consulta.

## Kanban

Un tablero es una nota normal con cuerpo Markdown serializado por `KanbanModel`. `KanbanBoard` renderiza columnas/tarjetas y guarda mediante `NoteService`. La publicación de eventos la hace el owner shell, no el widget.

## Directorios runtime

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
- Vault filesystem con Markdown + frontmatter YAML.
- Cambios externos del vault no se reconcilian continuamente; se usan refresh explícitos y validaciones al reabrir documentos pesados como `.canvas`.
- El movimiento de documentos y carpetas lo coordina `FolderService`. La UI solo pide destino y refresca estado visible; el DAO activo adapta la operación a su backend.
- En filesystem, mover un documento es mover el fichero real del vault con `Files.move`; mover una carpeta es mover el directorio completo. Las colisiones conservan la extensión del documento y la metadata de adjuntos binarios se mueve mediante el sidecar privado.
- En SQLite, mover un documento actualiza la relación nota-carpeta y mover una carpeta actualiza la relación padre-hijo. Mover a raíz limpia la relación según el esquema SQLite, sin simular rutas de filesystem.
- Las escrituras del vault para Markdown, canvas y sidecar de metadata usan temporal en el mismo directorio y reemplazo atómico cuando la plataforma lo soporta. Si no hay soporte para `ATOMIC_MOVE`, el DAO usa reemplazo controlado y limpia temporales en error.
- La metadata de adjuntos binarios vive en `.jylos/document-metadata.json`. Si el JSON del sidecar está corrupto, se trata como error explícito de persistencia y nunca como índice vacío para evitar sobrescrituras silenciosas.
- Cambio filesystem -> filesystem recarga sesión sin reiniciar. Cambio sqlite <-> filesystem requiere reinicio.

## Notas privadas

`EncryptionService` cifra solo cuerpo de nota con AES-256-GCM y clave derivada por PBKDF2. Metadata sigue legible para listar. Guardar nota privada bloqueada está protegido para no sobrescribir ciphertext.

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
