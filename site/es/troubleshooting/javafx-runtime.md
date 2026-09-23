# Errores de runtime JavaFX

## Síntoma

La aplicación no arranca con un error de runtime de JavaFX, a menudo mencionando un runtime de JavaFX ausente o un módulo.

## Causa

Un `java -jar` directo no configura el module-path de JavaFX. Las aplicaciones JavaFX necesitan `--module-path` apuntando a los módulos de JavaFX.

## Solución

Usa los scripts de lanzamiento, que configuran el module-path por ti:

```bash
./scripts/launch-jylos.sh
```

```powershell
.\scripts\launch-jylos.ps1
# o
.\scripts\launch-jylos.bat
```

Ejecuta primero `./scripts/build_all.sh` si falta el JAR.

## Ejecución de desarrollo con Maven

```bash
mvn -f jylos/pom.xml javafx:run
```

Consulta [Instalar](/es/start/install) y [LAUNCH_APP](https://github.com/RGiskard7/jylos/blob/main/docs/LAUNCH_APP.md) para más detalles.
