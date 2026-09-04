#!/bin/bash
# Runs the behavioural tests for bundled plugins (macOS/Linux).
#
# Plugin sources are not part of the Maven module — they are compiled against the app
# like any third-party plugin would be — so `mvn test` does not see them. This script
# compiles plugins-source together with plugins-test and runs each test's main().

set -e

GREEN='\033[0;32m'
RED='\033[0;31m'
CYAN='\033[0;36m'
GRAY='\033[0;90m'
NC='\033[0m'

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
JYLOS_DIR="$PROJECT_ROOT/jylos"
PLUGINS_SOURCE="$PROJECT_ROOT/plugins-source"
PLUGINS_TEST="$PROJECT_ROOT/plugins-test"
BUILD_DIR="$JYLOS_DIR/target/plugin-tests"

if [ ! -d "$PLUGINS_TEST" ]; then
    echo -e "${GRAY}No plugins-test directory — nothing to run.${NC}"
    exit 0
fi

if [ -n "$JAVA_HOME" ]; then
    JAVAC="$JAVA_HOME/bin/javac"
    JAVA="$JAVA_HOME/bin/java"
else
    JAVAC="javac"
    JAVA="java"
fi

echo -e "${CYAN}Compiling application classes...${NC}"
cd "$JYLOS_DIR"
mvn compile -q

echo -e "${GRAY}Building plugin JARs (some tests load the packaged artifact, not sources)...${NC}"
"$SCRIPT_DIR/build-plugins.sh" > /dev/null

echo -e "${GRAY}Resolving classpath...${NC}"
CP_FILE="$JYLOS_DIR/target/plugin-test-classpath.txt"
mvn dependency:build-classpath -Dmdep.outputFile="$CP_FILE" -q > /dev/null
CLASSPATH="$JYLOS_DIR/target/classes:$(cat "$CP_FILE")"

rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR"

echo -e "${CYAN}Compiling plugin sources and tests...${NC}"
# A bundle with its own third-party dependencies (declared in its own pom.xml, see
# PLUGINS.md#third-party-dependencies) is excluded from this flat, shared-classpath
# compile: its classes belong on their own isolated classloader (the one PluginLoader
# gives them at runtime, and the one the JAR-loading test below recreates), and
# compiling them flat alongside everything else would let the JVM resolve them from
# here instead — silently hiding classloader-isolation bugs a real install would hit.
# Its own plugin.properties/*.java stay out; its test (which loads the built JAR
# instead) is unaffected by this exclusion.
JAR_ONLY_BUNDLES=$(find "$PLUGINS_SOURCE" -name pom.xml | sed 's|/pom.xml$||')
SOURCES=$(find "$PLUGINS_SOURCE" "$PLUGINS_TEST" -name "*.java" -not -name "package-info.java")
for bundle_dir in $JAR_ONLY_BUNDLES; do
    SOURCES=$(echo "$SOURCES" | grep -v "^$bundle_dir/" || true)
done
"$JAVAC" --release 21 -encoding UTF-8 -cp "$CLASSPATH" -d "$BUILD_DIR" $SOURCES

FAILURES=0
TEST_CLASSES=$(cd "$PLUGINS_TEST" && find . -name "*Test.java" | sed 's|^\./||; s|/|.|g; s|\.java$||')

for test_class in $TEST_CLASSES; do
    echo ""
    echo -e "${CYAN}Running $test_class${NC}"
    if "$JAVA" -cp "$BUILD_DIR:$CLASSPATH" "$test_class"; then
        echo -e "${GREEN}  $test_class passed${NC}"
    else
        echo -e "${RED}  $test_class FAILED${NC}"
        FAILURES=$((FAILURES + 1))
    fi
done

echo ""
if [ "$FAILURES" -gt 0 ]; then
    echo -e "${RED}$FAILURES plugin test class(es) failed${NC}"
    exit 1
fi
echo -e "${GREEN}All plugin tests passed${NC}"
