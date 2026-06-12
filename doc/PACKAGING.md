# Packaging

Native installers use **jpackage** (JDK 21+, full SDK). Run scripts from the **repository root**; they `cd` into `jylos/` for the Maven build.

## Uber JAR (required for launchers and jpackage)

```bash
./scripts/build_all.sh
# or
mvn -f jylos/pom.xml clean package -DskipTests
```

Output: `jylos/target/jylos-1.0.0-uber.jar`

Run with JavaFX module-path via `scripts/launch-jylos.*`, not plain `java -jar` on all platforms.

## Platform installers

| OS | Script | Output (typical) |
|----|--------|------------------|
| macOS | `./scripts/package-macos.sh` | `jylos/target/installers/Jylos-1.0.0.dmg` |
| Linux | `./scripts/package-linux.sh` | `jylos/target/installers/` (deb/rpm) |
| Windows portable | `.\scripts\package-windows.ps1` | `jylos\target\installers\Jylos\` (app-image folder) |
| Windows .exe installer | `.\scripts\package-windows-exe.ps1` | `jylos\target\installers\Jylos-<version>.exe` |
| Windows .msi installer | `.\scripts\package-windows-msi.ps1` | `jylos\target\installers\Jylos-<version>.msi` |

Each script:

1. Runs `mvn clean package -DskipTests` inside `jylos/`
2. Optionally runs `scripts/build-plugins.sh` (JARs → `jylos/plugins/`)
3. Invokes `jpackage` with `--main-class com.example.jylos.Launcher`

### Windows formats

`package-windows.ps1` is the single core script; `-Type portable|exe|msi` selects the
format (`package-windows-exe.ps1` / `package-windows-msi.ps1` are thin wrappers).

- **portable** (default) — an app-image folder with bundled runtime; run `Jylos.exe`
  inside it or zip the folder. Needs nothing beyond a JDK.
- **exe / msi** — real installers (dir chooser, Start-menu group, shortcut prompt,
  MIT license page). **Both require the WiX Toolset**, which jpackage uses to build
  Windows installers: WiX 3.x (`candle.exe`/`light.exe` on PATH) for JDK 17–21, or
  WiX 4+ (`wix.exe`, e.g. `dotnet tool install --global wix`) for JDK 22+.
- Upgrades: installers carry a **stable `--win-upgrade-uuid`**, so a newer MSI/EXE
  upgrades the previous install instead of installing side by side. Never change
  that UUID in `package-windows.ps1`.
- The installers are **unsigned**: SmartScreen may warn on first run. For public
  releases, sign with `signtool` and a code-signing certificate.

Icons (see `jylos/src/main/resources/app.properties` and [icons README](../jylos/src/main/resources/icons/README.md)):

| Asset | File | Used when |
|-------|------|-----------|
| In-app window + About | `icons/app-icon.png` | Running from JAR / IDE |
| Windows installer | `icons/icon.ico` | `jpackage` |
| macOS installer | `icons/icon.icns` | `jpackage` |
| Linux installer | `icons/icon.png` | `jpackage` |

Update **`app-icon.png`** before dev runs; update **`icon.*`** before native packages (Dock/taskbar icons come from the installer assets).

## Smoke check after packaging

1. App starts from the platform launcher.
2. Create, edit, and delete a note (SQLite mode).
3. Open **Graph View** (`Ctrl+G`) and click a node to open a note.
4. Repeat key flows in filesystem vault mode if you ship that configuration (optional Git bar).
5. Plugin manager lists JARs in `plugins/` when present (plugins must be Java 21 bytecode).
6. External themes: copy `themes/<id>/` into the user data `themes/` folder (see [themes/README.md](../themes/README.md)), then **Preferences → External theme**.
