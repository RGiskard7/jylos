#!/bin/bash
# Package script for Jylos - macOS (DMG installer)
# Usage: ./scripts/package-macos.sh
#
# Creates a native macOS DMG installer using jpackage.
# The resulting .app bundle includes a bundled JRE.

set -e

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
        local value=$(grep "^[[:space:]]*${key}[[:space:]]*=" "$props_file" 2>/dev/null | cut -d'=' -f2- | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')
        if [ -n "$value" ]; then
            echo "$value"
            return
        fi
    fi
    echo "$default"
}

# Read application metadata from app.properties
APP_NAME=$(read_property "app.name" "Jylos")
APP_VERSION=$(read_property "app.version" "2.4.0")
APP_VENDOR=$(read_property "app.vendor" "Jylos")
APP_DESCRIPTION=$(read_property "app.description" "A free and open-source note-taking application")
APP_COPYRIGHT=$(read_property "app.copyright" "Copyright 2025 Jylos")
APP_ICON=$(read_property "app.icon.macos" "src/main/resources/icons/app-icon.icns")
APP_CATEGORY=$(read_property "app.package.category.macos" "public.app-category.productivity")

echo "========================================"
echo "  $APP_NAME - macOS Package Builder"
echo "========================================"
echo ""

# Check if Java is installed
if ! command -v java &> /dev/null; then
    echo "Error: Java not found. Please install JDK 17 or higher."
    echo "Download from: https://adoptium.net/"
    exit 1
fi

JAVA_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}')
echo "Java found: $JAVA_VERSION"
echo ""

# Check if jpackage is available
if ! command -v jpackage &> /dev/null; then
    echo "Error: jpackage not found. jpackage is included in JDK 17+."
    echo "Please install JDK (not JRE) from: https://adoptium.net/"
    exit 1
fi

echo "jpackage found"
echo ""

# Build the JAR first
echo "Building JAR..."
mvn clean package -DskipTests

if [ $? -ne 0 ]; then
    echo "Error: Build failed"
    exit 1
fi

# Build plugins (required for packaged app to have plugins available)
BUILD_PLUGINS_SCRIPT="$ROOT_DIR/scripts/build-plugins.sh"
if [ -f "$BUILD_PLUGINS_SCRIPT" ]; then
    echo ""
    echo "Building plugins..."
    if "$BUILD_PLUGINS_SCRIPT"; then
        PLUGINS_DIR="$JYLOS_DIR/plugins"
        if [ -d "$PLUGINS_DIR" ]; then
            PLUGIN_COUNT=$(find "$PLUGINS_DIR" -maxdepth 1 -name "*.jar" 2>/dev/null | wc -l | tr -d ' ')
            echo "Plugins built: $PLUGIN_COUNT JAR(s) in plugins/"
        fi
    else
        echo "Warning: Plugin build failed. Packaged app will run without plugins."
    fi
else
    echo "Warning: build-plugins.sh not found. Packaged app will run without plugins."
fi

echo ""
echo "Creating macOS DMG installer..."
echo ""

OUTPUT_DIR="target/installers"
mkdir -p "$OUTPUT_DIR"

# Create a temporary input directory with JAR and optionally plugins
TEMP_INPUT_DIR=$(mktemp -d -t Jylos-jpackage-input-XXXXXX)
JAR_FILE=$(ls "target/jylos-"*"-uber.jar" 2>/dev/null | head -n1)
if [ -z "$JAR_FILE" ]; then
    echo "Error: uber-JAR not found in target/. Run 'mvn package' first."
    exit 1
fi
JAR_NAME=$(basename "$JAR_FILE")
cp "$JAR_FILE" "$TEMP_INPUT_DIR/"

# Include plugins if available (for --app-content)
SOURCE_PLUGINS_DIR="$JYLOS_DIR/plugins"
PLUGINS_INCLUDED=false

# Cleanup function
cleanup() {
    rm -rf "$TEMP_INPUT_DIR"
}
trap cleanup EXIT

echo "Packaging application (this may take several minutes)..."
echo ""

# Build jpackage command
JPACKAGE_CMD="jpackage \
    --input \"$TEMP_INPUT_DIR\" \
    --name \"$APP_NAME\" \
    --main-jar \"$JAR_NAME\" \
    --main-class com.example.jylos.Launcher \
    --type dmg \
    --dest \"$OUTPUT_DIR\" \
    --app-version \"$APP_VERSION\" \
    --vendor \"$APP_VENDOR\" \
    --description \"$APP_DESCRIPTION\" \
    --copyright \"$APP_COPYRIGHT\" \
    --mac-package-name \"$APP_NAME\" \
    --mac-app-category \"$APP_CATEGORY\" \
    --java-options \"-Dfile.encoding=UTF-8\" \
    --java-options \"-Dapple.awt.application.appearance=system\""

