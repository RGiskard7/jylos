<#
Build script for Jylos (Windows PowerShell)
Usage: .\scripts\build_all.ps1
#>
param()

Set-StrictMode -Version Latest

$root = Split-Path -Parent $PSScriptRoot
Write-Host "Root: $root"

function Confirm-YesNo($message) {
    $ans = Read-Host "$message (y/n)"
    return $ans -match '^[Yy]'
}

$mvn = 'mvn'
if (-not (Get-Command $mvn -ErrorAction SilentlyContinue)) {
    Write-Host "Maven (mvn) not found on PATH. I can try to install Maven and Java for you (requires admin rights)."
    if (-not (Confirm-YesNo "Attempt auto-install Maven and Java now?")) {
        Write-Error "Maven missing. Aborting. Please install Maven and Java manually and re-run the script."
        exit 1
    }

    # Try Chocolatey
    if (Get-Command choco -ErrorAction SilentlyContinue) {
        Write-Host "Installing with Chocolatey..."
        choco install -y temurin17 maven
    }
    elseif (Get-Command scoop -ErrorAction SilentlyContinue) {
        Write-Host "Installing with Scoop..."
        scoop install openjdk17 maven
    }
    elseif (Get-Command winget -ErrorAction SilentlyContinue) {
        Write-Host "Installing with winget... (may prompt for confirmation)"
        winget install --id EclipseAdoptium.Temurin.17 -e --source winget
        winget install --id Apache.Maven -e --source winget
    }
    else {
        Write-Warning "No known Windows package manager found (choco/scoop/winget). Please install Maven and Java manually and re-run the script."
        exit 1
    }

    # Re-evaluate availability
    if (-not (Get-Command $mvn -ErrorAction SilentlyContinue)) {
        Write-Warning "Maven still not found after attempted install. You may need to open a new shell or add Maven to PATH. Exiting."
        exit 1
    }
}

Push-Location $root
& mvn -f "Jylos\pom.xml" clean package -DskipTests
Pop-Location

Write-Host "Build complete. Jar in Jylos\target\jylos-1.0.0-uber.jar (if assembly used)."
