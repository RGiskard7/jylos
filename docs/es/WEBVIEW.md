# Motor web embebido: limitaciones del WebView de JavaFX y la opción JCEF

English: [../WEBVIEW.md](../WEBVIEW.md)

Hay tres páginas HTML que dibuja el `WebView` de JavaFX:

- el editor CodeMirror (`MarkdownEditorView`);
- la vista de lectura (el `previewWebView` de `EditorController`, que rellena `MarkdownPreview`);
- las notas de versión que se muestran antes de actualizar (`UpdateInstallSupport`).

El `WebView` usa el motor WebKit que trae JavaFX, que no es Chromium: le faltan formatos y
comportamientos de navegador que Obsidian y otras apps Electron tienen sin hacer nada.

Esta página recoge esas carencias, qué hace Jylos con cada una y qué supondría cambiar de
motor (JCEF).

## Limitaciones conocidas

| Limitación | Efecto | Qué hace Jylos | Dónde |
|---|---|---|---|
| No decodifica WebP (`naturalWidth` se queda en 0). Muchas webs lo sirven aunque la URL acabe en `.png`. | La imagen sale como su enlace | La imagen que falla se pasa a Java. Java la descarga (o decodifica el fichero de la bóveda ya incrustado), la lee con ImageIO y el lector WebP de TwelveMonkeys, y devuelve un PNG. | `WebImageConverter`, `WebViewImages`, `live-preview.js` (`imageConverted`), `MarkdownPreview` (`__jylosImages`) |
| Los emojis en color solo se dibujan si el emoji va solo en su propio elemento, con una fuente de emoji. Junto a otro texto en el mismo nodo salen corruptos, y un segundo emoji en el mismo elemento no se dibuja. Las secuencias (tonos de piel, familias con ZWJ, banderas, teclas) no se componen. Comprobado en macOS; en Windows y Linux, todavía no. | Emojis corruptos, o solo una parte de una secuencia | Todo emoji empieza como imagen: la suya de Twemoji, en color (incluida, CC-BY 4.0), o, para un símbolo como ↩ y lo que Twemoji no tenga, una rasterizada con la fuente monocroma Noto Emoji incluida. Si al dibujar uno en un canvas sale en color (macOS), cada emoji vuelve a ser texto en su propio elemento con una fuente de emoji, y los símbolos, texto normal; las secuencias siguen como imagen de Twemoji. | `emoji.js` (editor), `colour-emoji.js` y `MarkdownPreview.emojifyToImages` (vista de lectura), `EditorBridge.rasterizeEmoji` |
| `loadContent` estropea los caracteres de fuera del BMP escritos tal cual (pasa la página como UTF-8 modificado) | Cada uno sale como varios glifos rotos | Los emojis entran en la página como referencias numéricas (`&#x1F600;`); el resto de caracteres de fuera del BMP, aún no | `MarkdownPreview.emojifyToImages` |
| El propio WebKit anima el scroll | Un evento de rueda o trackpad no mueve nada durante 2 fotogramas y tarda unos 5 más en llegar (unos 100 ms en total). Sumado a la inercia del trackpad, se nota retraso. Medido con un evento de 100 px. | Nada en el editor; la vista de lectura multiplica el paso de rueda por 1,8. No hay API pública para quitar la animación. | `EditorPreviewSupport.installAcceleratedScroll` |
| No hay composición por GPU; el scroll se repinta en el hilo de JavaFX | La fluidez depende del tamaño de la zona que se repinta | CodeMirror solo dibuja lo visible. Se recuerda la altura de los widgets para que el texto no salte. | `live-preview.js` (`imageHeights`, `blockHeights`) |
| `KeyboardEvent.key` llega vacío en macOS | Los símbolos con Option (`[`, `{`, `@`…) disparaban atajos de CodeMirror | Los atajos que dependen del código de tecla no se usan al escribir | `editor.js` (`typingSafeKeymap`) |
| `document.hasFocus()` no responde como en un navegador | El `hasFocus` de CodeMirror se equivoca | El foco se sigue con los eventos focus y blur del contenido | `live-preview.js` (`editorFocusField`) |
| No hay pegado nativo en `contentEditable` | Pegar no hace nada | Copiar, cortar y pegar pasan por el portapapeles de JavaFX en un filtro de teclado | `MarkdownEditorView.installDesktopEditingIntegration` |
| Las páginas se cargan con `loadContent`: origen opaco, sin URL | No cargan recursos `file://` ni funcionan los saltos `#fragmento` | Las imágenes de la bóveda y las fuentes de KaTeX van incrustadas como `data:`; los saltos a anclas se hacen en JavaScript | `MarkdownPreview.resolveImageSource`, el manejador de clics de la vista de lectura |
| JavaScript solo llega a métodos de clases **públicas** | Un puente que no es público falla sin avisar | Los puentes son clases públicas (`EditorBridge`, `PreviewJavaBridge`) | `EditorPreviewSupport.PreviewJavaBridge` |
| JavaScript se ejecuta en el hilo de JavaFX | Una llamada síncrona a Java bloquea la interfaz | El trabajo caro (bloques de plugins, conversión de imágenes) se hace en segundo plano y se envía a la página | `EditorBlockRenderSupport`, `WebViewImages` |
| Descarta los pesos de fuente que no son múltiplos de 100 (p. ej. 750) | Los encabezados pierden su grosor | Solo se usan múltiplos de 100 | tema del editor en `live-preview.js` |
| No decodifica AVIF | Las imágenes AVIF no se ven | Aún nada: no hay un lector de AVIF en Java puro | — |

