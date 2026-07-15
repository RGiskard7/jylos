# Changelog

## [Unreleased]

- Los metadatos de la versión en tiempo de compilación ahora provienen del filtrado de Maven (`release.version`) en lugar de los recursos de la aplicación codificados, por lo que las versiones de CI etiquetadas incorporan la versión de la aplicación correspondiente.

- Las compilaciones de versiones de GitHub Actions inyectan la etiqueta semántica de la versión en los scripts de empaquetado nativos y de Maven.

- El workflow de release ajusta temporalmente la versión Maven desde el tag antes de compilar, evitando tener que editar `pom.xml` manualmente para cada release.

- Jylos ahora comprueba las versiones de GitHub al iniciarse y muestra una pequeña notificación no bloqueante en la aplicación cuando hay una versión pública más reciente disponible.

- El menú **Ayuda** incluye ahora **Buscar actualizaciones...**, usando el mismo comprobador de releases y mostrando feedback explícito si no hay actualización o si GitHub no se puede consultar.

## [2.4.0] - 2026-07-10

Release centrada en **fiabilidad del canvas**, **persistencia segura de metadata en vault filesystem**, **mejor compatibilidad con Obsidian**, y una pasada de rendimiento sobre **preview Markdown** y flujos calientes de apertura/listado.

### Canvas y compatibilidad con Obsidian

- **Canvas más completo y usable**: se añadieron nodos de archivo, mejoras en nodos embebidos, etiquetas y colores de aristas, controles laterales más cercanos al flujo de Obsidian y ajustes de interacción para conectar elementos con menos fricción.
- **Aristas más robustas**: se revisó la creación de conexiones entre nodos y grupos, evitando estados donde los elementos quedaban difíciles de mover o seleccionar tras activar el modo de conexión.
- **Edición externa más fiable**: al reabrir documentos desde un vault filesystem, Jylos vuelve a consultar el estado real del archivo para reflejar cambios hechos desde Obsidian u otras herramientas cuando corresponde.
- **Persistencia de `.canvas` segura**: los cambios de metadata como favorito/fijado ya no pueden sobrescribir un canvas real con contenido vacío procedente de una nota ligera de listado.
- **JSON de canvas compatible**: la normalización del documento se limita a guardados reales de canvas, preservando estructura válida y evitando escrituras innecesarias.

### Metadata de documentos en filesystem

- **Favoritos y fijados unificados por tipo de documento**: Markdown, canvas y adjuntos binarios comparten el mismo flujo de negocio en UI/service, con persistencia específica encapsulada en la capa filesystem.
- **Sidecar privado para adjuntos**: la metadata de documentos que no pueden contener frontmatter se guarda en `.jylos/document-metadata.json`, sin modificar binarios ni mezclar lógica de persistencia en la UI.
- **Markdown sigue usando frontmatter**: las notas `.md` mantienen su modelo compatible con vaults Markdown/Obsidian.
- **Cache como aceleración, no fuente de verdad**: al leer documentos completos, la metadata se obtiene desde su persistencia real para evitar estados visuales falsos tras cerrar/reabrir.
- **Cobertura de regresión**: se añadieron tests para persistencia de canvas, metadata filesystem, restauración/cache y contratos DAO.

### Rendimiento y preview Markdown

- **Preview más ligera en notas comunes**: `highlight.js` solo se carga cuando el HTML contiene bloques de código.
- **KaTeX bajo demanda**: los assets de fórmulas matemáticas se cargan únicamente cuando la nota contiene delimitadores LaTeX.
- **Soporte de fórmulas LaTeX** en preview Markdown mediante KaTeX offline.
- **Menos trabajo al abrir/listar en filesystem**: ajustes en lectura ligera, cache y rutas calientes para reducir I/O innecesario en vaults grandes.
- **Espaciado de preview ajustado**: se redujo margen extra al inicio/final del documento renderizado.
- **Visor PDF más escalable**: los PDFs ya no rasterizan todas las páginas al abrirse; ahora se abre el documento, se crean placeholders ligeros, se renderizan bajo demanda las páginas cercanas al viewport con una cache LRU pequeña, se calcula la página visible por posición real del viewport, se prioriza esa zona aunque el usuario haga scroll rápido y se recuerda la posición de scroll por documento durante la sesión. Esto reduce memoria, bloqueo inicial y lag en documentos grandes.

### UI, i18n y documentación

- **Vistas de adjuntos más consistentes**: se añadieron acciones de favorito/fijado donde faltaban y se alinearon con el flujo general del editor.
- **Menús contextuales más completos**: se amplió el soporte de acciones sobre documentos desde la lista.
- **Menú superior más integrado con el sistema**: el `MenuBar` usa la barra nativa del SO cuando JavaFX lo soporta, liberando espacio vertical en macOS sin cambiar el comportamiento en Windows/Linux.
- **Creación de canvas desde carpetas**: el menú contextual de una carpeta ahora incluye `Nuevo Canvas` debajo de `Nueva Nota`, creando el canvas directamente en esa carpeta.
- **Internacionalización documentada**: nueva guía para añadir idiomas y ajustes en bundles EN/ES.
- **Documentación y tooling actualizados a `2.4.0`**: `pom`, metadatos de aplicación, scripts de packaging, README, docs operativas, web landing y catálogo JBang.
- **Comentarios de intención revisados** en zonas sensibles: metadata filesystem, protección frente a sobrescritura de canvas y carga lazy de assets del preview.
- **Pulido final de UI**: botones de cierre en Graph/Kanban neutralizados y alineados con el resto de toolbars, scrollbar vertical real en el editor RichTextFX y scrollbar visible en el visor PDF.

### Commits incluidos y cambios de cierre

- `2026-07-08` — `c14b883` — `feat: Mejora de la funcionalidad del lienzo con soporte para nodos de archivo y etiquetado de bordes`
- `2026-07-05` — `1c0a9f5` — `feat: Mejora en la gestión de notas y etiquetas, incluyendo optimizaciones en el acceso a datos y nuevas pruebas de rendimiento`
- Cambios de cierre no comiteados al preparar la release:
  - persistencia segura de metadata para canvas/adjuntos en filesystem;
  - protección contra sobrescritura de `.canvas` en toggles de favorito/fijado;
  - renderizado KaTeX bajo demanda y carga lazy de `highlight.js`;
  - renderizado PDF bajo demanda con cache pequeña y restauración de posición de scroll;
  - ajustes finales de toolbar, diálogos, scrollbars y controles Graph/Kanban;
  - actualización de versión, documentación y catálogo JBang.

## [2.3.0] - 2026-07-04

Release centrada en el **cierre del saneamiento arquitectónico**, la **normalización del wiring de la UI**, el **endurecimiento de guardas y contratos internos**, y un pulido importante de **temas, snippets CSS, overlays y diálogos** para dejar el proyecto en un estado más coherente de cara a una release pública.

### Arquitectura y saneamiento

- **Fronteras UI/service más claras**: se consolidó el uso de `TagService`, `FolderService` y `NoteService` como owners de sus workflows reales, reduciendo atajos desde la UI hacia DAOs cuando ya existía una capa de servicio válida.
- **Composición UI más explícita y homogénea**: controladores y supports principales quedaron alineados alrededor de wiring explícito, callbacks tipados y responsabilidades mejor delimitadas.
- **`AppContext` eliminado del flujo operativo**: se cerró su uso como atajo global, reforzando dependencias explícitas y eliminando residuos del patrón anterior.
- **`EventBus` más acotado**: se movieron flujos UI uno-a-uno hacia callbacks directos y se reservaron los eventos para fan-out real, coordinación shell y extensibilidad.
- **Taxonomía de `ui/controller` saneada**: separación de helpers, supports, stores y catálogos con naming y ubicación más consistentes, sin rehacer media arquitectura.
- **Guardas arquitectónicas reforzadas**: nuevos tests y reglas para evitar regresiones en composición UI, límites service/data, uso de `EventBus` y ownership de tags.

### Implementación y mantenimiento

- **MainController más limpio** dentro de su núcleo legítimo de coordinación, extrayendo flujos secundarios a supports cohesionados sin fragmentar su rol shell.
- **Servicios e índices más coherentes**: mejor separación entre negocio, infraestructura técnica e índices/cachés, con documentación normativa actualizada.
- **Cambio de vault sin reinicio en modo filesystem**: `filesystem -> filesystem` ahora rehace la sesión activa en caliente con confirmación de cambios sin guardar, cierre limpio de tabs, rewire de servicios/controladores, invalidación de índices/cachés y protección frente a callbacks tardíos del vault anterior; `sqlite <-> filesystem` sigue requiriendo reinicio.
- **Ajustes de robustez en filesystem/SQLite**: mejoras incrementales en carga de vault, refrescos, cachés, concurrencia y coherencia post-restore.
- **Acceso persistente más consistente**: se sincronizó mejor el acceso concurrente a la base de datos entre DAOs y se pulieron consultas calientes y resolución de nombres de archivo en SQLite.
- **Backlinks, índices y tareas internas más sólidos**: se ajustaron suscripciones, tareas y flujos incrementales para reducir estados intermedios frágiles.
- **Plugins y overlays alineados** con el modelo saneado de wiring y publicación de acciones.

### UX, theming y diálogos

- **Temas y snippets CSS** integrados de forma más consistente en overlays y diálogos visibles, evitando superficies que se quedaban con estilo por defecto.
- **Botonera unificada**: colores, bordes, hover y estados activos se normalizaron para reducir inconsistencias visuales entre core, overlays y plugins.
- **Nuevo color principal** más legible: se sustituyó el acento anterior por una variante más oscura y con matiz violeta para asegurar contraste correcto con texto blanco.
- **Command Palette y Quick Switcher** más integrados con el sistema real de temas y snippets.
- **Pulido de modales**: mejor coherencia visual en inputs, tabs, botones y fondos en diálogos clave como Preferencias, Acerca de, Git Sync y paneles de análisis.
- **Indicadores y microdetalles UI** revisados: conteo canónico en barra inferior, estado de guardado del editor más estable y ajustes en headers y empty states.

### Rendimiento percibido

- **Menos trabajo inútil en editor/preview**: la preview ya no se re-renderiza cuando no está visible y el debounce de refresco se suavizó para reducir tirones con `WebView` y Markdown pesado.
- **Syntax highlighting más conservador**: ajuste de debounce para aliviar recalculados agresivos en notas medianas y grandes.

### Documentación y distribución

- **Documentación técnica y de usuario actualizada** para reflejar el estado saneado de la arquitectura, el uso de JBang, el empaquetado y el flujo de lanzamiento.
- **Versión alineada a `2.3.0`** en `pom`, metadatos de app, scripts de packaging, README, docs operativas y catálogo JBang.

### Commits incluidos en esta release (pendientes de push al remoto al cerrar esta versión)

- `2026-07-04` — `61a76d8` — `refactor: Refactorización de estilos de interfaz de usuario y mejora del manejo de diálogos`
- `2026-07-03` — `b6bee5e` — `refactor: Clarificación de la propiedad de relaciones entre notas y etiquetas`
- `2026-07-03` — `a7ed3a5` — `refactor: Actualización de la gestión de notas y carpetas para usar la capa de servicio.`
- `2026-07-02` — `c617892` — `refactor: Consolidación y mejora de la nomenclatura en controladores y clases de soporte`
- `2026-07-02` — `d1295e9` — `refactor: Eliminación de AppContext y mejora en la gestión de eventos`
- `2026-07-02` — `af0b616` — `refactor: Extracción de lógica de creación de notas y flujos de trabajo a clases de soporte`
- `2026-07-02` — `5fd6047` — `refactor: Mejora en la inyección de dependencias y gestión de eventos en BacklinkService`
- `2026-07-02` — `dde91c9` — `refactor: Transición a funciones de devolución de llamada explícitas para el manejo de eventos en componentes de interfaz de usuario`
- `2026-07-02` — `c5308fd` — `refactor: Mejora en la gestión de dependencias y eliminación de algunas referencias a AppContext`
- `2026-07-02` — `b594b97` — `refactor: Reorganización y refactorización de paquetes de ui/controller`
- `2026-07-01` — `e584084` — `refactor: Encapsulación de métodos de configuración en controladores`
- `2026-07-01` — `aae2001` — `refactor: Simplificación y mejora en la gestión de servicios en controladores`
- `2026-07-01` — `d90ab4e` — `docs: Actualización de la documentación para incluir referencias a ARCHITECTURE_GUIDELINES.md`
- `2026-06-30` — `9284144` — `refactor: Actualización de la versión y mejoras en la documentación y gestión de notas`
- `2026-06-30` — `9f51362` — `refactor: Mejora en la gestión de nombres de archivos y optimización de consultas en SQLiteDB`
- `2026-06-30` — `c1cfa6d` — `refactor: Simplificación de la gestión de servicios en NoteService`
- `2026-06-30` — `9d20c1a` — `refactor: Mejora en la gestión de tareas y suscripciones en EventBus y BacklinksSupport`
- `2026-06-30` — `575bfd9` — `feat+fix: Sincronización del acceso a la base de datos entre DAOs`

## [2.2.0] - 2026-06-25

Esta versión gira en torno a **Canvas** (visor → editor compatible con Obsidian), **transclusión** y **enlaces enriquecidos**, y un refuerzo importante de las **notas privadas** (modelo de desbloqueo por-nota/global, indicadores visuales y protección frente a borrado/exportación), además de varias correcciones de listas/paneles.

### Feat: Canvas — grupos al estilo Obsidian (mover contenido, renombrar, alinear)

- **El grupo arrastra su contenido**: al mover un grupo, los nodos cuyo centro está dentro de él se desplazan con el grupo (la pertenencia es geométrica y se calcula al empezar el arrastre).
- **Renombrar el grupo**: doble clic sobre un grupo edita su etiqueta in situ (Enter o perder foco confirma, Esc cancela).
- **Redimensionar**: el grupo se redimensiona con el mismo tirador de esquina que el resto de nodos.
- **Alinear miembros**: el menú contextual del grupo añade un submenú **Alinear** (izquierda / centrar horizontal / derecha / arriba / centrar vertical / abajo) que coloca sus nodos internos respecto a su caja envolvente común.
- **Tests**: `CanvasModelTest` +1 — `setNodeLabel` asigna y limpia la etiqueta. 257/257 verdes.

### Fix: Canvas — color de marco completo y tirador de redimensión visible

- **Color = marco completo**: colorear un nodo o grupo ahora tiñe **todo el borde** (los cuatro lados), como en Obsidian, en vez de solo una barra a la izquierda.
- **Redimensión usable**: el tirador de la esquina inferior derecha ahora es **visible** — era un nodo no gestionado en el `Pane`, que se quedaba a 0×0 (invisible aunque agarrable de casualidad); ahora se dibuja como un **punto circular** de 16 px (acento, borde blanco, sombra, realce al pasar el ratón) dentro de la esquina. Disponible en **todos los tipos de nodo** (texto, fichero, enlace y grupo).

