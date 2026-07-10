# Plugins

Jylos supports external plugins as JAR files loaded at startup. The core application does not import concrete plugin classes directly.

Primary source references:
- `docs/PLUGINS.md`
- `docs/ARCHITECTURE.md`
- `README.md`
- `AGENTS.md`

## Plugin model

Plugins are discovered from the application’s plugin directories, including `plugins/` under the app base directory.

A failing or incompatible plugin should be skipped with a warning rather than blocking app startup.

## Building sample plugins

The repository provides helper scripts to compile sources under `plugins-source/` and write JARs to `jylos/plugins/`.

```bash
./scripts/build-plugins.sh
```

```powershell
.\scripts\build-plugins.ps1
```

These sample plugins are compiled with `javac --release 21`, so they must match the app’s Java version.

## Authoring plugins

The documented pattern is to extend `AbstractPlugin` in `com.example.jylos.plugin` and use stable command ids from `PluginIds` when exposing palette actions.

The public extension points in `PluginContext` include:
- command-palette registrations,
- menu items,
- side panels,
- preview enhancers,
- toolbar buttons,
- editor hooks,
- note-open requests,
- refresh requests,
- and typed event-bus access.

## Editor hooks

Plugins can hook the editor lifecycle with:
- `onBeforeTextInsert(...)`
- `onBeforeSave(...)`
- `onAfterSave(...)`

The docs note an important constraint: hooks run on the JavaFX Application Thread and must stay fast. Exceptions are logged and skipped, and hooks are removed when the plugin is disabled.

## Lifecycle

The documented lifecycle is:
1. discover plugin JARs,
2. load with per-plugin classloaders,
3. register metadata and UI hooks,
4. initialize enabled plugins,
5. disable and clean up on shutdown.

This design gives plugin isolation and allows re-enable after disable through the plugin manager UI.

## Things to watch when changing plugin support

- Keep plugin classes out of core hardcoded references.
- Preserve classloader isolation and cleanup.
- Preserve the typed callback path for `requestOpenNote(...)`.
- Keep plugin startup failure non-fatal.
- Rebuild sample plugins after changing plugin-source code.
