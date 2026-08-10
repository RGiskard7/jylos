# Scripts

All paths are relative to the **repository root** unless noted.

## Build and run

| Script | Purpose |
|--------|---------|
| `build_all.sh` / `.ps1` | `mvn package` → `jylos/target/jylos-<version>-uber.jar` |
| `build-editor-web.sh` / `.ps1` | Bundle the CodeMirror 6 editor (`jylos/editor-web/`) with esbuild into the offline JAR resource |
| `launch-jylos.sh` / `.bat` / `.ps1` | Run uber-JAR with JavaFX module-path; cwd `jylos/` |
| `run_all.sh` / `.ps1` | Dev run (Maven/JavaFX) |
| `get-javafx-module-path.sh` | Print module-path for current OS (debugging) |

## Plugins and themes

| Script | Purpose |
|--------|---------|
| `build-plugins.sh` / `.ps1` | Compile `plugins-source/` → `jylos/plugins/*.jar` (Java 21 bytecode). A directory with a `plugin.properties` descriptor builds as one multi-file plugin |
| `test-plugins.sh` | Compile `plugins-source/` + `plugins-test/` and run each plugin test class' `main()` |
| `build-themes.sh` / `.ps1` | Install `themes/*` → `jylos/themes/` (optional `--appdata`) |

## Quality (optional)

| Script | Purpose |
|--------|---------|
| `smoke-phase-gate.sh` / `.ps1` | Smoke checks |
| `hardening-storage-matrix.sh` / `.ps1` | SQLite vs filesystem contract tests |

## Packaging

| Script | Purpose |
|--------|---------|
| `package-linux.sh` | Linux package |
| `package-macos.sh` | macOS package |
| `package-windows.ps1` | Windows portable app-image (core; `-Type portable|exe|msi`) |
| `package-windows-exe.ps1` | Windows .exe installer (WiX required) |
| `package-windows-msi.ps1` | Windows .msi installer (WiX required) |
| `setup-packaging-windows.ps1` | One-time install of the Windows packaging prerequisites (JDK 21, WiX) |
| `cleanup-installers.ps1` | Remove packaging artifacts |

## Manual testing

| Script | Purpose |
|--------|---------|
| `test_restore.sh` | Seeds a trashed note in `data/` to test filesystem-vault note restore manually |
| `test_restore_folder.sh` | Seeds a trashed folder in `data/` to test filesystem-vault folder restore manually |
| `trigger_restore.sh` | Same as `test_restore.sh`, targeting an arbitrary vault path (`$1`, defaults to `data/`) |

## Other

- `schema.txt` — SQLite schema notes (reference).