### Feat: Canvas — eliminar desde el menú contextual y grupos usables

- El menú contextual de un **nodo** o una **arista** incluye ahora **Eliminar** (además del selector de color).
- **Nodos de grupo** rediseñados: fondo translúcido (visibles y arrastrables por toda su área, no solo por el borde) y etiqueta por defecto al crearlos, de modo que "Añadir grupo" produce un grupo claramente visible.

### Feat: Canvas — más paridad con Obsidian (flechas, redimensionar, color, nodos enlace/grupo)

- **Flechas en las aristas**: cada arista dibuja una punta de flecha en el extremo destino, orientada según la dirección (como Obsidian).
- **Redimensionar nodos**: el nodo seleccionado muestra un tirador en su esquina inferior derecha; arrastrarlo cambia el tamaño (con mínimo y clip redondeado sincronizados) y se persiste como `width`/`height` enteros.
- **Color de nodo y arista**: clic derecho sobre un nodo o una arista abre un selector con los 6 presets de Obsidian (1–6) y "Sin color"; se guarda en el campo `color` (o se elimina al limpiar).
- **Crear nodos de enlace y grupo**: nuevos botones en la barra — enlace (pide la URL) y grupo (rectángulo etiquetado) — además del nodo de texto ya existente.
- **Tests**: `CanvasModelTest` +3 — `addLinkNode`/`addGroupNode`, `resizeNode` (redondeo) y `setNodeColor`/`setEdgeColor` (asignar y limpiar). 256/256 verdes.

### Feat: notas privadas — indicador en el editor y protección en exportación

- **Indicador en el editor**: las notas privadas muestran un candado junto al título — cerrado cuando está bloqueada (🔒) y abierto cuando es legible esta sesión. (En la lista ya aparece el candado.)
- **Exportación protegida**: el export individual de una nota privada se bloquea con aviso (hazla normal primero), y el "exportar bóveda" **omite** las notas privadas, de modo que su contenido nunca se vuelca en claro a una carpeta sin proteger.

### Feat: notas privadas — desbloqueo por-nota vs global, toggle en el menú contextual, protección de borrado

Mejoras de usabilidad y lógica del cifrado, manteniendo el modelo AES-GCM/PBKDF2 existente:

- **Desbloqueo por-nota vs global**: al abrir una nota privada bloqueada y meter la contraseña, ahora se desbloquea **solo esa nota** (las demás siguen 🔒). El desbloqueo de **todas** las notas es una acción explícita: nuevo **Herramientas → Desbloquear Notas Privadas**. Internamente `EncryptionService` separa "tener la clave" (`hasKey`, necesaria para cifrar/descifrar) de la revelación: `revealNote(id)` (una), `unlock()` (todas) y `canRead(id)` como puerta de lectura.
- **Convertir privada/normal desde la lista**: el menú contextual de una nota incluye **Hacer Privada** / **Hacer Normal (Descifrar)** según su estado, operando sobre esa nota concreta (pide la contraseña solo si hace falta para cifrar/descifrar).
- **Las notas privadas no se pueden eliminar**: el borrado se bloquea (esté la nota bloqueada o no) en el punto central `deleteNote`, y la opción "Mover a papelera" aparece deshabilitada en el menú contextual de notas privadas. Para borrarla hay que **hacerla normal** primero, evitando perder una nota cifrada por un descuido.
- **Indicador visual**: las notas privadas muestran un **icono de candado** en la lista. Al **bloquear** (lock) la nota abierta se recarga directamente a 🔒 (ya no rebota a un prompt de desbloqueo).
- **Robustez**: cifrar/descifrar y el guard anti-sobrescritura usan `hasKey()` en vez del estado global, de modo que una nota revelada individualmente se guarda re-cifrada correctamente; descifrar el historial también usa `hasKey()`.
- **Tests**: `EncryptionServiceTest` +4 — reveal por-nota no es desbloqueo global, unlock global, `acquireKey` sin revelar, y `lock` limpia clave y revelaciones. 253/253 verdes.

### Fix: notas cifradas — fuga de ciphertext en listados, pérdida de datos al desmarcar, y favoritos

Tres correcciones de la auditoría de cifrado/favoritos:

- **Confidencialidad**: las vistas por **carpeta** y por **etiqueta** (y la búsqueda dentro de carpeta) mostraban el cuerpo cifrado en crudo (`JENC1:…`) como vista previa de una nota privada. `getAllNotes` ya lo sustituía por el candado 🔒, pero `getNotesByFolder`/`getNotesByTag` no. Ahora todos los listados pasan por un mismo helper `scrubEncryptedForList`, así que **ningún listado expone ciphertext** (en SQLite y en bóveda).
- **Pérdida de datos**: al **desmarcar como privada** una nota que estaba abierta bloqueada (el editor mostraba 🔒), se guardaba el placeholder `🔒` como contenido en claro, destruyendo el cuerpo. Ahora `handleTogglePrivate` detecta el placeholder y recupera el contenido real (descifrado) del almacén antes de guardar, y **recarga el editor** para reflejar el nuevo estado.
- **Favoritos**: marcar/desmarcar favorito desde la **lista** de notas no refrescaba el panel de Favoritos del sidebar (la ruta antigua usaba un evento que no disparaba ese refresh). Ahora publica `NoteSavedEvent` —el flag se persiste de verdad—, igual que el botón del editor, y el panel se actualiza en vivo (ambos modos).
- **Tests**: `FileSystemDAOContractTest` +1 — los listados por carpeta y `getAllNotes` sustituyen el cuerpo cifrado por el placeholder. 249/249 verdes.

### Fix (tests): `EncryptionServiceTest` destruía la configuración real de contraseña maestra

`EncryptionServiceTest` usaba el **mismo nodo de Preferences que la app** (`userNodeForPackage(EncryptionService.class)`) y borraba `enc.salt`/`enc.verifier` en cada `@BeforeEach`/`@AfterEach`. Ejecutar `mvn test` **eliminaba la contraseña maestra del usuario** (salt + verificador), dejando `isConfigured()` en `false`: la app dejaba de pedir desbloqueo al abrir notas privadas y volvía a pedir crear contraseña maestra. Ahora el test **respalda los valores reales en `@BeforeAll` y los restaura en `@AfterAll`**, de modo que la suite ya nunca toca la configuración del usuario.

### Feat: Canvas — edición Fase 2 (mover/crear/editar/borrar nodos, conectar/borrar aristas, guardar)

Edición completa sobre el visor de canvas:

- **Mover nodos**: arrastra un nodo para reposicionarlo; las aristas conectadas le siguen en tiempo real. Un clic simple no mueve (sigue abriendo nota/enlace con doble clic).
- **Crear nodo de texto**: botón **+** en la barra; crea un nodo en el centro de la vista y abre directamente su edición.
- **Editar texto en el nodo**: doble clic en un nodo de texto lo convierte en un editor in situ; se confirma con **⌘+Enter** o al perder el foco, y se cancela con **Esc**. El resto del tiempo el nodo muestra la **previsualización** renderizada.
- **Conectar nodos (aristas)**: botón **conectar** en la barra activa el modo conexión (cursor en cruz); clic en el nodo origen y luego en el destino dibuja la arista, eligiendo automáticamente los lados que se miran según la posición. Se pueden encadenar varias; **Esc** o clic en el fondo cancela.
- **Borrar arista**: clic sobre una arista la selecciona (resaltada en color de acento) y **Supr/Backspace** la elimina.
- **Borrar nodo**: selecciónalo (clic simple, resaltado con halo de acento) y pulsa **Supr/Backspace**; se eliminan también las aristas conectadas a él.
- **Crear un canvas nuevo**: desde **Archivo → Nuevo Canvas**, el command palette (*New Canvas*) o el menú contextual de la barra; crea un `.canvas` vacío en la carpeta actual y lo abre. Solo en modo vault Markdown (un canvas es un fichero del vault); en modo SQLite avisa. El `NoteDAOFileSystem` ahora reconoce adjuntos al crear: escribe el contenido en crudo conservando la extensión (sin `.md` ni frontmatter).
- **Barra de herramientas**: añadir nodo, conectar, zoom +/−, **ajustar al contenido**, y **Guardar** (se habilita solo cuando hay cambios).
- **Guardado fiel** (`.canvas`): nuevo `CanvasModel.Document`, un documento mutable respaldado por el JSON original; al guardar se actualizan solo los campos tocados (p. ej. `x`/`y`, redondeados a enteros como Obsidian) y se **preservan los campos desconocidos** → round-trip seguro con Obsidian.
- **Scroll en nodos**: los nodos de nota/texto largos ahora se pueden **desplazar con la rueda** (sin hacer zoom del lienzo); arrastrar el nodo sigue moviéndolo y la rueda sobre el fondo sigue haciendo zoom.
- **Tests**: `CanvasModelTest` +7 — `moveNode`/`addTextNode`/`setNodeText`/`removeNode` (borra también sus aristas), `addEdge` (lados en blanco omitidos)/`removeEdge`, y round-trip que preserva campos desconocidos; `FileSystemDAOContractTest` +1 — crear un adjunto `.canvas` escribe JSON en crudo sin frontmatter ni `.md`. 248/248 verdes.

### Feat: visor de Canvas (`.canvas`, compatible Obsidian) — Fase 1

Abre y visualiza ficheros **JSON Canvas** (`.canvas`) del vault, el formato de lienzo de Obsidian. Esta primera fase es **solo lectura** (la edición —crear/mover/conectar/guardar— vendrá en una fase posterior).

- **Apertura**: los `.canvas` del vault ahora aparecen en la lista de notas (como un adjunto más) y se abren en un **visor de lienzo infinito** con **zoom** (rueda, hacia el cursor) y **pan** (arrastrar el fondo); se ajusta al contenido al abrirse.
- **Render**: nodos de **texto**, **fichero**, **enlace** (clic abre el navegador) y **grupo** (rectángulo etiquetado), más las **aristas** entre nodos con su lado/etiqueta. Respeta los **colores** de Obsidian (presets 1–6 y `#rrggbb`). Los nodos se renderizan con controles JavaFX (texto legible), no sobre un Canvas.
- **Contenido en los nodos**: los nodos de **imagen** muestran la imagen incrustada; los de **nota** muestran su título + el cuerpo **renderizado como previsualización** (no el Markdown en crudo), y los de **texto** renderizan su Markdown — encabezados, negrita/cursiva, código, listas y citas. Doble clic en un nodo de nota la abre. El contenido se recorta al tamaño del nodo. (Editar el texto del nodo —ver/editar la fuente— llegará con la fase de edición.)
- **Render Markdown sin WebView**: nuevo `util/MarkdownMini`, que reutiliza el parser CommonMark del proyecto y construye nodos JavaFX (`TextFlow`) con estilo. Se usa en los nodos del canvas porque un `WebView` por nodo no compone bien en una superficie con zoom/pan (y evita su coste de memoria).
- **Distinción en la lista**: los `.canvas` usan ahora un icono propio (rejilla, `fth-grid`) para diferenciarlos de notas e imágenes/PDF.
- **Arquitectura**: nuevo `util/CanvasModel` (parseo tolerante del JSON con Gson — nueva dependencia, justificada para un formato JSON externo; entradas malformadas se descartan sin romper el documento) y `ui/components/CanvasView` (visor con pan/zoom). Se integra en el flujo de adjuntos existente: `AttachmentType` reconoce `.canvas` y `EditorController` enruta al visor (igual que PDF/imágenes). Estilado con variables de tema (claro/oscuro). 
- **Tests**: `CanvasModelTest` (4) — parseo de nodos/aristas, entradas vacías/ inválidas, descarte de entradas malformadas, defaults numéricos. 240/240 verdes.

### Feat: transclusión de notas (embeds `![[ ]]`)

Permite **incrustar** el contenido de otra nota (o una de sus secciones) dentro de la vista previa, estilo Obsidian. Escribe `![[Nota]]` para embeber la nota entera o `![[Nota#Encabezado]]` para una sección.

- **Render**: el embed se muestra como un bloque con un encabezado clicable (abre la nota origen) y el cuerpo renderizado (Markdown completo: encabezados, listas, código, etc.). Los `[[wiki-links]]` dentro del embed también se resuelven.
- **Secciones**: `#Encabezado` extrae desde ese encabezado hasta el siguiente del mismo nivel o superior.
- **Seguridad/robustez**: recursión acotada (profundidad máx. 3) con detección de ciclos por rama, de modo que `A → B → A` o cadenas profundas degradan a un aviso en vez de colgarse. Notas/secciones inexistentes muestran un placeholder discreto. Todo el texto incrustado se escapa.
- **Arquitectura**: nuevo `util/Transclusion` (puro y testeable: recibe un resolutor `título → contenido`). Se integra en `MarkdownPreview` **antes** de los wiki-links (porque `![[X]]` contiene `[[X]]`) usando tokens-placeholder que se reinyectan tras CommonMark — evita los problemas de meter HTML en bloques Markdown. El contenido se resuelve con el `NoteService` vivo mediante wiring explícito, igual que `NoteTitleIndex`.
- **Creación**: se teclea `![[` (el autocompletado de `[[` existente la cubre); sin botón nuevo, coherente con cómo se crean los wiki-links.
- **CSS** del embed en ambos temas. **Tests**: `TransclusionTest` (8) — expansión, secciones, ciclos, profundidad, nota/sección ausente, wiki-links internos, anidamiento. 236/236 verdes.

### Fix: el árbol de carpetas y la lista de notas perdían la selección al refrescarse

Al refrescarse (p. ej. cuando termina la carga en segundo plano del vault, o tras una recarga), tanto el árbol de carpetas como la lista de notas **borraban la selección y el scroll** y volvían al root/arriba — algo más visible al usar transclusión, porque resolver el contenido de los embeds toca la caché de notas en cada render del preview.

- **Árbol de carpetas**: `applyFolderTreeBuildResult` recuerda la carpeta seleccionada y la vuelve a seleccionar (con scroll) tras reconstruir.
- **Lista de notas**: cada recarga hacía `clearSelection()` + `setAll()` (instancias nuevas), reseteando selección y scroll. Ahora un helper `applyNotesPreservingSelection` mantiene la nota seleccionada y su scroll **cuando sigue presente** (refresco del mismo contexto); al navegar a otra carpeta/etiqueta la nota anterior no está y la selección se limpia sola, como antes.
- En ambos casos la re-selección está **guardada** para no republicar el evento (no re-navega ni recarga el editor, preservando el cursor/los cambios sin guardar). Beneficia a cualquier refresco, no solo a la transclusión.

### Fix: la lista central de notas se vaciaba al (auto)guardar

