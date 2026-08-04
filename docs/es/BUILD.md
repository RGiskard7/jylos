# Build

English: [../BUILD.md](../BUILD.md)

Comandos para compilar, ejecutar y probar Jylos desde la raíz del repositorio.

## Build completo

```bash
./scripts/build_all.sh
```

```powershell
.\scripts\build_all.ps1
```

Salida: `jylos/target/jylos-<version>-uber.jar`.

Equivalente Maven:

```bash
mvn -f jylos/pom.xml clean package -DskipTests
```

## Ejecutar

```bash
./scripts/launch-jylos.sh
```

```powershell
.\scripts\launch-jylos.ps1
.\scripts\launch-jylos.bat
```

Los launchers resuelven módulos JavaFX y evitan depender de `java -jar` plano.

## Desarrollo con Maven

```bash
mvn -f jylos/pom.xml clean compile exec:java -Dexec.mainClass="com.example.jylos.Launcher"
```

## Tests

```bash
mvn -f jylos/pom.xml test
```

Compilar sin tests:

```bash
mvn -f jylos/pom.xml -DskipTests compile
```

Política de tests: [TESTING.md](TESTING.md).

## Plugins y temas

```bash
./scripts/build-plugins.sh
./scripts/build-themes.sh
```

Windows:

```powershell
.\scripts\build-plugins.ps1
.\scripts\build-themes.ps1
```

## Bundle del editor CodeMirror

Los builds Maven normales usan el bundle offline generado e incluido en
`jylos/src/main/resources`; no hace falta Node.js para compilar o ejecutar Jylos. Después
de modificar cualquier fichero de `jylos/editor-web/src/` o sus dependencias npm fijadas, regenera
el asset desde la raíz del repositorio:

```bash
./scripts/build-editor-web.sh
```

```powershell
.\scripts\build-editor-web.ps1
```

Los scripts usan `npm ci` y `package-lock.json`, y empaquetan CodeMirror con esbuild.

## Empaquetado

Ver [PACKAGING.md](PACKAGING.md).
