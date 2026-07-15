# Workspaces

English: [../WORKSPACES.md](../WORKSPACES.md)

Los workspaces guardan y restauran grupos de notas abiertas y estado básico de layout. Sirven para cambiar entre contextos de trabajo.

Se guardan en datos locales de Jylos, nunca dentro de tus notas, y funcionan en SQLite y vault Markdown.

## Uso

**Archivo -> Workspaces** o paleta de comandos `Workspace:*`.

| Acción | Resultado |
|--------|-----------|
| Save Current Workspace | Actualiza workspace activo o actúa como Save As |
| Save Current Workspace As... | Pide nombre y guarda estado |
| Open Workspace... | Restaura workspace |
| Manage Workspaces... | Abrir o borrar workspaces |

## Qué guarda

- nombre, id, timestamps;
- tabs abiertas y nota activa;
- modo editor/split/preview;
- focus mode, sidebar visible y posiciones de split panes;
- storage mode para avisar si no coincide.

## No guarda todavía

- filtros del grafo;
- selección/expansión del árbol de carpetas.

## Comportamiento

- Restaurar es aditivo: no cierra tabs ya abiertas.
- Notas inexistentes se omiten con aviso no bloqueante.
- Si storage mode no coincide, avisa y abre lo que pueda.
- Workspaces vacíos son válidos.
- Líneas corruptas se ignoran, no rompen carga.

## Arquitectura

| Capa | Tipo | Rol |
|------|------|-----|
| Modelo | `Workspace` | Record inmutable y parse/serialize |
| Persistencia | `WorkspaceRepository` | Lee/escribe `data/workspaces.dat` |
| Servicio | `WorkspaceService` | List/save/update/delete |
| Controller | `WorkspaceController` | Diálogos y acciones |
| Host | `MainController` | Captura y aplica estado vivo |
