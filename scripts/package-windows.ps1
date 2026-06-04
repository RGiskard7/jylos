<#
Package script for Jylos - Windows (MSI installer)
Usage: .\scripts\package-windows.ps1
#>
param()

Set-StrictMode -Version Latest

$root = Split-Path -Parent $PSScriptRoot
Push-Location (Join-Path $root 'jylos')

# Function to read property from app.properties
function Read-AppProperty {
    param([string]$key, [string]$defaultValue)
    $propsFile = Join-Path (Get-Location) "src\main\resources\app.properties"
    if (Test-Path $propsFile) {
        $content = Get-Content $propsFile -Raw
        if ($content -match "(?m)^\s*$key\s*=\s*(.+)$") {
            return $matches[1].Trim()
        }
    }
    return $defaultValue
}

# Read application metadata from app.properties
$APP_NAME = Read-AppProperty "app.name" "Jylos"
$APP_VERSION = Read-AppProperty "app.version" "1.0.0"
$APP_VENDOR = Read-AppProperty "app.vendor" "Jylos"
$APP_DESCRIPTION = Read-AppProperty "app.description" "A free and open-source note-taking application"
$APP_COPYRIGHT = Read-AppProperty "app.copyright" "Copyright 2025 Jylos"
$APP_ICON = Read-AppProperty "app.icon.windows" "src/main/resources/icons/app-icon.ico"
$APP_CATEGORY = Read-AppProperty "app.package.category.windows" "Productivity"

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  $APP_NAME - Windows Package Builder" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Check if JDK 17+ is installed
$javaVersion = java -version 2>&1 | Select-String "version"
if (-not $javaVersion) {
    Write-Host "Error: Java not found. Please install JDK 17 or higher." -ForegroundColor Red
    Write-Host "Download from: https://adoptium.net/" -ForegroundColor Yellow
    Pop-Location
    exit 1
}

Write-Host "Java found: $javaVersion" -ForegroundColor Green
Write-Host ""

# Check if jpackage is available (requires JDK, not just JRE)
$jpackagePath = $null

# First, try to find jpackage in PATH
try {
    $null = jpackage --version 2>&1
    if ($LASTEXITCODE -eq 0) {
        $jpackagePath = "jpackage"
    }
} catch {
    # jpackage not in PATH, continue to check other locations
}

# If not in PATH, try to find it in JAVA_HOME/bin
if (-not $jpackagePath) {
    $javaHome = $env:JAVA_HOME
    if ($javaHome) {
        $jpackageInJavaHome = Join-Path $javaHome "bin\jpackage.exe"
        if (Test-Path $jpackageInJavaHome) {
            $jpackagePath = $jpackageInJavaHome
        }
    }
}

# If still not found, try to find JAVA_HOME from java executable
if (-not $jpackagePath) {
    $javaCmd = Get-Command java -ErrorAction SilentlyContinue
    if ($javaCmd) {
        $javaPath = $javaCmd.Path
        # Navigate up from bin to JDK root
        $possibleJavaHome = Split-Path (Split-Path $javaPath -Parent) -Parent
        $jpackageInJavaHome = Join-Path $possibleJavaHome "bin\jpackage.exe"
        if (Test-Path $jpackageInJavaHome) {
            $jpackagePath = $jpackageInJavaHome
        }
    }
}

# If still not found, search common JDK installation locations
if (-not $jpackagePath) {
    $commonJdkPaths = @(
        "C:\Program Files\Java\jdk-*",
        "C:\Program Files\Eclipse Adoptium\jdk-*",
        "C:\Program Files\Microsoft\jdk-*",
        "$env:LOCALAPPDATA\Programs\Eclipse Adoptium\jdk-*",
        "$env:ProgramFiles\Eclipse Adoptium\jdk-*"
    )
    
    foreach ($pattern in $commonJdkPaths) {
        $jdkDirs = Get-ChildItem -Path $pattern -Directory -ErrorAction SilentlyContinue | 
            Sort-Object Name -Descending
        foreach ($jdkDir in $jdkDirs) {
            $jpackageCandidate = Join-Path $jdkDir.FullName "bin\jpackage.exe"
            if (Test-Path $jpackageCandidate) {
                $jpackagePath = $jpackageCandidate
                break
            }
        }
        if ($jpackagePath) { break }
    }
}

