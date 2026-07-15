# CI/CD

Practical guide to Jylos CI/CD. It explains what GitHub Actions runs, how to cut
a release, and which parts are automated.

Español: [es/CICD.md](es/CICD.md).

## Quick Summary

- Regular development happens on `develop`.
- Stable public code lands on `main`.
- Tests run on pull requests into `main` and on pushes to `main`.
- Releases are published by pushing a semantic tag: `vX.Y.Z`.
- The application version is derived from the tag during packaging.
- Windows and Linux packages are built automatically.
- macOS packaging is kept in the workflow, but temporarily disabled because ARM runners can stay queued for hours.

## Workflows

### `latest-jylos.yml`

Continuous verification workflow.

Runs on:

- `pull_request` into `main`;
- direct `push` to `main`.

It does:

- repository checkout;
- Java 21 Temurin setup;
- Maven cache setup;
- `xvfb` installation on Ubuntu for JavaFX/headless tests;
- compile and test:

```bash
xvfb-run -a mvn -B -f jylos/pom.xml clean test
```

It does not build installers. Its purpose is to verify that the branch still
compiles and the test suite passes.

### `release-jylos.yml`

Release publishing workflow.

Runs only on tags matching:

```text
v*.*.*
```

Valid examples:

```text
v2.4.1
v2.5.0
v3.0.0
```

It does:

- validates that the tag is semantic;
- derives `JYLOS_RELEASE_VERSION` by removing the leading `v`;
- sets up Java 21 Temurin;
- temporarily sets the Maven project version with `versions:set`;
- runs tests;
- packages Windows and Linux builds;
- uploads intermediate package artifacts;
- extracts release notes from `changelog.md`;
- generates `SHA256SUMS.txt`;
- publishes the GitHub Release with `softprops/action-gh-release`.

Currently published assets:

- `jylos-windows-x64.exe`
- `jylos-windows-x64.msi`
- `jylos-windows-portable.zip`
- `jylos-linux-amd64.deb`
- `jylos-linux-amd64.rpm`
- `SHA256SUMS.txt`

### `openwiki-update.yml`

OpenWiki documentation workflow.

Runs on:

- `workflow_dispatch`, manually from GitHub;
- `push` to `main`, except ignored paths.

Ignored paths:

- `openwiki/**`;
- `AGENTS.md`;
- `CLAUDE.md`.

Reason: avoid loops where the workflow creates OpenWiki changes and then
triggers itself again only because of those generated changes.

It does:

- installs Node.js 22;
- installs `openwiki`;
- runs `openwiki code --update --print`;
- opens an automated pull request with changes in `openwiki`, `AGENTS.md`, and `CLAUDE.md`.

Required secret:

```text
OPENAI_API_KEY
```

## Normal Development Flow

Work on `develop`:

```bash
git checkout develop
git pull origin develop
# work, commit
git push origin develop
```

Then:

1. Open a GitHub pull request from `develop` into `main`.
2. Wait for green checks.
3. Merge the pull request on GitHub.
4. Update local `main`.

```bash
git checkout main
git pull origin main
```

## Normal Release Flow

Before tagging, make sure `changelog.md` contains a section for the version:

```markdown
## [2.4.1] - 2026-07-16
```

Create and push the tag:

```bash
git checkout main
git pull origin main
git tag v2.4.1
git push origin v2.4.1
```

Replace `v2.4.1` with the real version.

No extra commit is required only to bump `pom.xml`. The release workflow uses the
tag as the source of truth during packaging.

## Application Version

The app reads its version from:

```text
jylos/src/main/resources/version.properties
```

That resource uses Maven resource filtering. During release, GitHub Actions passes:

```bash
-Drelease.version=${JYLOS_RELEASE_VERSION}
```

Result:

- tag `v2.4.1`;
- `JYLOS_RELEASE_VERSION=2.4.1`;
- packaged `version.properties` contains `2.4.1`;
- `About Jylos` shows `v2.4.1`;
- the update checker compares against GitHub Releases.

For local builds without that parameter, Maven uses the `pom.xml` fallback.

## Release Notes

`release-jylos.yml` extracts release notes from `changelog.md`.

There must be an exact section:

```markdown
## [2.4.1] - 2026-07-16
```

The workflow takes all content until the next `## [...]` heading.

If the section is missing or empty, the release fails on purpose. This prevents
publishing a release without notes.

## Landing Page and Downloads

`docs/index.html` uses stable GitHub URLs:

```text
https://github.com/RGiskard7/jylos/releases/latest/download/<asset>
```

This means `index.html` does not need to be edited for every release as long as
asset names stay stable.

Important: while macOS packaging is disabled, the workflow does not publish:

```text
jylos-macos-arm64.dmg
```

If the landing page shows a macOS download, that link only works when the latest
release actually contains that asset.

## macOS

The macOS block remains in `release-jylos.yml`, but the matrix entry is commented:

```yaml
matrix:
  os:
    - windows-latest
    - ubuntu-latest
    # - macos-14-arm64
```

Reason: `macos-14-arm64` can wait hours for a runner.

To re-enable it:

```yaml
matrix:
  os:
    - windows-latest
    - ubuntu-latest
    - macos-14-arm64
```

The `Package macOS DMG` step is already present and only runs when
`runner.os == 'macOS'`.

## Retrying a Failed Release

If the tag already exists and the release failed before publishing anything useful:

```bash
git push origin --delete v2.4.1
git tag -d v2.4.1
git checkout main
git pull origin main
git tag v2.4.1
git push origin v2.4.1
```

Do not reuse public tags unless there is no better option. If users may have
consumed the release, create a new version instead, for example `v2.4.2`.

## Signing

Current state:

- Windows packages are unsigned;
- macOS packaging is disabled in CI and has no automated signing/notarization;
- Linux builds `.deb` and `.rpm` files, but not a signed package repository.

Consequences:

- Windows may show SmartScreen warnings.
- macOS may block or warn about unsigned DMGs.
- Linux packages are installed as manually downloaded files.

When real certificates exist, signing steps must run before publishing assets to
the GitHub Release.

## Common Failures

### Release Fails Because Notes Are Missing

Check `changelog.md`:

```markdown
## [X.Y.Z] - YYYY-MM-DD
```

It must match tag `vX.Y.Z`.

### Linux Fails Because of Script Permissions

The workflow calls `.sh` scripts with `bash ./scripts/...`, so it does not depend
on executable file bits.

### JavaFX Tests Hang in GitHub Actions

Ubuntu uses `xvfb-run`. Keep that path if new UI tests are added.

### macOS Never Starts

This is not a Jylos failure if the job stays at:

```text
Waiting for a runner to pick up this job...
```

It means no runner is available. That is why macOS is disabled.

## Pre-Release Checklist

- `main` contains the final merge.
- `latest-jylos.yml` is green on the pull request or `main`.
- `changelog.md` has a section for the version.
- The chosen version follows semver.
- If asset names changed, update `docs/index.html`.
- If macOS remains disabled, do not promise a new DMG in release notes.

