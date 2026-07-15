# Testing

English: [../TESTING.md](../TESTING.md)

Jylos usa JUnit 5 como gate de release. Objetivo: proteger comportamiento, compatibilidad de storage y reglas arquitectónicas; no inflar número de tests.

Ejecutar todo:

```bash
mvn -f jylos/pom.xml test
```

Compilar:

```bash
mvn -f jylos/pom.xml -DskipTests compile
```

## Tipos

### Unit tests

Para lógica pura sin filesystem, base de datos, JavaFX runtime ni Git. Ejemplos: parsers, Markdown, modelos de grafo/canvas, cifrado, plantillas.

Reglas: inputs pequeños, asserts directos, bordes importantes.

### Integración y contrato

Para persistencia o procesos externos: DAOs SQLite/filesystem, `.canvas`, frontmatter, Git, import/export.

Reglas: `@TempDir`, no escribir rutas de usuario, verificar estado tras reabrir/recargar.

### Guardas arquitectónicas

Solo para reglas difíciles de expresar como comportamiento: `service` sin JavaFX, `data` sin UI, no volver a `AppContext`, fronteras UI/service.

No usar guardas para estilo trivial, logs, comentarios o nombres privados.

### Smoke UI

Para wiring FXML barato: carga de vistas, `fx:id` críticos, toolbars/paneles visibles, controles esenciales de visores.

No intentar automatizar QA visual complejo con unit tests.

## Qué no añadir

- Tests de strings de logger.
- Nombres de métodos privados.
- Whitespace/comentarios.
- `source.contains(...)` para detalles no arquitectónicos.
- `sleep` para sincronización si hay alternativa determinista.

## Huecos conocidos

- Restauración real de scroll PDF al cambiar tabs.
- Interacciones completas de `CanvasView`.
- UI Kanban más allá del modelo.
- Tests de interacción JavaFX más amplios.
