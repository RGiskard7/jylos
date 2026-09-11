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

Un bundle puede necesitar librerías que el core no proporciona. Se declaran en un
`pom.xml` propio dentro del bundle — un POM Maven real, resuelto con
`mvn -f pom.xml dependency:build-classpath` (descargas con checksum verificado a la caché
local `~/.m2` de siempre), el mismo mecanismo de resolución de dependencias que ya usa el
resto del proyecto. No forma parte del reactor principal — `jylos/pom.xml` nunca lo
referencia — así que estas dependencias nunca acaban en el uber-jar de la app; solo
existen para compilar este bundle:

```
plugins-source/com/example/jylos/plugin/builtin/mcp/
├── plugin.properties
├── pom.xml
├── McpServerPlugin.java
└── ...
```

Solo hace falta listar los artefactos que el bundle importa directamente; Maven resuelve
el resto del árbol de dependencias de forma transitiva, igual que hace con el propio
`jylos/pom.xml`. Nada se vendoriza en el repositorio — cada JAR se descarga de Maven
Central (o de lo que apunte tu `~/.m2/settings.xml`) en el momento de compilar.

Los JAR resueltos se añaden al classpath de compilación del bundle y **se empaquetan
dentro del propio JAR del plugin**. Es deliberado: un plugin se instala y se elimina como
un único fichero — el selector del gestor acepta un solo `*.jar` y
`PluginLoader.deletePluginJar` borra un solo fichero — así que dependencias que solo
existieran en el classpath local de Maven nunca podrían viajar con un plugin instalado.
Empaquetarlo mantiene el plugin como artefacto autocontenido e instalable, y no exige
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