Al guardar (sobre todo en cada autoguardado mientras editabas), la lista central de notas se quedaba **vacía** hasta navegar a otra carpeta. Causa (diagnosticada con trazas): guardar disparaba **dos** recargas de la lista — una desde `handleSave` y otra desde la reconstrucción del árbol de carpetas (que `NoteSavedEvent` provocaba → reselección de carpeta → segundo `loadNotesForFolder`). Las dos cargas competían y la segunda, que por una race del prune de la caché durante la reescritura del fichero devolvía 0 notas, llegaba la última y **sobrescribía** el resultado correcto.

- **`handleSave` ya no recarga la lista central**: guardar el contenido no cambia qué notas hay en la carpeta, así que era una recarga redundante.
- **`NoteSavedEvent` ya no reconstruye el árbol de carpetas** (su estructura/conteos no cambian al guardar contenido), eliminando la segunda carga; solo se refrescan Recientes/Favoritos, cuyo orden sí puede cambiar.
- Defensa en profundidad en el prune (`pruneStaleCacheEntries`): actualiza primero y borra solo las claves obsoletas (nunca deja la caché vacía en una ventana intermedia) y **se salta la pasada si fuera a vaciar la caché entera** por un fallo transitorio del filesystem.

### Fix: las listas de Recientes/Favoritos del sidebar parpadeaban vacías

`applyRecentNotes`/`applyFavoriteNotes` hacían `clear()` + `add()` en bucle sobre la lista observable enlazada a la `ListView`, así que en **cada recarga** (p. ej. tras cada autosave, que dispara `NoteSavedEvent` → el sidebar recarga recientes/favoritos) la lista se veía **vaciar y rellenar** (parpadeo). Más visible al editar transclusiones, porque uno se detiene en el popup de autocompletado de `![[`/`[[` y, durante esa pausa, salta el autosave. Ahora se usa `setAll(...)` (reemplazo atómico), que no parpadea.

### Feat: rich links (tarjetas de enlace)

Convierte una URL en una **tarjeta visual** (título, descripción, miniatura y dominio) en lugar de un enlace plano, al estilo de Glyphary/Obsidian.

- **Cómo se usa**: botón en la barra de formato del editor (icono marcador), comando **Insert Rich Link** en la paleta (`Ctrl+P`) o acción de menú. Pega una URL → se descarga su metadata en segundo plano (nunca bloquea la UI) → se inserta un bloque que se renderiza como tarjeta en la vista previa.
- **Formato en disco**: bloque de texto plano `::: rich-link` con `url/title/description/image/siteName`, legible y portable; solo `url` es obligatorio. Degrada a texto legible en cualquier otro editor.
- **Metadata**: `RichLinkService` descarga la página (timeout, User-Agent de navegador, tope de 512 KB) y extrae OpenGraph (`og:title/description/image/site_name`) con fallback a `<title>`, meta description y el host. Ante cualquier fallo inserta una tarjeta mínima (URL + dominio), sin lanzar excepción.
- **Enlaces externos**: al pulsar una tarjeta (o cualquier enlace `http(s)` del preview) se abre en el **navegador del sistema** vía el nuevo `SystemBrowser` (valida el esquema; usa `ProcessBuilder`, sin shell), en lugar de navegar dentro del WebView.
- **Arquitectura**: `util/RichLinks` (formato + parseo OpenGraph + render a tarjeta, puro y testeable), `service/RichLinkService` (única parte con red), `util/SystemBrowser`; integración en el pipeline de `MarkdownPreview` (antes de CommonMark, como los wiki-links) con CSS de tarjeta en ambos temas; acción `RICH_LINK` en `SystemActionEvent` cableada en editor, paleta y barra de formato.
- **i18n** EN/ES con paridad (`dialog.rich_link.*`, `tooltip.format.rich_link`).
- **Tests**: `RichLinksTest` (10) — OpenGraph + fallbacks, generación/round-trip del bloque, render a tarjeta, **escapado de HTML** e imágenes solo `http(s)` (seguridad); `SystemBrowserTest` (2) — rechazo de esquemas no `http(s)`. 228/228 verdes.

### Fix: los enlaces de la vista previa no abrían (bug latente)

Al pulsar un wiki-link o un enlace externo en la vista previa no ocurría nada. Causa (preexistente, no introducida por los rich links): el puente JS→Java `window.javaApp` se exponía desde una clase `private`, y JavaFX WebView **solo puede invocar métodos de una clase `public`** desde JavaScript, así que las llamadas fallaban en silencio. Se hace `public` la clase `PreviewJavaBridge`. Ahora los wiki-links abren la nota y los enlaces `http(s)` (incluidas las tarjetas rich-link) abren en el navegador del sistema.

### Fix: texto ilegible en la fila seleccionada de la paleta de comandos / quick switcher

La fila seleccionada usa texto blanco sobre fondo de acento, pero los manejadores de *hover* instalados cuando la fila no estaba seleccionada **no se limpiaban** al seleccionarse; al pasar el ratón por encima cambiaban el fondo al de hover (gris muy claro en tema claro) dejando el texto blanco → invisible. Se limpian los manejadores de ratón en el estado seleccionado, en `CommandPalette` y `QuickSwitcher`.

### Feat: snippets CSS por usuario (estilo Obsidian)

Permite retocar la interfaz con pequeños ficheros `.css` propios que se superponen al tema activo, sin tener que crear un tema completo. Funciona con temas integrados y externos.

- **Cómo funciona**: coloca ficheros `.css` en la carpeta `snippets/` (en `<appData>/snippets`, creada al arrancar) y actívalos desde **Preferencias → Snippets CSS**. Cada snippet activo se añade a la escena **después** del tema, así que sus reglas tienen prioridad. La activación se guarda en preferencias y se reaplica al arrancar.
- **UI**: la sección en Preferencias lista los snippets disponibles con casillas, más botones **Abrir carpeta** y **Recargar** (re-escanea sin cerrar el diálogo, conservando tu selección).
- **Seguridad**: el nombre de un snippet debe ser un fichero `.css` simple (sin separadores de ruta ni `..`), de modo que nunca puede apuntar fuera de su carpeta. Un snippet activado que ya no exista se ignora sin romper el arranque.
- **Arquitectura**: nuevo `CssSnippetCatalog` (descubre/valida/resuelve, espeja `ThemeCatalog`); `ThemeCommand.applyTheme` añade los snippets tras el tema y los limpia al reaplicar (rutas que contienen `/snippets/`); persistencia vía `UiPreferencesStore` (`ui.snippets.enabled`); carpeta `snippets/` creada por `AppDataDirectory`.
- **Snippets adaptables al tema**: Jylos marca el root de la escena con la clase `theme-dark` / `theme-light` (igual que las clases de `body` de Obsidian), de modo que un único snippet puede servir para claro y oscuro vía `.root.theme-dark { … }` / `.root.theme-light { … }`.
- **Ejemplos incluidos**: nueva carpeta `snippets-examples/` con tres snippets **adaptables** (cada uno con variante clara y oscura) — **Atom One**, **Nord** y **Solarized** — más un README con la guía de instalación y la lista de variables para crear los propios. (La carpeta runtime `jylos/snippets/` sí está en `.gitignore`, como `data/`/`logs/`: contiene los snippets activos del usuario; los ejemplos versionados viven en `snippets-examples/`.)
- **i18n** EN/ES con paridad (`dialog.preferences.css_snippets*`).
- **Tests**: `CssSnippetCatalogTest` (6 casos) — validación de nombres (incluye intentos de traversal), escaneo/orden/deduplicación y resolución de activos saltando los ausentes. 216/216 verdes.

### Fix: texto de checkboxes invisible en modo oscuro

Las etiquetas de los `CheckBox`/`RadioButton` no son nodos `.label`, así que sin un color explícito su texto caía al valor por defecto de la plataforma (oscuro) y resultaba **invisible sobre el fondo oscuro** — visible, p. ej., en el diálogo de Preferencias. Se añade una regla global en el tema oscuro (`.check-box, .radio-button { -fx-text-fill: -fx-text-main }`). El tema claro no se ve afectado (su texto por defecto ya contrasta).

### Fix: contador de columnas Kanban ilegible (negro sobre acento)

El badge con el número de tarjetas (`.kanban-lane-count`) se pintaba con texto oscuro sobre la píldora de color de acento en todos los temas. Causa: `.kanban-lane-count` y la regla global `.label` tienen la **misma especificidad** y, al ir `.label` después en la hoja, ganaba y forzaba su color de texto. Se sube la especificidad del badge a `.label.kanban-lane-count` en ambos temas, de modo que recupera `-fx-accent-contrast` (texto sobre acento).

## [2.1.0] - 2026-06-14

### Versión 2.1.0

Se sube la versión a **2.1.0**, recogiendo las funcionalidades añadidas desde la 2.0.0 (espacios de trabajo, búsqueda avanzada, panel de sincronización Git, Knowledge Insights, Kanban multilínea) que ya estaban marcadas como `@since 2.1.0` en el código pero seguían reportándose como 2.0.0. Actualizado en `app.properties`, `AppConfig`, `pom.xml` y los README.

### Refactor: auditoría de mantenimiento de la UI (2026-06-14)

Pasada de mantenimiento y eficiencia centrada en la capa UI/JavaFX, conservadora y sin cambios de comportamiento:

- **Lógica de plantillas fuera del controlador**: la sustitución de placeholders (`{{title}}`, `{{date}}`, `{{time}}`, `{{datetime}}`) vivía en `MainController`. Se extrae a `util/NoteTemplates` (función pura, con una sobrecarga que acepta el reloj para poder testearla) y se añade `NoteTemplatesTest` (7 casos). El controlador queda más pequeño y la lógica es ahora unit-testable.
- **Duplicado eliminado**: `MainController` reimplementaba un `findNoteByTitle` (búsqueda lineal por título) que ya existía en `NoteService`. Se elimina el privado y se usa el del servicio — una sola fuente de verdad.
- **Código muerto**: se elimina `MarkdownProcessor.containsMarkdown`, sin llamadas en todo el proyecto.
- **Verificado en la auditoría (sin cambios necesarios)**: el pipeline del preview (WebView) ya está bien acotado — assets (highlight.js, KaTeX, CSS) cacheados estáticamente una sola vez, KaTeX y emoji inyectados solo cuando la nota los usa, render del editor con debounce (120 ms) e índice de títulos caliente (`NoteTitleIndex`) sin reescaneos por tecla; el visor PDF cierra el `PDDocument` (try-with-resources) y rasteriza fuera del hilo FX; los componentes efímeros (paleta de comandos, quick switcher) se crean una vez y se cachean, y los suscriptores del `EventBus` son controladores de vida-aplicación (sin fugas). 210/210 tests verdes.

### Fix: icono de nota fijada — chincheta real (2026-06-14)

El icono de "nota fijada" era `fth-map-pin` (el marcador de ubicación de Google Maps), tanto en la lista de notas como en el botón de la barra del editor. Se reemplaza por `bi-pin-angle` (Bootstrap Icons), que es la chincheta diagonal clásica. Se añade `ikonli-bootstrapicons-pack:12.3.1` como dependencia Maven — mismo lenguaje visual que Feather (trazo outline, 2 px, minimalista), mismo proveedor (Ikonli). La clase CSS `.feather-pin-active` y la lógica de color/estado no cambian.

### Cambio: Git — un único panel consolidado (2026-06-14)

Los segmentos "cambios pendientes" y "commit" de la barra de estado abrían diálogos propios que **duplicaban** lo que ya hacía el panel Git Sync (estado + cambios + commit + pull/push/sync + log). Ahora ambos abren el panel consolidado. Eliminado el código muerto resultante en `GitController` (`showChangesDialog`, `showCommitDialog`, `reloadChangeSections`, `formatChangeStats`, la celda `GitChangeCell` y sus imports) y 8 claves i18n huérfanas. El historial de commits sigue siendo un diálogo aparte.

Pulida también la cabecera del panel: rama (en negrita) y resumen de cambios en la primera línea, y la URL del remote en una línea propia con elipsis (antes mezclaba tres tamaños de fuente en una sola fila y la URL larga desbordaba). El resumen deja de pintarse en verde (color reservado para líneas añadidas) y pasa a un tono neutro — nuevas clases CSS `git-status-remote` / `git-status-summary` en ambos temas.

### Fix: ubicación de almacenamiento real en Preferencias (2026-06-14)

El diálogo de Preferencias mostraba una ruta de BD **hardcodeada** (`jylos/data/database.db`) tanto en la etiqueta como en el valor, ignorando el modo real. Ahora muestra la ubicación real: la carpeta del vault en modo filesystem, o el fichero SQLite con ruta absoluta en modo base de datos. Clave renombrada `dialog.preferences.db_location` → `dialog.preferences.storage_location` (sin ruta horneada).

### Fix: coherencia de interfaz — i18n y placeholders (2026-06-14)

Auditoría de la UI buscando disonancias (strings hardcodeados, placeholders mal asignados, mezcla de idiomas):

- **Filtro de carpetas del sidebar**: usaba por error el placeholder del buscador de notas (`app.search.placeholder`, *"prueba tag:java modified:last-week"*), que anunciaba operadores de búsqueda que no aplican a un filtro de nombres de carpeta. Nueva clave `tab.folders.filter` ("Filtrar carpetas…"), coherente con los otros filtros del sidebar.
- **Command Palette y Quick Switcher**: textos en inglés hardcodeados (placeholders y pistas de teclado) migrados a i18n (`palette.*`, `switcher.*`) en los tres bundles.
- **Diálogo "Acerca de"**: la versión estaba horneada en la cadena traducible (`about.app_name=Jylos v2.0.0`) y duplicaba la etiqueta de versión. Ahora `about.app_name=Jylos` y la versión sale de fuente única (`AppConfig`) vía `about.version=Version {0}`.
- **Verificado (sin cambios necesarios)**: las 674 claves i18n tienen paridad en los 3 bundles, los 118 handlers de FXML existen, todas las `%clave` resuelven, y los operadores de búsqueda anunciados en el tooltip están todos implementados.
- Eliminado un fichero basura `sh` (0 bytes) de la raíz del repo.

### Fix: arranque instantáneo en vaults grandes / iCloud — carga de contenido en segundo plano (2026-06-14)

El arranque se quedaba colgado varios minutos con un vault Markdown en iCloud. Causa (preexistente, no introducida por los cambios recientes; verificado por `git diff`): el constructor de `NoteDAOFileSystem` hacía `refreshCache()`, que lee una cabecera de 16 KB de **cada** fichero del vault en el hilo de init. En iCloud con "Optimizar almacenamiento", leer cualquier byte de un fichero desalojado fuerza su descarga completa → el barrido entero bloqueaba el arranque.

