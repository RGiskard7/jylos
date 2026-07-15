# Ejecutar la aplicación

English: [../LAUNCH_APP.md](../LAUNCH_APP.md)

Jylos usa JavaFX 23. En desarrollo, usa los launchers del repositorio porque preparan correctamente el module-path de JavaFX.

## Scripts recomendados

```bash
./scripts/launch-jylos.sh
```

```powershell
.\scripts\launch-jylos.ps1
.\scripts\launch-jylos.bat
```

## Maven

```bash
mvn -f jylos/pom.xml clean compile exec:java -Dexec.mainClass="com.example.jylos.Launcher"
```

## Por qué no siempre `java -jar`

El uber-JAR contiene la app, pero JavaFX puede requerir módulos nativos en `--module-path`. Los scripts detectan la plataforma y localizan los JARs correctos de JavaFX.

## Requisitos

- JDK 21.
- Maven 3.9+ para compilar.
- JavaFX 23 resuelto por Maven o por los scripts.
