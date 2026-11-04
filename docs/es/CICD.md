# CI/CD

English: [../CICD.md](../CICD.md)

Guia practica del CI/CD de Jylos. Objetivo: saber que ejecuta GitHub Actions,
como hacer una release y que partes son automaticas.

## Resumen rapido

- El desarrollo normal va por `develop`.
- La rama publica estable es `main`.
- Los tests se ejecutan al abrir PR hacia `main` y al hacer push en `main`.
- Las releases se publican al subir un tag semantico: `vX.Y.Z`.
- La version de la app se toma del tag durante el empaquetado.
- Windows y Linux se empaquetan automaticamente.
- macOS esta conservado en el workflow, pero desactivado por colas largas de runners ARM.

## Workflows

### `latest-jylos.yml`

Workflow de verificacion continua.

Se ejecuta en:

- `pull_request` hacia `main`;
- `push` directo a `main`.

Hace:

- checkout del repo;
- setup de Java 21 Temurin;
- cache de Maven;
- instala `xvfb` en Ubuntu para tests JavaFX/headless;
- ejecuta:

```bash
xvfb-run -a mvn -B -f jylos/pom.xml clean test
```

- construye los JAR de plugins y ejecuta sus tests (`scripts/test-plugins.sh`,
  también con `xvfb`): los plugins se compilan como plugins de terceros, así que la
  ejecución de Maven no los ve;
- en un job aparte, `editor-web`, Node 22 + `npm ci && npm test` en
  `jylos/editor-web` (los tests propios del editor CodeMirror).

No genera instaladores. Sirve para confirmar que la rama no rompe build ni ninguna
suite de tests.

### `mutacion.yml`

Gate de mutation testing para las clases de la capa de datos con mayor riesgo.

Se ejecuta en:

- `pull_request`;
- `workflow_dispatch`.

A proposito, no en `push`: PIT es mucho mas lento que la suite normal, asi que
solo hace de gate en el merge, no en cada commit de `develop`.

Hace:

- checkout del repo;
- setup de Java 21 Temurin;
- instala `xvfb` para la ejecucion headless de los tests;
- `mvn -B -f jylos/pom.xml test`;
- `org.pitest:pitest-maven:mutationCoverage` sobre las `TARGET_CLASSES`
  configuradas, y falla el job si el resultado queda por debajo de
  `MUTATION_THRESHOLD`;
- escribe un resumen KILLED/SURVIVED/NO_COVERAGE/TIMED_OUT en el resumen del job;
- sube el informe HTML de PIT como artefacto.

`TARGET_CLASSES` y `MUTATION_THRESHOLD` viven en el bloque `env` del workflow.
El umbral es un suelo medido, no un objetivo: solo sube, y solo despues de que
una ejecucion real confirme el numero nuevo.

### `release-jylos.yml`

Workflow de publicacion.

Se ejecuta solo con tags:

```text
v*.*.*
```

Ejemplos validos:

```text
v2.4.1
v2.5.0
v3.0.0
```

Hace:

- **job `validate`, primero y en segundos:** `scripts/check-release-version.sh`
  comprueba que la `<revision>` de `jylos/pom.xml` es igual al tag y que
  `changelog.md` tiene una sección no vacía para él. Si no coinciden, no se
  construye nada;
- valida que el tag sea semantico;
- obtiene `JYLOS_RELEASE_VERSION` quitando la `v`;
- configura Java 21 Temurin;
- pasa la version del tag a Maven mediante la propiedad estandar `revision`;
- ejecuta tests;
- empaqueta Windows y Linux (`.deb`/`.rpm` en Ubuntu; el `.pkg.tar.zst` de Arch Linux en un contenedor `archlinux`, porque `makepkg` solo existe en Arch);
- recoge el JAR normal y el uber-JAR generados por Maven;
- sube artefactos intermedios;
- extrae release notes desde `changelog.md`;
- publica GitHub Release con `softprops/action-gh-release`.

Assets publicados actualmente:

- `jylos-windows-x64.exe`
- `jylos-windows-x64.msi`
- `jylos-windows-portable.zip`
- `jylos-linux-amd64.deb`
- `jylos-linux-amd64.rpm`
- `jylos-linux-amd64.pkg.tar.zst`
- `jylos.jar`
- `jylos-uber.jar`

Los scripts de empaquetado locales (`scripts/package-*.sh`/`.ps1`) generan estos
mismos nombres estandarizados junto a la salida cruda de jpackage, asi que un
instalador construido en local es identico al publicado por CI.