- **Carga diferida (filesystem)**: el arranque construye una caché **solo de metadatos** (título desde el nombre de fichero + timestamps; sin leer contenido → sin descargas), así que la lista de notas aparece al instante y la app es usable de inmediato. El contenido, tags y enlaces se cargan en un **hilo daemon en segundo plano** con un pool acotado (≤32, I/O-bound), **sin retener el lock global** — una operación de la UI (abrir nota, pulsar carpeta) durante la carga no se bloquea.
- **Refresco al terminar**: el DAO notifica vía `setOnContentLoaded` (nuevo en la interfaz `NoteDAO`, no-op por defecto); `MainController` repinta lista + tags y emite `NotesRefreshRequestedEvent` (sidebar, grafo, índices) en el hilo FX.
- **Sin cambio de comportamiento donde importa**: el constructor de un argumento sigue cargando **síncrono y completo** (back-compat para tests y llamadas que necesitan la caché poblada al volver). Solo `FactoryDAOFileSystem` (única construcción en prod) activa el modo diferido. SQLite no se ve afectado (default no-op).
- **Mejora de concurrencia**: el barrido de contenido usa un pool dedicado y dimensionado para I/O en lugar del `ForkJoinPool` común (que el `.parallel()` saturaba con descargas bloqueantes).
- **Espacio en disco**: se eliminó un `app.log` obsoleto de 2 GB (resto de la tormenta de `SEVERE` previa) que tenía el disco al 99% y hacía fallar incluso las escrituras.
- **Tests:** `NoteDAOFileSystemTest` +1 (modo diferido: la nota es listable por título de inmediato y el contenido/enlaces aparecen tras `awaitContentLoaded`). 203/203 tests verdes.

### Fix: rendimiento de backlinks en vaults grandes / iCloud — sin tormenta de timeouts (2026-06-14)

En vaults Markdown alojados en iCloud con "Optimizar almacenamiento del Mac", leer **cualquier** byte de un fichero desalojado fuerza su descarga **completa** bajo demanda y bloquea hasta terminar (`java.io.IOException: Operation timed out`). El índice de backlinks releía el **contenido completo de cada nota** (un segundo `getNoteById` por nota, además de la cabecera ya leída al arrancar), provocando lentitud extrema y una avalancha de logs `SEVERE` con stack trace.

- **Sin segunda lectura por nota**: el DAO de filesystem extrae los enlaces salientes (`[[wiki]]` / `[label](note)`, misma semántica que `WikiLinkResolver`) **en el momento de leer** la nota y los cachea en `Note.linkTargets` (campo transitorio). `BacklinkService.ensureIndexed` reutiliza esos targets y **no hace I/O propio**. Se conserva el caché forward/inverse validado por `modified`.
- **Cobertura documentada**: en notas listadas (lectura *lightweight*) los targets cubren la cabecera indexada (~16 KB); una nota abierta o guardada (lectura completa) cubre todo el cuerpo. Es un trade-off deliberado para no releer cada fichero. `Note.setContent(...)` invalida el caché de targets, de modo que una edición en memoria fuerza re-derivación.
- **Modo SQLite intacto**: si una nota no trae targets precomputados, `BacklinkService` los deriva **en memoria** desde su contenido (sin lectura de fichero). El DAO SQLite ya carga el cuerpo completo, así que la cobertura es total.
- **Menos ruido de log (Task 3)**: un fallo de lectura en `NoteDAOFileSystem.getNoteById` pasa de `SEVERE` con stack trace a `WARNING` con mensaje (la nota se omite y el llamante maneja el `null`), evitando que un disco lento/offline o un fichero iCloud desalojado inunde el log.
- **Tests:** `NoteDAOFileSystemTest` +2 (lectura completa y *lightweight* pre-indexan los enlaces salientes). 202/202 tests verdes.

### Feat: Workspaces — guardar y restaurar contextos de trabajo (2026-06-13)

Permite guardar el estado de trabajo actual con un nombre y restaurarlo después (estilo VS Code/Obsidian, pero simple). Funciona en SQLite y vault Markdown; se persiste en la config local de Jylos, nunca dentro de las notas.

- **Acceso**: `File → Workspaces` (Save Current / Save As… / Open… / Manage…) y command palette (`Workspace:`). Borrado desde *Manage*.
- **Qué guarda**: nombre + id + timestamps, **tabs abiertas** (ids, en orden) y **nota activa**, **modo de vista** (editor/split/preview), y **layout básico** (focus mode, sidebar visible, posiciones de los dos split panes) + modo de almacenamiento (para avisar de discrepancias).
- **Robustez**: restaurar es **aditivo** (reabre las notas del workspace y activa la guardada; no cierra tus tabs actuales → sin pérdida de trabajo). Notas inexistentes → se omiten con aviso no bloqueante. Discrepancia de almacenamiento → aviso. Workspace vacío permitido. Líneas corruptas en el fichero → se ignoran sin romper.
- **Arquitectura nueva** (paquete `workspace`): `Workspace` (record + `serialize()`/`parse()` con separadores de control, sin dependencia JSON), `WorkspaceRepository` (fichero `<appData>/data/workspaces.dat`, una línea por workspace), `WorkspaceService` (list/save/update/delete, upsert por nombre). UI en `ui/controller/WorkspaceController`; captura/restauración del estado vivo en `MainController` (`captureLiveWorkspace`/`applyWorkspace`). `EditorTabs.getOpenIds()` nuevo.
- **Fuera de v1** (documentado): filtros del grafo y selección/expansión del árbol de carpetas no se persisten todavía.
- **i18n** EN/ES con paridad (`workspace.*`, `menu.workspaces`, `action.workspace_*`).
- **Tests**: `WorkspaceServiceTest` (8) — round-trip de serialización, líneas corruptas, save/load, overwrite por nombre conservando id, update por id, delete, y resiliencia ante línea corrupta.

### Fix: GitService ahora fuerza locale C en los subprocesos git (2026-06-13)

`GitService` clasificaba el resultado de git buscando frases en inglés ("nothing to commit", "conflict", "rejected"…), pero en sistemas con locale no inglés git responde traducido (p. ej. "nada para hacer commit"), devolviendo `ERROR` en casos como *nothing to commit*. Ahora cada invocación fija `LC_ALL=C`/`LANG=C`, de modo que los mensajes son estables en inglés y la clasificación funciona en cualquier idioma del sistema.

### Feat: búsqueda avanzada con operadores (2026-06-13)

Mejora incremental sobre la búsqueda full-text existente: si escribes texto normal funciona igual que siempre; si usas operadores, se aplican filtros (AND). Funciona en SQLite y vault Markdown, fuera del hilo de UI.

- **Operadores** (estilo Gmail/Obsidian): texto libre, `"frases exactas"`, `tag:`, `folder:`, `title:`, `body:`, `created:`, `modified:`, `favorite:true|false`, `private:`/`encrypted:`, `has:tag|links|backlinks`, `is:orphan`, y negación con prefijo `-` (`-tag:archive`). Fechas: `today`, `yesterday`, `last-week`, `last-month`, `YYYY`, `YYYY-MM`, `YYYY-MM-DD`.
- **Parser robusto y predecible** (`SearchQueryParser`): respeta comillas, tolera espacios extra, **nunca lanza excepción**. Operador desconocido → se busca como texto literal con aviso; valor inválido (fecha/booleano/`has`/`is`) → se descarta con aviso y el resto de la consulta sigue.
- **Arquitectura nueva** (paquete `search`): `SearchQueryParser`, `SearchQuery`, `SearchFilter`, `SearchResult`, `AdvancedSearchService`, `SearchDates`. El servicio reutiliza `TagService` y `GraphAnalysisService` (orphan/links/backlinks) — **sin segunda lógica de resolución de enlaces**. Metadata cara computada perezosamente y solo cuando la consulta la usa; degradación a "no match" si no está disponible, sin romper.
- **Integración no invasiva**: `NotesListController.performSearch` delega en `AdvancedSearchService` reutilizando el caché de contenido completo; el render de resultados (lista actual) no cambia. Si el servicio no está disponible, cae al filtrado simple anterior.
- **UI**: placeholder con ejemplo (`Search notes… try tag:java modified:last-week`) y tooltip «Search syntax» en la caja de búsqueda. i18n EN/ES con paridad.
- **Tests:** `SearchQueryParserTest` (13) y `AdvancedSearchServiceTest` (8) — texto libre, frases, tag/folder/title/body, negación, fechas, combinación AND y consultas inválidas. 192/192 tests verdes.
- **Doc:** nuevo `docs/SEARCH.md` con la sección "Advanced Search Syntax".

### Feat: Graph 2.0 + Knowledge Insights (2026-06-13)

Convierte el grafo de conocimiento en una herramienta analítica, sin lógica de enlaces paralela: todo reutiliza `GraphBuilder` + `WikiLinkResolver`. Funciona en SQLite y vault Markdown.

- **Panel «Knowledge Insights»** (`View → Knowledge Insights`, atajo `Ctrl/Cmd+Shift+K`, y command palette): diálogo tabulado de solo lectura con resumen (notas, enlaces, backlinks, tags, media de enlaces/nota), **notas más conectadas** (top 10), **notas huérfanas**, **enlaces rotos**, **notas sin tags** y **uso de tags**. Filas clicables (doble clic abre la nota; en un enlace roto abre la nota origen). El cómputo corre fuera del hilo de JavaFX.
- **Graph health score** (`KnowledgeInsightsService.healthScore`): puntuación simple y explicable 0–100 — parte de 100, resta hasta 40 por proporción de huérfanas, hasta 20 por sin-tags y 5 por enlace roto (tope 25), con el desglose visible. Orientativo, no absoluto.
- **Filtros del grafo** en el panel de ajustes: **filtrar por texto**, **por tag** y **por carpeta**, además de los toggles existentes de huérfanas/no resueltas/tags. El filtrado re-renderiza desde el `GraphData` ya construido (cacheado) — **sin reconstrucción pesada del modelo**.
- **Arquitectura nueva** (paquete `insights`): `GraphAnalysisService` (análisis estructural puro y testeable vía `analyze(GraphData)`), `KnowledgeInsightsService`, y DTOs `KnowledgeHealthReport` / `NoteConnectivityInfo` / `BrokenLinkInfo`. UI en `ui/components/KnowledgeInsightsPanel`; filtros en `GraphController`.
- **Definiciones** documentadas: *enlace resuelto* (ambos extremos existen), *enlace roto* (a nota inexistente / nodo ghost), *huérfana* (sin enlaces resueltos). 
- **i18n** EN/ES con paridad (`insights.*`, `graph.filter_*`, `action.knowledge_insights`) y CSS del panel en ambos temas. Acción `KNOWLEDGE_INSIGHTS` + comando `cmd.knowledge_insights`.
- **Tests:** nuevo `KnowledgeInsightsTest` (6 casos: huérfanas, enlaces rotos, ranking de conectividad, health score con clamp/tope y vault vacío). 171/171 tests verdes.
- **Doc:** nuevo `docs/GRAPH.md`.

### Feat: panel «Git Sync» de primera clase para vaults Markdown (2026-06-13)

Filosofía: *tus notas, tu repositorio, tu control*. Sin sincronización en la nube, sin backend, sin cuentas — solo Git, gestionado visualmente. Disponible **solo en modo Markdown vault** (no afecta a SQLite).

- **Nuevo panel consolidado** (`ui/components/GitSyncPanel.java`): una única ventana estilo IDE con estado del repositorio (rama, remoto, ↑adelante/↓atrás), lista unificada de cambios con prefijos `M / A / D / ?? / UU`, campo de mensaje de commit, registro de actividad y todas las operaciones seguras: *Refresh, Stage All, Unstage All, Commit, Pull, Push, Sync*. Toggle de stage/unstage por archivo en cada fila.
- **Acceso:** menú `Tools → Git → Panel de Git Sync…` (atajo `Ctrl/Cmd+Shift+G`) y command palette (`Git: Sync Panel`). Se abre vía `GIT_PANEL` (`SystemActionEvent`) → `GitController.showSyncPanel()`.
- **Conflictos:** `GitService.listChanges` ahora detecta rutas sin fusionar (códigos `DD/AU/UD/UA/DU/AA/UU`), las marca como `conflicted`, **nunca** como staged, y el panel muestra un aviso para resolución manual. No se auto-resuelve ningún conflicto y no se hace force push jamás.
- **`GitService` extendido** (reutilizado, no duplicado): `stageAll` (`git add -A`), `unstageAll` (`git reset -q`) y detección de conflictos. Se conservan `GitStatus`/`GitChange`/`GitResult` existentes.
- **No bloquea la UI:** toda operación corre en un `Task` daemon fuera del hilo de JavaFX; mientras tanto los botones se deshabilitan y se muestra una barra de progreso indeterminada. Errores claros para Git ausente, sin repo, sin remoto, conflicto en pull, push rechazado y fallo de credenciales.
- **i18n** EN/ES con paridad de claves (`git.panel.*`, `action.git_panel`) y CSS en ambos temas (`.git-change-badge`, `.git-conflict-banner`, `.git-primary-btn`, `.git-log-area`…).
- **Tests:** `GitServiceTest` amplía a 8 casos — round-trip de `stageAll`/`unstageAll` y detección de un conflicto de merge real. 165/165 tests verdes.
- **Doc:** nuevo `docs/GIT.md`.

## [2.0.0] - 2026-06-13

Versión **major** por dos rupturas de compatibilidad: el requisito mínimo de Java sube de 17 a **21** (quien ejecute el JAR con Java 17 ya no puede; los instaladores empaquetan su propio runtime y no se ven afectados) y el **formato de datos** incorpora notas cifradas (`JENC1:`, columna `is_private` / frontmatter `private:`) que una v1.0.0 no puede leer. El resto del contenido de esta versión —pestañas, editor RichTextFX, cifrado, Kanban, importadores, historial, API de plugins, instaladores— es aditivo y está detallado en las entradas siguientes.

### Fix: pulido exhaustivo de UI — coherencia visual y limpieza (2026-06-13)

