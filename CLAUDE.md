<!-- OPENWIKI:START -->

## OpenWiki

This repository uses OpenWiki for recurring code documentation. Start with `openwiki/quickstart.md`, then follow its links to architecture, workflows, domain concepts, operations, integrations, testing guidance, and source maps.

The scheduled OpenWiki GitHub Actions workflow refreshes the repository wiki. Do not hand-edit generated OpenWiki pages unless explicitly asked; prefer updating source code/docs and letting OpenWiki regenerate.

## Changelog

Keep `## [Unreleased]` undated at the top. When preparing a release, move relevant
bullets into a single `## [x.y.z] - YYYY-MM-DD` section using the release date.
Do not duplicate version sections; use `Fixes #123` in PR descriptions when a PR
should close a GitHub issue.

<!-- OPENWIKI:END -->
