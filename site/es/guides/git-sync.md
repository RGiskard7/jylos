# Sincronización Git

Cuando tu vault es un repositorio Git, Jylos lo versiona con Git normal — sin nube de Jylos, sin backend y sin cuenta, solo tu propio repositorio gestionado visualmente desde la app.

El soporte de Git está disponible **solo en modo vault Markdown**. En modo SQLite la UI de Git está oculta.

## El panel de sincronización Git

Abre **Herramientas → Git → Panel de sincronización Git…** (o **Ctrl/Cmd+Shift+G**) para una única ventana estilo IDE que consolida todo el flujo:

- **Estado del repositorio** — rama actual, URL del remoto y cuántos commits llevas de adelanto/retraso respecto a tu upstream (`↑n ↓n`).
- **Cambios** — una lista unificada de cambios del árbol de trabajo, cada uno con su estado (`M` modificado, `A` añadido, `D` borrado, `R` renombrado, `??` sin seguimiento, `UU` conflicto). Cada fila sin conflicto tiene una acción explícita **Preparar** / **Quitar**.
- **Ramas** — lista las ramas locales y crea nuevas.
- Campo de **mensaje de commit**.
- **Registro de actividad** — una transcripción con marca de tiempo de cada operación.

## Operaciones

| Acción | Qué hace |
| --- | --- |
| Refrescar | Hace fetch/prune de `origin` y relee estado y cambios. |
| Preparar todo | `git add -A -- .` bajo la raíz del vault. |
| Quitar todo | `git reset -q HEAD -- .` bajo la raíz del vault. |
| Commit | Confirma exactamente los ficheros preparados. |
| Pull | `git pull --no-rebase`; requiere upstream. |
| Push | `git push` normal; el primer push configura el upstream. |
| Sincronizar | Commit de lo preparado → pull → push, parando en el primer error. |
| Configurar remoto… | Valida/hace fetch de `origin` y configura el seguimiento. |
| Nueva rama… | Crea y cambia a una rama local desde un árbol limpio. |

Si el vault aún no es un repositorio, el panel muestra un aviso de **Inicializar Git**. Si `git` no está instalado, muestra un mensaje claro y desactiva las acciones.

## Garantías de seguridad

- Nada destructivo se ejecuta automáticamente; cada acción es un clic explícito.
- Sin force push, nunca.
- Sin preparación implícita: un commit nunca anula tus elecciones de preparación por fichero.
- Los repositorios anidados se mantienen aislados: Jylos nunca prepara ni confirma ficheros dentro de un submódulo.
- Los límites de tamaño de GitHub se comprueban antes de subir (los blobs de más de 100 MiB se reportan).
- Los conflictos nunca se resuelven automáticamente.
- La UI nunca se bloquea: las llamadas a Git corren fuera del hilo de JavaFX y pueden cancelarse.

## Autenticación

La autenticación la gestiona tu instalación de Git del sistema: un agente SSH, un helper de credenciales de Git o una GitHub CLI ya autenticada. Jylos no recoge credenciales.

## Relacionado

- Detalles técnicos: [GIT.md](https://github.com/RGiskard7/jylos/blob/main/docs/GIT.md)
