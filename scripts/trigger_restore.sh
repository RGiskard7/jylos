#!/bin/bash
# Seeds a trashed test folder in a filesystem vault so its restore can be tested
# manually. Defaults to the repo-local "data" vault (same convention as
# test_restore.sh/test_restore_folder.sh); pass a vault path as $1 to target a
# different one.
VAULT_DIR="${1:-data}"
rm -rf "$VAULT_DIR/TestFolder" 2>/dev/null
mkdir -p "$VAULT_DIR/.trash/TestFolder"
touch "$VAULT_DIR/.trash/TestFolder/MyDoc.md"
