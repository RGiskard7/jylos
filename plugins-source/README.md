# Plugin sources

Java sources of Jylos's own plugins (core plugins). They are built into JARs that the
installers ship next to the app; the core application does not compile them into its JAR.
See [docs/PLUGINS.md](../docs/PLUGINS.md) for core and community plugins.

## Build

From repository root:

```bash
./scripts/build-plugins.sh
```

Output directory: **`jylos/plugins/`**. Each JAR's manifest carries `Plugin-Class`,
`Plugin-Id` and `Plugin-Min-Jylos` (the version they are built with). `scripts/test-plugins.sh`
builds them and runs `plugins-test/`, which checks those manifests too.

## Guidelines

- Implement the plugin API under `com.example.jylos.plugin` (prefer `AbstractPlugin`).
- A new plugin here is a core plugin: add its id and default state to `CorePlugins` and its
  JAR name to the shipped list (`CorePluginsGuardTest` fails otherwise).
- Plugins are compiled with **Java 21** (`--release 21`); do not target newer bytecode.
- Use stable command ids (`PluginIds`) for palette entries.
- Do not add plugin-specific code to the core module.
- Test enable/disable and shutdown via the in-app plugin manager.
