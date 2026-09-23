# JAR Not Found

## Symptom

A launcher reports that the uber-JAR is missing.

## Fix

Build it first:

```bash
./scripts/build_all.sh
```

```powershell
.\scripts\build_all.ps1
```

The build produces `jylos/target/jylos-<version>-uber.jar`.

See [Install](/start/install).
