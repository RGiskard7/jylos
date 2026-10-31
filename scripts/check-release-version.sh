#!/usr/bin/env bash
# Verifies that the repository is ready to be released as version X.Y.Z:
#   - jylos/pom.xml <revision> is X.Y.Z (so every build, not only the release one,
#     identifies itself with the released version);
#   - changelog.md has a non-empty "## [X.Y.Z] - YYYY-MM-DD" section (release notes).
#
# Run by the release workflow before anything is built, and runnable locally.
# Usage: scripts/check-release-version.sh X.Y.Z
# Prepare both with: scripts/prepare-release.sh X.Y.Z
set -euo pipefail

ROOT="${JYLOS_REPO_ROOT:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
VERSION="${1:-}"
VERSION="${VERSION#v}"

if [[ ! "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    echo "Usage: $0 X.Y.Z (got: '${1:-}')" >&2
    exit 2
fi

status=0

pom_version="$(grep -m1 '<revision>' "$ROOT/jylos/pom.xml" | sed -E 's/.*<revision>([^<]+)<\/revision>.*/\1/')"
if [[ "$pom_version" != "$VERSION" ]]; then
    echo "jylos/pom.xml <revision> is '$pom_version', expected '$VERSION'." >&2
    status=1
fi

if ! grep -Eq "^## \[${VERSION//./\\.}\] - [0-9]{4}-[0-9]{2}-[0-9]{2}$" "$ROOT/changelog.md"; then
    echo "changelog.md has no '## [$VERSION] - YYYY-MM-DD' section." >&2
    status=1
else
    notes="$(awk -v version="$VERSION" '
        $0 ~ "^## \\[" version "\\]" { found=1; next }
        found && $0 ~ "^## \\[" { exit }
        found && NF { print }
    ' "$ROOT/changelog.md")"
    if [[ -z "$notes" ]]; then
        echo "changelog.md section [$VERSION] is empty." >&2
        status=1
    fi
fi

if [[ $status -ne 0 ]]; then
    echo "Run: scripts/prepare-release.sh $VERSION, commit, then tag v$VERSION." >&2
    exit 1
fi
echo "Release $VERSION is consistent: pom.xml and changelog.md agree."
