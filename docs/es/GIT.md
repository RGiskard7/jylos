# Git

English: [../GIT.md](../GIT.md)

La integración Git solo aplica en modo filesystem vault cuando la raíz del vault está dentro de un repositorio Git.

## Panel Git Sync

`Herramientas → Git → Panel Git Sync…` (atajo **Ctrl/Cmd+Shift+G**) abre el espacio
único de trabajo Git:

- Estado de repositorio: rama, remoto, upstream y commits pendientes de subir/bajar.
- Lista de cambios acotada a la bóveda, con estados preparados y sin preparar
  separados si un archivo cambia de nuevo tras prepararlo.
- Preparar/despreparar por archivo, o todo el contenido de la bóveda.
- Mensaje de commit, commit, pull, push, sincronización y configuración de remoto.
- Creación y cambio de ramas locales cuando la bóveda es la raíz del repositorio.
- Registro de actividad con marca temporal. Crece con el diálogo redimensionable; la
  operación activa muestra el progreso más reciente de Git y se puede cancelar.

## Arquitectura

| Capa | Rol |
|------|-----|
| `git/GitService` | Operaciones Git y lectura de estado |
| `ui/components/GitSyncPanel` | Panel visual de cambios/sync |
| `ui/controller/GitController` | Wiring, acciones de menú/status y diálogos |

La UI no ejecuta comandos Git directamente. Pasa por `GitService`.

## Comportamiento

- Si no hay repo, se muestra estado sin remoto/repositorio.
- La lista muestra cualquier cambio bajo la bóveda, incluidos adjuntos y archivos de soporte.
- Si un archivo se modifica de nuevo después de prepararlo, el panel muestra por separado
  el estado preparado y el cambio posterior sin preparar.
- Si un repositorio Git anidado registrado como submódulo tiene cambios internos sin
  confirmar, el panel lo marca como bloqueante y no como archivo preparable. Esos cambios
  se confirman primero desde ese repositorio; la bóveda padre solo puede preparar su gitlink.
- `Commit` confirma solo los archivos ya preparados; no ejecuta `git add` implícito.
- Jylos nunca prepara ni confirma archivos internos de un submódulo o repositorio Git anidado;
  informa del bloqueo en lugar de indicar un preparado que Git no puede realizar.
- Para remotos de GitHub, Jylos revisa el historial alcanzable antes de subir e informa de blobs
  superiores al límite de 100 MiB. Hay que quitarlos del historial o usar Git LFS; borrarlos
  solo del árbol de trabajo no basta para el primer push.
- `Stage All` y `Unstage All` se limitan a la raíz de la bóveda, incluso si está dentro de un repositorio padre.
- Si existen cambios preparados fuera de una bóveda anidada, Jylos rechaza el commit para no incluir trabajo ajeno.
- `Pull` requiere upstream. El primer `Push` configura el upstream de la rama actual.
- El botón `Actualizar` del panel es el único que hace `fetch --prune`; la barra inferior no usa red.
- El panel muestra la operación en curso, el último progreso nativo de Git y un registro con cada resultado. Las transferencias remotas no tienen un timeout fijo arbitrario: una bóveda grande puede tardar en empaquetarse y subirse, pero el usuario puede cancelar explícitamente la operación.
- El selector de rama permite crear o cambiar ramas locales solo con el árbol limpio. En una bóveda anidada se bloquea, porque cambiaría también el repositorio padre.
- Las operaciones largas deben ir fuera del FX thread.
- Errores Git se muestran como feedback de UI y se loguean.
- Jylos nunca borra `.git/index.lock`: informa del bloqueo para que el usuario cierre el otro proceso Git.

## SSH con GitHub

Jylos usa el ejecutable Git instalado en el sistema. Por tanto, SSH se configura una
vez en el sistema y el panel Git Sync lo reutiliza sin guardar credenciales.

1. Comprueba si ya tienes una clave pública:

   ```bash
   ls ~/.ssh/id_ed25519.pub
   ```

2. Si no existe, créala. Salvo que quieras otro nombre de archivo, acepta la ruta por
   defecto:

   ```bash
   ssh-keygen -t ed25519 -C "tu-correo@example.com"
   ```

3. Inicia el agente y carga la clave privada. En macOS:

   ```bash
   eval "$(ssh-agent -s)"
   ssh-add --apple-use-keychain ~/.ssh/id_ed25519
   ```

   En Linux o Git Bash usa `ssh-add ~/.ssh/id_ed25519`. Si elegiste otro nombre, como
   `key_github`, sustitúyelo en estos comandos.

4. Copia la **clave pública** y añádela en GitHub: **Settings → SSH and GPG keys → New
   SSH key**. En macOS:

   ```bash
   pbcopy < ~/.ssh/id_ed25519.pub
   ```

   En otros sistemas usa `cat ~/.ssh/id_ed25519.pub` y copia la línea completa. No
   compartas ni subas nunca el archivo privado, el que no termina en `.pub`.

5. Verifica la conexión:

   ```bash
   ssh -T git@github.com
   ```

6. En `Configurar remoto…`, usa la URL SSH de GitHub, por ejemplo:

   ```text
   git@github.com:OWNER/REPOSITORY.git
   ```

En el primer push la rama no tiene upstream todavía. Es normal: Jylos lo configura al
terminar correctamente el `git push --set-upstream origin <rama>` estándar.

## Limitaciones

- No sustituye un cliente Git completo.
- No gestiona conflictos complejos visualmente.
- No firma commits.
- No resuelve conflictos, rebases, merges, resets ni operaciones forzadas visualmente; para eso usa un cliente Git completo.
- Usa la autenticación del Git del sistema (SSH agent, credential helper o GitHub CLI); Jylos no guarda credenciales.
