# Packaging

Native installers use **jpackage** (JDK 17+, full SDK). Run scripts from the **repository root**; they `cd` into `jylos/` for the Maven build.

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
| Windows | `.\scripts\package-windows.ps1` | `Jylos\target\installers\` |

Each script:

1. Runs `mvn clean package -DskipTests` inside `jylos/`
2. Optionally runs `scripts/build-plugins.sh` (JARs → `jylos/plugins/`)
3. Invokes `jpackage` with `--main-class com.example.jylos.Launcher`

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
5. Plugin manager lists JARs in `plugins/` when present (plugins must be Java 17 bytecode).
6. Built-in and external themes apply without errors.
