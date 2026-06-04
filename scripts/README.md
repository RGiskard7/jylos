# Scripts

All paths are relative to the **repository root** unless noted.

## Build and run

| Script | Purpose |
|--------|---------|
| `build_all.sh` / `.ps1` | `mvn package` → `jylos/target/jylos-1.0.0-uber.jar` |
| `launch-jylos.sh` / `.bat` / `.ps1` | Run uber-JAR with JavaFX module-path; cwd `jylos/` |
| `run_all.sh` / `.ps1` | Dev run (Maven/JavaFX) |
| `get-javafx-module-path.sh` | Print module-path for current OS (debugging) |

## Plugins and themes

| Script | Purpose |
|--------|---------|
| `build-plugins.sh` / `.ps1` | Compile `plugins-source/` → `jylos/plugins/*.jar` (Java 17 bytecode) |
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
| `package-windows.ps1` | Windows package |
| `cleanup-installers.ps1` | Remove packaging artifacts |

## Other

- `schema.txt` — SQLite schema notes (reference).
