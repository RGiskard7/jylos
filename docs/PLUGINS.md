# Plugins

Español: [es/PLUGINS.md](es/PLUGINS.md)

## Model

External plugins are JAR files loaded at startup. The core app does not import concrete plugin classes.

Search paths include `plugins/` under the application base directory (`AppDataDirectory.getBaseDirectory()`), plus paths used when the app is packaged (see `PluginLoader.java`).

## Installing plugins

Use **Tools → Manage plugins → Install plugin...** and select a `.jar` file. Jylos copies the JAR into the primary user plugin directory and loads it immediately when possible.

Manual installation is still supported: place the JAR in the primary plugins directory shown by `PluginLoader.getPluginsDirectoryFile()` (normally `<appData>/plugins`) and restart Jylos.

## Removing plugins

Use **Tools → Manage plugins → Remove** on a plugin card to uninstall a plugin from the primary user plugin directory. Jylos shuts the plugin down, removes its registered UI contributions, closes its classloader, deletes the JAR and clears its disabled preference.

Plugins loaded from protected application bundle locations are not deleted by the manager; copy them to the user plugin directory first if you want the manager to own their lifecycle.

## Build sample plugins

From repository root:

```bash
./scripts/build-plugins.sh
```

```powershell
.\scripts\build-plugins.ps1
```

Compiles sources under `plugins-source/` with **`javac --release 21`** and writes JARs to **`jylos/plugins/`** (created if missing). JARs built for a newer Java release than the app runtime will not load (`UnsupportedClassVersionError`).

### Single-file and multi-file plugins

By default **one source file is one plugin**, compiled into its own JAR.

A plugin too large for a single file declares itself as a **bundle**: put a
`plugin.properties` descriptor in its directory and every `.java` below that directory is
compiled together into one JAR.

```properties
plugin.class=com.example.jylos.plugin.builtin.dataview.DataviewPlugin
plugin.jar=DataviewPlugin
```

`plugin.class` is required — with several classes implementing `Plugin` in one bundle,
auto-detection would pick an arbitrary one. `plugin.jar` is optional and defaults to the
directory name.

## Test plugins

```bash
./scripts/test-plugins.sh
```

Plugin sources are compiled against the app like any third-party plugin, so `mvn test` does
not see them. This script compiles `plugins-source/` together with `plugins-test/` and runs
the `main()` of every `*Test` class found there, failing on a non-zero exit.

## Authoring

- Extend `AbstractPlugin` in `com.example.jylos.plugin`.
- Register stable command ids via `PluginIds` when exposing palette commands.

### Where a first-party plugin lives

`plugins-source/com/example/jylos/plugin/builtin/` holds every first-party plugin that
ships as an external JAR — one plugin, one subtree, regardless of whether it is a single
file (`WordCountPlugin.java`) or a multi-file bundle (`dataview/`). Size is not the
criterion: everything here goes through the same `PluginLoader`/`URLClassLoader` path as a
third-party plugin, and depends on `scripts/build-plugins.sh` having produced its JAR.

**Mermaid is the one deliberate exception.** It lives in
`jylos/src/main/java/com/example/jylos/plugin/mermaid/`, compiled straight into the core
app (`PluginLifecycle.registerCoreAndExternalPlugins` instantiates it directly — no JAR,
no classloader). Packaging treats the plugin build as best-effort: a failed
`build-plugins.sh` still ships the app, just without those JARs. Mermaid diagram rendering
is common enough, and expected reliably enough, that it must not depend on that step
succeeding — so it is compiled in, not built from `plugins-source/`. Do not move other
first-party plugins here on the same reasoning without weighing that trade-off: it opts a
plugin out of the JAR mechanism (no independent enable/disable-by-file, no
`isPluginRemovable`) in exchange for unconditional availability.

## Extension points (PluginContext)

