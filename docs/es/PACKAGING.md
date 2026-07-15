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