Los nombres de los JAR son estables a proposito, igual que los instaladores
nativos. Herramientas como JBang pueden usar
`releases/latest/download/jylos-uber.jar` para la ultima release. Quien necesite
una version fija puede usar el mismo nombre de asset bajo una URL de tag concreta,
por ejemplo `releases/download/v2.4.5/jylos-uber.jar`.

### `openwiki-update.yml`

Workflow de documentacion OpenWiki.

Se ejecuta en:

- `workflow_dispatch`, manual desde GitHub;
- `push` a `main`, salvo cambios ignorados.

Ignora:

- `openwiki/**`;
- `AGENTS.md`;
- `CLAUDE.md`.

Motivo: evitar bucles donde el workflow crea cambios de OpenWiki y luego se vuelve
a disparar solo por esos cambios.

Hace:

- instala Node.js 22;
- instala `openwiki`;
- ejecuta `openwiki code --update --print`;
- crea PR automatica con cambios en `openwiki`, `AGENTS.md` y `CLAUDE.md`.

Necesita secreto:

```text
OPENAI_API_KEY
```

### `deploy-pages.yml`

Compila y publica el sitio web VitePress (página de aterrizaje + docs) en GitHub Pages.

Se ejecuta en:

- `push` a `main`, limitado a cambios bajo `site/**` y al propio workflow;
- `workflow_dispatch`.

Hace:

- checkout del repositorio;
- configuración de Node.js 22;
- `npm ci` dentro de `site/`;
- `vitepress build` con `VITEPRESS_BASE=/`;
- subida de `site/.vitepress/dist` y despliegue a GitHub Pages.

El código fuente del sitio vive en `site/` y es un proyecto Node autocontenido
(`site/package.json`). Desarrollo local:

```bash
cd site
npm install
npm run docs:dev
```

## Flujo normal de desarrollo

Trabajar en `develop`:

```bash
git checkout develop
git pull origin develop
# trabajar, commitear
git push origin develop
```

Luego:

1. Abrir PR en GitHub: `develop` hacia `main`.
2. Esperar checks verdes.
3. Hacer merge del PR en GitHub.
4. Actualizar `main` local.

```bash
git checkout main
git pull origin main
```

## Release normal

Preparar la versión en un solo paso, en la rama que se mergeará a `main`:

```bash
scripts/prepare-release.sh 2.4.1          # la fecha por defecto es hoy
```

Pone la `<revision>` de `jylos/pom.xml` a `2.4.1` y convierte las entradas de
`## [Unreleased]` en `## [2.4.1] - <fecha>` (dejando un `## [Unreleased]` vacío
arriba), y después ejecuta `scripts/check-release-version.sh`. No hace commit, ni
tag, ni push. Revisar el diff, commitear, mergear a `main` y entonces:

```bash
git checkout main
git pull origin main
git tag v2.4.1
git push origin v2.4.1
```

Por qué importa actualizar el `pom.xml`: los paquetes del release toman la versión
del tag, pero cualquier otro build (IDE, `build_all`, el CI de pull requests,
`mvn package`) la toma de `<revision>`. Sin actualizarla, esos builds se identifican
con una versión antigua. El job `validate` del workflow de release rechaza un tag
que no coincida con `<revision>` o sin sección en el changelog, así que ya no pueden
desincronizarse.

El badge de versión del README lee el último GitHub Release, y la landing
(`docs/index.html`) la consulta al cargar: ninguno hay que tocarlo en cada release.

## Version de la app

La app lee version desde:

```text
jylos/src/main/resources/version.properties
```

Ese recurso usa Maven resource filtering. En release, GitHub Actions pasa:

```bash
-Drevision=${JYLOS_RELEASE_VERSION}
-Drelease.version=${JYLOS_RELEASE_VERSION}
```

Resultado:

- tag `v2.4.1`;
- `JYLOS_RELEASE_VERSION=2.4.1`;
- Maven genera `jylos-2.4.1.jar` y `jylos-2.4.1-uber.jar`;
- la release publica esos archivos como assets estables: `jylos.jar` y `jylos-uber.jar`;
- `version.properties` empaquetado con `2.4.1`;
- el `META-INF/MANIFEST.MF` de ambos JAR lleva `Implementation-Version: 2.4.1`;
- panel `Acerca de Jylos` muestra `v2.4.1`;
- update checker compara contra GitHub Releases.

En builds locales sin parametro, Maven usa fallback del `pom.xml`.

Los scripts de empaquetado nativo siguen la misma regla:

