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

No genera instaladores. Sirve para confirmar que la rama no rompe build ni tests.

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

- valida que el tag sea semantico;
- obtiene `JYLOS_RELEASE_VERSION` quitando la `v`;
- configura Java 21 Temurin;
- pasa la version del tag a Maven mediante la propiedad estandar `revision`;
- ejecuta tests;
- empaqueta Windows y Linux;
- recoge el JAR normal y el uber-JAR generados por Maven;
- sube artefactos intermedios;
- extrae release notes desde `changelog.md`;
- genera `SHA256SUMS.txt`;
- publica GitHub Release con `softprops/action-gh-release`.

Assets publicados actualmente:

- `jylos-windows-x64.exe`
- `jylos-windows-x64.msi`
- `jylos-windows-portable.zip`
- `jylos-linux-amd64.deb`
- `jylos-linux-amd64.rpm`
- `jylos.jar`
- `jylos-uber.jar`
- `SHA256SUMS.txt`

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

Antes de taggear, comprobar que `changelog.md` tiene seccion de version:

```markdown
## [2.4.1] - 2026-07-16
```

Crear y subir tag:

```bash
git checkout main
git pull origin main
git tag v2.4.1
git push origin v2.4.1
```

Cambiar `v2.4.1` por la version real.

No hace falta subir otro commit solo para versionar `pom.xml`. El workflow usa el
tag como fuente de verdad durante release.

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
- Linux genera `.deb` y `.rpm`, pero no repositorio firmado.

Consecuencias:

- Windows puede mostrar SmartScreen.
- macOS puede bloquear o advertir si se distribuye DMG sin firma.
- Linux instala paquetes descargados manualmente.

Cuando haya certificados reales, los pasos de firma deben ir antes de publicar assets
en GitHub Release.

## Fallos habituales

### Release falla porque no encuentra notas

Revisar `changelog.md`:

```markdown
## [X.Y.Z] - YYYY-MM-DD
```

Debe coincidir con tag `vX.Y.Z`.

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

- `main` contiene merge final.
- `latest-jylos.yml` verde en PR o `main`.
- `changelog.md` tiene seccion de version.
- Version elegida sigue semver.
- Si se cambian nombres de assets, actualizar `docs/index.html`.
- Si macOS sigue apagado, no prometer DMG nuevo en release notes.
