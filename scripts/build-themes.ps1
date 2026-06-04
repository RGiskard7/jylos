# Build/install script for Jylos external themes (Windows PowerShell)
# Validates theme descriptors and copies themes to runtime directories.

param(
    [switch]$Clean,
    [switch]$AppData
)

$ErrorActionPreference = "Stop"

$ProjectRoot = Split-Path -Parent $PSScriptRoot
$SourceThemes = Join-Path $ProjectRoot "themes"
$AppThemes = Join-Path (Join-Path $ProjectRoot "jylos") "themes"

Write-Host "Building/Installing Jylos themes..." -ForegroundColor Cyan
Write-Host "Source: $SourceThemes" -ForegroundColor Gray
Write-Host "Project runtime target: $AppThemes" -ForegroundColor Gray

if (-not (Test-Path $SourceThemes)) {
    Write-Host "ERROR: themes source directory not found: $SourceThemes" -ForegroundColor Red
    Write-Host "Create themes under .\themes\<theme-id> with theme.properties + theme.css" -ForegroundColor Yellow
    exit 1
}

if (-not (Test-Path $AppThemes)) {
    New-Item -ItemType Directory -Path $AppThemes -Force | Out-Null
}

if ($Clean) {
    Write-Host "Cleaning project runtime themes..." -ForegroundColor Yellow
    Get-ChildItem -Path $AppThemes -ErrorAction SilentlyContinue | Remove-Item -Recurse -Force
}

$copied = 0
$skipped = 0

Get-ChildItem -Path $SourceThemes -Directory | ForEach-Object {
    $themeDir = $_.FullName
    $themeId = $_.Name
    $props = Join-Path $themeDir "theme.properties"
    $css = Join-Path $themeDir "theme.css"

    if (-not (Test-Path $props)) {
        Write-Host "Skipping $themeId (missing theme.properties)" -ForegroundColor Yellow
        $skipped++
        return
    }
    if (-not (Test-Path $css)) {
        Write-Host "Skipping $themeId (missing theme.css)" -ForegroundColor Yellow
        $skipped++
        return
    }

    $dest = Join-Path $AppThemes $themeId
    if (Test-Path $dest) {
        Remove-Item -Path $dest -Recurse -Force
    }
    New-Item -ItemType Directory -Path $dest -Force | Out-Null
    Copy-Item -Path $props -Destination (Join-Path $dest "theme.properties") -Force
    Copy-Item -Path $css -Destination (Join-Path $dest "theme.css") -Force

    Write-Host "[OK] Installed theme: $themeId -> $dest" -ForegroundColor Green
    $copied++
}

if ($AppData) {
    $appDataBase = Join-Path $env:APPDATA "Jylos"
    $appDataThemes = Join-Path $appDataBase "themes"
    if (-not (Test-Path $appDataThemes)) {
        New-Item -ItemType Directory -Path $appDataThemes -Force | Out-Null
    }
    Get-ChildItem -Path $appDataThemes -ErrorAction SilentlyContinue | Remove-Item -Recurse -Force
    Copy-Item -Path (Join-Path $AppThemes "*") -Destination $appDataThemes -Recurse -Force
    Write-Host "[OK] Installed themes to app data: $appDataThemes" -ForegroundColor Green
}

Write-Host "----------------------------------------" -ForegroundColor Cyan
Write-Host "Themes installed: $copied" -ForegroundColor Cyan
Write-Host "Themes skipped:   $skipped" -ForegroundColor Cyan
Write-Host "Done." -ForegroundColor Green
