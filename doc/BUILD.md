# Build and run

## Requirements

- JDK 21
- Maven 3.9+

## Build

From repository root:

```bash
./scripts/build_all.sh
```

```powershell
.\scripts\build_all.ps1
```

Produces: `jylos/target/jylos-1.0.0-uber.jar`

Equivalent:

```bash
mvn -f jylos/pom.xml clean package -DskipTests
```

## Run (recommended)

Launch scripts set JavaFX `--module-path` and run from `jylos/` so `data/`, `logs/`, and `plugins/` resolve correctly:

```bash
./scripts/launch-jylos.sh
```

```powershell
.\scripts\launch-jylos.bat
```

Alternatives:

```bash
./scripts/run_all.sh
mvn -f jylos/pom.xml javafx:run
mvn -f jylos/pom.xml exec:java -Dexec.mainClass="com.example.jylos.Launcher"
```

IDE: run via `Launcher` with JavaFX on the module-path (same as `launch-jylos.*`). Plain `Main` or missing `javafx.web` modules will fail at runtime.

## Tests

```bash
mvn -f jylos/pom.xml test
```

Optional integration smoke (storage backends):

```bash
./scripts/smoke-phase-gate.sh
./scripts/hardening-storage-matrix.sh
```

## Plugins and themes

```bash
./scripts/build-plugins.sh      # output: jylos/plugins/
./scripts/build-themes.sh       # copies themes/ → jylos/themes/
./scripts/build-themes.sh --appdata   # also copies to user AppData themes folder
```

## Packaging

[PACKAGING.md](PACKAGING.md)

## Troubleshooting

| Issue | Action |
|-------|--------|
| `JAR not found` | Run `build_all` first |
| JavaFX runtime missing | Use `launch-jylos.*` |
| `java` / `mvn` not found | Install JDK 21 and Maven 3.9+ |
| JavaFX parent POM warnings | Safe to ignore during `mvn package` |