if (-not $jpackagePath) {
    Write-Host "Error: jpackage not found. jpackage is included in JDK 17+." -ForegroundColor Red
    Write-Host ""
    Write-Host "jpackage is located in the JDK bin directory, not the JRE." -ForegroundColor Yellow
    Write-Host ""
    Write-Host "You appear to have Java installed, but jpackage was not found." -ForegroundColor Yellow
    Write-Host "This usually means you have JRE installed instead of JDK." -ForegroundColor Yellow
    Write-Host ""
    Write-Host "To fix this:" -ForegroundColor Yellow
    Write-Host "  1. Download JDK 17+ (not JRE) from: https://adoptium.net/" -ForegroundColor Yellow
    Write-Host "  2. Install the JDK" -ForegroundColor Yellow
    Write-Host "  3. Set JAVA_HOME to the JDK directory (e.g., C:\Program Files\Eclipse Adoptium\jdk-17.0.12+8)" -ForegroundColor Yellow
    Write-Host "  4. Add %JAVA_HOME%\bin to your PATH" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "Current Java location: $($javaCmd.Path)" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "After installing JDK, verify jpackage exists at:" -ForegroundColor Yellow
    Write-Host "  <JDK_INSTALL_DIR>\bin\jpackage.exe" -ForegroundColor Cyan
    Pop-Location
    exit 1
}

Write-Host "jpackage found" -ForegroundColor Green
Write-Host ""

# Build the JAR first
Write-Host "Building JAR..." -ForegroundColor Cyan
& mvn clean package -DskipTests
if ($LASTEXITCODE -ne 0) {
    Write-Host "Error: Build failed" -ForegroundColor Red
    Pop-Location
    exit 1
}

# Build plugins (required for packaged app to have plugins available)
$buildPluginsScript = Join-Path $root "scripts\build-plugins.ps1"
if (Test-Path $buildPluginsScript) {
    Write-Host ""
    Write-Host "Building plugins..." -ForegroundColor Cyan
    & $buildPluginsScript
    if ($LASTEXITCODE -ne 0) {
        Write-Host "Warning: Plugin build failed. Packaged app will run without plugins." -ForegroundColor Yellow
    } else {
        $pluginsDir = Join-Path (Get-Location) "plugins"
        if (Test-Path $pluginsDir) {
            $pluginCount = (Get-ChildItem -Path $pluginsDir -Filter "*.jar" -ErrorAction SilentlyContinue).Count
            Write-Host "Plugins built: $pluginCount JAR(s) in plugins/" -ForegroundColor Green
        }
    }
} else {
    Write-Host "Warning: build-plugins.ps1 not found. Packaged app will run without plugins." -ForegroundColor Yellow
}

Write-Host ""
Write-Host "Creating Windows installer..." -ForegroundColor Cyan
Write-Host ""

$jarPath = Join-Path (Get-Location) "target\jylos-1.0.0-uber.jar"

# Use a shorter path to avoid Windows 32000 character path limit
# Try to use a temp directory with shorter path, or fall back to target/installers
$tempDir = $env:TEMP
$shortOutputDir = Join-Path $tempDir "Jylos-installer"

# Check if current path is too long (rough estimate)
$currentPath = (Get-Location).Path
if ($currentPath.Length -gt 100) {
    Write-Host "Using shorter output path to avoid Windows path length limit..." -ForegroundColor Yellow
    $outputDir = $shortOutputDir
} else {
    $outputDir = Join-Path (Get-Location) "target\installers"
}

# Create output directory
New-Item -ItemType Directory -Force -Path $outputDir | Out-Null

