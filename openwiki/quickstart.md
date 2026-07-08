# Jylos OpenWiki quickstart / Inicio rápido

## English

Jylos is a local-first desktop knowledge-management app built with Java 21, JavaFX 23, Maven, and either SQLite or a filesystem Markdown vault. It focuses on Markdown notes, wiki-links, backlinks, graph navigation, a Kanban board, optional per-note encryption, plugins, and theme/snippet customization.

Start here:
- [Architecture](architecture.md)
- [Build and run](build-and-run.md)
- [Plugins](plugins.md)

## Español

Jylos es una aplicación de escritorio de gestión del conocimiento local-first construida con Java 21, JavaFX 23, Maven y SQLite o una bóveda Markdown en el sistema de archivos. Se centra en notas Markdown, wiki-links, backlinks, navegación por grafo, un tablero Kanban, cifrado opcional por nota, plugins y personalización mediante temas/snippets.

Empieza aquí:
- [Arquitectura](architecture.md)
- [Compilación y ejecución](build-and-run.md)
- [Plugins](plugins.md)

## What this repository contains / Qué contiene este repositorio

The root README describes the product as a cross-platform desktop note app with Obsidian-like workflows: wiki-links, backlinks, graph view, Kanban boards, canvas files, private notes, Git integration for vault mode, external plugins, and theme/snippet support.

El README raíz describe el producto como una app de notas de escritorio multiplataforma con flujos tipo Obsidian: wiki-links, backlinks, vista de grafo, tableros Kanban, archivos canvas, notas privadas, integración con Git en modo bóveda, plugins externos y soporte para temas/snippets.

Key source anchors / Anclas principales:
- `README.md` — product overview, feature list, commands, and troubleshooting.
- `README.es.md` — Spanish product overview and mirrored usage guidance.
- `AGENTS.md` — contributor and agent workflow notes.
- `docs/ARCHITECTURE.md` — runtime architecture and source package map.
- `docs/ARCHITECTURE_GUIDELINES.md` — boundaries for future code changes.
- `docs/BUILD.md` — build/run/test commands.
- `docs/PLUGINS.md` — plugin extension points and lifecycle.
- `docs/GIT.md` — Git workflow for vault mode.

Puntos clave del código / source anchors:
- `README.md` — visión del producto, funcionalidades, comandos y troubleshooting.
- `README.es.md` — visión del producto y guía equivalente en español.
- `AGENTS.md` — notas para contribuidores y agentes.
- `docs/ARCHITECTURE.md` — arquitectura en tiempo de ejecución y mapa de paquetes.
- `docs/ARCHITECTURE_GUIDELINES.md` — límites para cambios futuros.
- `docs/BUILD.md` — comandos de compilación, ejecución y pruebas.
- `docs/PLUGINS.md` — puntos de extensión y ciclo de vida de plugins.
- `docs/GIT.md` — flujo Git para el modo bóveda.

## Repository map / Mapa del repositorio

- `jylos/` — Maven module for the desktop app.
- `plugins-source/` — sample/built-in plugin sources.
- `themes/` — external theme sources copied into the app runtime.
- `scripts/` — build, launch, plugin, and theme helper scripts.
- `docs/` — canonical documentation that this wiki mirrors and refines.
- `resources/` — screenshots and other repo assets used by the README.

- `jylos/` — módulo Maven de la aplicación de escritorio.
- `plugins-source/` — fuentes de plugins de ejemplo/integrados.
- `themes/` — fuentes de temas externos copiadas al runtime de la app.
- `scripts/` — scripts auxiliares de build, lanzamiento, plugins y temas.
- `docs/` — documentación canónica que este wiki refleja y afina.
- `resources/` — capturas y otros recursos usados por el README.

## Working model / Modelo de trabajo

This is a desktop monolith, not a client/server app. The main flow is:

`ui/` controllers and views → `service/` business rules → `data/dao/` storage implementations

The app can run against either:
- **SQLite** by default, or
- a **filesystem Markdown vault** with YAML frontmatter and optional Git integration.

Important change rule from `AGENTS.md` and `docs/ARCHITECTURE.md`:
- keep `MainController` thin;
- put self-contained feature logic in `*Controller` or `*Support` classes with `wire(...)`-style wiring;
- route persistence through services/DAOs rather than direct UI code.

Este es un monolito de escritorio, no una app cliente/servidor. El flujo principal es:

`ui/` controllers and views → `service/` business rules → `data/dao/` storage implementations

La app puede ejecutarse sobre:
- **SQLite** por defecto, o
- una **bóveda Markdown en filesystem** con frontmatter YAML e integración opcional con Git.

Regla importante de cambio tomada de `AGENTS.md` y `docs/ARCHITECTURE.md`:
- mantener `MainController` delgado;
- colocar la lógica autocontenida en clases `*Controller` o `*Support` con wiring tipo `wire(...)`;
- enrutar persistencia por services/DAOs en lugar de código UI directo.

## When making changes / Cuando hagas cambios

Use these docs as the canonical entry points:
- architecture or layering changes → [Architecture](architecture.md)
- build, run, packaging, or tests → [Build and run](build-and-run.md)
- plugin API or plugin runtime behavior → [Plugins](plugins.md)

If a change affects the README, architecture, or build instructions, update the corresponding source doc too so the wiki stays aligned with the repository.

Usa estas páginas como puntos de entrada canónicos:
- cambios de arquitectura o capas → [Arquitectura](architecture.md)
- build, ejecución, empaquetado o pruebas → [Compilación y ejecución](build-and-run.md)
- API de plugins o comportamiento en runtime → [Plugins](plugins.md)

Si un cambio afecta el README, la arquitectura o las instrucciones de build, actualiza también el documento fuente correspondiente para mantener alineado el wiki con el repositorio.