Un bundle que declara sus propias dependencias de terceros (vía `pom.xml`) se excluye de esa compilación plana
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
| `showInfo(title, header, content)` / `showError(title, message)` | Un `Alert` de información/error, con tema aplicado, de solo lectura — el contenido se muestra como texto plano, no seleccionable |
| `showCopyableInfo(title, header, content)` | Igual que `showInfo`, pero el contenido es un área de texto real, seleccionable/copiable (más un botón "Copy") — úsalo en vez de `showInfo` para cualquier cosa pensada para pegarse en otro sitio (un fragmento de plantilla, una URL) |
| `applyTheme(DialogPane \| Dialog<?> \| Scene)` | Aplica el tema actual de la app a un diálogo o ventana que construyes **tú**. Un plugin que necesite más de lo que ofrecen `showInfo`/`showCopyableInfo`/`showError` (un formulario propio, su propio subtipo de `Alert`, un `Stage` sin bordes) sigue necesitando esto — es lo que esos tres helpers llaman internamente, y la única forma soportada de dar tema a UI propia de un plugin. Alcanzar `com.example.jylos.ui.UiDialogs` directamente en su lugar se rechaza al cargar (ver [Límite del classloader](#límite-del-classloader) más abajo) — es una clase interna, no forma parte de este API, y todo plugin integrado que hacía justo eso ya está migrado a `applyTheme` |
| `showThemed(Dialog<T>)` | `applyTheme(dialog)` seguido de `dialog.showAndWait()`, para el caso común donde no hace falta nada más entre medias |
| `runWithProgress(title, header, Task<T>, onSuccess, onFailure)` | Ejecuta un `Task` en segundo plano detrás de un diálogo de progreso con tema aplicado — la ceremonia que necesita un plugin haciendo E/S real (exportar una bóveda, una copia de seguridad): construir el diálogo, enlazar la barra de progreso, arrancar un hilo daemon, cerrar el diálogo y entregar el resultado a `onSuccess`/`onFailure` (cada uno ya diferido más allá del conocido bug de JavaFX de diálogo modal en blanco). Ver `PublishPlugin` para un ejemplo real |
| `getPluginPreferences()` | El nodo `java.util.prefs.Preferences` propio de este plugin, namespaced por su id — para ajustes que deben sobrevivir a un reinicio (una clave de API, "última carpeta usada"). Úsalo en vez de `Preferences.userNodeForPackage(TuPropiaClase.class)`: el nodo que devuelve está bajo el subárbol de preferencias del propio host, con garantía de no colisionar con el de otro plugin (ni, peor, con las claves de activado/desactivado que usa el propio host) |

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

1. Descubrir JARs en los directorios de plugins.
2. Cargar con un `URLClassLoader` dedicado por plugin, que además acota el
   límite descrito abajo, y luego comprobar `Plugin.getHostApiVersion()`
   contra lo que soporta este build — un plugin incompatible se rechaza con
   un mensaje que nombra el desajuste, no un fallo genérico.
3. Registrar metadata, comandos, menús, preview enhancers y paneles; inicializar los plugins activados.
4. Deshabilitar: retirar hooks de UI, comandos y suscripciones a eventos; cerrar classloaders al salir de la app.

### Límite del classloader

Cada plugin tiene su propio `PluginClassLoader` (un `URLClassLoader`), para
que los JAR de dependencias de un plugin no choquen con los de otro — pero, a
diferencia de un `URLClassLoader` normal, una petición de una clase
`com.example.jylos.*` fuera de
[`PluginApiSurface`](https://github.com/RGiskard7/jylos/blob/develop/jylos/src/main/java/com/example/jylos/plugin/PluginApiSurface.java)
nunca llega al classloader de la propia app (su padre), aunque ese padre
pudiera resolverla — falla de inmediato con un `ClassNotFoundException` que
nombra la clase exacta y apunta de vuelta a esta tabla, en vez de cargar y
romperse en algún release futuro en cuanto esa clase interna cambie de forma
o de sitio. `PluginApiSurface` es la única fuente de verdad de la lista
permitida: la misma que `PluginClassLoader` impone en tiempo de carga es la
que `PluginContractGuardTest` usa para escanear el código fuente de cada
plugin integrado en tiempo de build, así que las dos no pueden desincronizarse.

Esto es un **límite de contrato** impuesto por el compilador/cargador, no un
**sandbox** de seguridad — la distinción sigue importando, y sigue siendo
honesto trazarla: un plugin no está confinado a un conjunto restringido de
*operaciones* (E/S de fichero, red, reflexión sobre el propio JDK, …), solo a
un conjunto restringido de *clases internas de la app* que puede alcanzar por
nombre. Una vez cargado, un plugin sigue corriendo con los privilegios
completos del proceso de la JVM — nada de esto impide a un plugin decidido
alcanzar una clase no listada por una vía de reflexión más exótica que un
`Class.forName` simple. Lo que esto da es honesto, no absoluto: un plugin
normal (integrado o de terceros, escrito contra el API documentado) falla
rápido y con claridad en cuanto se sale de ese API, en vez de funcionar en
silencio hoy y romperse sin aviso en algún refactor interno futuro.

**Considerado y descartado: un checksum por JAR** (grabar un hash al
instalar, rechazar cargar un JAR cuyos bytes cambiaran después). Se retiró
tras construirlo y probarlo de verdad: la primera capacidad, literal, de este
sistema de plugins es *"añadir o quitar un plugin colocando o borrando un
fichero JAR"* — un desarrollador (o un usuario probando su propio build)
recompila y suelta un JAR actualizado con el mismo nombre habitualmente, y
eso es exactamente indistinguible, en bytes, de una manipulación. Un hash
grabado una vez no tiene forma de diferenciar ambos casos, y aquí no hay
firma ni modelo de confianza al que recurrir que sí pudiera (ver el límite
del classloader de arriba para la garantía de integridad que sí merece la
pena mantener en su lugar — acotar *qué puede alcanzar* un plugin, que no
choca con recompilarlo).

El desmontaje no depende de que el plugin colabore. `PluginManager` llama a su
`shutdown()` y después retira todas sus aportaciones —entradas de menú, paneles, preview
enhancers, hooks de editor, botones de toolbar, renderizadores de bloque, comandos y
suscripciones a eventos— aunque ese `shutdown()` haya lanzado. Cancelar tus propias
suscripciones en `shutdown()` sigue siendo buena práctica y no cuesta nada (`cancel()` es
idempotente), pero un plugin que falla a mitad del desmontaje ya no deja handlers vivos
detrás, cosa que además impedía que su classloader se recolectara nunca.

Un plugin roto no debe impedir arranque de la app. Los plugins desactivados desde el gestor quedan persistidos como desactivados y no se inicializan al siguiente arranque, evitando que registren botones, menús o paneles antes de aplicar su estado.
