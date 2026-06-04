#!/bin/bash
VAULT_DIR="/Users/edu/Library/Mobile Documents/iCloud~md~obsidian/Documents/Obsidian Vault"
rm -rf "$VAULT_DIR/TestFolder" 2>/dev/null
mkdir -p "$VAULT_DIR/.trash/TestFolder"
touch "$VAULT_DIR/.trash/TestFolder/MyDoc.md"
