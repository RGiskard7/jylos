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

## Metodología: código nuevo vs existente

### Código nuevo: TDD

Test antes que implementación. El fallo inicial demuestra que el test puede fallar: escribir el test, ejecutarlo, confirmar que falla por el motivo correcto (falta implementación, no un typo), luego escribir el mínimo código para pasarlo.

### Código existente: caracterización

No lleva rojo-verde-refactor — se caracteriza: fija el comportamiento actual como red de seguridad antes de refactorizar, sin juzgar si es correcto.

1. Escribir el test. Pasa en verde desde el primer run, porque describe comportamiento que ya existe.
2. Romper el código de producción a propósito — justo lo que el test dice proteger.
3. Confirmar que falla, y que falla con el mensaje esperado, no cualquier fallo.
4. Restaurar el código.
5. Confirmar que vuelve a pasar.

Un test que nunca se ha visto fallar no demuestra nada, por muy verde que esté la suite. Al informar de un trabajo de caracterización, di explícitamente qué rompiste y qué mensaje de fallo diste — "la suite está en verde" no es prueba por sí sola.

### Fijar un umbral de mutación

Tras caracterizar un módulo, decide si merece entrar en el gate de mutación (ver [Mutation testing](#mutation-testing) más abajo). Mide primero la puntuación real, fija el umbral en ese número medido o justo por debajo — nunca uno aspiracional, y nunca lo bajes después para que pase un PR.

## Tipos

### Unit tests

Para lógica pura sin filesystem, base de datos, JavaFX runtime ni Git. Ejemplos: parsers, Markdown, modelos de grafo/canvas, cifrado, plantillas.

Reglas: inputs pequeños, asserts directos, bordes importantes.

### Integración y contrato

Para persistencia o procesos externos: DAOs SQLite/filesystem, leer/escribir/renombrar/mover/borrar en vault, `.canvas`, frontmatter, metadata de documentos, Git, import/export.

Reglas: `@TempDir`, no escribir rutas de usuario, verificar estado tras reabrir/recargar.

En filesystem, comprobar que las colisiones conservan la extensión original y que un sidecar corrupto falla sin sobrescribirse. En paridad SQLite/filesystem, comprobar el mismo comportamiento visible aunque la persistencia interna sea distinta.

### Guardas arquitectónicas

Solo para reglas difíciles de expresar como comportamiento: `service` sin JavaFX, `data` sin UI, no volver a localizadores globales eliminados, fronteras UI/service.

No usar guardas para estilo trivial, logs, comentarios o nombres privados.

### Smoke UI

Para wiring FXML barato: carga de vistas, `fx:id` críticos, toolbars/paneles visibles, controles esenciales de visores.
El puente CodeMirror se prueba cargando el WebView real y verificando documento, edición, deshacer/rehacer, portapapeles y menú contextual de plataforma, protección de solo lectura, navegación de enlaces, resaltado de lenguajes fenced y cambio entre fuente/vista previa en vivo sin mutar texto.

No intentar automatizar QA visual complejo con unit tests.

## Mutation testing

Cobertura de líneas solo demuestra que una línea se ejecutó, no que un test se enteraría si se rompiera. Las clases de mayor riesgo — los DAOs de nota/carpeta/etiqueta en los dos backends, SQLite y filesystem, donde un bug significa pérdida silenciosa de datos — se comprueban además con mutation testing: un pase automático inyecta fallos pequeños en el código de producción y confirma que la suite realmente falla para cada uno.

CI lo exige como gate (`.github/workflows/mutacion.yml`, PIT vía `pitest-maven`) sobre esas clases. El umbral solo sube a medida que mejora la cobertura real; nunca se baja para que pase un PR. Para ejecutarlo en local:

```bash
mvn -f jylos/pom.xml org.pitest:pitest-maven:mutationCoverage \
  -DtargetClasses=<paquete.completo.NombreClase> \
  -DtargetTests=com.example.jylos.tests.*
```

Esto complementa, no sustituye, verificar por sabotaje los tests escritos a mano: romper el código a propósito, comprobar que el test da rojo con el mensaje esperado, restaurar, comprobar verde de nuevo. Una "suite en verde" sola no es prueba de que un test proteja nada — al informar, di qué se ha verificado y cómo.

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
