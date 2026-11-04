#!/bin/bash
# Package script for Jylos - Arch Linux (pacman .pkg.tar.zst)
# Usage: ./scripts/package-linux-arch.sh
#
# jpackage has no pacman target, so this builds a jpackage app-image (the app plus
# its own Java runtime: the same files the DEB/RPM install under /opt/jylos) and
# wraps it with makepkg. Run it on Arch Linux (or in an archlinux container) as a
# regular user: makepkg refuses to run as root.

set -euo pipefail

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
ROOT_DIR="$( cd "$SCRIPT_DIR/.." && pwd )"
JYLOS_DIR="$ROOT_DIR/jylos"

cd "$JYLOS_DIR"

# Function to read property from app.properties
read_property() {
    local key=$1
    local default=$2
    local props_file="$JYLOS_DIR/src/main/resources/app.properties"
    if [ -f "$props_file" ]; then
        local value
        value=$(grep "^[[:space:]]*${key}[[:space:]]*=" "$props_file" 2>/dev/null | cut -d'=' -f2- | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')
        if [ -n "$value" ]; then
            echo "$value"
            return
        fi
    fi
    echo "$default"
}

read_pom_version() {
    local version
    version=$(grep -m1 "<version>" "$JYLOS_DIR/pom.xml" | sed -E 's/.*<version>([^<]+)<\/version>.*/\1/')
    if [[ "$version" == *'${revision}'* ]]; then
        version=$(grep -m1 "<revision>" "$JYLOS_DIR/pom.xml" | sed -E 's/.*<revision>([^<]+)<\/revision>.*/\1/')
    fi
    echo "$version"
}

# Read application metadata from app.properties
APP_NAME=$(read_property "app.name" "Jylos")
APP_VERSION="${JYLOS_RELEASE_VERSION:-$(read_property "app.version" "2.4.0")}"
if [[ "$APP_VERSION" == *'${'* ]]; then
    APP_VERSION="$(read_pom_version)"
fi
APP_VENDOR=$(read_property "app.vendor" "Jylos")
APP_DESCRIPTION=$(read_property "app.description" "A free and open-source note-taking application")
APP_COPYRIGHT=$(read_property "app.copyright" "Copyright 2025 Jylos")
APP_ICON=$(read_property "app.icon.linux" "src/main/resources/icons/app-icon.png")
APP_CATEGORY=$(read_property "app.package.category.linux" "Office")
PACKAGE_NAME=$(read_property "app.package.name" "jylos")

echo "========================================"
echo "  $APP_NAME - Arch Linux Package Builder"
echo "========================================"
echo ""

# makepkg is what produces a package pacman accepts (.PKGINFO, .MTREE, zstd).
if ! command -v makepkg &> /dev/null; then
    echo "Error: makepkg not found. Run this on Arch Linux (or in an archlinux container)" >&2
    echo "with the base-devel group installed." >&2
    exit 1
fi
if [ "$(id -u)" -eq 0 ]; then
    echo "Error: makepkg refuses to run as root. Run this script as a regular user." >&2
    exit 1
fi
# Arch's JDK packages link java/javac into /usr/bin but not jpackage.
JPACKAGE=""
for candidate in "$(command -v jpackage 2>/dev/null || true)" \
        "${JAVA_HOME:+$JAVA_HOME/bin/jpackage}" /usr/lib/jvm/default/bin/jpackage; do
    if [ -n "$candidate" ] && [ -x "$candidate" ]; then
        JPACKAGE="$candidate"
        break
    fi
done
if ! command -v java &> /dev/null || [ -z "$JPACKAGE" ]; then
    echo "Error: java/jpackage not found. Install a JDK 21 (e.g. 'pacman -S jdk21-openjdk')." >&2
    exit 1
fi
echo "Java found: $(java -version 2>&1 | awk -F '"' '/version/ {print $2}')"
echo ""

# pacman's pkgver may not contain '-', ':' or '/'.
if [[ ! "$APP_VERSION" =~ ^[0-9A-Za-z._+]+$ ]]; then
    echo "Error: version '$APP_VERSION' is not a valid pacman pkgver." >&2
    exit 1
fi

case "$(uname -m)" in
    x86_64)          ARCH_PKG_ARCH="x86_64"; LINUX_ARCH="amd64" ;;
    aarch64|arm64)   ARCH_PKG_ARCH="aarch64"; LINUX_ARCH="arm64" ;;
    *)               ARCH_PKG_ARCH="$(uname -m)"; LINUX_ARCH="$(uname -m)" ;;
esac

# Build the JAR first
echo "Building JAR..."
mvn clean package -DskipTests -Drevision="$APP_VERSION" -Drelease.version="$APP_VERSION"

# Build plugins (required for packaged app to have plugins available)
BUILD_PLUGINS_SCRIPT="$ROOT_DIR/scripts/build-plugins.sh"
if [ -f "$BUILD_PLUGINS_SCRIPT" ]; then
    echo ""
    echo "Building plugins..."
    if ! bash "$BUILD_PLUGINS_SCRIPT"; then
        echo "Warning: Plugin build failed. Packaged app will run without plugins."
    fi
else
    echo "Warning: build-plugins.sh not found. Packaged app will run without plugins."
fi

OUTPUT_DIR="$JYLOS_DIR/target/installers"
mkdir -p "$OUTPUT_DIR"

WORK_DIR=$(mktemp -d -t Jylos-arch-XXXXXX)
cleanup() {
    rm -rf "$WORK_DIR"
}
trap cleanup EXIT