- **Favoritos activos visibles:** la clase `.feather-favorite-active` (estrella de favorito activo en la lista de notas) no estaba definida en ningún tema, así que el estado activo era invisible. Definida en ambos temas en ámbar (`-fx-warning`), con variante de contraste cuando la fila está seleccionada (claro → `-fx-accent-contrast`, oscuro → `-fx-selected-text`).
- **Diálogo de Preferencias recortado:** el contenido creció (fila de acento) pero el tamaño era fijo (480×520) y se recortaba la parte inferior. Ahora el contenido va en un `ScrollPane` transparente (`.preferences-scroll`, solo barra vertical y solo si hace falta) y el diálogo pasa a 500×620.
- **i18n en tabs del editor:** el tooltip del botón × de cada pestaña era «Close» hardcodeado; ahora usa la clave `tooltip.close_note` (EN/ES) vía el i18n inyectado en `EditorTabs`.
- **Placeholder en inglés:** `wordCountLabel` arrancaba con `text="0 words"` en el FXML (visible un instante en locale español); ahora arranca vacío y lo rellena el contador i18n en cuanto hay nota.
- **CSS muerto eliminado:** `.path-close-btn` (el botón de cierre duplicado de la barra de ruta se eliminó hace tiempo) y `.kanban-card-preview` (del Kanban intermedio reemplazado), en ambos temas.
- **Claves i18n huérfanas eliminadas:** `kanban.more`, `kanban.new_card_title`, `kanban.no_status` (restos del Kanban por-status reemplazado por el modelo de tablero-en-nota), en los 3 bundles — paridad intacta (`I18nBundleFallbackGuardTest`).
- **Auditoría sin hallazgos pendientes:** cobertura de `:hover` completa en ambos temas para todas las clases interactivas; todos los diálogos pasan por `UiDialogs.show()` (tematizado consistente); las únicas styleClass sin CSS son contenedores de layout intencionadamente transparentes (`graph-canvas-holder`, `stacked-header`). Verificado: 163/163 tests, uber-JAR y arranque sin errores (JDK 21).

### Fix: scripts de empaquetado Windows en PowerShell 5.1 (2026-06-12)

- **`scripts/package-windows.ps1`**: sustituidos guiones largos Unicode (`—`) y separadores decorativos (`──`) por ASCII. Sin BOM, PowerShell 5.1 leía el UTF-8 como Windows-1252 y el byte `0x94` del em dash se interpretaba como comilla tipográfica, rompiendo el parseo (`MissingEndCurlyBrace` en las líneas 111–115). Los wrappers `package-windows-exe.ps1` y `package-windows-msi.ps1` heredaban el fallo.

### Fix: auto-deteccion JDK 21 y WiX embebido en empaquetado Windows (2026-06-12)

- **`scripts/package-windows.ps1`**: elige JDK 21+ aunque `java` en PATH sea 17; usa `.tools/wix314` si existe.
- **`scripts/setup-packaging-windows.ps1`**: setup one-shot (winget JDK 21 + descarga WiX 3.14 sin admin).
- **`docs/PACKAGING.md`**: documentado el flujo de setup.

### Feat: instaladores Windows, API de plugins, importadores, historial, Kanban+ y firma macOS (2026-06-08)

- **Instaladores Windows (.exe / .msi):** `package-windows.ps1` es ahora el núcleo parametrizado (`-Type portable|exe|msi`; portable por defecto) con dos wrappers `package-windows-exe.ps1` / `package-windows-msi.ps1`. Ambos instaladores requieren **WiX Toolset** (lo usa jpackage), incluyen selector de directorio, grupo de menú Inicio, acceso directo, página de licencia MIT y un **UUID de upgrade estable** (las versiones nuevas actualizan en vez de duplicarse). Documentado en `docs/PACKAGING.md`. *(No ejecutables en macOS — pendientes de prueba en Windows.)*
- **Fase 5a — API de plugins ampliada:** nuevos `EditorHook`/`EditorHooks` (dispatcher con cadena en orden de registro, hooks que lanzan se saltan) con `onBeforeTextInsert` (inserciones programáticas: diálogos de enlace/imagen, autocompletado `[[`, plantillas), `onBeforeSave` (transforma el contenido antes de persistir, editor sincronizado) y `onAfterSave`; y `ToolbarRegistry` → `PluginContext.registerToolbarButton(...)` (botones Feather en el toolbar, limpieza automática al deshabilitar). `WordCountPlugin` de ejemplo usa el botón. Tests `EditorHooksTest` + lifecycle ampliado. API documentada en `docs/PLUGINS.md`.
- **Importadores (menú Archivo):** **bóveda Obsidian** (jerarquía de carpetas recreada, título = nombre de fichero salvo `title:` explícito en frontmatter, cuerpo sin frontmatter intacto, etiquetas preservadas, `.obsidian/.trash` omitidos) y **Evernote `.enex`** (parser XML endurecido sin entidades externas; ENML→Markdown vía `EnexConverter`: negrita/cursiva/código, encabezados, listas, enlaces, checkboxes; etiquetas conservadas; adjuntos como placeholder). Importación aditiva y tolerante a fallos por nota (resumen + diálogo de errores). `ImportService` + `ImportSupport`; tests `ImportServiceTest`.
- **Historial de versiones de nota:** `NoteHistoryService` guarda un snapshot del contenido **tal como estaba almacenado** antes de cada `updateNote` (las notas privadas se historian **cifradas**), con ventana de coalescencia de 60 s (el autoguardado no genera ruido) y tope de 50 snapshots/nota con poda. Visor (Herramientas → Historial, `Ctrl/Cmd+Shift+H`): lista de versiones + **diff por líneas** (`LineDiff`, LCS con fallback prefijo/sufijo para notas enormes) + **restaurar** (el estado previo a la restauración se snapshotea, así que es reversible). Tests `NoteHistoryServiceTest`.
- **Kanban:** **límites WIP** y **colores por columna** como anotaciones serializables en el encabezado (`## Doing [wip=3] [color=#e06c75]`), configurables desde el menú ⋯ de columna (badge rojo al superar el límite; franja de color en la columna); **miniaturas** en tarjetas que referencian imágenes o PDFs (primera página vía PDFBox), resueltas en absoluto o relativo al fichero del tablero. Tests de round-trip en `KanbanModelTest`.
- **macOS firmado/notarizado (opt-in):** `package-macos.sh` firma con jpackage si `JYLOS_MAC_SIGN_IDENTITY` está definida (certificado *Developer ID Application*) y notariza+staplea el DMG si además `JYLOS_NOTARY_PROFILE` apunta a un perfil de `notarytool`; sin variables, build local sin firmar como siempre. Pasos de configuración en `docs/PACKAGING.md`.
### Docs: instalar temas externos tras el instalador (2026-06-12)

- **`themes/README.md`**: rutas por SO (macOS/Windows/Linux), copiar `themes/<id>/` al AppData, script `--appdata` y activación en Preferencias.
- **`README.md`**, **`README.es.md`**, **`docs/PACKAGING.md`**: enlace/resumen; los instaladores no incluyen temas.

### Feat: color de acento personalizable + tamaño de texto persistente (2026-06-08)

- **Color de acento (estilo Obsidian):** en **Preferencias** hay ahora un check «Color de acento personalizado» + selector de color. Se aplica como override `-fx-accent`/`-fx-accent-hover`/`-fx-selected-bg` en línea sobre la raíz de la escena, así que recolorea selección/foco/toggles en los temas integrados **y externos**; desactivado, cada tema usa su acento por defecto (morado en claro/oscuro, verde fósforo en Retro). Valor validado (`#rrggbb` o nada) — `UiPreferencesStoreTest`.
- **Tamaño de texto:** el zoom de interfaz (`Ctrl/Cmd +/−/0`) ahora **persiste** entre sesiones (antes solo persistía si pasabas por Preferencias); Preferencias y zoom comparten la misma preferencia.
- **Temas externos verificados:** `jylos/themes/retro-phosphor` instalado y sincronizado con el source; `base=dark` apila la hoja integrada + overlay correctamente (tests `ThemeCatalogTest`/`ThemeCommandTest` verdes).
### Fix: botón de cerrar nota duplicado (2026-06-08)

- Con las pestañas, había **dos botones de cerrar** la nota: la × de la pestaña y otra × en la barra de ruta de la nota. Se elimina la de la barra de ruta (`closeNoteBtn` + su handler en `EditorController`); cerrar se hace desde la pestaña (×), que es el sitio estándar. Sin pérdida de funcionalidad.
### Fix: coherencia de temas claro/oscuro (2026-06-08)

- **Tema oscuro:** foco de campos de texto, estado seleccionado de toggles y el icono de toggle seleccionado usaban un morado distinto (`#9f7aea`, el de `-fx-folder-all`) en lugar del acento del tema (`-fx-accent`, `#7c3aed`). Unificado a `-fx-accent`/`-fx-accent-contrast`, igual que el tema claro — fin de los "dos morados".
- **Auditoría:** verificada la **paridad de variables** de diseño entre ambos temas (sin variables huérfanas) y revisadas las asimetrías de selectores (el resto eran equivalencias `:filled:hover`/`:hover`, `.toolbar-btn-primary`/`.toolbar-btn.toolbar-btn-primary`, etc. — ambos temas cubren los mismos componentes). Los componentes nuevos (pestañas, Kanban, indicador de guardado) usan solo variables de tema. Sin errores de CSS al arrancar.
### Docs: documentación al día con las funcionalidades reales (2026-06-08)

- **READMEs (EN/ES):** nueva sección **«Why Jylos / Por qué Jylos»** — honesta, sin marketing: inspirada en Obsidian (el autor es fan), pero **no es un clon, alternativa ni competidor**; app independiente, local-first, offline, MIT, para la comunidad. Reflejadas las funcionalidades nuevas (editor con resaltado RichTextFX, pestañas, indicador de guardado, modo concentración, **Kanban**, **notas privadas/cifrado**, persistencia de splits) y stack (RichTextFX, PDFBox).
- **`docs/ARCHITECTURE.md`:** editor `CodeArea` (no `TextArea`), `EditorTabs`, overlays grafo+Kanban (`OverlaySupport`), modo focus, `KanbanModel`, cifrado (`EncryptionService`), columnas `status`/`is_private` + migraciones idempotentes, y el patrón «feature support» de `MainController` con los nuevos controladores.
- **`docs/EVENT_BUS_CONTRACT.md`:** acciones nuevas (`KANBAN_VIEW`, `FOCUS_MODE`, `PRIVATE_TOGGLE`/`NOTES_LOCK`) y delegación a los support.
### Refactor: adelgazar MainController (patrón "feature support") (2026-06-08)

- **`MainController` 3558 → 2822 líneas (−736, −21%)** extrayendo responsabilidades a clases dedicadas con `wire(...)` + callbacks (`i18n`, `status`, `scene`). Patrón documentado en `AGENTS.md`; los handlers FXML quedan como delegadores finos.
  - **`GitController`** — barra de estado Git + operaciones + diálogos (commit/cambios/historial). ~450 líneas fuera de MainController.
  - **`PrivacySupport`** — prompts de contraseña maestra (setup/unlock) y errores de las notas cifradas.
  - **`FocusModeSupport`** — modo concentración (estado + entrar/salir).
  - **`OverlaySupport`** — overlays de `centerStack` (grafo + Kanban): toggle/show/hide mutuamente excluyentes; posee el `KanbanBoard` lazy.
  - **`StatusBarSupport`** — contadores palabras/caracteres + indicador de almacenamiento.
  - **`BacklinksSupport`** — sección de backlinks del panel derecho (cálculo off-thread + render).
- Sin cambios funcionales; suite 149/149. Lo que queda en MainController es su núcleo legítimo de coordinación (flujo de nota: abrir/guardar/cerrar/pestañas/navegación) — extraerlo fragmentaría lógica cohesiva.
### Feat: Fase 4 — notas privadas cifradas (AES-256 con contraseña maestra) (2026-06-08)

- **Cifrado por nota** (no la bóveda entera, no solo SQLite). Marcas una nota como **privada** (`Tools → Hacer Nota Privada/Pública`, `Cmd/Ctrl+Shift+L`) y **solo su cuerpo** se cifra en reposo; las notas normales quedan en claro.
- **Contraseña maestra**: nuevo `service/EncryptionService` — clave AES-256 derivada con **PBKDF2-HMAC-SHA256** (210k iteraciones) sobre salt aleatorio; cifrado **AES-GCM** (IV por nota). La contraseña no se guarda: solo salt + un verificador. Estado de sesión **desbloqueado/bloqueado** (`Tools → Bloquear Notas Privadas`); al abrir una nota privada bloqueada se pide desbloquear.
- **Ambos modos de almacenamiento**: flag `Note.isPrivate` persistido como columna `is_private` en SQLite (migración idempotente) y `private:` en el frontmatter de la bóveda; el cuerpo cifrado se guarda como `JENC1:base64(iv‖ciphertext)`. En la bóveda el frontmatter (título/fechas) queda legible para listar la nota como 🔒 sin la clave.
- **Seguridad de datos**: `NoteService` cifra al guardar y descifra al leer; con la sesión bloqueada la lista muestra 🔒 (nunca el ciphertext) y **se bloquea el guardado** de una nota privada para no sobrescribir el cifrado con texto plano.
- Tests `EncryptionServiceTest` (round-trip, contraseña incorrecta, IV distintos, cifrar bloqueado falla). Suite 149/149.

### Chore: línea base Java 21 + JavaFX 23; Kanban de columnas fijas (2026-06-07)

- **JavaFX 21 → 23.0.2 y Java 17 → 21.** El crash nativo de CoreText en macOS persistía en JavaFX 21; se sube a JavaFX 23 (que trae correcciones del subsistema de fuentes). Como JavaFX 23 es bytecode Java 21, el proyecto pasa a compilar/ejecutar con **JDK 21** (núcleo `pom.xml` source/target 21; plugins `--release 21`; launchers detectan la versión de JavaFX más alta, no solo 21.x). Badges/docs/i18n actualizados a Java 21 / JavaFX 23. **Requiere JDK 21 para compilar y ejecutar.**
- **Kanban — modelo Obsidian/Trello (rediseño):** un **tablero es una nota** cuyo cuerpo es Markdown (`## columna`, `- tarjeta`), identificada por un marcador oculto `%% jylos-kanban %%` (sin cambios de esquema; funciona en SQLite y bóveda). La vista tiene **selector de tableros** + «Nuevo tablero»; permite **añadir/renombrar/borrar columnas**, **crear/editar/borrar tarjetas de texto**, **arrastrarlas entre columnas**, y por tarjeta: abrir nota enlazada (`[[Título]]`) o **convertir en nota**. Parse/serialize en `util/KanbanModel` (tests `KanbanModelTest`). Toolbar idéntica a la del grafo (mismas clases y padding). Fondo oscuro corregido.
  - El anterior modelo «agrupar todas las notas por `status`» se retira. La columna `status` añadida antes queda **sin uso** (nullable, inofensiva; se puede retirar en una migración futura).

### Feat: Fase 3 UI/UX — focus, Kanban, split persistente, tema claro (2026-06-07)

