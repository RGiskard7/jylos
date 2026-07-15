# Git

English: [../GIT.md](../GIT.md)

La integración Git solo aplica en modo filesystem vault cuando la raíz del vault está dentro de un repositorio Git.

## Qué ofrece

- Estado del repo en status bar.
- Lista de cambios.
- Preparar/despreparar archivos.
- Commit con mensaje.
- Pull, push y sincronizar.
- Configuración de remoto.

## Arquitectura

| Capa | Rol |
|------|-----|
| `git/GitService` | Operaciones Git y lectura de estado |
| `ui/components/GitSyncPanel` | Panel visual de cambios/sync |
| `ui/controller/GitController` | Wiring, acciones de menú/status y diálogos |

La UI no ejecuta comandos Git directamente. Pasa por `GitService`.

## Comportamiento

- Si no hay repo, se muestra estado sin remoto/repositorio.
- Adjuntos del vault aparecen como cambios igual que notas `.md`.
- Las operaciones largas deben ir fuera del FX thread.
- Errores Git se muestran como feedback de UI y se loguean.

## Limitaciones

- No sustituye un cliente Git completo.
- No gestiona conflictos complejos visualmente.
- No firma commits.
