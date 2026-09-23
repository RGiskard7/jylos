# Instalar Jylos

Jylos es una aplicación de escritorio local-first para Windows, macOS y Linux. Puedes instalar un paquete ya compilado, ejecutarla con JBang o compilarla desde el código fuente.

## Descargar un paquete

Los paquetes compilados de la versión actual están en la página de [GitHub Releases](https://github.com/RGiskard7/jylos/releases/latest):

| Plataforma | Assets |
| --- | --- |
| Windows | instalador `.exe`, instalador `.msi`, ZIP portable |
| Linux | `.deb`, `.rpm` (vía `jpackage`) |
| macOS | Sin instalador nativo todavía — usa JBang, el uber-JAR o compila desde el código fuente |
| Cualquier plataforma | uber-JAR (requiere Java 21 + JavaFX 23 en el `PATH`) |

## Ejecutar con JBang

La forma más rápida de probar Jylos sin compilar nada:

```bash
jbang jylos@RGiskard7/jylos
```

JBang descarga Java 21 y los módulos de JavaFX automáticamente si hace falta.

## Compilar desde el código fuente

Requisitos: **Java JDK 21** y **Maven 3.9+**.

```bash
java -version
mvn -version
```

Clona el repositorio y compila:

```bash
git clone https://github.com/RGiskard7/jylos.git
cd jylos
./scripts/build_all.sh
```

En PowerShell de Windows:

```powershell
.\scripts\build_all.ps1
```

Esto genera `jylos/target/jylos-<version>-uber.jar`.

## Ejecutar después de compilar

Los scripts de lanzamiento configuran el module-path de JavaFX (obligatorio salvo que uses el uber-JAR con un runtime compatible):

```bash
./scripts/launch-jylos.sh
```

```powershell
.\scripts\launch-jylos.ps1
# o
.\scripts\launch-jylos.bat
```

> **Nota:** un `java -jar` directo sin el module-path suele fallar con JavaFX. Prefiere los scripts de lanzamiento.

## Siguientes pasos

- [Primer arranque](/es/start/first-launch)
- [Primeros pasos](/es/start/getting-started)