# Check if WiX is available for MSI (optional, EXE works without it)
$wixAvailable = $false
$lightPath = Get-Command light.exe -ErrorAction SilentlyContinue
$candlePath = Get-Command candle.exe -ErrorAction SilentlyContinue
if ($lightPath -and $candlePath) {
    $wixAvailable = $true
}

# Determine installer type
# MSI requires WiX Toolset
# EXE may not be supported in all JDK versions, so we use app-image as fallback
$installerType = "app-image"
$installerExtension = "app"
$installerName = $APP_NAME

if ($wixAvailable) {
    Write-Host "WiX Toolset found. Creating MSI installer..." -ForegroundColor Green
    $installerType = "msi"
    $installerExtension = "msi"
    $installerName = "$APP_NAME-$APP_VERSION.msi"
} else {
    Write-Host "WiX Toolset not found. Creating application image (app-image)..." -ForegroundColor Yellow
    Write-Host "This creates a portable application folder that can be distributed directly." -ForegroundColor Cyan
    Write-Host "Note: To create MSI installers, install WiX from https://wixtoolset.org" -ForegroundColor Cyan
}

# Use jpackage to create installer
Write-Host "Running jpackage (this may take several minutes)..." -ForegroundColor Yellow
Write-Host ""

# Create a temporary input directory with only the JAR to avoid recursive copying
$tempInputDir = Join-Path $env:TEMP "Jylos-jpackage-input-$(Get-Random)"
New-Item -ItemType Directory -Force -Path $tempInputDir | Out-Null
Copy-Item -Path $jarPath -Destination $tempInputDir -Force

