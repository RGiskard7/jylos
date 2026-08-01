# Plugins

English: [../PLUGINS.md](../PLUGINS.md)

Los plugins externos son JARs cargados al arrancar. Core no importa clases concretas de plugins.

## Instalación

Usa **Herramientas → Gestionar plugins → Instalar plugin...** y selecciona un `.jar`. Jylos copia el JAR al directorio principal de plugins del usuario y lo carga inmediatamente cuando es posible.

La instalación manual sigue estando soportada: coloca el JAR en el directorio principal de plugins (`<appData>/plugins`, el que expone `PluginLoader.getPluginsDirectoryFile()`) y reinicia Jylos.

## Eliminación

Usa **Herramientas → Gestionar plugins → Eliminar** en la tarjeta de un plugin para desinstalarlo del directorio principal de plugins del usuario. Jylos apaga el plugin, retira sus aportaciones de UI, cierra su classloader, borra el JAR y limpia su preferencia de desactivado.

Los plugins cargados desde ubicaciones protegidas del paquete de la aplicación no se eliminan desde el gestor; cópialos primero al directorio de plugins del usuario si quieres que el gestor controle su ciclo de vida.

## Build

```bash
./scripts/build-plugins.sh
```

```powershell
.\scripts\build-plugins.ps1
```

Compila `plugins-source/` con `javac --release 21` y escribe JARs en `jylos/plugins/`.

## Autoría

- Extender `AbstractPlugin`.
- Usar ids estables desde `PluginIds` para comandos.
- Recompilar tras cambios.

## Extension points

| API | Uso |
|-----|-----|
| `registerCommand(...)` | Paleta de comandos |
| `registerMenuItem(...)` / `addMenuSeparator(...)` | Menú dinámico de plugins |
| `registerSidePanel(...)` | Nodo JavaFX en panel derecho |
| `registerPreviewEnhancer(...)` | CSS/JS en preview Markdown |
| `registerToolbarButton(...)` | Botón de toolbar |
| `registerEditorHook(EditorHook)` | Hooks del editor |
| `requestOpenNote(note)` | Pedir al shell abrir nota |
| `requestRefreshNotes()` | Pedir refresh fan-out |
| `subscribe(...)` / `publish(...)` | Eventos tipados |

## Hooks de editor

- `onBeforeTextInsert` transforma snippets programáticos.
- `onBeforeSave` transforma contenido antes de persistir.
- `onAfterSave` observa guardado correcto.

Deben ser rápidos, se ejecutan en JavaFX Application Thread y se eliminan al deshabilitar plugin.

## Ciclo de vida

1. Descubrir JARs.
2. Cargar con classloaders dedicados.
3. Registrar metadata, comandos, menús, preview enhancers y paneles.
4. Deshabilitar limpia UI/hooks/comandos.

Un plugin roto no debe impedir arranque de la app. Los plugins desactivados desde el gestor quedan persistidos como desactivados y no se inicializan al siguiente arranque, evitando que registren botones, menús o paneles antes de aplicar su estado.
