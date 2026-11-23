# JavaFX en macOS: una tecla muerta repetida pierde una pulsación de cada dos

| | |
|---|---|
| Estado | Documentado aquí; no se ha enviado a JavaFX. Si algún día se envía, anotar aquí y en `docs/es/WEBVIEW.md` su identificador JDK. |
| Afecta | A toda entrada de texto de JavaFX en macOS: el editor y la vista de lectura (`WebView`) y cualquier `TextField`/`TextArea`. |
| Compensado en Jylos | En todas partes: `DeadKeyRepeats`, que `Main` instala en cada ventana, las abiertas al arrancar y las que se abren después (diálogos). Cubre las páginas web y los `TextField`/`TextArea` de JavaFX. |
| Visto con | JavaFX 23.0.2 (la de Jylos) y 24.0.2, JDK 22.0.2, macOS 27.0.1, distribución Spanish - ISO. |

El fallo, redactado con el formato de un informe de JavaFX (entorno, pasos, resultado esperado y
real, los eventos grabados) por si algún día hiciera falta enviarlo tal cual, y el programa que lo
reproduce sin código de Jylos están en inglés en [docs/upstream/javafx-macos-dead-keys](../../upstream/javafx-macos-dead-keys/):
`REPORT.md`, `DeadKeyRepro.java` y `README.md` (qué pasa, la solución y cuándo quitarla).

## Qué pasa

En macOS, una tecla muerta (`` ` `` `´` `^` `¨` `~` en un teclado español; `'` y `"` en U.S.
International) se escribe como una composición del método de entrada: la marca queda pendiente
hasta la tecla siguiente. Al pulsarla otra vez, la pendiente debería confirmarse y quedar otra
pendiente. JavaFX lo hace en la 2.ª, 4.ª… pulsación, pero la 3.ª, 5.ª… llegan como una
composición nueva sin nada confirmado: el control sustituye la marca pendiente y esa pulsación
se pierde. Con cinco pulsaciones salen tres marcas, así que no se puede escribir el ``` de un
bloque de código. Las apps sobre Chromium (Electron) y las nativas de macOS no tienen el problema.

## La solución en Jylos

`DeadKeyRepeats` (`ui`, instalado en cada ventana por `Main.start`): si llega la composición del mismo acento suelto que ya estaba
pendiente y no se confirma nada, primero confirma el pendiente y después pasa la composición.
Un acento suelto es un símbolo modificador de Unicode, una letra modificadora de espaciado
(U+02B0 a U+02FF), `~`, `'` o `"`. Las demás composiciones (japonés, chino) pasan sin cambios.
Los tests reproducen las secuencias grabadas con un teclado real: `DeadKeyRepeatsTest` en un
`TextField` y un `TextArea` de una ventana abierta después de instalarlo (como un diálogo), y
`MarkdownEditorViewTest` en el editor.

## Cuándo revisarlo

En cada actualización de JavaFX, ejecutar `DeadKeyRepro.java` con esa versión y pulsar `` ` ``
cinco veces en cada control:

- **Cinco marcas en los dos:** JavaFX está arreglado. Quitar `DeadKeyRepeats` y su instalación
  en `Main.start`, mantener los tests (deben seguir pasando) y actualizar esta página y
  `WEBVIEW.md`.
- **Tres:** se mantiene.
- **Otra cosa:** la secuencia grabada en los tests ya no es la real; grabarla de nuevo con un
  teclado real antes de cambiar nada.

Un teclado simulado (`java.awt.Robot`, el `Robot` de JavaFX) no compone las teclas muertas de
forma fiable y no sirve para comprobarlo.
