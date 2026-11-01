#!/usr/bin/env bash
# Prepares the repository for releasing version X.Y.Z, in one step:
#   - sets jylos/pom.xml <revision> to X.Y.Z, so every build (IDE, build_all, CI on
#     pull requests) identifies itself with the version being released;
#   - turns the current "## [Unreleased]" bullets into "## [X.Y.Z] - <date>",
#     leaving an empty "## [Unreleased]" on top.
# It does not commit, tag or push: review the diff, commit, then push the tag.
#
# Usage: scripts/prepare-release.sh X.Y.Z [YYYY-MM-DD]   (date defaults to today)
set -euo pipefail

ROOT="${JYLOS_REPO_ROOT:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
VERSION="${1:-}"
VERSION="${VERSION#v}"
DATE="${2:-$(date +%F)}"
POM="$ROOT/jylos/pom.xml"
CHANGELOG="$ROOT/changelog.md"

if [[ ! "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    echo "Usage: $0 X.Y.Z [YYYY-MM-DD] (got: '${1:-}')" >&2
    exit 2
fi
if [[ ! "$DATE" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}$ ]]; then
    echo "Date must look like YYYY-MM-DD (got: '$DATE')" >&2
    exit 2
fi
if grep -Eq "^## \[${VERSION//./\\.}\]" "$CHANGELOG"; then
    echo "changelog.md already has a section for $VERSION; nothing to move." >&2
    exit 1
fi
if ! grep -q '^## \[Unreleased\]$' "$CHANGELOG"; then
    echo "changelog.md has no '## [Unreleased]' heading." >&2
    exit 1
fi
unreleased="$(awk '
    /^## \[Unreleased\]$/ { found=1; next }
    found && /^## \[/ { exit }
    found && NF { print }
' "$CHANGELOG")"
if [[ -z "$unreleased" ]]; then
    echo "The [Unreleased] section is empty: there is nothing to release." >&2
    exit 1
fi

# 1. pom.xml: the single <revision> line.
tmp="$(mktemp)"
awk -v version="$VERSION" '
    !done && /<revision>[^<]*<\/revision>/ { sub(/<revision>[^<]*<\/revision>/, "<revision>" version "</revision>"); done=1 }
    { print }
' "$POM" > "$tmp" && mv "$tmp" "$POM"

# 2. changelog.md: new section right under an emptied [Unreleased].
tmp="$(mktemp)"
awk -v header="## [$VERSION] - $DATE" '
    !done && /^## \[Unreleased\]$/ { print; print ""; print header; done=1; next }
    { print }
' "$CHANGELOG" > "$tmp" && mv "$tmp" "$CHANGELOG"

"$ROOT/scripts/check-release-version.sh" "$VERSION"
echo
echo "Next: review 'git diff', commit, merge to main, then:"
echo "  git tag v$VERSION && git push origin v$VERSION"