## El contrato entre Java y la página

- **De Java a la página:** cadenas de JavaScript que se ejecutan con
  `WebEngine.executeScript`. Los argumentos se codifican con Gson
  (`window.JylosEditor.setDocument(...)`).
- **De la página a Java:** un objeto puente colocado en `window` (`javaEditor` en el editor,
  `javaApp` en la vista de lectura).
- **Llamadas síncronas.** Algunas llamadas de la página a Java devuelven un valor que la
  página usa al momento: `transformInsertion`, `readClipboard`, `resolveImageSource` y
  `rasterizeEmoji`. Si el motor hace asíncronas esas llamadas, primero hay que convertirlas
  en petición y respuesta.
- **Reinyección.** En la vista de lectura el puente se vuelve a colocar tras cada carga, y lo
  que llega antes de que la página esté lista espera a que lo esté
  (`MarkdownEditorView.whenReady`; `bridgeReady()` en la vista de lectura).

## La opción JCEF (en valoración)

**Estado.** Se está valorando; de momento Jylos sigue con el WebView de JavaFX. Un prototipo
midió lo que aportaría y lo que costaría:
- **Scroll:** un paso de rueda de 100 px llega en un fotograma, frente a unos 100 ms con WebView.
- **Coste:** unos 290 MB más de memoria y unos 240 MB más en disco por plataforma.
- **Escritura:** en macOS, JCEF descarta todo carácter que no sea ASCII cuando dibuja fuera de
  pantalla, así que Jylos necesitaría una capa de teclado propia.
- **Soporte:** JCEF no tiene una integración oficial con JavaFX; esa pieza la mantendría Jylos.

**Qué es.** JCEF (Java Chromium Embedded Framework) mete Chromium, a través de CEF, dentro de
una aplicación Java. Lo usan los IDE de JetBrains para sus paneles web; CEF también está
detrás de apps de escritorio como Spotify o Steam.

**Qué arreglaría.** Es la misma familia de motor que el de Obsidian (Chromium), así que
traería:
- WebP, AVIF y emojis completos (tonos de piel, banderas, familias);
- el scroll nativo de un navegador;
- CSS y JavaScript actuales.

**Qué costaría:**
- **Hecho para Swing/AWT.** La integración de JCEF está pensada para Swing/AWT, no para
  JavaFX. Dentro de JavaFX tiene que dibujar fuera de pantalla y copiar cada fotograma a una
  imagen de JavaFX. Eso renuncia a parte del camino por GPU, así que la mejora del scroll hay
  que medirla con un prototipo, no darla por hecha.
- **Binarios nativos.** CEF trae binarios nativos grandes para cada plataforma. En macOS,
  además, hay que firmar y notarizar sus procesos auxiliares junto con la app.
- **Puente asíncrono.** Las llamadas de la página a Java son asíncronas, así que hay que
  rediseñar las llamadas síncronas de arriba.
- **Alternativa.** JxBrowser (comercial) es un motor Chromium para Java con soporte de
  JavaFX.

**Pendiente antes de decidir:**
- cómo se nota el scroll con un trackpad de verdad;
- el tamaño de la app empaquetada, medido y no estimado;
- Windows y Linux.

## Dónde vive el motor

Las tres páginas se muestran a través de una interfaz pequeña, `HtmlView` (`ui/web`): cargar una
página, ejecutar o evaluar código, dar a la página un objeto Java (que se vuelve a colocar en cada
carga) y actuar cuando ha cargado. `JavaFxHtmlView` es su implementación sobre el WebView de
JavaFX y la única clase que conoce el motor, junto con `ExternalLinksBridge`; también es el nodo
que el FXML coloca donde se muestra una página. `WebEngineConfinementGuardTest` mantiene
`javafx.scene.web` y `netscape.javascript` fuera del resto del código, así que cambiar de motor
es escribir otra implementación de `HtmlView`.
