# Launch Guide

Español: [es/LAUNCH_APP.md](es/LAUNCH_APP.md)

See also [BUILD.md](BUILD.md) for build and test commands.

## Recommended launchers

Set JavaFX module-path and run from `jylos/` (so `data/`, `logs/`, `plugins/`, `themes/`, and `snippets/` resolve correctly):

```bash
./scripts/build_all.sh   # required first time (uber-JAR)
./scripts/launch-jylos.sh
```

```powershell
.\scripts\build_all.ps1
.\scripts\launch-jylos.bat
```

## Alternatives

```bash
jbang jylos@RGiskard7/jylos
./scripts/run_all.sh
mvn -f jylos/pom.xml javafx:run
mvn -f jylos/pom.xml exec:java -Dexec.mainClass="com.example.jylos.Launcher"
```

## Runtime directories

Created on first launch when writable (typically under `jylos/`):

- `data/` — SQLite DB or filesystem vault
- `logs/`
- `plugins/`
- `themes/`
- `snippets/`
- `backups/` — SQLite startup backups (when using SQLite mode)

## Common issues

- **JavaFX runtime missing**: use `launch-jylos.*`, not plain `java -jar`.
- **JAR not found**: run `build_all` first.
- Confirm `java -version` and `mvn -version` (JDK 21, Maven 3.9+).
