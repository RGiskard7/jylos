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
| `git` | `GitService`, integración con repos Git de vault |
| `search` | Parser/modelo y servicio de búsqueda avanzada |
| `service` | Reglas de aplicación: notas, carpetas, tags, backlinks, títulos, cifrado, backups |
| `data.dao` | Implementaciones SQLite y filesystem |
| `data.models` | `Note`, `Folder`, `Tag`, `ToDoNote` |
| `event` | `EventBus` y eventos tipados |
| `plugin` | Loader, manager y API de plugins |
| `util` | Markdown, wiki-links, preview, highlighter, exportación, Kanban |
| `workspace` | Captura y restauración de workspaces |
| `config` | Logging |

## Patrón `MainController`

`MainController` coordina shell, wiring, apertura/guardado/cierre de notas y callbacks entre features. No debe absorber cuerpos de features nuevas. Cada feature autocontenida vive en `*Controller` o `*Support` con `wire(...)`.

## Composición UI

- `MainView.fxml`: toolbar, centro con split principal, overlays de grafo/Kanban, panel derecho y status bar.
- Split central: sidebar, lista de notas, editor/preview.
- Overlays: grafo (`GraphView.fxml`) y Kanban (`KanbanBoard`) se gestionan por `OverlaySupport`.
- Editor: `EditorTabs`, `CodeArea` de RichTextFX, `WebView` para preview y autocompletado `[[`.
- Panel derecho: metadata, backlinks y paneles de plugins.
- Focus mode: oculta chrome de UI y deja solo editor.

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
- Cambio filesystem -> filesystem recarga sesión sin reiniciar. Cambio sqlite <-> filesystem requiere reinicio.

## Notas privadas

`EncryptionService` cifra solo cuerpo de nota con AES-256-GCM y clave derivada por PBKDF2. Metadata sigue legible para listar. Guardar nota privada bloqueada está protegido para no sobrescribir ciphertext.

## Plugins y eventos

Core no importa clases concretas de plugins. `PluginLoader` escanea `plugins/`. `PluginContext.requestOpenNote(...)` usa callback directo del shell.

`EventBus` queda para fan-out, dominio y extensibilidad. Flujos UI uno-a-uno usan callbacks.

## Convenciones

- JDK 21.
- Sin wildcard imports.
- Logging con `LoggerConfig.getLogger(Class)`.
- I/O largo fuera del FX thread; UI con `Platform.runLater`.
- i18n en `messages*.properties`.
