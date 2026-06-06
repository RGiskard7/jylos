# Changelog

## [Unreleased]

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

- **`doc/ARCHITECTURE.md`**: reescrito (grafo, git, backlinks, `GraphCanvas`, paquetes actuales, UI con overlay; eliminado `AppShellServices` obsoleto).
- **`doc/PLUGINS.md`**, **`doc/PACKAGING.md`**, **`doc/LAUNCH_APP.md`**, **`doc/BUILD.md`**, **`doc/EVENT_BUS_CONTRACT.md`**, **`doc/README.md`**: iconos, Java 17 en plugins, smoke del grafo, `SystemActionEvent`, enlaces a README/i18n/icons.
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
- Restored [doc/LAUNCH_APP.md](doc/LAUNCH_APP.md) and [doc/EVENT_BUS_CONTRACT.md](doc/EVENT_BUS_CONTRACT.md); updated [doc/README.md](doc/README.md) index.
- No intentional removal of README branding or portfolio visuals.

## [1.0.0] - 2026-03-03

Initial release: local note-taking with Markdown preview, folders, tags, trash, favorites, command palette, optional plugin JARs, built-in and external themes, SQLite and filesystem storage backends.
