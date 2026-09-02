# Empaquetado

English: [../PACKAGING.md](../PACKAGING.md)

Los instaladores nativos usan `jpackage` con JDK 21+. Ejecutar scripts desde la raíz del repo.

## Build base

```bash
mvn -f jylos/pom.xml clean package -DskipTests
```

Salida: `jylos/target/jylos-<version>-uber.jar`.

Usa `scripts/launch-jylos.*` para ejecutar con module-path JavaFX correcto.

## Scripts

| OS | Script | Salida típica |
|----|--------|---------------|
| macOS | `./scripts/package-macos.sh` | `jylos/target/installers/Jylos-<version>.dmg` |
| Linux | `./scripts/package-linux.sh` | `jylos/target/installers/` (`deb`/`rpm`) |
| Windows portable | `.\scripts\package-windows.ps1` | `jylos\target\installers\Jylos\` |
| Windows `.exe` | `.\scripts\package-windows-exe.ps1` | `Jylos-<version>.exe` |
| Windows `.msi` | `.\scripts\package-windows-msi.ps1` | `Jylos-<version>.msi` |

Cada script compila, puede construir plugins y llama `jpackage` con `com.example.jylos.Launcher`.

## Versionado release

CI establece `JYLOS_RELEASE_VERSION` desde el tag. El workflow ajusta Maven con `versions:set` y los scripts pasan `-Drelease.version=...`. Build local usa versión de `pom.xml`.

## Windows

`package-windows.ps1` es el núcleo; `-Type portable|exe|msi` selecciona formato. `.exe` y `.msi` requieren WiX. `setup-packaging-windows.ps1` instala JDK 21 y WiX 3.14 localmente.

Los instaladores tienen UUID de upgrade estable. No cambiarlo. Paquetes sin firma pueden mostrar SmartScreen.

## macOS

`package-macos.sh` genera DMG sin firmar por defecto. Para firma/notarización:

```bash
xcrun notarytool store-credentials jylos-notary \
    --apple-id you@example.com --team-id TEAMID --password <app-specific-password>

export JYLOS_MAC_SIGN_IDENTITY="Developer ID Application: Your Name (TEAMID)"
export JYLOS_NOTARY_PROFILE="jylos-notary"
./scripts/package-macos.sh
```

## Actualizador dentro de la app (builds sin firmar)

Las releases de Jylos no están firmadas ni notarizadas (arriba se explica por
qué: cuesta dinero que el proyecto no tiene ahora mismo). Un instalador sin
firmar descargado por el navegador lleva una marca de "viene de internet"
(`com.apple.quarantine` en macOS, el flujo de datos alternativo
`Zone.Identifier` en Windows), y Gatekeeper/SmartScreen repiten su aviso o
bloqueo contra esa marca **en cada descarga** — hay que confirmarlo a mano
cada vez, no solo la primera.

Para no repetir ese paso en cada actualización, `UpdateChecker` (consulta
GitHub Releases), `UpdateInstaller` (descarga, verifica, lanza) y
`UpdateInstallSupport` (los diálogos de confirmación) implementan una **vía
opcional de actualización dentro de la app**: al pulsar "Instalar ahora" en
el aviso de actualización, Jylos descarga el asset de la release para la
plataforma actual directamente (no por el navegador, así que nunca lleva esa
marca), lo verifica contra el `digest` SHA-256 que el propio GitHub calculó
al subir el asset, y — solo tras una confirmación explícita más — se cierra
y entrega el control al instalador nativo.

**Qué demuestra la verificación de checksum y qué no:** detecta corrupción en
el transporte o manipulación por el camino entre GitHub y la máquina del
usuario. **No** avala la release en sí — una cuenta de GitHub o un pipeline
de release comprometidos podrían publicar un asset malicioso que pase la
verificación igualmente, porque el checksum "esperado" viene de la misma
release que el fichero que se comprueba. Solo una firma de código real cierra
ese hueco. Si GitHub no reporta digest para el asset de esa plataforma,
`UpdateInstaller.verifyDigest` devuelve `false` y la app cae de vuelta al
enlace normal "Abrir descargas" en vez de ejecutar un fichero sin verificar.

**"Abrir descargas" sigue siempre disponible** como enlace normal junto a
"Instalar ahora" en el aviso — quien prefiera la descarga y los avisos de
seguridad propios del navegador nunca tiene que usar la vía dentro de la app.

**Eliminar esto en cuanto el proyecto pueda pagar firma real:** una vez haya
notarización de macOS (`JYLOS_MAC_SIGN_IDENTITY`/`JYLOS_NOTARY_PROFILE`,
arriba) y certificado de firma de Windows en todas las releases, una
descarga normal firmada por el navegador deja de disparar avisos repetidos
del sistema y todo este mecanismo deja de ser necesario. Todos los ficheros
implicados — código, tests, claves de i18n — llevan el comentario exacto
`REMOVABLE: in-app updater`, así que
`grep -rn "REMOVABLE: in-app updater" jylos/src docs/` encuentra de golpe
cada punto a tocar; la checklist completa de eliminación (paso a paso, qué
borrar y qué dejar) está en el javadoc de `UpdateInstaller.java`, sección
"Removing this later".

## Iconos

| Asset | Archivo | Uso |
|-------|---------|-----|
| App/About | `icons/app-icon.png` | JAR/IDE |
| Windows | `icons/icon.ico` | `jpackage` |
| macOS | `icons/icon.icns` | `jpackage` |
| Linux | `icons/icon.png` | `jpackage` |

## Smoke test

1. App arranca.
2. Crear/editar/borrar nota en SQLite.
3. Abrir grafo.
4. Probar vault filesystem.
5. Ver plugins si existen.
6. Probar tema externo.
