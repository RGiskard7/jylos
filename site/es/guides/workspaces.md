# Workspaces

Los workspaces te permiten guardar y restaurar grupos de notas abiertas y el estado del layout, útil para alternar entre contextos de escritura, investigación o personales.

Los workspaces se guardan en los datos locales de Jylos (nunca dentro de tus notas) y funcionan en modo SQLite y en modo vault Markdown.

## Usar workspaces

**Archivo → Workspaces** (también en la paleta de comandos bajo *Workspace:*):

| Acción | Qué hace |
| --- | --- |
| Guardar workspace actual | Actualiza el workspace activo, o se comporta como *Guardar como* si no hay ninguno. |
| Guardar workspace actual como… | Pide un nombre y guarda el estado actual. |
| Abrir workspace… | Elige un workspace guardado y lo restaura. |
| Gestionar workspaces… | Abre o borra workspaces guardados. |

Flujo típico: abre las notas que quieras → *Guardar workspace actual como…* → nómbralo → más tarde, *Abrir workspace…* para traer de vuelta esas notas.

## Qué guarda un workspace

- **Nombre**, id y marcas de tiempo.
- **Pestañas de notas abiertas** (en orden) y la nota activa.
- **Modo de vista** — editor / dividido / vista previa.
- **Layout básico** — modo concentración, visibilidad de la barra lateral y posiciones de los divisores.
- **Modo de almacenamiento** en el momento de guardar (para avisar de desajustes).

No se guarda (todavía): los filtros del grafo y la selección/expansión del árbol de carpetas.

## Comportamiento

- **La restauración es aditiva** — reabrir un workspace no cierra pestañas que ya tenías abiertas.
- **Las notas ausentes no fallan** — una nota que ya no existe se omite con un mensaje de estado.
- **Desajuste de almacenamiento** — se muestra un aviso no bloqueante si el workspace se guardó con otro modo de almacenamiento.

## Relacionado

- Detalles técnicos: [WORKSPACES.md](https://github.com/RGiskard7/jylos/blob/main/docs/WORKSPACES.md)
