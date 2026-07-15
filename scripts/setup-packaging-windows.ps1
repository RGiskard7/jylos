<#
.SYNOPSIS
  One-time setup for Windows .exe / .msi packaging (JDK 21 + WiX).

.DESCRIPTION
  - Verifies JDK 21+ is installed (offers winget install if missing).
  - Downloads WiX 3.14 binaries into .tools/wix314/ (no admin required).
  package-windows.ps1 picks up both automatically; no manual PATH edits needed.

.EXAMPLE
  .\scripts\setup-packaging-windows.ps1
#>
Set-StrictMode -Version Latest

$root = Split-Path -Parent $PSScriptRoot
$wixDir = Join-Path $root '.tools\wix314'
$wixZipUrl = 'https://github.com/wixtoolset/wix3/releases/download/wix3141rtm/wix314-binaries.zip'

function Test-Jdk21Installed {
    if ($env:JAVA_HOME) {
        $javaExe = Join-Path $env:JAVA_HOME 'bin\java.exe'
        if (Test-Path $javaExe) {
            $ver = & $javaExe -version 2>&1 | Out-String
            if ($ver -match 'version "(2[1-9]|[3-9]\d)') {
                return $true
            }
        }
    }

    foreach ($pattern in @(
            'C:\Program Files\Java\jdk-*',
            'C:\Program Files\Eclipse Adoptium\jdk-*',
            'C:\Program Files\Microsoft\jdk-*',
            "$env:LOCALAPPDATA\Programs\Eclipse Adoptium\jdk-*")) {
        foreach ($jdkDir in (Get-ChildItem -Path $pattern -Directory -ErrorAction SilentlyContinue)) {
            $javaExe = Join-Path $jdkDir.FullName 'bin\java.exe'
            if (-not (Test-Path $javaExe)) { continue }
            $ver = & $javaExe -version 2>&1 | Out-String
            if ($ver -match 'version "(2[1-9]|[3-9]\d)') {
                return $true
            }
        }
    }
    return $false
}

Write-Host '========================================' -ForegroundColor Cyan
Write-Host '  Jylos - Windows packaging setup' -ForegroundColor Cyan
Write-Host '========================================' -ForegroundColor Cyan
Write-Host ''

# -- JDK 21+ ---------------------------------------------------------------------
if (Test-Jdk21Installed) {
    Write-Host 'JDK 21+: already installed' -ForegroundColor Green
} else {
    Write-Host 'JDK 21+ not found.' -ForegroundColor Yellow
    $winget = Get-Command winget -ErrorAction SilentlyContinue
    if ($winget) {
        Write-Host 'Installing Eclipse Temurin 21 via winget...' -ForegroundColor Cyan
        & winget install --id EclipseAdoptium.Temurin.21.JDK `
            --accept-package-agreements --accept-source-agreements
        if ($LASTEXITCODE -ne 0) {
            Write-Host 'Error: JDK install failed. Install manually from https://adoptium.net/' -ForegroundColor Red
            exit 1
        }
        Write-Host 'JDK 21 installed. Open a new terminal if java -version still shows an older release.' -ForegroundColor Green
    } else {
        Write-Host 'Error: install JDK 21+ manually: https://adoptium.net/' -ForegroundColor Red
        exit 1
    }
}

# -- WiX 3.14 binaries (portable, no admin) --------------------------------------
$candle = Join-Path $wixDir 'candle.exe'
$light = Join-Path $wixDir 'light.exe'
if ((Test-Path $candle) -and (Test-Path $light)) {
    Write-Host "WiX 3.14: already present at $wixDir" -ForegroundColor Green
} else {
    Write-Host 'Downloading WiX 3.14 binaries...' -ForegroundColor Cyan
    New-Item -ItemType Directory -Force -Path $wixDir | Out-Null
    $zipPath = Join-Path $env:TEMP 'wix314-binaries.zip'
    Invoke-WebRequest -Uri $wixZipUrl -OutFile $zipPath -UseBasicParsing
    Expand-Archive -Path $zipPath -DestinationPath $wixDir -Force
    Remove-Item -Path $zipPath -Force -ErrorAction SilentlyContinue
    if (-not ((Test-Path $candle) -and (Test-Path $light))) {
        Write-Host 'Error: WiX download/extract failed.' -ForegroundColor Red
        exit 1
    }
    Write-Host "WiX 3.14: installed to $wixDir" -ForegroundColor Green
}

Write-Host ''
Write-Host 'Setup complete. You can now run:' -ForegroundColor Green
Write-Host '  .\scripts\package-windows-exe.ps1' -ForegroundColor Cyan
Write-Host '  .\scripts\package-windows-msi.ps1' -ForegroundColor Cyan
Write-Host '  .\scripts\package-windows.ps1          # portable app-image' -ForegroundColor Cyan
