# Plugins

Jylos carga JARs de plugins externos desde `plugins/` bajo el directorio base de la aplicación, con un ciclo de vida seguro de carga/desactivación y una interfaz de gestión.

## Plugins integrados

Vienen con Jylos como plugins de primera parte compilados en JARs:

- **Dataview** — consulta los metadatos de tus notas como una base de datos (ver la [guía de Dataview](/es/guides/dataview)).
- **Servidor MCP** — expone tu vault a clientes MCP mediante un servidor HTTP local.
- **Publish** — exporta todo el vault a un sitio web estático autocontenido.
- **Outline** — un esquema de la nota en un panel lateral.
- **Calendar** — una vista de calendario de tus notas.
- **Daily Notes** — crea la nota de hoy.
- **Reading Time** — muestra el tiempo estimado de lectura.
- **Auto Backup** — programa copias de seguridad del vault.
- **Templates** — crea notas a partir de plantillas.
- **AI** — integración con un asistente de IA.
- **Word Count** — recuento de palabras/caracteres.
- **Table of Contents** — genera un índice de contenidos para una nota.

El soporte de diagramas Mermaid está compilado **en el núcleo de la app** en lugar de enviarse como plugin JAR, por lo que siempre está disponible.

## Instalar y quitar

- **Herramientas → Gestionar plugins → Instalar plugin…** selecciona un `.jar` y lo copia en tu directorio de plugins de usuario, cargándolo cuando es posible.
- **Herramientas → Gestionar plugins → Quitar** apaga el plugin, elimina sus contribuciones de UI, cierra su classloader y borra el JAR.

También puedes soltar un JAR en el directorio de plugins manualmente y reiniciar.

## API de plugins

Un plugin extiende `AbstractPlugin` y recibe un `PluginContext` que expone estos puntos de extensión:

- `registerCommand(...)` — entradas de la paleta de comandos.
- `registerMenuItem(...)` — entradas del menú dinámico de plugins.
- `registerSidePanel(...)` — un nodo en el panel derecho.
- `registerPreviewEnhancer(...)` — CSS/JS y post-procesado HTML por nota para la vista previa.
- `registerToolbarButton(...)` — un botón en la barra de herramientas principal.
- `registerEditorHook(...)` — `onBeforeTextInsert`, `onBeforeSave`, `onAfterSave`.
- `registerEditorBlockRenderer(...)` — renderiza un bloque cercado inline en Live Preview.
- Acceso al bus de eventos, diálogos con tema, diálogos de progreso y preferencias por plugin.

Cada plugin recibe su propio classloader con un límite impuesto: puede alcanzar la superficie documentada de la API de plugins, pero las peticiones a otras clases internas `com.example.jylos.*` fallan rápido.

## Compilar desde el código fuente

Las fuentes de los plugins viven en `plugins-source/` y se compilan a `jylos/plugins/` con:

```bash
./scripts/build-plugins.sh
```

```powershell
.\scripts\build-plugins.ps1
```

## Relacionado

- Detalles técnicos: [PLUGINS.md](https://github.com/RGiskard7/jylos/blob/main/docs/PLUGINS.md)
- [Guía de Dataview](/es/guides/dataview)
- [Servidor MCP](https://github.com/RGiskard7/jylos/blob/main/docs/MCP.md)
