# Plugins

English: [../PLUGINS.md](../PLUGINS.md)

Los plugins externos son JARs cargados al arrancar. Core no importa clases concretas de plugins.

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

Un plugin roto no debe impedir arranque de la app.