- **Vista Kanban (F3.2):** nuevo `ui/components/KanbanBoard` como overlay (menú Ver → Tablero Kanban, `Cmd/Ctrl+Shift+K`). Columnas por la propiedad `status` (todo/doing/done + las que existan + «sin estado»); arrastrar una tarjeta entre columnas cambia su `status` y persiste; clic abre la nota. Barra de herramientas tipo grafo con **botón de cierre** (y **Escape**), **scroll horizontal** cuando hay muchas columnas, y **botón «+ Nueva tarjeta»** por columna que crea una nota con ese estado.
  - **Persistencia de `status` (ambos modos):** nuevo campo `Note.status`, columna `status` en SQLite (migración idempotente `ALTER TABLE` en `SQLiteDB.initDatabase`) y clave `status:` en el frontmatter de la bóveda. Tests `NoteStatusPersistenceTest`.
- **Modo concentración (F3.1):** `Cmd/Ctrl+Shift+F` oculta sidebar, lista, panel derecho, toolbar y barra de estado dejando solo el editor; restaura el layout previo (respeta paneles ya colapsados). Nueva acción `FOCUS_MODE`.
- **Split panes persistentes (F3.4):** se recuerdan y restauran las proporciones del divisor sidebar|contenido y lista|editor entre sesiones (`UiPreferencesStore`). El divisor editor/preview lo sigue gobernando el modo de vista (50/50).
- **Tema claro mejorado (F3.5):** texto atenuado/tenue ahora cumple contraste WCAG AA, y sidebar/cabecera/bordes mejor diferenciados.
- **Drag & drop notas→carpeta (F3.3):** verificado que ya funcionaba desde lista y cuadrícula hacia el árbol de carpetas; sin cambios.

### Fix: crash de cuadrícula (CoreText) y codificación de «Acerca de» (2026-06-07)

- **Crash de JavaFX en macOS** al renderizar glifos en la cuadrícula (`CTFontCopyTable`, pre-existente): se sube **JavaFX 21 → 21.0.7** y se corrige la detección de versión en los launchers (`launch-jylos.sh`, `run_all.sh`, `get-javafx-module-path.sh`, `launch-jylos.bat`) para usar el módulo más reciente en runtime (antes fijaban 21.0.0).
- **«Acerca de» con acentos rotos:** `AppConfig` leía `app.properties` (UTF-8) como ISO-8859-1; ahora se lee con `InputStreamReader` UTF-8 → «Copyright © 2026 Eduardo Díaz Sánchez» correcto.

### Feat: editor con resaltado de sintaxis Markdown (RichTextFX) (2026-06-06)

