<#
.SYNOPSIS
  Packages Jylos for Windows with jpackage.

.DESCRIPTION
  Single core script for the three Windows distribution formats:

    portable  app-image folder (no installer, run Jylos.exe directly)  [default]
    exe       .exe installer (requires WiX Toolset)
    msi       .msi installer (requires WiX Toolset)

  jpackage uses the WiX Toolset to build BOTH exe and msi installers on Windows.
  Install WiX 3.x (candle.exe/light.exe on PATH) for JDK 17-21, or WiX 4+ (wix.exe)
  for JDK 22+. The portable app-image needs no extra tooling.

  Thin wrappers exist for discoverability: package-windows-exe.ps1 and
  package-windows-msi.ps1 simply call this script with the matching -Type.

.PARAMETER Type
  portable (default) | exe | msi

.EXAMPLE
  .\scripts\package-windows.ps1                # portable app-image
  .\scripts\package-windows.ps1 -Type exe     # .exe installer
  .\scripts\package-windows.ps1 -Type msi     # .msi installer
#>
param(
    [ValidateSet('portable', 'exe', 'msi')]
    [string]$Type = 'portable'
)

Set-StrictMode -Version Latest

# Stable MSI upgrade GUID: MUST NEVER CHANGE between releases, or Windows will treat
# new versions as a different product and install them side by side instead of upgrading.
$UPGRADE_UUID = '6f1d2f7e-9b3a-4c5d-8e2f-3a7b1c9d4e60'

$root = Split-Path -Parent $PSScriptRoot

function Get-JdkMajorVersion {
    param([string]$JdkHome)
    $javaExe = Join-Path $JdkHome 'bin\java.exe'
    if (-not (Test-Path $javaExe)) { return 0 }
    $output = & $javaExe -version 2>&1 | Out-String
    if ($output -match 'version "(\d+)') {
        return [int]$matches[1]
    }
    return 0
}

function Find-PackagingJdkHome {
    $homes = [System.Collections.Generic.List[string]]::new()
    if ($env:JAVA_HOME) { $homes.Add($env:JAVA_HOME) }

    $javaCmd = Get-Command java -ErrorAction SilentlyContinue
    if ($javaCmd) {
        $homes.Add((Split-Path (Split-Path $javaCmd.Path -Parent) -Parent))
    }

    foreach ($pattern in @(
            'C:\Program Files\Java\jdk-*',
            'C:\Program Files\Eclipse Adoptium\jdk-*',
            'C:\Program Files\Microsoft\jdk-*',
            "$env:LOCALAPPDATA\Programs\Eclipse Adoptium\jdk-*")) {
        $jdkDirs = Get-ChildItem -Path $pattern -Directory -ErrorAction SilentlyContinue
        foreach ($jdkDir in $jdkDirs) {
            $homes.Add($jdkDir.FullName)
        }
    }

    $bestHome = $null
    $bestVersion = 0
    foreach ($jdkHome in ($homes | Select-Object -Unique)) {
        $major = Get-JdkMajorVersion $jdkHome
        if ($major -ge 21 -and $major -gt $bestVersion) {
            $bestHome = $jdkHome
            $bestVersion = $major
        }
    }
    return $bestHome
}

function Ensure-BundledWixOnPath {
    param([string]$RepoRoot)
    $wixDir = Join-Path $RepoRoot '.tools\wix314'
    $candle = Join-Path $wixDir 'candle.exe'
    $light = Join-Path $wixDir 'light.exe'
    if ((Test-Path $candle) -and (Test-Path $light)) {
        $env:Path = "$wixDir;$env:Path"
        return $wixDir
    }
    return $null
}

$packagingJdk = Find-PackagingJdkHome
if (-not $packagingJdk) {
    Write-Host 'Error: JDK 21+ not found (Maven and jpackage require it).' -ForegroundColor Red
    Write-Host '  Install: winget install EclipseAdoptium.Temurin.21.JDK' -ForegroundColor Yellow
    Write-Host '  Or download: https://adoptium.net/' -ForegroundColor Yellow
    exit 1
}
$env:JAVA_HOME = $packagingJdk
$env:Path = "$(Join-Path $packagingJdk 'bin');$env:Path"

$bundledWixDir = Ensure-BundledWixOnPath -RepoRoot $root

Push-Location (Join-Path $root 'jylos')

function Read-AppProperty {
    param([string]$key, [string]$defaultValue)
    $propsFile = Join-Path (Get-Location) 'src\main\resources\app.properties'
    if (Test-Path $propsFile) {
        $content = Get-Content $propsFile -Raw
        if ($content -match "(?m)^\s*$key\s*=\s*(.+)$") {
            return $matches[1].Trim()
        }
    }
    return $defaultValue
}

function Read-PomVersion {
    $pomFile = Join-Path (Get-Location) 'pom.xml'
    $content = Get-Content $pomFile -Raw
    if ($content -match '<version>([^<]+)</version>') {
        return $matches[1]
    }
    return '2.4.0'
}

