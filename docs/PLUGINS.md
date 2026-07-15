# Plugins

Español: [es/PLUGINS.md](es/PLUGINS.md)

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

Compiles sources under `plugins-source/` with **`javac --release 21`** and writes JARs to **`jylos/plugins/`** (created if missing). JARs built for a newer Java release than the app runtime will not load (`UnsupportedClassVersionError`).

## Authoring

- Extend `AbstractPlugin` in `com.example.jylos.plugin`.
- Register stable command ids via `PluginIds` when exposing palette commands.
- Built-in Mermaid support lives in `plugins-source/.../mermaid/` (rebuild after changes).

## Extension points (PluginContext)

| API | What it gives you |
|-----|-------------------|
| `registerCommand(...)` | Command-palette entries (optional shortcut) |
| `registerMenuItem(...)` / `addMenuSeparator(...)` | Entries in the dynamic plugin menu |
| `registerSidePanel(...)` | A JavaFX node in the right panel |
| `registerPreviewEnhancer(...)` | CSS/JS injected into the Markdown preview |
| `registerToolbarButton(buttonId, tooltip, iconLiteral, action)` | A button in the main toolbar (Feather icon literal like `fth-clock`, or text); removed automatically on disable |
| `registerEditorHook(EditorHook)` | Editor lifecycle hooks (below) |
| `requestOpenNote(note)` | Ask the shell owner to open a note directly in the editor UI |
| `requestRefreshNotes()` | Ask the shell to fan out a notes refresh event |
| `subscribe(...)` / `publish(...)` | Typed `EventBus` access |

### Editor hooks

`EditorHook` has three default methods — override what you need:

- `String onBeforeTextInsert(Note note, String text)` — fires for **programmatic
  snippet insertions** (link/image dialogs, `[[` autocompletion, to-do/code
  templates), *not* per keystroke. Return the (transformed) snippet; `null` keeps it.
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
2. Load with dedicated classloaders (per-plugin isolation).
3. Register metadata, menu entries, preview enhancers, side panels; initialize enabled plugins.
4. Disable: unregister UI hooks and commands; shut down classloaders on app exit.

## Notes

- A failing or incompatible JAR should log a warning and be skipped — it must not prevent other plugins or app startup (`PluginLoader` catches `Throwable` on load).
- Shut down plugins and close classloaders on exit to avoid leaks.
- Re-enable after disable re-runs initialization (see plugin manager UI).
