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

### Plugins de un fichero y multi-fichero

Por defecto **un fichero fuente es un plugin**, compilado a su propio JAR.

Un plugin demasiado grande para un solo fichero se declara como **bundle**: coloca un
descriptor `plugin.properties` en su directorio y todos los `.java` bajo él se compilan
juntos en un único JAR.

```properties
plugin.class=com.example.jylos.plugin.builtin.dataview.DataviewPlugin
plugin.jar=DataviewPlugin
```

`plugin.class` es obligatorio: con varias clases que implementan `Plugin` en un bundle, la
autodetección elegiría una arbitraria. `plugin.jar` es opcional y por defecto usa el nombre
del directorio.

## Pruebas de plugins

```bash
./scripts/test-plugins.sh
```

Las fuentes de plugins se compilan contra la aplicación como cualquier plugin de terceros,
así que `mvn test` no las ve. Este script compila `plugins-source/` junto a `plugins-test/`
y ejecuta el `main()` de cada clase `*Test`, fallando si alguna devuelve código distinto de cero.

## Autoría

- Extender `AbstractPlugin`.
- Usar ids estables desde `PluginIds` para comandos.
- Recompilar tras cambios.

### Dónde vive un plugin de primera parte

`plugins-source/com/example/jylos/plugin/builtin/` contiene todo plugin de primera parte
que se distribuye como JAR externo — un plugin, un subárbol, sea de un fichero
(`WordCountPlugin.java`) o multi-fichero (`dataview/`). El tamaño no es el criterio: todo
lo de aquí pasa por el mismo camino `PluginLoader`/`URLClassLoader` que un plugin de
terceros, y depende de que `scripts/build-plugins.sh` haya generado su JAR.

**Mermaid es la única excepción deliberada.** Vive en
`jylos/src/main/java/com/example/jylos/plugin/mermaid/`, compilado directamente en el core
(`PluginLifecycle.registerCoreAndExternalPlugins` lo instancia directamente — sin JAR, sin
classloader). El empaquetado trata el build de plugins como best-effort: si
`build-plugins.sh` falla, la app se empaqueta igual, solo que sin esos JARs. El renderizado
de diagramas Mermaid es lo bastante común, y se espera lo bastante fiable, como para no
depender de que ese paso tenga éxito — por eso está compilado dentro, no construido desde
`plugins-source/`. No muevas aquí otros plugins de primera parte por el mismo razonamiento
sin sopesar el trade-off: saca al plugin del mecanismo de JAR (sin activar/desactivar por
fichero independiente, sin `isPluginRemovable`) a cambio de disponibilidad incondicional.

## Extension points

| API | Uso |
|-----|-----|
| `registerCommand(...)` | Paleta de comandos |
| `registerMenuItem(...)` / `addMenuSeparator(...)` | Menú dinámico de plugins |
| `registerSidePanel(...)` | Nodo JavaFX en panel derecho |
| `registerPreviewEnhancer(...)` | CSS/JS en preview Markdown, más post-procesado de HTML por nota (abajo) |
| `registerToolbarButton(...)` | Botón de toolbar |
| `registerEditorHook(EditorHook)` | Hooks del editor |
| `registerEditorBlockRenderer(language, renderer)` | Renderiza un bloque cercado dentro del Live Preview del editor |
| `requestOpenNote(note)` | Pedir al shell abrir nota |
| `requestRefreshNotes()` | Pedir refresh fan-out |
| `subscribe(...)` / `publish(...)` | Eventos tipados |

## Preview enhancers

`PreviewEnhancer` tiene tres métodos por defecto:

- `getHeadInjections()` / `getBodyInjections()` — recursos estáticos (CSS, JS) añadidos a
  todo documento de vista previa.
- `transformHtml(PreviewContext context, String html)` — post-procesa el **cuerpo
  renderizado de la nota** y, a diferencia de los anteriores, sabe *qué* nota se está
  renderizando (`context.note()`, `context.darkTheme()`). Es lo que permite a un plugin
  sustituir contenido por nota, por ejemplo convertir un bloque <code>```dataview</code>
  en una tabla generada.

Reglas: los transforms se encadenan en orden de registro (cada uno ve la salida del
anterior), se ejecutan en el hilo de render de la vista previa (no en el de JavaFX), y uno
que lance excepción o devuelva `null` deja el HTML intacto en vez de vaciar la nota. Se
ejecutan *antes* de decidir si se inyectan los recursos de resaltado de sintaxis, de modo
que un plugin que elimina el único bloque de código de la nota no deja highlight.js
cargado. Ejemplo: `DataviewPlugin` (ver [DATAVIEW.md](DATAVIEW.md)).

## Renderizadores de bloque del editor

`registerEditorBlockRenderer(String language, EditorBlockRenderer renderer)` muestra un
bloque cercado <code>```language</code> como HTML generado **dentro del editor**, volviendo a
su código fuente mientras el cursor está dentro. Junto con un `PreviewEnhancer`, un plugin
puede hacer que el mismo bloque se renderice igual en modo lectura y mientras se edita.

Los resultados se **empujan, no se piden**: la aplicación extrae los bloques reclamados,
llama al renderizador en un hilo de fondo y entrega el marcado terminado al editor como una
tabla de consulta. El JavaScript de un `WebView` se ejecuta en el hilo de JavaFX, así que
dejar que el editor llamara de vuelta a Java mientras construye decoraciones pondría el
trabajo del plugin — y su E/S — en el hilo de la interfaz durante el scroll. Los renders se
agrupan al escribir y se recalculan cuando cambia la nota o cualquier otra, ya que un bloque
puede resumir toda la bóveda.

El HTML devuelto se inserta tal cual, así que el renderizador debe escapar todo lo que venga
del contenido de las notas. Devolver `null` deja el bloque mostrando su código fuente. Los
renderizadores se eliminan automáticamente al deshabilitar el plugin.

## Hooks de editor

- `onBeforeTextInsert` transforma inserciones programáticas (diálogos de
  enlace/imagen, autocompletado `[[` y plantilla de tarea), no pulsaciones ni
  pegado; devolver `null` conserva el valor original.
- `onBeforeSave` transforma contenido antes de persistir.
- `onAfterSave` observa guardado correcto.

Deben ser rápidos, se ejecutan en JavaFX Application Thread y se eliminan al deshabilitar plugin.

## Ciclo de vida

1. Descubrir JARs.
2. Cargar con classloaders dedicados.
3. Registrar metadata, comandos, menús, preview enhancers y paneles.
4. Deshabilitar limpia UI/hooks/comandos.

Un plugin roto no debe impedir arranque de la app. Los plugins desactivados desde el gestor quedan persistidos como desactivados y no se inicializan al siguiente arranque, evitando que registren botones, menús o paneles antes de aplicar su estado.
