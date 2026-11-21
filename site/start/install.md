# Install Jylos

Jylos is a local-first desktop app for Windows, macOS and Linux. You can install a prebuilt package, run it with JBang, or build it from source.

## Download a package

Prebuilt packages for the current release are available on the [GitHub Releases](https://github.com/RGiskard7/jylos/releases/latest) page:

| Platform | Assets |
| --- | --- |
| Windows | `.exe` installer, `.msi` installer, portable ZIP |
| Linux | `.deb`, `.rpm` (via `jpackage`), `.pkg.tar.zst` for Arch Linux (`sudo pacman -U jylos-linux-amd64.pkg.tar.zst`) |
| macOS | `.dmg` installer for Apple Silicon (`jylos-macos-arm64.dmg`); on Intel Macs use JBang or build from source |
| Linux x64 | uber-JAR (`jylos-uber.jar`, requires Java 21): it bundles JavaFX for Linux only, so on other systems use JBang, which adds JavaFX for yours |

### Nix and NixOS

For `x86_64` Linux, a flake installs the release package (data in `~/.local/share/jylos`):

```bash
nix run "github:RGiskard7/jylos?dir=packaging/nix"
```

To keep it in your profile: `nix profile install "github:RGiskard7/jylos?dir=packaging/nix"`. On NixOS, as a flake input:

```nix
inputs.jylos.url = "github:RGiskard7/jylos?dir=packaging/nix";
# …
environment.systemPackages = [ inputs.jylos.packages.x86_64-linux.default ];
```

- **Updates** come with the flake, not from the app: its updater finds no package for NixOS.
- **Wayland:** JavaFX draws only through X11, so Jylos runs through XWayland, which must be enabled.

## Run with JBang

The fastest way to try Jylos without building anything:

```bash
jbang jylos@RGiskard7/jylos
```

JBang automatically downloads Java 21 and the JavaFX modules if needed.

## Build from source

Requirements: **Java JDK 21** and **Maven 3.9+**.

```bash
java -version
mvn -version
```

Clone the repository and build:

```bash
git clone https://github.com/RGiskard7/jylos.git
cd jylos
./scripts/build_all.sh
```

On Windows PowerShell:

```powershell
.\scripts\build_all.ps1
```

This produces `jylos/target/jylos-<version>-uber.jar`.

## Run after building

The launcher scripts set the JavaFX module path (required unless you use the uber-JAR with a compatible runtime):

```bash
./scripts/launch-jylos.sh
```

```powershell
.\scripts\launch-jylos.ps1
# or
.\scripts\launch-jylos.bat
```

> **Note:** plain `java -jar` without the module path often fails on JavaFX. Prefer the launcher scripts.

## Next steps

- [First launch](/start/first-launch)
- [Getting started](/start/getting-started)