JAR_FILE=$(ls "target/jylos-"*"-uber.jar" 2>/dev/null | head -n1)
if [ -z "$JAR_FILE" ]; then
    echo "Error: uber-JAR not found in target/. Run 'mvn package' first." >&2
    exit 1
fi
mkdir -p "$WORK_DIR/input"
cp "$JAR_FILE" "$WORK_DIR/input/"

JPACKAGE_ARGS=(
    --type app-image
    --input "$WORK_DIR/input"
    --name "$APP_NAME"
    --main-jar "$(basename "$JAR_FILE")"
    --main-class com.example.jylos.Launcher
    --dest "$WORK_DIR/pkg"
    --app-version "$APP_VERSION"
    --vendor "$APP_VENDOR"
    --description "$APP_DESCRIPTION"
    --copyright "$APP_COPYRIGHT"
)
ICON_PATH="$JYLOS_DIR/$APP_ICON"
if [ -f "$ICON_PATH" ]; then
    JPACKAGE_ARGS+=(--icon "$ICON_PATH")
    echo "Using icon: $ICON_PATH"
fi
SOURCE_PLUGINS_DIR="$JYLOS_DIR/plugins"
if [ -n "$(find "$SOURCE_PLUGINS_DIR" -maxdepth 1 -name '*.jar' 2>/dev/null)" ]; then
    JPACKAGE_ARGS+=(--app-content "$SOURCE_PLUGINS_DIR")
    echo "Including plugins via --app-content"
fi

echo ""
echo "Running jpackage (app-image, this may take several minutes)..."
"$JPACKAGE" "${JPACKAGE_ARGS[@]}"

APP_IMAGE="$WORK_DIR/pkg/$APP_NAME"
if [ ! -x "$APP_IMAGE/bin/$APP_NAME" ]; then
    echo "Error: jpackage did not produce $APP_IMAGE/bin/$APP_NAME" >&2
    exit 1
fi

cp "$ROOT_DIR/LICENSE" "$WORK_DIR/pkg/LICENSE"

# Same entry the DEB/RPM get from jpackage --linux-shortcut.
cat > "$WORK_DIR/pkg/$PACKAGE_NAME.desktop" <<EOF
[Desktop Entry]
Name=$APP_NAME
Comment=$APP_DESCRIPTION
Exec=/opt/$PACKAGE_NAME/bin/$APP_NAME
Icon=$PACKAGE_NAME
Terminal=false
Type=Application
Categories=$APP_CATEGORY;
EOF

# Values go in as single-quoted bash strings; escape any single quote they contain.
quote() {
    printf "'%s'" "$(printf '%s' "$1" | sed "s/'/'\\\\''/g")"
}

# Runtime libraries JavaFX loads (GTK windowing, X11 input, fonts, audio) and
# xdg-utils for opening links. The Java runtime itself is bundled in the app-image.
cat > "$WORK_DIR/pkg/PKGBUILD" <<EOF
pkgname=$(quote "$PACKAGE_NAME")
pkgver=$(quote "$APP_VERSION")
pkgrel=1
pkgdesc=$(quote "$APP_DESCRIPTION")
arch=($(quote "$ARCH_PKG_ARCH"))
url='https://github.com/RGiskard7/jylos'
license=('MIT')
depends=('gtk3' 'libxtst' 'libxxf86vm' 'alsa-lib' 'fontconfig' 'xdg-utils')
# The bundled runtime ships its own native libraries: leave them as jpackage built them.
options=('!strip' '!debug')

package() {
    install -d "\$pkgdir/opt/\$pkgname" "\$pkgdir/usr/bin"
    cp -a "\$startdir/$APP_NAME/." "\$pkgdir/opt/\$pkgname/"
    ln -s "/opt/\$pkgname/bin/$APP_NAME" "\$pkgdir/usr/bin/\$pkgname"
    install -Dm644 "\$startdir/\$pkgname.desktop" "\$pkgdir/usr/share/applications/\$pkgname.desktop"
    if [ -f "\$startdir/$APP_NAME/lib/$APP_NAME.png" ]; then
        install -Dm644 "\$startdir/$APP_NAME/lib/$APP_NAME.png" "\$pkgdir/usr/share/pixmaps/\$pkgname.png"
    fi
    install -Dm644 "\$startdir/LICENSE" "\$pkgdir/usr/share/licenses/\$pkgname/LICENSE"
}
EOF

echo ""
echo "Running makepkg..."
(
    cd "$WORK_DIR/pkg"
    # --nodeps: the runtime libraries above are needed to run Jylos, not to package it.
    PKGDEST="$OUTPUT_DIR" PKGEXT='.pkg.tar.zst' makepkg --nodeps --force --noconfirm
)

BUILT_PKG="$OUTPUT_DIR/$PACKAGE_NAME-$APP_VERSION-1-$ARCH_PKG_ARCH.pkg.tar.zst"
if [ ! -f "$BUILT_PKG" ]; then
    echo "Error: makepkg did not produce $BUILT_PKG" >&2
    exit 1
fi

# ── Standardized filename copy ──────────────────────────────────────────────
# Mirrors the naming release-jylos.yml gives its release assets, so the website
# can link to the latest release's package without knowing its version.
STANDARD_PKG="$OUTPUT_DIR/jylos-linux-$LINUX_ARCH.pkg.tar.zst"
cp "$BUILT_PKG" "$STANDARD_PKG"

echo ""
echo "========================================"
echo "  Package created successfully!"
echo "========================================"
echo ""
echo "Installer location: $BUILT_PKG"
echo "Standardized copy:  $STANDARD_PKG"
echo ""
echo "Install with: sudo pacman -U $STANDARD_PKG"
