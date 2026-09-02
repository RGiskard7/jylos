# Packaging

Español: [es/PACKAGING.md](es/PACKAGING.md)

Native installers use **jpackage** (JDK 21+, full SDK). Run scripts from the **repository root**; they `cd` into `jylos/` for the Maven build.

## Uber JAR (required for launchers and jpackage)

```bash
./scripts/build_all.sh
# or
mvn -f jylos/pom.xml clean package -DskipTests
```

Output: `jylos/target/jylos-<version>-uber.jar`

Run with JavaFX module-path via `scripts/launch-jylos.*`, not plain `java -jar` on all platforms.

## Platform installers

| OS | Script | Output (typical) |
|----|--------|------------------|
| macOS | `./scripts/package-macos.sh` | `jylos/target/installers/Jylos-<version>.dmg` |
| Linux | `./scripts/package-linux.sh` | `jylos/target/installers/` (deb/rpm) |
| Windows portable | `.\scripts\package-windows.ps1` | `jylos\target\installers\Jylos\` (app-image folder) |
| Windows .exe installer | `.\scripts\package-windows-exe.ps1` | `jylos\target\installers\Jylos-<version>.exe` |
| Windows .msi installer | `.\scripts\package-windows-msi.ps1` | `jylos\target\installers\Jylos-<version>.msi` |

Each script:

1. Runs `mvn clean package -DskipTests` inside `jylos/`
2. Optionally runs `scripts/build-plugins.sh` (JARs → `jylos/plugins/`)
3. Invokes `jpackage` with `--main-class com.example.jylos.Launcher`

Release automation sets `JYLOS_RELEASE_VERSION` from the pushed tag (for example
`v2.4.1` becomes `2.4.1`). The release workflow temporarily sets the Maven
project version from that tag with `versions:set`, then the packaging scripts pass
the same value to Maven as `-Drelease.version=...`, so the Maven build log,
`app.properties`, `version.properties`, installers and GitHub Release all agree.
Local builds without that environment variable use the version declared in
`jylos/pom.xml`.

### Windows formats

`package-windows.ps1` is the single core script; `-Type portable|exe|msi` selects the
format (`package-windows-exe.ps1` / `package-windows-msi.ps1` are thin wrappers).

- **portable** (default) — an app-image folder with bundled runtime; run `Jylos.exe`
  inside it or zip the folder. Needs nothing beyond a JDK.
- **exe / msi** — real installers (dir chooser, Start-menu group, shortcut prompt,
  MIT license page). **Both require the WiX Toolset**, which jpackage uses to build
  Windows installers: WiX 3.x (`candle.exe`/`light.exe` on PATH) for JDK 17–21, or
  WiX 4+ (`wix.exe`, e.g. `dotnet tool install --global wix`) for JDK 22+.
- **Windows one-time setup:** `.\scripts\setup-packaging-windows.ps1` installs JDK 21
  (winget) and WiX 3.14 binaries under `.tools/wix314/`. `package-windows.ps1` then
  auto-selects JDK 21+ and bundled WiX — no manual `JAVA_HOME` or PATH edits.
- Upgrades: installers carry a **stable `--win-upgrade-uuid`**, so a newer MSI/EXE
  upgrades the previous install instead of installing side by side. Never change
  that UUID in `package-windows.ps1`.
- The installers are **unsigned**: SmartScreen may warn on first run. For public
  releases, sign with `signtool` and a code-signing certificate.

### macOS signing & notarization (optional)

`package-macos.sh` builds an unsigned DMG by default. For public distribution
(no Gatekeeper warnings) sign and notarize by exporting two variables before
running the script — both steps require an Apple Developer account:

```bash
# 1. One-time: install a "Developer ID Application" certificate in the keychain,
#    then store notarytool credentials (uses an app-specific password):
xcrun notarytool store-credentials jylos-notary \
    --apple-id you@example.com --team-id TEAMID --password <app-specific-password>

