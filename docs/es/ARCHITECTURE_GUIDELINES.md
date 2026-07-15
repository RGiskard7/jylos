# Guía de arquitectura

English: [../ARCHITECTURE_GUIDELINES.md](../ARCHITECTURE_GUIDELINES.md)

Reglas normativas para crecer Jylos sin reescrituras ni degradar límites.

## Alcance

- Aplica a código nuevo.
- Aplica a código existente cuando se toca por una tarea real.
- No exige refactors amplios solo por cumplir reglas mecánicas.

## Postura arquitectónica

Jylos es un monolito desktop con núcleo por capas y subsistemas de feature:

```text
ui/ -> service/ -> data/dao/
          |
          +-> graph/, git/, search/, workspace/, insights/, plugin/
```

No busca DI pesado, hexagonal estricta ni reescritura por slices. Busca responsabilidades claras y crecimiento predecible.

## Dirección de dependencias

Permitido por defecto:

1. `ui/*` depende de servicios, modelos, eventos tipados y APIs de features que coordina.
2. `service/*` depende de DAOs, modelos, utilidades y colaboradores internos.
3. `data.dao/*` depende de modelos, helpers de almacenamiento e infraestructura baja.
4. `data.models/*` no depende de UI, DAOs ni JavaFX.

Prohibido por defecto:

- `service/*` dependiendo de JavaFX/UI.
- `data/*` dependiendo de `ui/*`.
- controladores llamando storage directo si ya existe servicio owner.
- features entrando en internals UI ajenos sin API/callback pequeño.

## UI

Nombres:

- `*Controller`: controller FXML o coordinador de feature con API estable.
- `*Support`: helper propiedad de otro controller, una conducta cohesionada.
- `*Store`: persistencia de preferencias/estado UI.
- `*Catalog`: catálogo o cache de recursos/opciones UI.
- `*Operations`: helper imperativo pequeño; no bolsa de todo.
- Otros roles estrechos ya existentes: `*Command`, `*Routing`, `*Registry`, `*Layout`, `*Initialization`, `*Settings`, `*Management`, `*Lifecycle`, `*Ui`.

Si una clase no encaja, parar y renombrar/mover antes de añadir lógica.

## `MainController`

Puede poseer bootstrap, wiring, apertura/guardado/cierre/tabs/navegación y dispatch de acciones shell. No debe contener cuerpos de features autocontenidas.

## Inyección

- Helpers nuevos propiedad de `MainController`: preferir `wire(...)`.
- Controllers creados por FXML: setters aceptables.
- Evitar mezclar `wire(...)`, setters dispersos y lookups globales en la misma clase.

## Servicios

`service/*` es capa de aplicación/negocio, no API HTTP. Puede contener servicios de entidad, feature, técnicos e índices.

Reglas:

- exponer operaciones de caso de uso;
- encapsular reglas compartidas;
- coordinar DAOs cuando haga falta;
- no depender de UI JavaFX;
- no convertirse en helpers estáticos genéricos.

Relaciones nota-tag: `TagService` es owner canónico.

## Feature packages

`graph`, `git`, `search`, `workspace`, `insights`, `plugin` son límites válidos cuando una capacidad tiene vocabulario propio, cruza capas y no encaja limpiamente en una sola capa.

## EventBus

`EventBus` es pub-sub in-process estilo Observer. No es service locator ni sustituto de llamadas directas.

Buenos usos:

- fan-out de refresh;
- coordinación cross-feature;
- extensibilidad de plugins;
- acciones shell disparadas desde varios sitios.

Malos usos:

- llamadas padre-hijo simples;
- request/response con retorno inmediato;
- invariantes críticas cuyo orden debe ser explícito;
- esconder mutaciones core en cadenas de eventos.

UI uno-a-uno como abrir nota, cambiar tema, marcar editor modificado o mostrar estado debe usar callbacks.

## Checklist de review

Antes de merge:

1. ¿La clase pertenece claramente a `ui`, `service`, `data`, `util` o feature package?
2. ¿El nombre refleja rol real?
3. ¿Dependencias explícitas y acotadas?
4. ¿La UI evita política de storage?
5. ¿Servicios sin JavaFX?
6. ¿DAOs sin UI?
7. ¿EventBus solo para fan-out/extensibilidad real?
8. ¿Tests o guardas cubren el límite tocado?
9. ¿Docs actualizadas si cambia arquitectura?