- en CI, `JYLOS_RELEASE_VERSION` viene del tag enviado;
- en local, define `JYLOS_RELEASE_VERSION` si quieres reproducir una version de
  release sin editar `pom.xml`.

Ejemplo:

```bash
JYLOS_RELEASE_VERSION=2.4.5 ./scripts/package-macos.sh
```

```powershell
$env:JYLOS_RELEASE_VERSION = "2.4.5"
.\scripts\package-windows.ps1 -Type exe
```

## Release notes

`release-jylos.yml` extrae notas desde `changelog.md`.

Debe existir una seccion exacta:

```markdown
## [2.4.1] - 2026-07-16
```

El workflow toma todo hasta la siguiente cabecera `## [...]`.

Si la seccion falta o esta vacia, release falla. Esto es intencional: evita publicar
releases sin notas.

## Landing y descargas

`docs/index.html` usa URLs estables de GitHub:

```text
https://github.com/RGiskard7/jylos/releases/latest/download/<asset>
```

Por eso no hay que editar `index.html` en cada release si los nombres de assets no
cambian.

Importante: mientras macOS siga desactivado, no se publica:

```text
jylos-macos-arm64.dmg
```

Si la landing muestra descarga macOS, ese enlace solo funcionara cuando exista ese
asset en la ultima release.

## macOS

El bloque macOS sigue en `release-jylos.yml`, pero la matriz lo tiene comentado:

```yaml
matrix:
  os:
    - windows-latest
    - ubuntu-latest
    # - macos-14-arm64
```

Motivo: `macos-14-arm64` puede quedar horas esperando runner.

Para reactivarlo:

```yaml
matrix:
  os:
    - windows-latest
    - ubuntu-latest
    - macos-14-arm64
```

El step `Package macOS DMG` ya esta preparado y solo corre si `runner.os == 'macOS'`.

## Reintentar release fallida

Si tag ya existe y la release fallo antes de publicar algo util:

```bash
git push origin --delete v2.4.1
git tag -d v2.4.1
git checkout main
git pull origin main
git tag v2.4.1
git push origin v2.4.1
```

No reutilizar tags publicados publicamente salvo que no haya alternativa. Si release
ya fue consumida por usuarios, mejor crear version nueva, por ejemplo `v2.4.2`.

## Firmas

Estado actual:

- Windows sin firma de codigo;
- macOS desactivado en CI y sin firma/notarizacion automatica;
- Linux genera `.deb`, `.rpm` y `.pkg.tar.zst` de Arch, pero no repositorio firmado (ni paquete en AUR).

Consecuencias:

- Windows puede mostrar SmartScreen.
- macOS puede bloquear o advertir si se distribuye DMG sin firma.
- Linux instala paquetes descargados manualmente.

Cuando haya certificados reales, los pasos de firma deben ir antes de publicar assets
en GitHub Release.

## Fallos habituales

### Release falla en el job `validate`

El tag no coincide con la `<revision>` de `jylos/pom.xml`, o `changelog.md` no tiene
sección `## [X.Y.Z] - YYYY-MM-DD` para él; normalmente porque no se ejecutó
`scripts/prepare-release.sh` antes de crear el tag. No se ha construido ni publicado
nada, así que el tag se puede mover sin problema:

```bash
git push origin --delete vX.Y.Z && git tag -d vX.Y.Z
scripts/prepare-release.sh X.Y.Z    # después commit y merge a main
git checkout main && git pull
git tag vX.Y.Z && git push origin vX.Y.Z
```

### Linux falla por permisos de script

El workflow llama scripts `.sh` con `bash ./scripts/...`, no depende del bit ejecutable.

### Tests JavaFX cuelgan en GitHub Actions

Ubuntu usa `xvfb-run`. Si se anaden tests UI nuevos, mantener ese flujo.

### macOS no empieza

No es error de Jylos si queda en:

```text
Waiting for a runner to pick up this job...
```

Es falta de runner disponible. Por eso macOS esta apagado.

## Checklist antes de release

- `main` contiene merge final, incluido el commit de `scripts/prepare-release.sh`.
- `latest-jylos.yml` verde en PR o `main`.
- `scripts/check-release-version.sh X.Y.Z` pasa en local (el workflow de release
  lo vuelve a ejecutar y se detiene si no coincide).
- Version elegida sigue semver.
- Si se cambian nombres de assets, actualizar `docs/index.html`.
- Si macOS sigue apagado, no prometer DMG nuevo en release notes.
