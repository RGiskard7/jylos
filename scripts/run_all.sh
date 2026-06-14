#!/bin/bash
set -euo pipefail

# Run script for Jylos (Linux / macOS)
# Usage: ./scripts/run_all.sh
# 
# This script launches Jylos with JavaFX module-path configuration if needed.
#
# IMPORTANT: Run with bash, not sh:
#   ./scripts/run_all.sh
#   OR: bash ./scripts/run_all.sh

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR/jylos"

# Discover the uber jar by glob so the script never couples to a hardcoded version.
JAR=$(ls target/jylos-*-uber.jar 2>/dev/null | head -n1)
[ -z "$JAR" ] && JAR="target/jylos-uber.jar"
M2_REPO="$HOME/.m2/repository"

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

if [ -f "$JAR" ]; then
    echo "Launching Jylos..."
    echo "JAR: $(pwd)/$JAR"
    echo "Platform: $OS_NAME ($ARCH) -> JavaFX suffix: $PLATFORM_SUFFIX"
    
    # Build JavaFX module path from Maven repository
    JAVAFX_MODULES=""
    if [ -d "$M2_REPO/org/openjfx" ]; then
        # Find JavaFX version — pick the HIGHEST 21.x present (a plain glob stops at
        # "21", which would pin an older runtime than the app was built against).
        JAVAFX_VERSION=$(ls -1 "$M2_REPO/org/openjfx/javafx-controls" 2>/dev/null \
            | grep -E '^[0-9]+(\.[0-9]+)*$' | sort -V | tail -1)
        
        if [ -n "$JAVAFX_VERSION" ]; then
            echo "JavaFX version: $JAVAFX_VERSION"
            
            # Include all required JavaFX modules (javafx.web requires javafx.media)
            for module in javafx-base javafx-controls javafx-fxml javafx-graphics javafx-media javafx-web; do
                MODULE_DIR="$M2_REPO/org/openjfx/$module/$JAVAFX_VERSION"
                if [ -d "$MODULE_DIR" ]; then
                    jar_file=""
                    
                    # First, try to find platform-specific JAR
                    if [ -n "$PLATFORM_SUFFIX" ]; then
                        for f in "$MODULE_DIR"/"$module"-*-"$PLATFORM_SUFFIX".jar; do
                            if [ -f "$f" ]; then
                                jar_file="$f"
                                break
                            fi
                        done
                    fi
                    
                    # If no platform-specific JAR, try generic one
                    if [ -z "$jar_file" ]; then
                        for f in "$MODULE_DIR"/"$module"-"$JAVAFX_VERSION".jar; do
                            if [ -f "$f" ]; then
                                jar_file="$f"
                                break
                            fi
                        done
                    fi
                    
                    if [ -n "$jar_file" ] && [ -f "$jar_file" ]; then
                        if [ -z "$JAVAFX_MODULES" ]; then
                            JAVAFX_MODULES="$jar_file"
                        else
                            JAVAFX_MODULES="$JAVAFX_MODULES:$jar_file"
                        fi
                    fi
                fi
            done
        fi
    fi
    
    # Launch with module-path if JavaFX modules were found
    if [ -n "$JAVAFX_MODULES" ]; then
        echo "Using JavaFX module-path..."
        java --module-path "$JAVAFX_MODULES" --add-modules javafx.base,javafx.controls,javafx.fxml,javafx.graphics,javafx.media,javafx.web -jar "$JAR"
    else
        echo "JavaFX modules not found in Maven repository. Attempting standard JAR launch..."
        java -jar "$JAR"
    fi
    
    if [ $? -ne 0 ]; then
        echo "JAR launch failed. Attempting via Maven exec:java..."
        mvn -f 'pom.xml' exec:java "-Dexec.mainClass=com.example.jylos.Main" 2>&1
    fi
else
    echo "Packaged JAR not found, running via Maven..."
    mvn clean compile exec:java -Dexec.mainClass=com.example.jylos.Main
fi
