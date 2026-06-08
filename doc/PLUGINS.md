# Plugins

## Model

External plugins are JAR files loaded at startup. The core app does not import concrete plugin classes.

Search paths include `plugins/` under the application base directory (`AppDataDirectory.getBaseDirectory()`), plus paths used when the app is packaged (see `PluginLoader.java`).

## Build sample plugins

From repository root:

```bash
./scripts/build-plugins.sh
```

```powershell
.\scripts\build-plugins.ps1
```

Compiles sources under `plugins-source/` with **`javac --release 21`** and writes JARs to **`jylos/plugins/`** (created if missing). JARs built for Java 21+ will not load (`UnsupportedClassVersionError`).

## Authoring

- Extend `AbstractPlugin` in `com.example.jylos.plugin`.
- Register stable command ids via `PluginIds` when exposing palette commands.
- Built-in Mermaid support lives in `plugins-source/.../mermaid/` (rebuild after changes).

## Lifecycle

1. Discover JARs in plugin directories.
2. Load with dedicated classloaders (per-plugin isolation).
3. Register metadata, menu entries, preview enhancers, side panels; initialize enabled plugins.
4. Disable: unregister UI hooks and commands; shut down classloaders on app exit.

## Notes

- A failing or incompatible JAR should log a warning and be skipped — it must not prevent other plugins or app startup (`PluginLoader` catches `Throwable` on load).
- Shut down plugins and close classloaders on exit to avoid leaks.
- Re-enable after disable re-runs initialization (see plugin manager UI).
