# Espacios de trabajo

English: [../WORKSPACES.md](../WORKSPACES.md)

> Un espacio de trabajo es un contexto con nombre (escritura, investigación, un proyecto):
> sus pestañas abiertas, el atrás y adelante de cada una, la pestaña activa, el modo de vista
> y la disposición de los paneles. Abrir uno sustituye las pestañas abiertas por las suyas.

Se guardan en los datos locales de Jylos, nunca dentro de tus notas, y funcionan en SQLite y
en bóveda Markdown. Guía de uso: [site/es/guides/workspaces.md](../../site/es/guides/workspaces.md).

## Uso

La **barra de estado** muestra el espacio en uso ("Sin espacio" si no hay ninguno). Su menú
lista "Sin espacio" y los 8 espacios usados más recientemente (por nombre; la preferencia
`workspace.recent` guarda los ids), guarda el que está en uso, guarda como espacio nuevo,
abre el diálogo de gestión ("Todos los espacios (n)…" si hay más de 8) y activa o desactiva
*Guardar el espacio al cambiar a otro*. El diálogo de gestión tiene un campo de búsqueda que
no distingue mayúsculas ni tildes; Enter abre el primero encontrado.

**Sin espacio** es un contexto más: al abrir un espacio desde él se guardan sus pestañas,
disposición e historial (en `session.dat`, con el id reservado `jylos:no-workspace`), y al
elegirlo de nuevo se guarda el espacio en uso (si está activado) y vuelven.

**Archivo → Espacios de trabajo** y la paleta de comandos (*Espacio:*) tienen las mismas acciones:

| Acción | Qué hace |
|--------|----------|
| Guardar espacio actual | Actualiza el espacio en uso; si no hay ninguno, funciona como *Guardar como*. |
| Guardar espacio actual como… | Pide un nombre y guarda el estado actual (el mismo nombre sobrescribe); ese espacio pasa a estar en uso. |
| Gestionar espacios… | Abrir (también con doble clic), renombrar o eliminar espacios. *Espacio: abrir…* de la paleta también lo abre. |

## Qué guarda

- **nombre**, id y fechas de creación y modificación
- **pestañas abiertas** (ids de notas y el grafo, en orden) y la **pestaña activa**
- **el atrás y adelante de cada pestaña**, hasta 20 pasos cada uno, y **si está fijada** (en `workspace-history.dat`)
- **modo de vista**: edición, dividido o lectura
- **disposición básica**: modo concentración, barra lateral visible u oculta y los dos divisores
- **modo de almacenamiento** al guardar (para avisar si no coincide)

Las pestañas nuevas vacías no se guardan: sus ids no se conservan. Tampoco los filtros del
grafo ni la selección del árbol de carpetas.

## Comportamiento

- **Abrir sustituye las pestañas.** Con *Guardar el espacio al cambiar a otro* activado (por
  defecto; preferencia `workspace.save_on_switch`), primero se guarda el espacio en uso.
  Después se cierran todas las pestañas, tras preguntar por los cambios sin guardar de la nota
  abierta (cancelar lo deja todo como estaba), y se abren las del espacio con su historial.
- **Vuelve la última sesión.** Al cerrar, las pestañas abiertas, su historial y el espacio en
  uso se guardan en `session.dat` y `session-history.dat`; el siguiente arranque los reabre.
  Qué reabre es una preferencia de la bóveda (Preferencias > Bóveda > *Al abrir la bóveda*,
  `VaultSettings.startup`): todas las pestañas de la última vez o solo las fijadas, y delante
  la pestaña que estaba activa, una pestaña nueva, la página de inicio o la nota diaria de hoy
  (`StartupSupport`). Al abrir otra bóveda solo se aplica lo de delante.
- **Pestañas fijadas.** El menú de una pestaña (clic derecho) la fija: las fijadas van primero,
  muestran una chincheta en lugar de la × de cerrar (un clic en ella la desfija) y conservan lo
  que muestran: lo que se abre desde una va a una pestaña nueva, atrás y adelante traen delante
  la pestaña fijada en lugar de quitarle su nota, y *Cerrar las demás* las respeta
  (`EditorTabsModel`).
- **Página de inicio.** Una nota o un lienzo de la bóveda (Preferencias > Bóveda), que se abre
  con *Ir a la página de inicio*: la casa de la barra superior (Cmd/Ctrl o clic central: en una
  pestaña nueva), el menú Ver o la paleta de comandos. La casa y su entrada del menú solo
  aparecen si la bóveda tiene página de inicio.
- **Las notas que faltan no dan error.** Una nota que ya no existe se omite, en las pestañas y
  en su historial; al abrir un espacio, la barra de estado dice cuántas se omitieron.
- **Otro modo de almacenamiento.** Aviso sin bloquear; se abre lo que se pueda.
- **Se permiten espacios vacíos.**
- **Las líneas corruptas se ignoran** en cualquiera de estos ficheros.

## Ficheros

Todos en `<appData>/data/`:

| Fichero | Contenido |
|---------|-----------|
| `workspaces.dat` | Un espacio por línea (`Workspace.serialize()`). Mantiene su forma, así que las versiones anteriores lo siguen leyendo. |
| `workspace-history.dat` | Una línea por id de espacio: el atrás y adelante de cada pestaña, en el orden de sus pestañas, y una tercera parte `pinned` si está fijada (las versiones anteriores leen las dos primeras y la dejan). |
| `session.dat`, `session-history.dat` | La última sesión, con las mismas formas; su id y nombre son los del espacio en uso. También las pestañas de "Sin espacio", con el id `jylos:no-workspace`. |

## Arquitectura

| Capa | Tipo | Rol |
|------|------|-----|
| Modelo | `Workspace`, `TabHistory` | Records inmutables; `Workspace` con parse/serialize |
| Persistencia | `WorkspaceRepository`, `WorkspaceHistoryRepository`, `SessionStore` | Los ficheros de arriba |
| Servicio | `WorkspaceService` | Listar, guardar (con historial), renombrar, eliminar |
| Controller | `WorkspaceController` | Indicador y menú de la barra de estado, diálogos, cambio de espacio y última sesión |
| Pestañas | `TabsSupport`, `EditorTabsModel` | Cerrar todas, reabrir con historial, leer el historial |
| Host | `MainController` | Captura el estado vivo y aplica la disposición |