$APP_NAME = Read-AppProperty 'app.name' 'Jylos'
$APP_VERSION = Read-AppProperty 'app.version' '2.4.0'
if ($env:JYLOS_RELEASE_VERSION) {
    $APP_VERSION = $env:JYLOS_RELEASE_VERSION
}
if ($APP_VERSION -like '*${*') {
    $APP_VERSION = Read-PomVersion
}
$APP_VENDOR = Read-AppProperty 'app.vendor' 'Jylos'
$APP_DESCRIPTION = Read-AppProperty 'app.description' 'A free and open-source note-taking application'
$APP_COPYRIGHT = Read-AppProperty 'app.copyright' 'Copyright 2025 Jylos'
$APP_ICON = Read-AppProperty 'app.icon.windows' 'src/main/resources/icons/app-icon.ico'

Write-Host '========================================' -ForegroundColor Cyan
Write-Host "  $APP_NAME - Windows packager ($Type)" -ForegroundColor Cyan
Write-Host '========================================' -ForegroundColor Cyan
Write-Host ''

# -- jpackage from the selected JDK 21+ home (not whatever java is first on PATH) -
$jpackagePath = Join-Path $packagingJdk 'bin\jpackage.exe'
if (-not (Test-Path $jpackagePath)) {
    Write-Host 'Error: jpackage not found in the selected JDK (install a full JDK, not a JRE).' -ForegroundColor Red
    Write-Host "  JAVA_HOME: $packagingJdk" -ForegroundColor Yellow
    Pop-Location
    exit 1
}
Write-Host "JDK:      $packagingJdk" -ForegroundColor Green
Write-Host "jpackage: $jpackagePath" -ForegroundColor Green

# -- WiX is mandatory for exe/msi (jpackage builds both installer types with it) -
if ($Type -ne 'portable') {
    $wix3 = (Get-Command light.exe -ErrorAction SilentlyContinue) -and
            (Get-Command candle.exe -ErrorAction SilentlyContinue)
    $wix4 = Get-Command wix.exe -ErrorAction SilentlyContinue
    if (-not ($wix3 -or $wix4)) {
        Write-Host "Error: WiX Toolset not found - required to build .$Type installers." -ForegroundColor Red
        Write-Host '  One-time setup (no admin): .\scripts\setup-packaging-windows.ps1' -ForegroundColor Yellow
        Write-Host '  Or install WiX 3.x (candle/light on PATH) or WiX 4+ (wix.exe).' -ForegroundColor Yellow
        Write-Host '  Download:  https://wixtoolset.org' -ForegroundColor Yellow
        Write-Host ''
        Write-Host 'Alternatively run without -Type for a portable app-image (no WiX needed).' -ForegroundColor Cyan
        Pop-Location
        exit 1
    }
    if ($bundledWixDir) {
        Write-Host "WiX Toolset: found (bundled at $bundledWixDir)" -ForegroundColor Green
    } else {
        Write-Host 'WiX Toolset: found' -ForegroundColor Green
    }
}
Write-Host ''

# -- Build uber-JAR --------------------------------------------------------------
Write-Host 'Building JAR...' -ForegroundColor Cyan
& mvn clean package -DskipTests "-Drelease.version=$APP_VERSION"
if ($LASTEXITCODE -ne 0) {
    Write-Host 'Error: Maven build failed' -ForegroundColor Red
    Pop-Location
    exit 1
}

$jarFile = Get-ChildItem -Path (Join-Path (Get-Location) 'target') -Filter 'jylos-*-uber.jar' |
    Select-Object -First 1
if (-not $jarFile) {
    Write-Host 'Error: uber-JAR not found in target/' -ForegroundColor Red
    Pop-Location
    exit 1
}

# -- Build plugins (best effort) ------------------------------------------------
$buildPluginsScript = Join-Path $root 'scripts\build-plugins.ps1'
if (Test-Path $buildPluginsScript) {
    Write-Host ''
    Write-Host 'Building plugins...' -ForegroundColor Cyan
    & $buildPluginsScript
    if ($LASTEXITCODE -ne 0) {
        Write-Host 'Warning: plugin build failed; packaging without plugins.' -ForegroundColor Yellow
    }
}

# -- Output dir (short path to dodge the Windows path-length limit) --------------
$currentPath = (Get-Location).Path
if ($currentPath.Length -gt 100) {
    $outputDir = Join-Path $env:TEMP 'Jylos-installer'
    Write-Host 'Using a short temp output path (Windows path-length limit).' -ForegroundColor Yellow
} else {
    $outputDir = Join-Path (Get-Location) 'target\installers'
}
New-Item -ItemType Directory -Force -Path $outputDir | Out-Null

# -- jpackage arguments ----------------------------------------------------------
$jpackageType = if ($Type -eq 'portable') { 'app-image' } else { $Type }