- El editor pasa de `TextArea` a **`CodeArea` de RichTextFX** (`org.fxmisc.richtext:richtextfx:0.11.5`, empaquetado en el uber-jar). Resaltado **completo** en el propio editor: encabezados, **negrita**/*cursiva*, `código` inline y en bloque, `[[wiki-links]]`, enlaces `[texto](url)`, listas, citas y tachado. Computado por `util/MarkdownHighlighter` y aplicado con debounce (200 ms) para no recalcular en cada tecla.
- Migración contenida en `EditorController` (API equivalente: `replaceText`, `moveTo`, `getCaretBounds`); el autocompletado de `[[`, los botones de formato, find/replace y copiar/cortar/pegar/deshacer siguen funcionando. Estilos `.md-*` y de caret/selección en tema claro y oscuro.
- Nuevos guard tests en `UiPresentationFxmlGuardTest` (CodeArea, tab bar, indicador de guardado presentes en el FXML).

### Feat: pestañas de notas e indicador de guardado inline (2026-06-06)

- **Pestañas (tabs):** nuevo componente `ui/components/EditorTabs` — una pestaña por nota abierta con título, dot de cambios sin guardar y botón de cerrar. `MainController` es la única fuente de verdad: abre/activa pestañas al cargar una nota, cierra la pestaña activa con `Ctrl+W`/botón (con confirmación de guardado), y al cerrar activa la pestaña vecina o vacía el editor. Un único editor/WebView compartido (se intercambia el contenido al cambiar de pestaña). Las pestañas se ocultan cuando no hay notas abiertas.
- **Indicador de guardado inline:** dot junto al título — **ámbar** con cambios sin guardar, **verde** cuando está guardado, oculto sin nota. Sincronizado con el estado `isModified` del editor y con cada pestaña. Nuevas claves i18n `tooltip.unsaved_changes` / `tooltip.saved` (EN/ES, con paridad verificada por `I18nBundleFallbackGuardTest`).

### Perf: render de preview, backlinks y grafo sin recomputar todo (2026-06-06)

- **P1 — Preview:** `MarkdownPreview.buildPreviewHtml` ya no llama `getAllNotes()` en cada render (cada tecla al editar). El set de títulos para resolver `[[wiki-links]]` vive ahora en `service/NoteTitleIndex` (caché caliente invalidada por `NoteCreated/Deleted/Saved/Updated/NotesRefresh`). Lectura O(1) en la ruta de render.
- **P2 — Backlinks:** `BacklinkService` mantiene un índice **bidireccional** (forward `noteId→targets` + inverso `título→ids`), de modo que `backlinksFor` hace lookup O(1) en vez de un `contains()` sobre todas las notas. Invalidación incremental por eventos de nota (solo la nota cambiada se vuelve a leer, y de forma perezosa fuera del hilo de FX).
- **P3 — Grafo:** `GraphBuilder` expone `invalidateNote(id)`/`invalidateAll()` y su caché de enlaces es concurrente. `GraphController` se suscribe a eventos de nota/carpeta: invalida solo la nota cambiada y **refresca el grafo en vivo si está abierto** (re-lee únicamente esa nota, no toda la bóveda). Nuevos tests `GraphBuilderIncrementalTest`.

### Fix: icono negro en «Crear nota» con tema Retro (2026-06-04)

- `themes/retro-phosphor/theme.css`: botones del estado vacío (Crear nota / Ir a archivo) con texto e iconos en `-fx-accent` verde fosforo, sin relleno oscuro del tema integrado.

### Fix: tema externo en Ajustes no se aplicaba (2026-06-04)

- `ThemeCommand.effectiveThemeSource`: la preferencia **Temas externos** ya no la anula el menú Vista → Sistema.
- Al guardar Ajustes se llama `applyThemeAndRefreshDependents`; si el modo es externo sin tema elegido, se usa el primer tema externo disponible.

### Fix: tema Retro Phosphor alineado con la UI actual (2026-06-04)

- **Temas externos**: `ThemeCommand.applyTheme` apila la hoja built-in (`base=dark|light|…`) y luego `theme.css` del tema.
- **`themes/retro-phosphor`**: `base=dark`, overlay con variables estándar de Jylos (nav lateral, grafo, git, plugins, adjuntos, tarjetas, editor).
- **`themes/README.md`** y plantilla `_template` documentan `base=`.
- **Cobertura ampliada (gaps de la UI actual):** se taparon las reglas de `dark-theme.css` con color *hardcodeado* que el overlay no pisaba y rompían el look fósforo — gris `#2d2d2d/#252525/#404040` y el acento **morado `#9f7aea`** en: campos de texto (foco), combos, botones de diálogo, *toggle-buttons* (estado seleccionado), tooltips, separadores (incl. divisor del modal de Git), **panel derecho** (cabecera/secciones/cerrar) y barra de formato del editor (botón de resaltado → ámbar). Verificado: el overlay parsea sobre `dark-theme.css` con **0 errores/0 lookups sin resolver** y render visual confirmado.

### Docs: capturas completas en README (2026-06-04)

- `README.md` / `README.es.md`: sección Screenshots/Capturas con `interfaz-1` … `interfaz-14` (sin subtítulos, separación `1.5em` entre imágenes); el banner sigue arriba.

### Docs: licencia MIT alineada (2026-06-04)

- `LICENSE`: copyright 2025–2026.
- `jylos/pom.xml`: bloques `<licenses>`, `<developers>` y `<url>` del repositorio.
- `README.md` / `README.es.md`: sección Licencia con autor y contacto.

### Fix: grafo sin enlaces basura (URLs, anclas, etiquetas) (2026-06-04)

- **`WikiLinkResolver.internalNoteTarget`**: filtra URLs (`http`, `www.`), anclas puras (`#…`), adjuntos y `[[wiki]]` inválidos; usado en extracción del grafo y en preview.
- **Grafo**: etiquetas desactivadas por defecto (`showTagsBtn` off, `Options.includeTags` false); solo aristas nota→nota por wiki-links/enlaces internos.
- **Verificado y blindado:** construido un grafo desde una bóveda con todo tipo de basura (URLs, `www.`, anclas `#`, `mailto`, imágenes, PDFs, tags inline `#proyecto #dfsdf`) → solo aparecen las notas reales + nodos *ghost* de wiki-links sin resolver (estilo Obsidian), cero basura. Nuevos tests `WikiLinkTargetTest` y `GraphBuilderCleanTest` que fallan si vuelve a colarse cualquier nodo basura.

### Acerca de: copyright 2026 y autor (2026-06-04)

- `app.copyright` → `Copyright © 2026 Eduardo Díaz Sánchez` (`app.properties`, fallbacks en `AppConfig`).
- `about.developer_credit` en i18n (EN/ES): creador **Eduardo Díaz Sánchez**.

### Docs: README.md y README.es.md alineados con el código (2026-06-04)

- Estructura de carpetas actualizada (`graph/`, `git/`, `ui/graph/`, `icons/`, `backups/`; eliminado `sync/` obsoleto).
- Documentadas funcionalidades recientes: grafo (Canvas nativo), wiki-links/backlinks, Git en bóveda, KaTeX, exportación/plantillas, iconos `app-icon.png` vs Feather/`icon.*`.
- `AGENTS.md`: layout ampliado (grafo, git, iconos).

### Docs: resto de documentación técnica (2026-06-04)

- **`docs/ARCHITECTURE.md`**: reescrito (grafo, git, backlinks, `GraphCanvas`, paquetes actuales, UI con overlay; eliminado `AppShellServices` obsoleto).
- **`docs/PLUGINS.md`**, **`docs/PACKAGING.md`**, **`docs/LAUNCH_APP.md`**, **`docs/BUILD.md`**, **`docs/EVENT_BUS_CONTRACT.md`**, **`docs/README.md`**: iconos, Java 17 en plugins, smoke del grafo, `SystemActionEvent`, enlaces a README/i18n/icons.
- **`plugins-source/README.md`**, **`themes/README.md`**: Java 17, tema sistema, `UiDialogs`.
- **`scripts/README.md`**: nota bytecode 17 en build-plugins.
- **i18n** `dialog.documentation.content` (EN/ES): texto de Ayuda → Documentación alineado con funciones actuales.

### Fix: icono de ventana y diálogo Acerca de (2026-06-04)

- La barra de título cargaba `com/example/jylos/ui/images/app-icon.png` (enero), no `icons/app-icon.png` (actualizado por el usuario).
- `app.icon.window` apunta a **`icons/app-icon.png`**; `AppIconLoader` centraliza la carga para la ventana principal y **Ayuda → Acerca de Jylos** (icono grande encima del nombre).

### Fix: filtrar por etiqueta no mostraba notas (bóveda) + plugins incompatibles (2026-06-04)

- **Etiquetas no filtraban (modo bóveda):** al pinchar una etiqueta no salían sus notas. Causa: en la bóveda las etiquetas son cadenas del *frontmatter* y **no tienen id**, pero `TagService.getNotesWithTag` abortaba si `tag.getId()==null`. Ahora **usa el título como clave alternativa** cuando no hay id (el DAO de ficheros ya empareja por título). Arregla también las aristas de etiqueta del grafo en modo bóveda. Verificado end-to-end y con test `TagFilterFilesystemTest`.
- **`listChanges` de Git** ya consideraba adjuntos; sin cambios aquí.
- **Plugins compilados para Java 21 no cargaban:** los JAR de `plugins/` tenían bytecode v65 (Java 21) y la app es Java 17 → `UnsupportedClassVersionError` al cargarlos (y rompía la carga de plugins). Corregido: los scripts de build de plugins compilan ahora con `--release 17`, los 9 jars se **recompilaron a Java 17**, y `PluginLoader` captura `Throwable` (no solo `Exception`) para que un jar incompatible/corrupto se reporte como fallo y **nunca tumbe la carga ni el arranque**.

### i18n saneado + modal de cambios Git estilo IDE (2026-06-04)

**i18n:**
- Eliminado un **bloque en inglés dentro del archivo español** (`messages_es`) que ensuciaba la traducción (panel de propiedades) — ya no hay idiomas mezclados.
- **Deduplicadas** todas las claves repetidas en los tres bundles → los tres tienen exactamente **517 claves, sin duplicados ni divergencias**.
- 3 textos de interfaz que estaban hardcodeados en FXML pasan a i18n: `panel.preview.title` ("Preview"/"Vista previa"), `panel.editor.content_placeholder`, `action.sort_folders`.
- Carga de bundles confirmada como **UTF-8** (Java 17), por lo que los acentos se muestran bien.
- **`I18nBundleFallbackGuardTest` reforzado**: ahora exige paridad exacta de claves entre los 3 idiomas y ausencia de duplicados → cualquier futura traducción que se desincronice rompe el test.

**Git:**
- Revisada la implementación (commit sin firma GPG, recuperación de `index.lock`, stage/unstage, status) — correcta.
- `listChanges` ahora incluye **adjuntos** (pdf/imágenes) además de notas `.md` (antes solo `.md`), coherente con que la bóveda los muestra.
- **Modal de cambios estilo IDE:** dos secciones — **«Preparados — se incluirán»** arriba y, tras una **línea separatoria**, **«Sin preparar — no se incluirán»** abajo, cada una con su contador. El botón `+`/`−` de cada fila mueve el fichero entre secciones (stage/unstage). Nuevo test `listChangesIncludesAttachmentsAndTracksStaging`.

### Nombre del proyecto: Jylos (2026-06-04)

**Resumen:** Se fija el nombre de la aplicación como **Jylos** en todo el proyecto (motivos legales). Código, recursos, scripts y documentación usan exclusivamente este nombre.

- **Paquetes Java:** todo el código, tests, recursos y `plugins-source` viven bajo `com.example.jylos` (directorios movidos con `git mv`, historial preservado).
- **Módulo Maven:** `artifactId` = `jylos`, `mainClass` = `com.example.jylos.Launcher`, jar `jylos-1.0.0-uber.jar`, directorio del módulo `jylos/` (en minúscula).
- **Recursos:** rutas bajo `/com/example/jylos/...` (FXML `fx:controller`, `getResourceAsStream`, bundle i18n, KaTeX, fuentes), verificadas dentro del jar.
- **Marca y rutas de datos:** título de la app, directorio de datos (`AppDataDirectory` → `Jylos`), protocolo de enlaces internos `jylos://`, propiedad de sistema `jylos.data.dir` y nodo de `Preferences` (deriva del paquete).
- **Scripts:** lanzadores `launch-jylos.*` y todas las rutas/nombres de jar coherentes (`package-macos.sh`, `build-plugins.sh`, etc.).
- **Plugins integrados:** los 9 jars de `plugins/` recompilados desde la fuente (bytecode bajo `com/example/jylos`).
- **Verificación:** 124 tests en verde; jar empaquetado y arrancable; sin trazas de nombres previos en texto ni en binarios.

### Backlinks, búsqueda full-text, KaTeX, plantillas/nota diaria y exportación de la bóveda (2026-06-03)

**Resumen:** Cinco funciones nuevas estilo Obsidian.

- **Backlinks** ("¿qué notas enlazan a esta?"): nueva sección colapsable en el panel derecho que lista las notas que enlazan a la actual (`[[wiki-links]]` o `[label](nota)`), reutilizando `WikiLinkResolver`. Nuevo `BacklinkService` (lee el contenido completo, cachea por id+modified). Clic en un resultado abre la nota.
- **Búsqueda de texto completo**: la búsqueda ahora indexa el **contenido completo** de cada nota (antes solo los ~900 car. del *preview* lightweight), con caché perezosa invalidada al cambiar notas. Resultados navegables en la lista (clic abre la nota).
- **Math con KaTeX (offline)**: render de `$…$`, `$$…$$`, `\(…\)`, `\[…\]` en la vista previa. KaTeX (JS + CSS + 20 fuentes woff2) se empaqueta y las fuentes se incrustan como `data:` URI (el WebView es de origen opaco); solo se inyecta cuando la nota contiene math. Verificado visualmente en el WebView.
- **Plantillas + nota diaria**: comandos *Open Today's Daily Note* (abre/crea `yyyy-MM-dd`) y *New Note from Template…* (elige una nota de una carpeta "Templates" y crea una copia con sustitución de `{{title}}`/`{{date}}`/`{{time}}`/`{{datetime}}`). Funciona en bóveda y SQLite.
- **Exportar todas las notas**: comando *Export All Notes (PDF/HTML)…* → elige formato y carpeta; exporta cada nota a su fichero (reutiliza `NoteExporter`), fuera del hilo de UI, con resumen.
- Nuevos tests: `MarkdownPreviewTest` (KaTeX solo con math; emojis como imágenes). i18n añadido en los 3 idiomas. Dependencias: ninguna nueva (KaTeX es un asset). Jar ~62 MB.
- **Entradas de menú + atajo:** los tres comandos nuevos están también en el menú **Archivo** (Nota Diaria con atajo `Ctrl/Cmd+Shift+D`, Nueva desde Plantilla, Exportar Todas las Notas), además de en la paleta de comandos. Cableados vía `SystemActionEvent` (`DAILY_NOTE`, `NEW_FROM_TEMPLATE`, `EXPORT_VAULT`).

### Fix definitivo: emojis en la vista previa (2026-06-03)

**Resumen:** Los emojis seguían sin verse en el preview (salían como ◆?). Diagnosticado **renderizando el WebView a imagen**: JavaFX/WebKit no pinta los emojis del plano suplementario (🚀, 📁…) ni vía fuentes del SO ni vía `@font-face` (los del rango bajo como ✅/⭐ sí). En cambio, **Java2D (AWT) sí rasteriza** todos los glifos desde la fuente Noto Emoji incluida.

- **Solución:** `MarkdownPreview` ahora **sustituye cada emoji por un `<img>` con la imagen rasterizada** (AWT + la fuente monocroma incluida) incrustada como `data:` URI. Las imágenes siempre se renderizan en el WebView. Se cachean por (emoji, tema).
- Tintado según el tema (claro/oscuro) para que contrasten; tamaño `1.05em` alineado al texto.
- Se **elimina el `@font-face`** anterior (~1,3 MB en cada HTML) que no funcionaba para estos emojis; el HTML del preview es ahora más ligero (solo lleva imágenes de los emojis que aparezcan).
- Verificado **visualmente** rasterizando el WebView en ambos temas: 🚀 ✅ 📁 ⭐ 😀 se ven correctamente.
- Aplica a la vista previa, al HTML exportado (autocontenido) y **también al PDF**: `NoteExporter` reutiliza `MarkdownPreview.emojifyToImages` para incrustar los emojis como `<img>` en el PDF (openhtmltopdf los pinta sin romper el espaciado, al no meter la fuente en el `font-family`). Verificado visualmente rasterizando el PDF: emojis presentes y espaciado correcto.

### Fix: exportación trunca el contenido + emojis fuera del PDF (2026-06-03)

- **Contenido truncado en la exportación:** las notas de la lista llevan solo una versión *lightweight* (contenido recortado a ~900 caracteres para la vista previa), y la exportación las usaba tal cual → el PDF/HTML salía cortado. Ahora `exportNote` resuelve el **contenido completo** antes de exportar (`resolveFullNoteForExport`): usa el texto **vivo del editor** si es la nota abierta, o lee el fichero completo del servicio en caso contrario. Verificado: una nota de ~6,8 KB exporta un PDF de 3 páginas con todo el texto.
- **Emojis fuera del PDF:** se retira la incrustación de emojis en el PDF (era monocroma y poco fiable). Los emojis se siguen viendo en la **vista previa** y en el **HTML exportado**; solo el PDF prescinde de ellos, a cambio de un texto siempre correcto y código más simple.

### Fix: PDF exportado ilegible (espaciado y título duplicado) (2026-06-03)

**Resumen:** El PDF exportado salía con huecos enormes entre palabras (parecía justificado) y con el título repetido. Corregido y **verificado visualmente** rasterizando el PDF.

- **Causa raíz del espaciado:** al incluir la fuente de emoji en el `font-family` global, openhtmltopdf usaba el **glifo de espacio (muy ancho) de la fuente de emoji para todos los espacios**. Ahora la fuente de emoji **no** está en el `font-family` del cuerpo; solo los emojis se envuelven en `<span class="emoji">` con esa fuente (vía rangos Unicode), así los espacios usan la fuente de texto normal. Resultado: texto con espaciado correcto y emojis que siguen renderizando.
- **Título duplicado:** se eliminó el `<h1>` del título que `buildPrintHtml` anteponía, porque el Markdown de la nota ya suele incluir su propio encabezado (y coincide con el export a HTML, que no añade título).
- Verificado renderizando el PDF a imagen: encabezados, negrita/cursiva/tachado, código en línea y en bloque, listas, citas y emojis se ven correctamente.

### Visores nativos de PDF/imagen y exportación a PDF/HTML (2026-06-03)

**Resumen:** La bóveda ahora muestra y renderiza PDFs e imágenes (estilo Obsidian), y las notas se pueden exportar a HTML y PDF. Todo offline.

- **PDF/imágenes en la bóveda:** el escaneo del vault incluye ahora `.pdf` y `.png/.jpg/.jpeg/.gif/.bmp` además de `.md`, y se listan junto a las notas con su icono (imagen/archivo). Clasificación por extensión en el nuevo [`AttachmentType`](jylos/src/main/java/com/example/jylos/util/AttachmentType.java); los binarios nunca se parsean como Markdown.
- **Visor nativo** ([`FileViewer`](jylos/src/main/java/com/example/jylos/ui/components/FileViewer.java)): al abrir un adjunto, el área del editor muestra un visor en vez del editor Markdown.
  - **Imágenes:** `ImageView` con ajustar/zoom (Fit, 100%, +/−) y desplazamiento.
  - **PDF:** páginas rasterizadas con **Apache PDFBox** fuera del hilo de UI (spinner mientras carga), lista vertical con scroll y zoom.
- **Exportar a HTML y PDF** ([`NoteExporter`](jylos/src/main/java/com/example/jylos/util/NoteExporter.java)): el diálogo de exportar añade filtros PDF y HTML. HTML reutiliza el preview (documento autocontenido); PDF se genera con **openhtmltopdf** (Markdown→XHTML vía jsoup→PDF) con CSS de impresión. Validado end-to-end offline (HTML y PDF válidos, render de páginas OK).
- **Dependencias** (todas offline, ~+11 MB al jar): `pdfbox 2.0.31`, `openhtmltopdf-pdfbox 1.0.10`, `jsoup 1.17.2`. El shade ya fusiona `META-INF/services`.
- Solo modo bóveda (en SQLite no hay ficheros). Tests: `AttachmentTypeTest` (clasificación). i18n `viewer.*` y `file_filter.pdf/html` en los 3 idiomas.
- **Pulido del PDF exportado:** se **incrusta la fuente de emoji** (Noto Emoji, como *fallback* de glifos) para que los emojis aparezcan en el PDF, y el **`baseUri`** apunta ahora a la carpeta de la nota para que las **imágenes con ruta relativa** (`![](img.png)`) se resuelvan e incrusten. Verificado ejecutando la exportación (fuente de emoji e imagen relativa quedan embebidas en el PDF).
- **Imágenes locales en la vista previa y el HTML exportado:** las imágenes referenciadas por una nota (`![](img.png)`) no se veían en el preview porque el `WebView` (origen opaco) no carga `file://`. Ahora `MarkdownPreview` **incrusta las imágenes locales como `data:` URI** (resueltas contra la carpeta de la nota vía jsoup), por lo que se ven tanto en la vista previa como en el HTML exportado (que queda autocontenido). Las imágenes remotas (`http(s)`) y los `data:`/`file:` se dejan intactos; se omiten imágenes >12 MB. Verificado por ejecución.

### Sistema de plugins: coherencia visual y limpieza (2026-06-02)

**Resumen:** Revisión del sistema de plugins centrada en coherencia artística y eliminación de estilos antiguos (sin cambiar la arquitectura ni el contrato `Plugin`).

- **`PluginManagerDialog` reescrito:** se elimina el ~100% de estilos inline (hex hardcodeados que duplicaban el tema) y los textos en inglés. Ahora usa **clases CSS del tema** (sigue claro/oscuro automáticamente vía `UiDialogs.apply(Scene)`), está **internacionalizado** (`dialog.plugin_manager.*`) y el interruptor de cada plugin es un **toggle tipo «switch» real** (pista + perilla dirigidos por CSS y estado `:selected`), en vez del falso «●» de texto.
- **Diálogos de plugin temados:** `PluginContext.showInfo/showError` pasan ahora por `UiDialogs`, así los `Alert` que muestran los plugins respetan el tema (antes salían claros sobre fondo oscuro).
- **Paneles de plugin coherentes:** el «chrome» de los paneles laterales de plugin (en [PluginSupport](jylos/src/main/java/com/example/jylos/ui/controller/PluginSupport.java)) deja de construirse a mano con estilos inline y reutiliza el **patrón de sección colapsable nativo** de la app (`panel-section`/`section-header`/`collapse-icon`/`section-title`/`section-content`), idéntico a la sección «Información de la nota».
- **CSS muerto eliminado** en ambos temas: las reglas de **cabeceras de pestaña del sidebar** (ocultas desde la migración a nav-bar) y las definiciones **duplicadas/legadas de `.plugin-panel*`**.
- Nuevo helper `UiDialogs.apply(Scene)` para temar ventanas basadas en `Stage` (como el gestor de plugins).

### Corrección: estilos de plugins externos y scroll del panel derecho (2026-06-03)

**Resumen:** Arregla una regresión introducida al limpiar el CSS: los estilos `.calendar-*` y `.outline-*` los usan **plugins externos** (Calendar, Outline) cargados en runtime, no eran código muerto. Al borrarlos, esos paneles quedaron sin estilo (celdas enormes que forzaban scroll horizontal).

- **Restaurados** los estilos `.calendar-*` y `.outline-*` en ambos temas, ahora con tokens del tema y celdas dimensionadas (28×28) para caber en el panel.
- **Sin scroll horizontal:** el `ScrollPane` del panel derecho usa `hbarPolicy="NEVER"` (combinado con celdas que caben), de modo que nunca aparece la barra horizontal.
- **Icono del panel de plugin:** si el plugin pasa un literal de icono Ikonli (p. ej. `fth-calendar`), ahora se renderiza como **icono real** en la cabecera en vez de mostrarse como el texto «fth-calendar»; si es emoji/texto, se antepone como antes.

### Editor/preview: toolbar, emojis y título en modo lectura (2026-06-02)

**Resumen:** Pulido del editor Markdown y su vista previa.

- **Barra de formato rediseñada:** los botones con texto abreviado ("B", "Hi", "Lk", "Img", "[ ]", ">", "</>") pasan a **iconos Feather** (negrita, cursiva, subrayado, enlace, imagen, código, viñetas, casilla) con botones planos y uniformes (30×28, sin borde por botón, fondo solo al pasar el ratón). Los casos que Feather no cubre se mantienen como glifos tipográficos limpios y consistentes: H1/H2/H3, tachado, resaltado (sobre un *swatch* amarillo), lista numerada (`1.`) y cita (`❝`). *Tooltips* internacionalizados (`tooltip.format.*`). Sin dependencias nuevas.
- **Emojis que salían como ◆? en el preview:** el WebKit de JavaFX **no renderiza las fuentes de emoji del SO** (verificado: el HTML llega con el emoji intacto, es un fallo de fuente, no de codificación). Solución: se **empaqueta una fuente de emoji monocroma** (Noto Emoji, instancia estática peso 400, `ui/preview/fonts/NotoEmoji-Regular.ttf`, ~0,9 MB) y se declara con `@font-face` **incrustada como `data:` URI base64**. Un `file://` no sirve: los documentos cargados con `loadContent` tienen origen opaco y WebKit bloquea recursos externos; un `data:` URI es del mismo origen y siempre carga. Familia `'Jylos Emoji'` al principio del grupo de emoji en la pila de fuentes. 100% offline; emojis en blanco y negro (glifos de contorno).
- **Listas desplegables (ComboBox) ilegibles en los diálogos:** la regla genérica `.combo-box` solo fijaba el radio/borde, así que el botón del combo caía al estilo claro por defecto de JavaFX (caja clara, texto invisible) en los modales. Añadidos fondo, borde, color de flecha y color de la celda mostrada a `.combo-box` en ambos temas. Además `UiDialogs` propaga ahora el stylesheet del tema **también a la `Scene`** del diálogo (no solo al `DialogPane`), para que los *popups* de los combos —ventanas aparte que leen los estilos de la escena— se vean correctamente.
- **Título en modo solo-preview:** en la vista de lectura el `TextField` del título se **sustituye por un `Label`** (`title-heading`) que se lee como un título normal, sin la apariencia ni el comportamiento de un input. El `Label` está enlazado al texto del campo (`textProperty().bind`), y `EditorController.setReadOnlyView` alterna su visibilidad.
- **Botones de la barra homogéneos:** todos los botones de formato (icono y texto) se fijan al mismo tamaño exacto (30×28, `min`/`pref`/`max`), centrados y sin separación de gráfico, para que la barra se vea uniforme.

### Fixes Git, temado de modales y coherencia SQLite (2026-06-02)

**Resumen:** Correcciones sobre la barra Git y los diálogos, más una revisión de coherencia del modo SQLite.

- **Commit Git fallaba siempre ("la operación de git falló"):** la firma GPG (`commit.gpgsign=true`) hacía fallar cada commit en apps GUI sin firmador interactivo. Ahora `GitService.commit` antepone siempre `-c commit.gpgsign=false` (igual que ya hacía `init`). El commit local funciona sin remoto; *Confirmar* hace solo commit local y *Confirmar y subir* añade `push` (luego se puede subir desde el segmento de **Sincronización**).
- **`index.lock: File exists`:** un proceso git interrumpido (o dos operaciones solapadas) dejaba un `.git/index.lock` huérfano que bloqueaba todos los `git add`/commit posteriores. Ahora `GitService` (1) **serializa** todas las llamadas a git (un único `git` en vuelo a la vez) y (2) si una orden falla por el lock, **elimina el `index.lock` huérfano y reintenta una vez**. Cubierto por `GitServiceTest.commitRecoversFromStaleIndexLock`.
- **Errores Git opacos:** `describeGitResult` ahora añade el detalle real de git (truncado a 160 car.) en los estados de error, en lugar de un mensaje genérico. Sin remoto, *Confirmar y subir* informa de `NO_REMOTE` de forma clara.
- **El mensaje de estado largo rompía la barra inferior:** un error git largo expandía el `statusLabel` y comprimía los segmentos git a "||||". Ahora `statusLabel` es el elemento flexible de la barra (`hgrow=ALWAYS`, `maxWidth=∞`), trunca con elipsis y muestra el texto completo en un *tooltip* al pasar el ratón; el `gitBar` fija su ancho mínimo (`minWidth=USE_PREF_SIZE`) para no comprimirse.
- **Colores oscuro/claro en los modales:** los diálogos de JavaFX no heredan el stylesheet de la escena, por lo que aparecían con aspecto claro (p. ej. `TextArea` blanco sobre fondo oscuro). Nuevo `ui/UiDialogs` registra los stylesheets del tema activo y los aplica a cada diálogo/alerta; reglas `.dialog-pane`/`.text-area` añadidas a ambos temas. Todos los `showAndWait()` de `DialogSupport`, `EditorController` y `MainController` (incluido el de remoto y carpeta nueva) pasan por el temado.
- **Coherencia SQLite:** *Mostrar en el explorador de archivos* se oculta en modo SQLite (no aplica sin ficheros). Nueva opción **Exportar nota…** en el menú contextual de la lista para ambos modos (evento `NoteExportRequestEvent` → `MainController.exportNote`).

### Barra Git completa estilo Tolaria (2026-06-02)

**Resumen:** La barra de estado pasa de un indicador único a **cinco segmentos** equivalentes a los de Tolaria, cada uno con su icono y acción.

- **Remoto** (`⎇ Sin remoto` / `Remoto configurado`) → modal para fijar la URL del remoto sobre el git local ya iniciado.
- **N cambios** → diálogo con la lista de notas modificadas (título, fichero, `+añadidas −borradas`) y **preparado por fichero** (botón `+`/`−` → `git add`/`reset`).
- **Confirmar** → modal con mensaje de commit y botones *Confirmar* / *Confirmar y subir*.
- **Sincronización** → muestra rama y `↑`/`↓` (ahead/behind), `✓` si está al día; clic = sincronizar.
- **Historial** → diálogo con los commits de **todas las ramas** (`git log --all`), con hash, autor, fecha y refs.
- Cuando la bóveda aún no es repo, solo aparece **Inicializar Git**.

**Nuevo en `GitService`:** `listChanges` (con `git diff --numstat` para `+/−` y conteo de líneas en *untracked*), `stage`/`unstage` por fichero, `history(--all)` (campos separados por `0x1F`) y `getRemoteUrl`. Modelos `GitChange` y `GitCommit`. Los diálogos heredan el stylesheet del tema (legibles en oscuro). Todo fuera del hilo de UI.

### Sincronización con Git (2026-06-02)

**Resumen:** Sincronización de la bóveda mediante Git, inspirada en Tolaria (que también maneja git a través del CLI del sistema). Implementado en Java con `ProcessBuilder` — sin librerías nativas.

- **`git/GitService.java`** (+ `GitStatus`, `GitResult`): `init` (con autor local, `.gitignore` y commit inicial), `status` (nº de cambios + ahead/behind vs upstream con `fetch`), `commit` (reintenta sin firma GPG si falla), `pull --no-rebase`, `push` (clasifica rechazos/auth/red y autoconfigura upstream), `sync` (commit → pull → push) y `setRemote`. Streams drenados en hilos aparte; timeouts.
- **UI:** indicador Git en la barra de estado (rama, `●` cambios, `↑`/`↓` ahead/behind, `✓` limpio) **clicable para sincronizar**; submenú **Herramientas → Git** (Sincronizar / Commit & push / Pull / Inicializar / Añadir remoto); comandos en la paleta (`Ctrl+Shift+S` para sincronizar).
- Solo activo con almacenamiento en **bóveda (ficheros)**; en SQLite el indicador se oculta. Todas las operaciones corren fuera del hilo de UI.
- Tests `GitServiceTest` contra un repo temporal (se saltan si no hay `git` instalado).

### UI, almacenamiento y temas (2026-06-02)

**Resumen:** Pulido de interfaz y ajustes para acabado profesional.

- **Lista de notas:** nueva opción de menú contextual *Mostrar en el explorador de archivos* (reveal en Finder/Explorer/Files, vía comandos del SO; solo en almacenamiento de ficheros). API limpia: `NoteDAO.resolveFilePath` (default vacío) → implementado en `NoteDAOFileSystem` → `NoteService.getNoteFilePath`.
- **Barra de estado:** conmutador de almacenamiento (bóveda/base de datos) a la izquierda como primer elemento; palabras/caracteres de la nota actual a la derecha con divisores `|`; eliminado el mensaje "Carpeta cargada" duplicado (`noteCountLabel` ahora muestra el conteo; el mensaje transitorio va al `statusLabel`).
- **Ajustes:** nueva preferencia de **tamaño de letra** de la interfaz (`ui.font.size`, 10–22 pt) aplicada al arrancar y al guardar.
- **Toolbar:** el botón *Cambiar diseño* usa icono `fth-layout` (antes `fth-columns`, que colisionaba con el de vista dividida del editor).
- **Temas externos:** `_template/` con `theme.properties` + `theme.css` documentados; el catálogo ignora carpetas `_`/ocultas; eliminado un `applyTheme(...)` sobrecargado muerto en `ThemeSupport`.
- **CSS:** reglas explícitas de legibilidad para los popups de `ComboBox` en ambos temas (texto/selección con variables de tema) — evita listas desplegables ilegibles en oscuro.

### Feature: vista de Grafo tipo Obsidian (2026-06-01)

**Resumen:** Nueva vista de grafo de conocimiento (nodos = notas y etiquetas, aristas = wiki-links y relaciones nota→etiqueta), renderizada con un motor force-directed propio en `<canvas>` (JS puro, sin librerías ni CDN, 100% offline) dentro de un `WebView`. Físicas tipo Obsidian (repulsión Barnes-Hut O(n log n), muelles en aristas, gravedad central y enfriamiento de *alpha* para no consumir CPU al asentarse), zoom/pan, arrastre de nodos, *hover* que resalta vecinos y atenúa el resto, clic para abrir la nota. Soporta grafo global y grafo local (nota actual + vecindario) y alternar etiquetas.

**Archivos nuevos:**
- `graph/GraphNode.java`, `graph/GraphEdge.java`, `graph/GraphData.java` (modelo + serialización JSON sin dependencias).
- `graph/GraphBuilder.java` (construye el grafo desde `NoteService`/`TagService`; aristas nota→nota vía `WikiLinkResolver`).
- `ui/graph/graph.html` (motor force-directed autocontenido en canvas).
- `ui/view/GraphView.fxml` + `ui/controller/GraphController.java` (host `WebView`, puente JS↔Java, build en hilo de fondo).
- `tests/GraphModelTest.java`.

**Archivos modificados:**
- `util/WikiLinkResolver.java`: nuevo `extractLinkTargets()` público (misma semántica que el preview ⇒ 100% compatible).
- `ui/view/MainView.fxml`: `<center>` envuelto en `StackPane` con el overlay del grafo.
- `ui/controller/MainController.java`: cableado, toggle, tema en vivo, abrir nota por id desde el grafo.
- `event/events/SystemActionEvent.java`: acción `GRAPH_VIEW`.
- `ui/view/ToolbarView.fxml` + `ui/controller/ToolbarController.java`: botón + ítem de menú (`Ctrl+G`).
- `ui/components/CommandPalette.java` + `ui/controller/CommandSupport.java`: comando `cmd.graph_view`.
- `i18n/messages*.properties`: claves `graph.*`, `action.graph_view`, `status.graph_opened`.
- `ui/css/modern-theme.css` + `ui/css/dark-theme.css`: estilos `.graph-view`/`.graph-toolbar`/`.graph-title`.

**Rationale:** Reutiliza el `WebView` (como el preview) y la resolución de wiki-links existente para máxima fidelidad y consistencia, sin añadir dependencias pesadas. El enfriamiento de *alpha* y el quadtree Barnes-Hut evitan el lag con vaults grandes; el grafo se construye fuera del hilo de JavaFX.

**Próximos pasos sugeridos:** grupos de color por carpeta/etiqueta configurables, sliders de fuerzas (repulsión/longitud), filtro de huérfanos, y modo local embebido junto al editor.

### UI: sidebar folder icon in light theme (2026-06-01)

- **modern-theme.css**: selected sidebar nav icon uses `-fx-text-muted` like the other tabs (fixes invisible white icon; no accent color).

### Themes: system mode follows OS (2026-06-01)

- **Sistema** en el menú usa siempre tema integrado (aunque Preferencias tenga tema externo).
- **Monitor** cada 1,5 s + reaplicación al enfocar la ventana cuando el menú está en Sistema.
- Detección macOS: soporta valor `(Dark)` de `defaults`.

### Themes: revert retro regression (2026-06-01)

- **Fix**: external themes load **only** their `theme.css` again (removed built-in light/dark underlay that caused white panels, scrollbars and preview).
- **Preview/dark UI**: `darkLike` in `theme.properties` drives Markdown preview and overlays.
- **Retro Phosphor**: green scrollbars, dark preview pane, note-list dividers, selected row text in palette (not white).
- Removed system-theme focus watcher and theme template folder.

### Plugins & themes (2026-06-01)

- **Plugins**: `PluginIds` (IDs estables en Command Palette), `AbstractPlugin`, `PluginContext.unregisterAllCommands()`, orden por dependencias, persistencia `plugin.disabled.*`, limpieza de menú/paneles/preview/comandos al deshabilitar, re-inicialización al volver a habilitar; `CommandPalette.removeCommandById()`.
- **Themes**: `ThemeSupport`/`ThemeCommand` unificados (detección macOS/Linux/Windows, limpieza de stylesheets, temas externos); arranque aplica preferencias antes del menú de tema; menú claro/oscuro/sistema restablece `themeSource` builtin; preferencias sin hack de tema temporal; `applyUiPreferencesFromStore` aplica tema vía `Platform.runLater`.
- **Tests**: `ThemeCommandTest`, `PluginIdsTest`.

### Notes list layout (2026-06-01)

- **NotesListController**: cells bind to list width (minus vertical scrollbar), `computePrefWidth` matches viewport, graphic clipped; preview uses wrapping `Label` instead of `TextFlow`; no horizontal scroll.
- **NotesListView.fxml** / **CSS**: list fills panel width; horizontal scrollbar hidden; panel title ellipsis.

### Tech debt closure (2026-05-31)

- **Tests**: `SQLiteTestSupport`; integration tests for WAL, folder delete/restore transactions, `NoteDAOSQLite` on real SQLite, and `DatabaseBackupService.backupDatabaseFile`.
- **NotesListController**: grid/list view mode owned here (init, switch, refresh); toolbar toggles stay in sync from `MainController`.
- **EditorController**: word count delegates to `NoteService.countWords`.
- **Plugins**: `AutoBackupPlugin` compiles again and uses safe DB backup; 9 plugin JARs rebuilt via `scripts/build-plugins.sh`.
- **NoteDAOSQLiteTest**: fixed `tagDAO` field typo (H2 contract tests retained for fast CI).

### Editor preview extraction (2026-05-31)

- **EditorController**: preview HTML, wiki-link bridge, word count, favorite/pin sync, zoom, and clipboard helpers consolidated here.
- **MainController**: ~2377 → ~2190 lines; removed mirror editor widgets, `JavaBridge`, and duplicate metadata/preview logic.
- **AutoBackupPlugin**: database backup uses `DatabaseBackupService.backupDatabaseFile()` (`VACUUM INTO` with safe fallback).

### UI controllers cleanup (2026-05-31)

- **MainController**: removed dead cached widgets, unused methods, and duplicate handlers; direct access to child controllers for folder tree and preview.
- **EditorController**, **NotesListController**, **SidebarController**, **ToolbarController**, **ContentOperations**: removed unused API and wired language/shortcuts correctly.

### SQLite hardening (2026-05-31)

- `SQLiteDB`: WAL, `busy_timeout`, `foreign_keys`; `openConnection()` throws `DataAccessException` instead of returning null; `folders` schema includes trash columns.
- `FolderDAOSQLite` / `NoteDAOSQLite`: folder delete/restore in a single transaction.
- `DatabaseBackupService`: `VACUUM INTO` with file-copy fallback; integration test asserts `journal_mode = wal`.

### Git / runtime (2026-06-01)

- `.gitignore`: `jylos/backups/` and auto-backup `*.db` patterns; backups were never in git history.
- `DatabaseBackupService`: startup SQLite backup logic moved out of `Main`.

### Code quality (2026-06-01)

- Class-level Javadoc on `Main`, `MainController`, `NotesListController`; `EventBus` example uses logger instead of `System.out`.
- `Locale.forLanguageTag` in `Main` (replaces deprecated constructor).

### Documentation (2026-05-31)

- **README.md / README.es.md**: kept banner, badges, and screenshots; fixed plugin/theme paths, doc links, and removed a hardcoded local path; documented sidebar nav and note-list preview.
- Restored [docs/LAUNCH_APP.md](docs/LAUNCH_APP.md) and [docs/EVENT_BUS_CONTRACT.md](docs/EVENT_BUS_CONTRACT.md); updated [docs/README.md](docs/README.md) index.
- No intentional removal of README branding or portfolio visuals.

## [1.0.0] - 2026-03-03

Initial release: local note-taking with Markdown preview, folders, tags, trash, favorites, command palette, optional plugin JARs, built-in and external themes, SQLite and filesystem storage backends.
