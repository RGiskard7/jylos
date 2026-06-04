#!/bin/bash
# Prints JavaFX module-path for IDE launch (macOS/Linux).
# Usage: ./scripts/get-javafx-module-path.sh

set -e

M2_REPO="${HOME}/.m2/repository"
JAVAFX_VERSION="21"
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
        echo "Unsupported OS for this script. Use package-windows.ps1 paths on Windows." >&2
        exit 1
        ;;
esac

MODULE_PATH=""
for module in base controls fxml graphics media web; do
    MODULE_DIR="$M2_REPO/org/openjfx/javafx-$module/$JAVAFX_VERSION"
    jar_file=""
    if [ -d "$MODULE_DIR" ]; then
        if [ -n "$PLATFORM_SUFFIX" ]; then
            jar_file=$(find "$MODULE_DIR" -maxdepth 1 -name "*${PLATFORM_SUFFIX}.jar" 2>/dev/null | head -n 1)
        fi
        if [ -z "$jar_file" ]; then
            jar_file=$(find "$MODULE_DIR" -maxdepth 1 -name "javafx-${module}-${JAVAFX_VERSION}.jar" 2>/dev/null | head -n 1)
        fi
        if [ -n "$jar_file" ] && [ -f "$jar_file" ]; then
            if [ -z "$MODULE_PATH" ]; then
                MODULE_PATH="$jar_file"
            else
                MODULE_PATH="$MODULE_PATH:$jar_file"
            fi
        fi
    fi
done

if [ -z "$MODULE_PATH" ]; then
    echo "JavaFX JARs not found. Run: mvn -f jylos/pom.xml compile" >&2
    exit 1
fi

echo "$MODULE_PATH"
