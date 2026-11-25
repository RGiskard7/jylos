<script setup>
import { data } from "../.vitepress/data/plugins.data.ts";
</script>

# Plugins

There are two kinds of plugins:

- **Core plugins** are Jylos's own. They come with the app, are updated with it, and you turn
  them on or off in **Tools → Manage plugins**.
- **Community plugins** are made by other people. You install them in your plugins folder; see
  [Community plugins](/guides/community-plugins).

## Core plugins

<table class="catalog-table">
  <thead><tr><th>Plugin</th><th>What it does</th><th>Default</th></tr></thead>
  <tbody>
    <tr v-for="p in data.core" :key="p.id">
      <td><strong>{{ p.name }}</strong></td>
      <td>{{ p.description.en }}</td>
      <td>{{ p.enabledByDefault ? "On" : "Off" }}</td>
    </tr>
  </tbody>
</table>

MCP Server opens a local port, and Publish and Auto Backup write files outside your vault, so
they start off until you want them. When you update Jylos, each plugin stays as you had it.

See also the [Dataview guide](/guides/dataview).

## Restricted mode

Community plugins are code by other people with full access to your computer and your notes, so
Jylos loads none until you allow them: in **Tools → Manage plugins**, turn on the switch of the
*Community plugins* section and confirm. A new install starts in restricted mode.

## Install and remove community plugins

- **Install plugin…** copies a `.jar` into your plugins folder and loads it.
- Or **Open plugins folder**, copy the JAR there and press **Reload plugins**; no restart needed.
- **Remove** on a community plugin's card shuts it down and deletes its JAR.

A plugin can say in its `MANIFEST.MF` the earliest Jylos it works with (`Plugin-Min-Jylos`); if
yours is older, it is not loaded and the plugin manager says which version it needs.

## Plugin API

A plugin extends `AbstractPlugin` and receives a `PluginContext` exposing these extension points:

- `registerCommand(...)` — command-palette entries.
- `registerMenuItem(...)` — entries in the dynamic plugin menu.
- `registerSidePanel(...)` — a node in the right panel.
- `registerPreviewEnhancer(...)` — CSS/JS and per-note HTML post-processing for the Markdown preview.
- `registerToolbarButton(...)` — a button in the main toolbar.
- `registerStatusBarItem(...)` — an item in the status bar, which the plugin keeps and updates.
- `registerEditorHook(...)` — `onBeforeTextInsert`, `onBeforeSave`, `onAfterSave`.
- `registerEditorBlockRenderer(...)` — render a fenced block inline in Live Preview.
- Event bus access, themed dialogs, progress dialogs and per-plugin preferences.

Each plugin gets its own classloader with an enforced boundary: it can reach the documented plugin API surface, but requests for other internal `com.example.jylos.*` classes fail fast.

## Build from source

The core plugins' sources live in `plugins-source/` and build to `jylos/plugins/` with:

```bash
./scripts/build-plugins.sh
```

```powershell
.\scripts\build-plugins.ps1
```

## Related

- Technical details, including the manifest a community plugin needs: [PLUGINS.md](https://github.com/RGiskard7/jylos/blob/main/docs/PLUGINS.md)
- [Community plugins](/guides/community-plugins)
- [Dataview guide](/guides/dataview)
- [MCP Server](https://github.com/RGiskard7/jylos/blob/main/docs/MCP.md)
