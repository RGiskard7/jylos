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
| `build-plugins.sh` / `.ps1` | Compile `plugins-source/` → `jylos/plugins/*.jar` (Java 21 bytecode). A directory with a `plugin.properties` descriptor builds as one multi-file plugin; its optional third-party dependencies, declared in its own `pom.xml` and resolved via Maven, are packed into that plugin's JAR |
| `test-plugins.sh` | Builds the plugin JARs first (`build-plugins.sh`), then compiles `plugins-source/` + `plugins-test/` and runs each plugin test class' `main()`. A bundle with its own `pom.xml` dependencies is tested only through its built JAR, not compiled flat with the rest |
| `build-themes.sh` / `.ps1` | Install `themes/*` → `jylos/themes/` (optional `--appdata`) |

## Quality (optional)

| Script | Purpose |
|--------|---------|
| `smoke-phase-gate.sh` / `.ps1` | Smoke checks |
| `hardening-storage-matrix.sh` / `.ps1` | SQLite vs filesystem contract tests |

## Release

| Script | Purpose |
|--------|---------|
| `prepare-release.sh X.Y.Z [date]` | Sets `jylos/pom.xml` `<revision>` and moves the `[Unreleased]` changelog bullets into `## [X.Y.Z] - date`. Does not commit or tag |
| `check-release-version.sh X.Y.Z` | Fails unless `<revision>` is `X.Y.Z` and `changelog.md` has a non-empty section for it; run by the release workflow before packaging |

## Packaging

| Script | Purpose |
|--------|---------|
| `package-linux.sh` | Linux package |
| `package-linux-arch.sh` | Arch Linux package (`.pkg.tar.zst` for pacman): jpackage app-image wrapped with `makepkg`. Run on Arch as a regular user |
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
