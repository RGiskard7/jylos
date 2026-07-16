# Grafo

English: [../GRAPH.md](../GRAPH.md)

Jylos incluye vista de grafo y análisis de conocimiento sobre notas, enlaces y tags.

## Grafo visual

- Vista global: todas las notas y enlaces resueltos.
- Vista local: nota actual y vecinos a profundidad configurable.
- Nodos opcionales de tags.
- Zoom, pan, drag y click para abrir nota.

## Arquitectura

| Capa | Tipo | Rol |
|------|------|-----|
| Modelo | `graph/GraphData` | Nodos/aristas inmutables |
| Builder | `graph/GraphBuilder` | Construye grafo desde servicios |
| Resolución | `WikiLinkResolver` | Misma semántica que preview/backlinks |
| UI | `GraphController` | Configuración, tareas background, acciones |
| Render | `GraphCanvas` | Canvas JavaFX y simulación de fuerzas |
| Insights | `KnowledgeInsightsService` | Métricas y listas de salud del grafo |

## Reglas

- Construcción pesada fuera del FX thread.
- Simulación se enfría; grafo inactivo no debe consumir CPU.
- Abrir nodo pasa por shell, no por acceso directo a editor.
- Búsqueda de links usa `WikiLinkResolver`, no parser duplicado.

## Knowledge Insights

Muestra resumen, notas más conectadas, huérfanas, enlaces rotos, notas sin etiquetas y uso de etiquetas. Es orientación, no métrica absoluta.
