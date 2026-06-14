#!/bin/bash
# Jylos - Simplified launcher for macOS/Linux
# This script automatically detects Java and JavaFX and launches the application
#
# IMPORTANT: Run with bash, not sh:
#   ./scripts/launch-jylos.sh
#   OR: bash ./scripts/launch-jylos.sh

# Colors for messages (only if terminal supports it)
if [ -t 1 ]; then
    GREEN='\033[0;32m'
    RED='\033[0;31m'
    YELLOW='\033[1;33m'
    NC='\033[0m'
else
    GREEN=''
    RED=''
    YELLOW=''
    NC=''
fi

# Print colored message (compatible with both bash and sh)
print_color() {
    color="$1"
    message="$2"
    printf "%b%s%b\n" "$color" "$message" "$NC"
}

echo ""
echo "========================================"
echo "  Jylos - Launcher"
echo "========================================"
echo ""

# Get script directory and navigate to Jylos directory
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
JYLOS_DIR="$(cd "$SCRIPT_DIR/../jylos" && pwd)"
# Discover the uber jar by glob so the launcher never couples to a hardcoded version.
JAR=$(ls "$JYLOS_DIR"/target/jylos-*-uber.jar 2>/dev/null | head -n1)
[ -z "$JAR" ] && JAR="$JYLOS_DIR/target/jylos-uber.jar"

# Check if JAR exists
if [ ! -f "$JAR" ]; then
    print_color "$RED" "Error: JAR not found at $JAR"
    echo ""
    echo "Please build the project first:"
    echo "  ./scripts/build_all.sh"
    echo ""
    echo "Or manually:"
    echo "  cd jylos"
    echo "  mvn clean package -DskipTests"
    echo ""
    exit 1
fi

# Check if Java is installed
if ! command -v java > /dev/null 2>&1; then
    print_color "$RED" "Error: Java not found in PATH"
    echo ""
    echo "Please install Java 17 or higher from:"
    echo "  https://adoptium.net/"
    echo ""
    echo "And make sure it's added to your PATH."
    echo ""
    exit 1
fi

# Check Java version
JAVA_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}')
print_color "$GREEN" "Java found: $JAVA_VERSION"
echo ""

# Detect platform for JavaFX platform-specific JARs
OS_NAME=$(uname -s)
ARCH=$(uname -m)

case "$OS_NAME" in
    Darwin*)
        if [ "$ARCH" = "arm64" ]; then
            PLATFORM_SUFFIX="mac-aarch64"
        else
            PLATFORM_SUFFIX="mac"
        fi
        ;;
    Linux*)
        if [ "$ARCH" = "aarch64" ]; then
            PLATFORM_SUFFIX="linux-aarch64"
        else
            PLATFORM_SUFFIX="linux"
        fi
        ;;
    *)
        PLATFORM_SUFFIX=""
        ;;
esac

echo "Detected platform: $OS_NAME ($ARCH) -> JavaFX suffix: $PLATFORM_SUFFIX"
echo ""

# Find JavaFX in Maven repository
M2_REPO="$HOME/.m2/repository"
JAVAFX_BASE="$M2_REPO/org/openjfx"

if [ ! -d "$JAVAFX_BASE" ]; then
    print_color "$YELLOW" "Warning: JavaFX not found in Maven repository"
    echo ""
    echo "Attempting to launch without module-path (may fail)..."
    echo ""
    cd "$JYLOS_DIR"
    java -jar "$JAR"
    exit $?
fi

# Find JavaFX version (21.x.x). Pick the HIGHEST 21.x present so the runtime
# modules match the version the app was built against (and include CoreText
# font crash fixes on macOS). A plain glob would stop at "21" (lexically first).
JAVAFX_VERSION=""
if [ -d "$JAVAFX_BASE/javafx-controls" ]; then
    JAVAFX_VERSION=$(ls -1 "$JAVAFX_BASE/javafx-controls" 2>/dev/null \
        | grep -E '^[0-9]+(\.[0-9]+)*$' | sort -V | tail -1)
fi

if [ -z "$JAVAFX_VERSION" ]; then
    print_color "$YELLOW" "Warning: JavaFX 21 not found in Maven repository"
    echo ""
    echo "Attempting to launch without module-path..."
    echo ""
    cd "$JYLOS_DIR"
    java -jar "$JAR"
    exit $?
fi

# Build module-path using specific JAR files (not directories)
# This prevents Java from scanning directories and picking up -sources.jar files
MODULE_PATH=""
MODULES=""

# Include all required JavaFX modules (javafx.web requires javafx.media)
for module in base controls fxml graphics media web; do
    MODULE_DIR="$JAVAFX_BASE/javafx-$module/$JAVAFX_VERSION"
    if [ -d "$MODULE_DIR" ]; then
        jar_file=""
        
        # First, try to find platform-specific JAR (e.g., javafx-base-21-mac-aarch64.jar)
        if [ -n "$PLATFORM_SUFFIX" ]; then
            for f in "$MODULE_DIR"/javafx-"$module"-*-"$PLATFORM_SUFFIX".jar; do
                if [ -f "$f" ]; then
                    jar_file="$f"
                    break
                fi
            done
        fi
        
        # If no platform-specific JAR found, try the generic one (without platform suffix)
        # But exclude -sources.jar, -javadoc.jar, and platform-specific ones
        if [ -z "$jar_file" ]; then
            for f in "$MODULE_DIR"/javafx-"$module"-"$JAVAFX_VERSION".jar; do
                if [ -f "$f" ]; then
                    jar_file="$f"
                    break
                fi
            done
        fi
        
        if [ -n "$jar_file" ] && [ -f "$jar_file" ]; then
            echo "  Found: $jar_file"
            if [ -z "$MODULE_PATH" ]; then
                MODULE_PATH="$jar_file"
            else
                MODULE_PATH="$MODULE_PATH:$jar_file"
            fi
            if [ -z "$MODULES" ]; then
                MODULES="javafx.$module"
            else
                MODULES="$MODULES,javafx.$module"
            fi
        else
            print_color "$YELLOW" "  Warning: javafx-$module not found"
        fi
    fi
done

echo ""

if [ -z "$MODULE_PATH" ]; then
    print_color "$YELLOW" "Warning: Could not find JavaFX modules"
    echo ""
    echo "Attempting to launch without module-path..."
    echo ""
    cd "$JYLOS_DIR"
    java -jar "$JAR"
    exit $?
fi

print_color "$GREEN" "JavaFX found (version $JAVAFX_VERSION)"
echo ""
echo "Launching Jylos..."
echo ""

# Change to Jylos directory so relative paths work
cd "$JYLOS_DIR"

# Launch with module-path
java --module-path "$MODULE_PATH" --add-modules "$MODULES" -jar "$JAR"

EXIT_CODE=$?

if [ $EXIT_CODE -ne 0 ]; then
    echo ""
    print_color "$RED" "Error launching application (code: $EXIT_CODE)"
    echo ""
fi

exit $EXIT_CODE