| API | What it gives you |
|-----|-------------------|
| `registerCommand(...)` | Command-palette entries (optional shortcut) |
| `registerMenuItem(...)` / `addMenuSeparator(...)` | Entries in the dynamic plugin menu |
| `registerSidePanel(...)` | A JavaFX node in the right panel |
| `registerPreviewEnhancer(...)` | CSS/JS injected into the Markdown preview, plus per-note HTML post-processing (below) |
| `registerToolbarButton(buttonId, tooltip, iconLiteral, action)` | A button in the main toolbar (Feather icon literal like `fth-clock`, or text); removed automatically on disable |
| `registerEditorHook(EditorHook)` | Editor lifecycle hooks (below) |
| `requestOpenNote(note)` | Ask the shell owner to open a note directly in the editor UI |
| `requestRefreshNotes()` | Ask the shell to fan out a notes refresh event |
| `subscribe(...)` / `publish(...)` | Typed `EventBus` access |

### Preview enhancers

`PreviewEnhancer` has three default methods:

- `String getHeadInjections()` / `String getBodyInjections()` — static assets (CSS, JS)
  added to every preview document.
- `String transformHtml(PreviewContext context, String html)` — post-processes the
  **rendered note body**, and unlike the injection hooks it knows *which* note is being
  rendered (`context.note()`, `context.darkTheme()`). This is what lets a plugin replace
  content per note — for example turning a fenced <code>```dataview</code> block into a
  generated table.

Rules: transforms chain in registration order (each sees the previous output), run on the
preview's background render thread (not the FX thread), and a transform that throws or
returns `null` leaves the HTML untouched rather than blanking the note. They run *before*
the pipeline decides whether to ship syntax-highlighting assets, so a plugin that removes
the note's only code block does not leave highlight.js behind. Example: `DataviewPlugin`
(see [DATAVIEW.md](DATAVIEW.md)).

### Editor hooks

`EditorHook` has three default methods — override what you need:

- `String onBeforeTextInsert(Note note, String text)` — fires for **programmatic
  snippet insertions** (link/image dialogs, `[[` autocompletion and the to-do
  template), *not* per keystroke or paste. Return the transformed snippet;
  `null` keeps the original value.
- `String onBeforeSave(Note note, String content)` — transform the content right
  before it is persisted (the editor view is kept in sync).
- `void onAfterSave(Note note, String content)` — observation after a successful save.

Rules: hooks chain in registration order (each receives the previous output), run on
the JavaFX Application Thread (keep them fast), a throwing hook is logged and
skipped, and all of a plugin's hooks are removed when it is disabled. Example: see
`WordCountPlugin` (toolbar button) in `plugins-source/`.

`requestOpenNote(Note)` is intentionally **not** an ad-hoc UI event path anymore: it
delegates to a typed callback owned by `MainController`, keeping plugin note-open
requests explicit while preserving the public plugin API.

## Lifecycle

1. Discover JARs in plugin directories.
2. Load with a dedicated `URLClassLoader` per plugin, so plugin dependency
   JARs don't collide with each other. This is namespace isolation, **not** a
   security sandbox: each classloader's parent is the app's own classloader,
   so plugin code can reflectively reach any internal Jylos class. Plugins run
   with the full privileges of the JVM process.
3. Register metadata, menu entries, preview enhancers, side panels; initialize enabled plugins.
4. Disable: unregister UI hooks and commands; shut down classloaders on app exit.

## Notes

- A failing or incompatible JAR should log a warning and be skipped — it must not prevent other plugins or app startup (`PluginLoader` catches `Throwable` on load). A plugin declaring `Plugin.getHostApiVersion()` (default `"1"`) that doesn't match the host's supported version is rejected the same way, with a message naming the mismatch instead of a generic failure.
- Plugins disabled in the manager are persisted as disabled and are not initialized on the next startup; this prevents disabled plugins from registering UI contributions before the manager applies their state.
- Shut down plugins and close classloaders on exit to avoid leaks.
- Re-enable after disable re-runs initialization (see plugin manager UI).
