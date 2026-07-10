# Build and run

This page summarizes the commands and operational notes documented in `docs/BUILD.md`, the root README, and `AGENTS.md`.

## Requirements

- JDK 21
- Maven 3.9+

The app uses JavaFX 23 modules, including `javafx.web` for the Markdown preview.

## Build

Recommended build script:

```bash
./scripts/build_all.sh
```

PowerShell equivalent:

```powershell
.\scripts\build_all.ps1
```

Equivalent Maven build:

```bash
mvn -f jylos/pom.xml clean package -DskipTests
```

The documented output artifact is the shaded JAR under `jylos/target/`.

## Run

Recommended launch path:

```bash
./scripts/launch-jylos.sh
```

PowerShell equivalent:

```powershell
.\scripts\launch-jylos.bat
```

The README and build docs also mention:
- `./scripts/run_all.sh`
- `mvn -f jylos/pom.xml javafx:run`
- `mvn -f jylos/pom.xml exec:java -Dexec.mainClass="com.example.jylos.Launcher"`
- JBang: `jbang jylos@RGiskard7/jylos`

The launch scripts are important because they set the JavaFX module path and run from the `jylos/` working directory so runtime folders like `data/`, `logs/`, `plugins/`, `themes/`, and `snippets/` resolve correctly.

## Tests and checks

```bash
mvn -f jylos/pom.xml test
```

Optional smoke checks mentioned in `docs/BUILD.md`:

```bash
./scripts/smoke-phase-gate.sh
./scripts/hardening-storage-matrix.sh
```

## Plugins and themes

Helpful helper scripts:

```bash
./scripts/build-plugins.sh
./scripts/build-themes.sh
./scripts/build-themes.sh --appdata
```

These populate runtime locations under `jylos/` or the user AppData folder, depending on the script variant.

## Packaging and troubleshooting

The repository includes separate packaging notes in `PACKAGING.md` and README troubleshooting notes for:
- missing JavaFX runtime modules,
- missing JDK/Maven installs,
- and harmless JavaFX parent-POM warnings.

If you are changing launch or packaging behavior, verify both the build scripts and the JavaFX module-path assumptions.