# 2. Per release:
export JYLOS_MAC_SIGN_IDENTITY="Developer ID Application: Your Name (TEAMID)"
export JYLOS_NOTARY_PROFILE="jylos-notary"
./scripts/package-macos.sh
```

With `JYLOS_MAC_SIGN_IDENTITY` set, jpackage signs the app bundle (`--mac-sign`).
With `JYLOS_NOTARY_PROFILE` also set, the script submits the DMG with
`xcrun notarytool submit --wait` and staples the ticket (`xcrun stapler staple`).
Unset, the script behaves exactly as before (unsigned local build).

Icons (see `jylos/src/main/resources/app.properties` and [icons README](../jylos/src/main/resources/icons/README.md)):

| Asset | File | Used when |
|-------|------|-----------|
| In-app window + About | `icons/app-icon.png` | Running from JAR / IDE |
| Windows installer | `icons/icon.ico` | `jpackage` |
| macOS installer | `icons/icon.icns` | `jpackage` |
| Linux installer | `icons/icon.png` | `jpackage` |

Update **`app-icon.png`** before dev runs; update **`icon.*`** before native packages (Dock/taskbar icons come from the installer assets).

## In-app updater (unsigned builds)

Jylos releases are not signed or notarized (see above — an Apple Developer
Program membership and a Windows code-signing certificate both cost money the
project cannot currently spend). A browser download of an unsigned installer
gets an OS-level "downloaded from the internet" marker
(`com.apple.quarantine` on macOS, the `Zone.Identifier` alternate data stream
on Windows), and macOS Gatekeeper / Windows SmartScreen re-runs its
block-or-warn check against that marker on **every single download** — the
user has to override it in system settings each time, not just once.

To avoid repeating that override on every update, `UpdateChecker` (checks
GitHub Releases), `UpdateInstaller` (downloads, verifies, launches) and
`UpdateInstallSupport` (the confirmation dialogs) implement an **optional
in-app update path**: the user clicks "Install now" on the update toast,
Jylos downloads the release asset for their platform directly (not via the
browser, so it never gets the marker above), verifies it against the SHA-256
`digest` GitHub itself computed when the asset was uploaded, and — only after
one more explicit confirmation — closes itself and hands off to the native
installer.

**What the checksum verification does and does not prove:** it catches
transport corruption and man-in-the-middle tampering between GitHub and the
user's machine. It does **not** vouch for the release itself — a compromised
Jylos release pipeline or GitHub account could still publish a malicious
asset that passes verification, because the "expected" checksum comes from
the same GitHub release as the file being checked. Nothing short of real code
signing closes that gap. When GitHub has not reported a digest for the
platform's asset, `UpdateInstaller.verifyDigest` returns `false` and the app
falls back to the normal "Open downloads" browser link rather than running an
unverified file.

**"Open downloads" always stays available** as a plain link next to "Install
now" on the update toast — a user who prefers the browser's own download and
security prompts never has to use the in-app path.

**Remove this once the project can afford real signing:** once macOS
notarization (`JYLOS_MAC_SIGN_IDENTITY`/`JYLOS_NOTARY_PROFILE`, see above) and
a Windows code-signing certificate are both in place for every release, a
normal signed browser download stops triggering repeated OS warnings and this
whole mechanism stops being necessary. Every file involved — code, tests,
i18n keys — carries the exact comment `REMOVABLE: in-app updater`, so
`grep -rn "REMOVABLE: in-app updater" jylos/src docs/` finds every single
touch point at once; the full removal checklist (step by step, what to
delete vs. what to leave alone) lives in `UpdateInstaller.java`'s class docs,
under "Removing this later".

## Smoke check after packaging

1. App starts from the platform launcher.
2. Create, edit, and delete a note (SQLite mode).
3. Open **Graph View** (`Ctrl+G`) and click a node to open a note.
4. Repeat key flows in filesystem vault mode if you ship that configuration (optional Git bar).
5. Plugin manager lists JARs in `plugins/` when present (plugins must be Java 21 bytecode).
6. External themes: copy `themes/<id>/` into the user data `themes/` folder (see [themes/README.md](../themes/README.md)), then **Preferences → External theme**.
