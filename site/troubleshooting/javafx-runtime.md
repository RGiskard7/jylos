# JavaFX Runtime Errors

## Symptom

The app fails to start with a JavaFX runtime error, often mentioning a missing JavaFX runtime or module.

## Cause

Plain `java -jar` does not set the JavaFX module path. JavaFX applications need `--module-path` pointing at the JavaFX modules.

## Fix

Use the launcher scripts, which set the module path for you:

```bash
./scripts/launch-jylos.sh
```

```powershell
.\scripts\launch-jylos.ps1
# or
.\scripts\launch-jylos.bat
```

Run `./scripts/build_all.sh` first if the JAR is missing.

## Maven development run

```bash
mvn -f jylos/pom.xml javafx:run
```

See [Install](/start/install) and [LAUNCH_APP](https://github.com/RGiskard7/jylos/blob/main/docs/LAUNCH_APP.md) for details.
