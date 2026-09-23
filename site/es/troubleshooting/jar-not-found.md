# JAR no encontrado

## Síntoma

Un lanzador informa de que falta el uber-JAR.

## Solución

Compílalo primero:

```bash
./scripts/build_all.sh
```

```powershell
.\scripts\build_all.ps1
```

La compilación genera `jylos/target/jylos-<version>-uber.jar`.

Consulta [Instalar](/es/start/install).