try {
    # Build base command
    # IMPORTANT: We use the Launcher class instead of Main class
    # This is required because jpackage cannot handle classes that extend JavaFX Application directly
    # The Launcher class does NOT extend Application, so jpackage can properly create the executable
    # The Launcher then calls Main.main() which starts the JavaFX application
    $jpackageArgs = @(
        "--input", $tempInputDir,
        "--name", $APP_NAME,
        "--main-jar", "jylos-1.0.0-uber.jar",
        "--main-class", "com.example.jylos.Launcher",
        "--type", $installerType,
        "--dest", $outputDir,
        "--app-version", $APP_VERSION,
        "--vendor", $APP_VENDOR,
        "--description", $APP_DESCRIPTION,
        "--copyright", $APP_COPYRIGHT,
        "--java-options", "-Dfile.encoding=UTF-8"
    )
    
    # Add icon if it exists
    $iconPath = Join-Path (Get-Location) $APP_ICON
    if (Test-Path $iconPath) {
        $jpackageArgs += @("--icon", $iconPath)
        Write-Host "Using icon: $iconPath" -ForegroundColor Green
    } else {
        Write-Host "Icon not found at $iconPath, skipping icon..." -ForegroundColor Yellow
    }

    # Include plugins: --app-content (JDK 18+) includes them in both app-image and MSI
    # For JDK 17 app-image only, we copy plugins after jpackage (see below)
    $sourcePluginsDir = Join-Path (Get-Location) "plugins"
    $pluginsIncludedViaAppContent = $false
    $jpackageSupportsAppContent = $false
    try {
        $helpOut = & $jpackagePath --help 2>&1
        if ($helpOut -match "app-content") { $jpackageSupportsAppContent = $true }
    } catch { }
    if ($jpackageSupportsAppContent -and (Test-Path $sourcePluginsDir)) {
        $pluginJars = Get-ChildItem -Path $sourcePluginsDir -Filter "*.jar" -ErrorAction SilentlyContinue
        if ($pluginJars.Count -gt 0) {
            $jpackageArgs += @("--app-content", $sourcePluginsDir)
            $pluginsIncludedViaAppContent = $true
            Write-Host "Including plugins via --app-content ($($pluginJars.Count) JAR(s))" -ForegroundColor Green
        }
    }
    
    Write-Host "Using Launcher class for JavaFX compatibility..." -ForegroundColor Green

    # Add Windows-specific options only for MSI installers
    if ($installerType -eq "msi") {
        $jpackageArgs += @(
            "--win-dir-chooser",
            "--win-menu",
            "--win-menu-group", $APP_NAME,
            "--win-shortcut"
        )
    }

    # Execute jpackage with progress indication
    Write-Host "Packaging application (downloading Java runtime if needed)..." -ForegroundColor Cyan
    $startTime = Get-Date

    # Run jpackage and capture output
    $jpackageOutput = & $jpackagePath $jpackageArgs 2>&1

    $endTime = Get-Date
    $duration = $endTime - $startTime

    # Display output
    if ($jpackageOutput) {
        Write-Host $jpackageOutput
    }

    if ($LASTEXITCODE -eq 0) {
        Write-Host ""
        Write-Host "========================================" -ForegroundColor Green
        Write-Host "  Package created successfully!" -ForegroundColor Green
        Write-Host "========================================" -ForegroundColor Green
        Write-Host ""
        
        if ($installerType -eq "app-image") {
            $appImagePath = Join-Path $outputDir $installerName
            Write-Host "Application image location: $appImagePath" -ForegroundColor Cyan

            # Copy plugins to app image if not already included via --app-content (JDK 17 fallback)
            if (-not $pluginsIncludedViaAppContent) {
                $destPluginsDir = Join-Path $appImagePath "plugins"
                if (Test-Path $sourcePluginsDir) {
                    $pluginJars = Get-ChildItem -Path $sourcePluginsDir -Filter "*.jar" -ErrorAction SilentlyContinue
                    if ($pluginJars.Count -gt 0) {
                        New-Item -ItemType Directory -Force -Path $destPluginsDir | Out-Null
                        Copy-Item -Path $pluginJars.FullName -Destination $destPluginsDir -Force
                        Write-Host "Plugins included: $($pluginJars.Count) JAR(s) copied to app image" -ForegroundColor Green
                    }
                }
            }

            Write-Host ""
            if ($outputDir -eq $shortOutputDir) {
                Write-Host "Note: Using temporary directory due to Windows path length limit." -ForegroundColor Yellow
                Write-Host "You may want to move the folder to a shorter path for distribution." -ForegroundColor Yellow
                Write-Host ""
            }
            Write-Host "This is a portable application folder. You can:" -ForegroundColor Green
            Write-Host "  1. Distribute the entire folder (users can run $APP_NAME.exe directly)" -ForegroundColor Yellow
            Write-Host "  2. Zip the folder and distribute it as a portable application" -ForegroundColor Yellow
            Write-Host "  3. Create an installer using a tool like Inno Setup or NSIS" -ForegroundColor Yellow
            Write-Host ""
            Write-Host "To run: Navigate to the folder and double-click $APP_NAME.exe" -ForegroundColor Cyan
        } else {
            $installerPath = Join-Path $outputDir $installerName
            Write-Host "Installer location: $installerPath" -ForegroundColor Cyan
            Write-Host ""
            Write-Host "You can now distribute this $($installerExtension.ToUpper()) installer." -ForegroundColor Green
            Write-Host "Users can install it like any other Windows application." -ForegroundColor Green
        }
    } else {
        Write-Host ""
        Write-Host "Error: Package creation failed" -ForegroundColor Red
        Write-Host ""
        if ($installerType -eq "msi") {
            Write-Host "MSI creation requires WiX Toolset." -ForegroundColor Yellow
            Write-Host "You can either:" -ForegroundColor Yellow
            Write-Host "  1. Install WiX from https://wixtoolset.org and add it to PATH" -ForegroundColor Yellow
            Write-Host "  2. The script will automatically use app-image format instead" -ForegroundColor Yellow
        } else {
            Write-Host "Please check the error messages above for details." -ForegroundColor Yellow
        }
    }
} finally {
    # Clean up temporary input directory
    if (Test-Path $tempInputDir) {
        Remove-Item -Path $tempInputDir -Recurse -Force -ErrorAction SilentlyContinue
    }
}

if ($LASTEXITCODE -ne 0) {
    Pop-Location
    exit 1
}

Pop-Location

