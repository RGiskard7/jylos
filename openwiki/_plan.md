# OpenWiki update plan

## Intended page updates
- /openwiki/quickstart.md
  - Source evidence: /.github/workflows/openwiki-update.yml
  - Change: add the daily cron / workflow_dispatch note and mention LangSmith tracing/OpenRouter config in the workflow anchor.
  - Why: current quickstart already references the workflow, but the workflow file has changed and the doc should match the new trigger/config details.

## Remaining questions
- No other source files changed in the repo diff.
- No connector evidence was provided, so no raw connector inspection is needed.
