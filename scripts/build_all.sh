#!/usr/bin/env bash
set -euo pipefail

# Build script for Jylos (Linux / macOS)
# Usage: ./scripts/build_all.sh

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
echo "Root: $ROOT_DIR"

# Use mvn from PATH if available, otherwise try common locations
if command -v mvn >/dev/null 2>&1; then
  MVN_CMD="mvn"
else
  echo "Maven not found on PATH. I can attempt to install JDK/Maven using your system package manager (will ask first)."
  read -p "Attempt auto-install (requires sudo/homebrew/sdkman)? (y/N) " yn
  if [ "$yn" = "y" ] || [ "$yn" = "Y" ]; then
    # Detect package manager
    if command -v brew >/dev/null 2>&1; then
      echo "Using Homebrew to install Java and Maven..."
      brew install maven
      brew install temurin
    elif command -v apt-get >/dev/null 2>&1; then
      echo "Using apt-get to install OpenJDK 17 and Maven (requires sudo)..."
      sudo apt-get update
      sudo apt-get install -y openjdk-17-jdk maven
    elif command -v dnf >/dev/null 2>&1; then
      echo "Using dnf to install OpenJDK 17 and Maven (requires sudo)..."
      sudo dnf install -y java-17-openjdk maven
    else
      echo "No supported package manager detected. Trying SDKMAN..."
      if [ -d "$HOME/.sdkman" ]; then
        source "$HOME/.sdkman/bin/sdkman-init.sh"
      else
        echo "Installing SDKMAN (interactive)..."
        curl -s "https://get.sdkman.io" | bash
        source "$HOME/.sdkman/bin/sdkman-init.sh"
      fi
      sdk install java 17.0.21-tem
      sdk install maven
    fi
  else
    echo "Maven missing. Aborting. Install Maven and re-run the script." >&2
    exit 1
  fi
fi

echo "Cleaning and packaging (skip tests)..."
cd "$ROOT_DIR"
mvn -f jylos/pom.xml clean package -DskipTests

JAR_PATH="$ROOT_DIR/jylos/target/jylos-2.0.0-uber.jar"
if [ -f "$JAR_PATH" ]; then
  echo "Built: $JAR_PATH"
else
  echo "Build finished but jar not found at $JAR_PATH" >&2
fi

echo "Done. Use ./scripts/run_all.sh to start the app."
