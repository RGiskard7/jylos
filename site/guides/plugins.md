# Plugins

Jylos loads external plugin JARs from `plugins/` under the application base directory, with a safe load/disable lifecycle and a manager UI.

## Built-in plugins

These ship with Jylos as first-party plugins built into JARs:

- **Dataview** — query your notes' metadata like a database (see the [Dataview guide](/guides/dataview)).
- **MCP Server** — expose your vault to MCP clients over a local HTTP server.
- **Publish** — export the whole vault to a self-contained static site.
- **Outline** — a note outline in a side panel.
- **Calendar** — a calendar view of your notes.
- **Daily Notes** — create a note for today.
- **Reading Time** — show estimated reading time.
- **Auto Backup** — schedule vault backups.
- **Templates** — create notes from templates.
- **AI** — AI assistant integration.
- **Word Count** — word/character counts.
- **Table of Contents** — generate a table of contents for a note.

Mermaid diagram support is compiled **into the core app** rather than shipped as a plugin JAR, so it is always available.

## Install and remove

- **Tools → Manage plugins → Install plugin…** selects a `.jar` and copies it into your user plugin directory, loading it when possible.
- **Tools → Manage plugins → Remove** shuts the plugin down, removes its UI contributions, closes its classloader and deletes the JAR.

You can also drop a JAR into the plugins directory manually and restart.

## Plugin API

A plugin extends `AbstractPlugin` and receives a `PluginContext` exposing these extension points:

- `registerCommand(...)` — command-palette entries.
- `registerMenuItem(...)` — entries in the dynamic plugin menu.
- `registerSidePanel(...)` — a node in the right panel.
- `registerPreviewEnhancer(...)` — CSS/JS and per-note HTML post-processing for the Markdown preview.
- `registerToolbarButton(...)` — a button in the main toolbar.
- `registerEditorHook(...)` — `onBeforeTextInsert`, `onBeforeSave`, `onAfterSave`.
- `registerEditorBlockRenderer(...)` — render a fenced block inline in Live Preview.
- Event bus access, themed dialogs, progress dialogs and per-plugin preferences.

Each plugin gets its own classloader with an enforced boundary: it can reach the documented plugin API surface, but requests for other internal `com.example.jylos.*` classes fail fast.

## Build from source

Plugin sources live in `plugins-source/` and build to `jylos/plugins/` with:

```bash
./scripts/build-plugins.sh
```

```powershell
.\scripts\build-plugins.ps1
```

## Related

- Technical details: [PLUGINS.md](https://github.com/RGiskard7/jylos/blob/main/docs/PLUGINS.md)
- [Dataview guide](/guides/dataview)
- [MCP Server](https://github.com/RGiskard7/jylos/blob/main/docs/MCP.md)
