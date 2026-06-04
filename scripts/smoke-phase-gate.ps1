param()
$ErrorActionPreference = 'Stop'

$Root = Split-Path -Parent $PSScriptRoot
$Pom = Join-Path $Root "jylos/pom.xml"

Write-Host "== Jylos Phase Gate =="
Write-Host "Root: $Root"
Write-Host ""

Write-Host "[1/3] Running unit/integration tests..."
mvn -f $Pom clean test

Write-Host ""
Write-Host "[2/3] Building package..."
mvn -f $Pom "-DskipTests" clean package

Write-Host ""
Write-Host "[3/3] Manual smoke checklist (required)"
Write-Host "- Create/edit/save note"
Write-Host "- Move note to trash and restore"
Write-Host "- Create folder + subfolder"
Write-Host "- Add/remove tags on note"
Write-Host "- Change theme (light/dark/system)"
Write-Host "- Open plugin manager"
Write-Host ""
Write-Host "Mark this phase as DONE only if all items pass."
