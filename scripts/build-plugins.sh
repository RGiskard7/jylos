#!/bin/bash
# Build script for Jylos plugins (macOS/Linux)
# Compiles and packages plugins as JAR files in the plugins/ directory

set -e

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
GRAY='\033[0;90m'
NC='\033[0m' # No Color

# Get script directory
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
PLUGINS_SOURCE="$PROJECT_ROOT/plugins-source"
JYLOS_DIR="$PROJECT_ROOT/jylos"
PLUGINS_OUTPUT="$JYLOS_DIR/plugins"
TARGET_DIR="$JYLOS_DIR/target/classes"

echo -e "${CYAN}Building Jylos Plugins...${NC}"
echo -e "${GRAY}Source: $PLUGINS_SOURCE${NC}"
echo -e "${GRAY}Output: $PLUGINS_OUTPUT${NC}"
echo ""

# Clean if requested
if [ "$1" = "--clean" ] || [ "$1" = "-c" ]; then
    echo -e "${YELLOW}Cleaning plugins directory...${NC}"
    if [ -d "$PLUGINS_OUTPUT" ]; then
        rm -f "$PLUGINS_OUTPUT"/*.jar
        echo -e "${GREEN}Cleaned plugins directory${NC}"
    fi
fi

# Ensure plugins directory exists
if [ ! -d "$PLUGINS_OUTPUT" ]; then
    mkdir -p "$PLUGINS_OUTPUT"
    echo -e "${GREEN}Created plugins directory: $PLUGINS_OUTPUT${NC}"
fi

# Check if plugins source exists
if [ ! -d "$PLUGINS_SOURCE" ]; then
    echo -e "${RED}ERROR: plugins-source directory not found!${NC}"
    echo -e "${RED}Expected location: $PLUGINS_SOURCE${NC}"
    exit 1
fi

# Find Java
if [ -n "$JAVA_HOME" ]; then
    JAVAC="$JAVA_HOME/bin/javac"
    JAR="$JAVA_HOME/bin/jar"
else
    JAVAC="javac"
    JAR="jar"
fi

# Check Java
if ! command -v "$JAVAC" &> /dev/null; then
    echo -e "${RED}ERROR: Java compiler not found!${NC}"
    echo -e "${YELLOW}Please set JAVA_HOME or add Java to PATH${NC}"
    exit 1
fi

JAVA_VERSION=$("$JAVAC" -version 2>&1 | head -n 1)
echo -e "${GRAY}Using: $JAVA_VERSION${NC}"
echo ""

# Build core classes first
echo -e "${CYAN}Building core classes...${NC}"
cd "$JYLOS_DIR"
mvn compile -q
if [ $? -ne 0 ]; then
    echo -e "${RED}ERROR: Failed to compile core classes${NC}"
    exit 1
fi

# Get classpath using Maven
echo -e "${GRAY}Building classpath with Maven...${NC}"
cd "$JYLOS_DIR"

# Try to get classpath from Maven (more reliable method)
MVN_CLASSPATH_OUTPUT=$(mvn dependency:build-classpath -DincludeScope=compile 2>&1)
# The classpath is on the line AFTER "[INFO] Dependencies classpath:"
MVN_CLASSPATH=$(echo "$MVN_CLASSPATH_OUTPUT" | grep -A 1 "^\[INFO\] Dependencies classpath:" | tail -n 1 | sed 's/^\[INFO\] //' | tr -d '\r' | xargs)

if [ -z "$MVN_CLASSPATH" ] || [ "$MVN_CLASSPATH" = "" ]; then
    echo -e "${YELLOW}WARNING: Could not get Maven classpath, using manual construction${NC}"
    CLASSPATH="$TARGET_DIR"
    
    # Detect platform for JavaFX
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
    
    echo -e "${GRAY}Detected platform: $OS_NAME ($ARCH) -> JavaFX suffix: $PLATFORM_SUFFIX${NC}"
    
    # Add JavaFX modules from Maven repo
    M2_REPO="$HOME/.m2/repository"
    JAVAFX_VERSION="21"
    
    for module in javafx-base javafx-controls javafx-fxml javafx-graphics javafx-web javafx-media; do
        MODULE_PATH="$M2_REPO/org/openjfx/$module/$JAVAFX_VERSION"
        if [ -d "$MODULE_PATH" ]; then
            JAR_FILE=""
            
            # First try platform-specific JAR
            if [ -n "$PLATFORM_SUFFIX" ]; then
                JAR_FILE=$(find "$MODULE_PATH" -name "*${PLATFORM_SUFFIX}.jar" -not -name "*-sources.jar" -not -name "*-javadoc.jar" | head -n 1)
            fi
            
            # If not found, try generic JAR (without platform suffix)
            if [ -z "$JAR_FILE" ]; then
                JAR_FILE=$(find "$MODULE_PATH" -name "${module}-${JAVAFX_VERSION}.jar" -not -name "*-sources.jar" -not -name "*-javadoc.jar" | head -n 1)
            fi
            
            # If still not found, try any JAR (last resort)
            if [ -z "$JAR_FILE" ]; then
                JAR_FILE=$(find "$MODULE_PATH" -name "*.jar" -not -name "*-sources.jar" -not -name "*-javadoc.jar" | head -n 1)
            fi
            
            if [ -n "$JAR_FILE" ] && [ -f "$JAR_FILE" ]; then
                CLASSPATH="$CLASSPATH:$JAR_FILE"
                echo -e "${GRAY}  Added: $(basename "$JAR_FILE")${NC}"
            else
                echo -e "${YELLOW}  Warning: $module not found${NC}"
            fi
        else
            echo -e "${YELLOW}  Warning: $module directory not found at $MODULE_PATH${NC}"
        fi
    done
    
    # Add other dependencies
    for dep in "org/xerial/sqlite-jdbc" "org/commonmark/commonmark" "org/controlsfx/controlsfx"; do
        DEP_PATH="$M2_REPO/$dep"
        if [ -d "$DEP_PATH" ]; then
            JAR_FILE=$(find "$DEP_PATH" -name "*.jar" -not -name "*-sources.jar" -not -name "*-javadoc.jar" | head -n 1)
            if [ -n "$JAR_FILE" ] && [ -f "$JAR_FILE" ]; then
                CLASSPATH="$CLASSPATH:$JAR_FILE"
                echo -e "${GRAY}  Added: $(basename "$JAR_FILE")${NC}"
            fi
        fi
    done
else
    CLASSPATH="$TARGET_DIR:$MVN_CLASSPATH"
    echo -e "${GREEN}Classpath obtained from Maven${NC}"
fi

echo -e "${GRAY}Classpath configured${NC}"
echo ""

# Multi-file plugins: a directory holding a plugin.properties descriptor is one plugin
# built from every .java below it, instead of the default "one source file = one JAR".
# Needed by plugins too large for a single file (parser + evaluator + renderer), which
# would otherwise fail to compile because their siblings are not on the source path.
BUNDLE_DESCRIPTORS=$(find "$PLUGINS_SOURCE" -name "plugin.properties")
BUNDLE_DIRS=""
for descriptor in $BUNDLE_DESCRIPTORS; do
    BUNDLE_DIRS="$BUNDLE_DIRS $(dirname "$descriptor")"
done

# Single-file plugins are every remaining source outside a bundle directory.
PLUGIN_FILES=$(find "$PLUGINS_SOURCE" -name "*.java" -not -name "package-info.java")
for bundle_dir in $BUNDLE_DIRS; do
    PLUGIN_FILES=$(echo "$PLUGIN_FILES" | grep -v "^$bundle_dir/" || true)
done

if [ -z "$PLUGIN_FILES" ] && [ -z "$BUNDLE_DIRS" ]; then
    echo -e "${YELLOW}WARNING: No plugin source files found in $PLUGINS_SOURCE${NC}"
    exit 0
fi

PLUGIN_COUNT=$(echo "$PLUGIN_FILES" | grep -c . || true)
echo -e "${CYAN}Found $PLUGIN_COUNT single-file plugin(s)${NC}"
echo ""

# Compile each plugin
COMPILED_PLUGINS=()

# --- Multi-file plugin bundles -------------------------------------------------
for bundle_dir in $BUNDLE_DIRS; do
    bundle_name=$(basename "$bundle_dir")
    echo -e "${CYAN}Building plugin bundle: $bundle_name...${NC}"

    # plugin.class is required: with several classes implementing Plugin in one
    # bundle, auto-detection would pick an arbitrary one.
    BUNDLE_CLASS=$(grep '^plugin.class=' "$bundle_dir/plugin.properties" | head -n 1 | cut -d'=' -f2- | tr -d '\r' | xargs)
    BUNDLE_JAR_NAME=$(grep '^plugin.jar=' "$bundle_dir/plugin.properties" | head -n 1 | cut -d'=' -f2- | tr -d '\r' | xargs)
    if [ -z "$BUNDLE_JAR_NAME" ]; then
        BUNDLE_JAR_NAME="$bundle_name"
    fi
    if [ -z "$BUNDLE_CLASS" ]; then
        echo -e "${RED}  ERROR: $bundle_dir/plugin.properties has no plugin.class entry${NC}"
        continue
    fi

    TEMP_DIR=$(mktemp -d -t jylos-bundle-XXXXXX)
    CLASSES_DIR="$TEMP_DIR/classes"
    mkdir -p "$CLASSES_DIR"
    BUNDLE_SOURCES=$(find "$bundle_dir" -name "*.java")

    # Third-party dependencies: any JAR under the bundle's lib/ directory. They join
    # the compile classpath and are packed into the plugin JAR itself (below), because
    # a plugin is installed and removed as a single file — the manager's file chooser
    # and deletePluginJar() both assume exactly one JAR — so a sidecar lib/ directory
    # next to the installed plugin could never travel with it.
    LIB_JARS=""
    if [ -d "$bundle_dir/lib" ]; then
        LIB_JARS=$(find "$bundle_dir/lib" -name "*.jar" | sort)
    fi
    BUNDLE_CLASSPATH="$CLASSPATH"
    for lib in $LIB_JARS; do
        BUNDLE_CLASSPATH="$BUNDLE_CLASSPATH:$lib"
    done
    LIB_COUNT=$(echo "$LIB_JARS" | grep -c . || true)
    if [ "$LIB_COUNT" -gt 0 ]; then
        echo -e "${GRAY}  Bundling $LIB_COUNT dependency JAR(s) from lib/${NC}"
    fi

    echo -e "${GRAY}  Compiling $(echo "$BUNDLE_SOURCES" | grep -c .) source file(s)...${NC}"
    if ! "$JAVAC" --release 21 -encoding UTF-8 -cp "$BUNDLE_CLASSPATH" -d "$CLASSES_DIR" $BUNDLE_SOURCES; then
        echo -e "${RED}  ERROR: Failed to compile bundle $bundle_name${NC}"
        rm -rf "$TEMP_DIR"
        continue
    fi

    # Merge each dependency's contents into the staging directory.
    LIB_FAILED=0
    for lib in $LIB_JARS; do
        UNPACK_DIR="$TEMP_DIR/unpack"
        rm -rf "$UNPACK_DIR"
        mkdir -p "$UNPACK_DIR"
        if ! (cd "$UNPACK_DIR" && "$JAR" xf "$lib"); then
            echo -e "${RED}  ERROR: Could not unpack $(basename "$lib")${NC}"
            LIB_FAILED=1
            break
        fi
        if [ -d "$UNPACK_DIR/META-INF" ]; then
            # A signed dependency keeps digests that no longer match once its classes
            # live in a different archive; leaving them in makes the JVM reject the whole
            # plugin JAR with "Invalid signature file digest" at load time.
            find "$UNPACK_DIR/META-INF" -maxdepth 1 -type f \
                \( -name '*.SF' -o -name '*.DSA' -o -name '*.RSA' -o -name '*.EC' \
                   -o -name 'INDEX.LIST' -o -name 'MANIFEST.MF' \) -delete
            # ServiceLoader registrations from different dependencies share file names,
            # so they are appended rather than overwritten — a plain copy would leave
            # only the last dependency's providers discoverable.
            if [ -d "$UNPACK_DIR/META-INF/services" ]; then
                mkdir -p "$CLASSES_DIR/META-INF/services"
                for service_file in "$UNPACK_DIR/META-INF/services"/*; do
                    [ -f "$service_file" ] || continue
                    cat "$service_file" >> "$CLASSES_DIR/META-INF/services/$(basename "$service_file")"
                    echo "" >> "$CLASSES_DIR/META-INF/services/$(basename "$service_file")"
                done
                rm -rf "$UNPACK_DIR/META-INF/services"
            fi
        fi
        # A dependency's module descriptor is meaningless on the classpath and would
        # collide with another dependency's.
        rm -f "$UNPACK_DIR/module-info.class"
        cp -R "$UNPACK_DIR/." "$CLASSES_DIR/"
        rm -rf "$UNPACK_DIR"
    done
    if [ "$LIB_FAILED" -eq 1 ]; then
        rm -rf "$TEMP_DIR"
        continue
    fi

    echo -e "${GRAY}  Plugin class: $BUNDLE_CLASS${NC}"
    MANIFEST_FILE="$TEMP_DIR/MANIFEST.MF"
    cat > "$MANIFEST_FILE" << EOF
Manifest-Version: 1.0
Plugin-Class: $BUNDLE_CLASS
Created-By: Jylos Plugin Builder

EOF

    JAR_PATH="$PLUGINS_OUTPUT/$BUNDLE_JAR_NAME.jar"
    # Packs the whole staging directory, not just com/: a dependency's packages
    # (org/, io/, …) must end up in the JAR too.
    if "$JAR" cfm "$JAR_PATH" "$MANIFEST_FILE" -C "$CLASSES_DIR" . 2>/dev/null && [ -f "$JAR_PATH" ]; then
        echo -e "${GREEN}  [OK] Created: $BUNDLE_JAR_NAME.jar${NC}"
        COMPILED_PLUGINS+=("$BUNDLE_JAR_NAME")
    else
        echo -e "${RED}  ERROR: Failed to create JAR for $bundle_name${NC}"
    fi
    rm -rf "$TEMP_DIR"
done


while IFS= read -r plugin_file; do
    # A here-string over an empty list still yields one empty line.
    if [ -z "$plugin_file" ]; then
        continue
    fi
    plugin_name=$(basename "$plugin_file" .java)

    # Skip package-info.java
    if [ "$plugin_name" = "package-info" ]; then
        continue
    fi
    
    echo -e "${CYAN}Building plugin: $plugin_name...${NC}"
    
    # Create temp directory for this plugin
    TEMP_DIR=$(mktemp -d -t jylos-plugin-XXXXXX)
    
    # Get package structure
    RELATIVE_PATH=$(echo "$plugin_file" | sed "s|$PLUGINS_SOURCE/||")
    PACKAGE_DIR=$(dirname "$RELATIVE_PATH")
    PLUGIN_PACKAGE_DIR="$TEMP_DIR/$PACKAGE_DIR"
    
    # Create package directory structure
    mkdir -p "$PLUGIN_PACKAGE_DIR"
    
    # Copy plugin source file
    cp "$plugin_file" "$PLUGIN_PACKAGE_DIR/"
    
    # Compile plugin
    echo -e "${GRAY}  Compiling...${NC}"
    "$JAVAC" --release 21 -encoding UTF-8 -cp "$CLASSPATH" -d "$TEMP_DIR" "$PLUGIN_PACKAGE_DIR/$plugin_name.java" 2>&1 | while IFS= read -r line; do
        if echo "$line" | grep -q "error:"; then
            echo -e "${RED}  ERROR: $line${NC}"
        elif echo "$line" | grep -q "warning:"; then
            echo -e "${YELLOW}  Warning: $line${NC}"
        fi
    done
    
    # Check if compilation succeeded
    CLASS_FILE=$(find "$TEMP_DIR" -name "$plugin_name.class" | head -n 1)
    if [ -z "$CLASS_FILE" ]; then
        echo -e "${RED}  ERROR: Failed to compile $plugin_name${NC}"
        rm -rf "$TEMP_DIR"
        continue
    fi
    
    # Get full class name
    RELATIVE_CLASS_PATH=$(echo "$CLASS_FILE" | sed "s|$TEMP_DIR/||")
    CLASS_NAME=$(echo "$RELATIVE_CLASS_PATH" | sed 's|/|.|g' | sed 's|\.class$||')
    
    echo -e "${GRAY}  Plugin class: $CLASS_NAME${NC}"
    
    # Create JAR
    JAR_NAME="$plugin_name.jar"
    JAR_PATH="$PLUGINS_OUTPUT/$JAR_NAME"
    
    echo -e "${GRAY}  Creating JAR: $JAR_NAME...${NC}"
    
    # Create manifest
    MANIFEST_FILE="$TEMP_DIR/MANIFEST.MF"
    cat > "$MANIFEST_FILE" << EOF
Manifest-Version: 1.0
Plugin-Class: $CLASS_NAME
Created-By: Jylos Plugin Builder

EOF
    
    # Remove source files from temp dir
    find "$TEMP_DIR" -name "*.java" -delete
    
    # Build JAR
    cd "$TEMP_DIR"
    "$JAR" cfm "$JAR_PATH" "$MANIFEST_FILE" com 2>/dev/null
    
    if [ $? -eq 0 ] && [ -f "$JAR_PATH" ]; then
        echo -e "${GREEN}  [OK] Created: $JAR_NAME${NC}"
        COMPILED_PLUGINS+=("$plugin_name")
    else
        echo -e "${RED}  ERROR: Failed to create JAR${NC}"
    fi
    
    # Cleanup
    cd "$PROJECT_ROOT"
    rm -rf "$TEMP_DIR"
done <<< "$PLUGIN_FILES"

echo ""
echo -e "${CYAN}========================================${NC}"
echo -e "${CYAN}Plugin Build Summary${NC}"
echo -e "${CYAN}========================================${NC}"
echo -e "${GREEN}Built: ${#COMPILED_PLUGINS[@]} plugin(s)${NC}"
for plugin in "${COMPILED_PLUGINS[@]}"; do
    echo -e "${GRAY}  - $plugin.jar${NC}"
done
echo ""
echo -e "${CYAN}Plugins location: $PLUGINS_OUTPUT${NC}"
echo -e "${GREEN}Done!${NC}"