# Add icon if it exists
ICON_PATH="$JYLOS_DIR/$APP_ICON"
if [ -f "$ICON_PATH" ]; then
    JPACKAGE_CMD="$JPACKAGE_CMD --icon \"$ICON_PATH\""
    echo "Using icon: $ICON_PATH"
else
    echo "Icon not found at $ICON_PATH, skipping icon..."
fi

# Include plugins via --app-content if jpackage supports it and we have plugins
if [ -d "$SOURCE_PLUGINS_DIR" ]; then
    PLUGIN_JARS=$(find "$SOURCE_PLUGINS_DIR" -maxdepth 1 -name "*.jar" 2>/dev/null)
    if [ -n "$PLUGIN_JARS" ]; then
        JPACKAGE_HELP=$(jpackage --help 2>&1 || true)
        if echo "$JPACKAGE_HELP" | grep -q "app-content"; then
            JPACKAGE_CMD="$JPACKAGE_CMD --app-content \"$SOURCE_PLUGINS_DIR\""
            PLUGINS_INCLUDED=true
            PLUGIN_COUNT=$(echo "$PLUGIN_JARS" | wc -l | tr -d ' ')
            echo "Including plugins via --app-content ($PLUGIN_COUNT JAR(s))"
        fi
    fi
fi

# ── Code signing (opt-in) ───────────────────────────────────────────────────
# Set JYLOS_MAC_SIGN_IDENTITY to a "Developer ID Application: Name (TEAMID)"
# certificate installed in the keychain and jpackage will sign the app bundle.
# Without it the DMG is unsigned (Gatekeeper will warn on other Macs).
if [ -n "${JYLOS_MAC_SIGN_IDENTITY:-}" ]; then
    echo "Code signing enabled (identity: $JYLOS_MAC_SIGN_IDENTITY)"
    JPACKAGE_CMD="$JPACKAGE_CMD --mac-sign --mac-signing-key-user-name \"$JYLOS_MAC_SIGN_IDENTITY\""
else
    echo "Code signing disabled (set JYLOS_MAC_SIGN_IDENTITY to enable). See docs/PACKAGING.md."
fi

# Use jpackage to create DMG installer
# Note: The uber-jar already includes JavaFX classes, so we don't need --module-path
eval $JPACKAGE_CMD

if [ $? -eq 0 ]; then
    echo ""
    echo "========================================"
    echo "  Package created successfully!"
    echo "========================================"
    echo ""
    
    if [ "$PLUGINS_INCLUDED" = false ] && [ -d "$SOURCE_PLUGINS_DIR" ]; then
        PLUGIN_JARS=$(find "$SOURCE_PLUGINS_DIR" -maxdepth 1 -name "*.jar" 2>/dev/null)
        if [ -n "$PLUGIN_JARS" ]; then
            echo "Note: Plugins could not be included via --app-content (JDK may be < 18)."
            echo "  Users can add plugins to: ~/Library/Application Support/$APP_NAME/plugins/"
        fi
    fi
    
    DMG_PATH="$OUTPUT_DIR/$APP_NAME-$APP_VERSION.dmg"
    echo "Installer location: $DMG_PATH"
    echo ""

    # ── Notarization (opt-in) ───────────────────────────────────────────────
    # Requires a signed build (JYLOS_MAC_SIGN_IDENTITY) and a notarytool keychain
    # profile created once with:
    #   xcrun notarytool store-credentials <profile> --apple-id <id> --team-id <team> --password <app-pwd>
    # Set JYLOS_NOTARY_PROFILE=<profile> to submit, wait, and staple the ticket.
    if [ -n "${JYLOS_NOTARY_PROFILE:-}" ]; then
        if [ -z "${JYLOS_MAC_SIGN_IDENTITY:-}" ]; then
            echo "Warning: JYLOS_NOTARY_PROFILE is set but the build is unsigned —"
            echo "         Apple rejects unsigned submissions. Skipping notarization."
        else
            echo "Submitting DMG for notarization (profile: $JYLOS_NOTARY_PROFILE)..."
            if xcrun notarytool submit "$DMG_PATH" \
                    --keychain-profile "$JYLOS_NOTARY_PROFILE" --wait; then
                echo "Stapling notarization ticket..."
                xcrun stapler staple "$DMG_PATH"
                echo "Notarized and stapled: $DMG_PATH"
            else
                echo "Error: notarization failed. Inspect with:"
                echo "  xcrun notarytool log <submission-id> --keychain-profile $JYLOS_NOTARY_PROFILE"
                exit 1
            fi
        fi
    else
        echo "Notarization disabled (set JYLOS_NOTARY_PROFILE to enable). See docs/PACKAGING.md."
    fi

    echo ""
    echo "Data will be stored in: ~/Library/Application Support/$APP_NAME/"
    echo ""
    echo "You can now distribute this DMG installer."
else
    echo ""
    echo "Error: Package creation failed"
    exit 1
fi
