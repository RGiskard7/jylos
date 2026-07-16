# Contrato de EventBus

English: [../EVENT_BUS_CONTRACT.md](../EVENT_BUS_CONTRACT.md)

`EventBus` coordina eventos tipados dentro del proceso. No debe usarse como llamada directa disfrazada.

## Permitido

- Fan-out tras cambios de dominio.
- Refresh de varias zonas tras guardar/borrar/restaurar.
- Acciones shell (`SystemActionEvent`) disparadas desde toolbar, menú o paleta.
- Extensibilidad de plugins.

## Evitar

- Flujos uno-a-uno con owner claro.
- Peticiones que necesitan valor de retorno inmediato.
- Lógica crítica escondida en cadenas de eventos.
- Suscripciones sin teardown.

## Reglas

- Eventos con nombre explícito.
- Un owner claro de publicación por workflow.
- Suscripciones cerca de la feature propietaria.
- Nada de loops de refresh.
- Componentes UI con owner claro deben usar callbacks, no singleton global.

Ejemplos de flujos que no van por bus: tema, mensajes de estado, apertura interna de nota, modificación del editor.