# Isolated input dir with only the JAR (prevents recursive content copying).
$tempInputDir = Join-Path $env:TEMP "Jylos-jpackage-input-$(Get-Random)"
New-Item -ItemType Directory -Force -Path $tempInputDir | Out-Null
Copy-Item -Path $jarFile.FullName -Destination $tempInputDir -Force

try {
    # Launcher (not Main) as main class: jpackage cannot launch a class that extends
    # javafx.application.Application directly.
    $jpackageArgs = @(
        '--input', $tempInputDir,
        '--name', $APP_NAME,
        '--main-jar', $jarFile.Name,
        '--main-class', 'com.example.jylos.Launcher',
        '--type', $jpackageType,
        '--dest', $outputDir,
        '--app-version', $APP_VERSION,
        '--vendor', $APP_VENDOR,
        '--description', $APP_DESCRIPTION,
        '--copyright', $APP_COPYRIGHT,
        '--java-options', '-Dfile.encoding=UTF-8'
    )

    $iconPath = Join-Path (Get-Location) $APP_ICON
    if (Test-Path $iconPath) {
        $jpackageArgs += @('--icon', $iconPath)
    } else {
        Write-Host "Icon not found at $iconPath; packaging without icon." -ForegroundColor Yellow
    }

    # Bundle plugin JARs inside the image (JDK 18+); portable falls back to a copy below.
    $sourcePluginsDir = Join-Path (Get-Location) 'plugins'
    $pluginsViaAppContent = $false
    $helpOut = & $jpackagePath --help 2>&1
    if (($helpOut -match 'app-content') -and (Test-Path $sourcePluginsDir)) {
        $pluginJars = Get-ChildItem -Path $sourcePluginsDir -Filter '*.jar' -ErrorAction SilentlyContinue
        if ($pluginJars.Count -gt 0) {
            $jpackageArgs += @('--app-content', $sourcePluginsDir)
            $pluginsViaAppContent = $true
        }
    }

    if ($Type -ne 'portable') {
        # Installer UX: dir chooser, Start-menu group, desktop shortcut prompt,
        # per-machine upgrade path via a stable upgrade UUID, MIT license page.
        $jpackageArgs += @(
            '--win-dir-chooser',
            '--win-menu',
            '--win-menu-group', $APP_NAME,
            '--win-shortcut',
            '--win-shortcut-prompt',
            '--win-upgrade-uuid', $UPGRADE_UUID
        )
        $licenseFile = Join-Path $root 'LICENSE'
        if (Test-Path $licenseFile) {
            $jpackageArgs += @('--license-file', $licenseFile)
        }
    }

    Write-Host "Running jpackage --type $jpackageType (this can take a few minutes)..." -ForegroundColor Cyan
    & $jpackagePath $jpackageArgs
    $jpackageExit = $LASTEXITCODE

    if ($jpackageExit -ne 0) {
        Write-Host ''
        Write-Host "Error: jpackage failed (exit $jpackageExit). See output above." -ForegroundColor Red
        exit 1
    }

    Write-Host ''
    Write-Host '========================================' -ForegroundColor Green
    Write-Host '  Package created successfully' -ForegroundColor Green
    Write-Host '========================================' -ForegroundColor Green

    if ($Type -eq 'portable') {
        $appImagePath = Join-Path $outputDir $APP_NAME
        # JDK 17 fallback: copy plugin JARs into the image when --app-content was unavailable.
        if (-not $pluginsViaAppContent -and (Test-Path $sourcePluginsDir)) {
            $pluginJars = Get-ChildItem -Path $sourcePluginsDir -Filter '*.jar' -ErrorAction SilentlyContinue
            if ($pluginJars.Count -gt 0) {
                $destPluginsDir = Join-Path $appImagePath 'plugins'
                New-Item -ItemType Directory -Force -Path $destPluginsDir | Out-Null
                Copy-Item -Path $pluginJars.FullName -Destination $destPluginsDir -Force
            }
        }
        Write-Host "Portable app-image: $appImagePath" -ForegroundColor Cyan
        Write-Host "Run $APP_NAME.exe inside that folder, or zip it for distribution." -ForegroundColor Green
    } else {
        $artifact = Get-ChildItem -Path $outputDir -Filter "*.$Type" |
            Sort-Object LastWriteTime -Descending | Select-Object -First 1
        if ($artifact) {
            Write-Host "Installer: $($artifact.FullName)" -ForegroundColor Cyan
        } else {
            Write-Host "Installer written to: $outputDir" -ForegroundColor Cyan
        }
        Write-Host 'Note: the installer is unsigned; SmartScreen may warn on first run.' -ForegroundColor Yellow
        Write-Host 'For releases, sign it with signtool and a code-signing certificate.' -ForegroundColor Yellow
    }
} finally {
    if (Test-Path $tempInputDir) {
        Remove-Item -Path $tempInputDir -Recurse -Force -ErrorAction SilentlyContinue
    }
}

Pop-Location
