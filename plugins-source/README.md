# Plugin sources

Java sources for sample external plugins. The core application does not compile these into the main JAR.

## Build

From repository root:

```bash
./scripts/build-plugins.sh
```

Output directory: **`jylos/plugins/`** (JAR files loaded at runtime).

## Guidelines

- Implement the plugin API under `com.example.jylos.plugin` (prefer `AbstractPlugin`).
- Plugins are compiled with **Java 17** (`--release 17`); do not target newer bytecode.
- Use stable command ids (`PluginIds`) for palette entries.
- Do not add plugin-specific code to the core module.
- Test enable/disable and shutdown via the in-app plugin manager.
