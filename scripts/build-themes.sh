#!/bin/bash
# Build/install script for Jylos external themes (macOS/Linux)
# Validates theme descriptors and copies themes to runtime directories.

set -euo pipefail

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
GRAY='\033[0;90m'
NC='\033[0m'

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
SOURCE_THEMES="$PROJECT_ROOT/themes"
APP_THEMES="$PROJECT_ROOT/jylos/themes"

COPY_TO_APPDATA=false
CLEAN=false

for arg in "$@"; do
  case "$arg" in
    --clean|-c) CLEAN=true ;;
    --appdata) COPY_TO_APPDATA=true ;;
  esac
done

echo -e "${CYAN}Building/Installing Jylos themes...${NC}"
echo -e "${GRAY}Source: $SOURCE_THEMES${NC}"
echo -e "${GRAY}Project runtime target: $APP_THEMES${NC}"

if [ ! -d "$SOURCE_THEMES" ]; then
  echo -e "${RED}ERROR: themes source directory not found at $SOURCE_THEMES${NC}"
  echo -e "${YELLOW}Create themes under ./themes/<theme-id> with theme.properties + theme.css${NC}"
  exit 1
fi

mkdir -p "$APP_THEMES"

if [ "$CLEAN" = true ]; then
  echo -e "${YELLOW}Cleaning project runtime themes...${NC}"
  rm -rf "$APP_THEMES"/*
fi

copied=0
skipped=0

for theme_dir in "$SOURCE_THEMES"/*; do
  [ -d "$theme_dir" ] || continue
  theme_id="$(basename "$theme_dir")"
  props="$theme_dir/theme.properties"
  css="$theme_dir/theme.css"

  if [ ! -f "$props" ]; then
    echo -e "${YELLOW}Skipping $theme_id (missing theme.properties)${NC}"
    skipped=$((skipped + 1))
    continue
  fi
  if [ ! -f "$css" ]; then
    echo -e "${YELLOW}Skipping $theme_id (missing theme.css)${NC}"
    skipped=$((skipped + 1))
    continue
  fi

  dest="$APP_THEMES/$theme_id"
  rm -rf "$dest"
  mkdir -p "$dest"
  cp "$props" "$dest/theme.properties"
  cp "$css" "$dest/theme.css"
  echo -e "${GREEN}[OK] Installed theme: $theme_id -> $dest${NC}"
  copied=$((copied + 1))
done

if [ "$COPY_TO_APPDATA" = true ]; then
  os_name="$(uname -s)"
  if [ "$os_name" = "Darwin" ]; then
    appdata_base="$HOME/Library/Application Support/Jylos"
  elif [ "$os_name" = "Linux" ]; then
    if [ -n "${XDG_CONFIG_HOME:-}" ]; then
      appdata_base="$XDG_CONFIG_HOME/Jylos"
    else
      appdata_base="$HOME/.config/Jylos"
    fi
  else
    appdata_base=""
  fi

  if [ -n "$appdata_base" ]; then
    appdata_themes="$appdata_base/themes"
    mkdir -p "$appdata_themes"
    rm -rf "$appdata_themes"/*
    cp -R "$APP_THEMES"/. "$appdata_themes"/
    echo -e "${GREEN}[OK] Installed themes to app data: $appdata_themes${NC}"
  fi
fi

echo -e "${CYAN}----------------------------------------${NC}"
echo -e "${CYAN}Themes installed: $copied${NC}"
echo -e "${CYAN}Themes skipped:   $skipped${NC}"
echo -e "${GREEN}Done.${NC}"
