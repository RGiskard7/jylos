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

### Dependencias de terceros

Un bundle puede incluir librerías que el core no proporciona. Coloca sus JAR en un
directorio `lib/` dentro del bundle:

```
plugins-source/com/example/jylos/plugin/builtin/mcp/
├── plugin.properties
├── McpServerPlugin.java
└── lib/
    └── some-sdk-1.2.0.jar
```

Se añaden al classpath de compilación del bundle y **se empaquetan dentro del propio JAR
del plugin**. Es deliberado: un plugin se instala y se elimina como un único fichero — el
selector del gestor acepta un solo `*.jar` y `PluginLoader.deletePluginJar` borra un solo
fichero — así que un directorio `lib/` junto al plugin instalado nunca podría viajar con
él. Empaquetarlo mantiene el plugin como artefacto autocontenido e instalable, y no exige
ningún cambio en cómo `PluginLoader` construye su classloader.

El build resuelve las partes del mezclado que si no fallarían en silencio:

- Se descartan los **ficheros de firma** (`*.SF`, `*.DSA`, `*.RSA`, `*.EC`). Los digests de
  una dependencia firmada dejan de cuadrar en cuanto sus clases viven en otro archivo, y la
  JVM rechazaría el JAR entero con `Invalid signature file digest` al cargarlo.
- Las entradas **`META-INF/services/*`** se concatenan en vez de sobrescribirse, para que
  `ServiceLoader` siga encontrando todos los proveedores cuando dos dependencias registran
  el mismo servicio.
- Se descarta el **`module-info.class`** de las dependencias: no significa nada en el
  classpath y dos dependencias colisionarían en él.

Dos advertencias a tener presentes:

- **Ganan las clases de la aplicación.** El classloader de un plugin tiene el de la
  aplicación como padre y Java delega primero en el padre, así que incluir una *versión
  distinta* de algo que el core ya trae (Gson, SnakeYAML, …) no lo sustituye: se carga la
  del core. Incluye librerías que el core no tenga ya.
- **No se comparten entre plugins.** Dos plugins que incluyan la misma librería llevan cada
  uno su copia, en su propio classloader. Es el precio de que la instalación sea
  autocontenida.

## Pruebas de plugins

```bash
./scripts/test-plugins.sh
```

Las fuentes de plugins se compilan contra la aplicación como cualquier plugin de terceros,
así que `mvn test` no las ve. Este script primero construye los JAR de plugins
(`build-plugins.sh`), luego compila `plugins-source/` junto a `plugins-test/` y ejecuta el
`main()` de cada clase `*Test`, fallando si alguna devuelve código distinto de cero.

Un bundle que trae sus propias dependencias en `lib/` se excluye de esa compilación plana
y se prueba solo a través de su JAR ya construido, cargado con su propio `URLClassLoader`
— el mismo aislamiento que `PluginLoader` le da en tiempo de ejecución. Compilar las
fuentes de ese bundle en plano junto a la prueba pondría sus librerías empaquetadas en el
mismo classpath que todo lo demás, lo cual puede ocultar bugs de carga de clases que una
instalación real sí sufriría (ver [MCP.md](MCP.md#pruebas) para uno concreto que esto
detectó).

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
| `subscribe(...)` / `publish(...)` | Eventos tipados; las suscripciones se cancelan solas al deshabilitar |

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
4. Deshabilitar limpia UI/hooks/comandos/suscripciones.

El desmontaje no depende de que el plugin colabore. `PluginManager` llama a su
`shutdown()` y después retira todas sus aportaciones —entradas de menú, paneles, preview
enhancers, hooks de editor, botones de toolbar, renderizadores de bloque, comandos y
suscripciones a eventos— aunque ese `shutdown()` haya lanzado. Cancelar tus propias
suscripciones en `shutdown()` sigue siendo buena práctica y no cuesta nada (`cancel()` es
idempotente), pero un plugin que falla a mitad del desmontaje ya no deja handlers vivos
detrás, cosa que además impedía que su classloader se recolectara nunca.

Un plugin roto no debe impedir arranque de la app. Los plugins desactivados desde el gestor quedan persistidos como desactivados y no se inicializan al siguiente arranque, evitando que registren botones, menús o paneles antes de aplicar su estado.
