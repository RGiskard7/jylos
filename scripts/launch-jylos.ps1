<#
Jylos - Simplified launcher for Windows (PowerShell)
This script automatically detects Java and JavaFX and launches the application
#>

Write-Host ""
Write-Host "========================================"
Write-Host "  Jylos - Launcher"
Write-Host "========================================"
Write-Host ""

# Get script directory and navigate to Jylos directory
$SCRIPT_DIR = Split-Path -Parent $MyInvocation.MyCommand.Path
$JYLOS_DIR = Join-Path $SCRIPT_DIR "..\jylos" | Resolve-Path
$JAR = Join-Path $JYLOS_DIR "target\jylos-2.0.0-uber.jar"

# Check if JAR exists
if (-not (Test-Path $JAR)) {
    Write-Host "Error: JAR not found at $JAR" -ForegroundColor Red
    Write-Host ""
    Write-Host "Please build the project first:"
    Write-Host "  .\scripts\build_all.ps1"
    Write-Host ""
    Write-Host "Or manually:"
    Write-Host "  cd jylos"
    Write-Host "  mvn clean package -DskipTests"
    Write-Host ""
    Read-Host "Press Enter to exit"
    exit 1
}

# Check if Java is installed
try {
    $javaVersion = java -version 2>&1 | Select-String "version"
    Write-Host "Java found: $javaVersion" -ForegroundColor Green
} catch {
    Write-Host "Error: Java not found in PATH" -ForegroundColor Red
    Write-Host ""
    Write-Host "Please install Java 17 or higher from:"
    Write-Host "  https://adoptium.net/"
    Write-Host ""
    Write-Host "And make sure it's added to your system PATH."
    Write-Host ""
    Read-Host "Press Enter to exit"
    exit 1
}

Write-Host ""

# Find JavaFX in Maven repository
$M2_REPO = Join-Path $env:USERPROFILE ".m2\repository"
$JAVAFX_BASE = Join-Path $M2_REPO "org\openjfx"

if (-not (Test-Path $JAVAFX_BASE)) {
    Write-Host "Warning: JavaFX not found in Maven repository" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "Attempting to launch without module-path (may fail)..."
    Write-Host ""
    Set-Location $JYLOS_DIR
    java -jar $JAR
    exit $LASTEXITCODE
}

# Find JavaFX version (21.x.x)
$JAVAFX_VERSION = $null
$controlsPath = Join-Path $JAVAFX_BASE "javafx-controls"
if (Test-Path $controlsPath) {
    $versionDirs = Get-ChildItem -Path $controlsPath -Directory -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -match '^[0-9]' } |
        Sort-Object { [version]($_.Name) } -Descending
    if ($versionDirs) {
        $JAVAFX_VERSION = $versionDirs[0].Name
    }
}

if (-not $JAVAFX_VERSION) {
    Write-Host "Warning: JavaFX 21 not found in Maven repository" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "Attempting to launch without module-path..."
    Write-Host ""
    Set-Location $JYLOS_DIR
    java -jar $JAR
    exit $LASTEXITCODE
}

# Build module-path - use specific JAR files, not directories
$MODULE_PATH = @()
$MODULES = @()

# javafx.web requires javafx.media, so we need to include it
$moduleNames = @("base", "controls", "fxml", "graphics", "media", "web")
foreach ($moduleName in $moduleNames) {
    $moduleDir = Join-Path $JAVAFX_BASE "javafx-$moduleName\$JAVAFX_VERSION"
    if (Test-Path $moduleDir) {
        # Find the actual JAR file (not -sources.jar or -javadoc.jar)
        $jarFile = Get-ChildItem -Path $moduleDir -Filter "javafx-$moduleName-*.jar" -ErrorAction SilentlyContinue | 
            Where-Object { $_.Name -notmatch '-(sources|javadoc)\.jar$' } | 
            Select-Object -First 1
        
        if ($jarFile) {
            # Use the JAR file path directly (Java module-path accepts individual JAR files)
            # This avoids Java scanning the directory and picking up -sources.jar files
            if ($jarFile.FullName -notin $MODULE_PATH) {
                $MODULE_PATH += $jarFile.FullName
                $MODULES += "javafx.$moduleName"
            }
        }
    }
}

if ($MODULE_PATH.Count -eq 0) {
    Write-Host "Warning: Could not find JavaFX modules" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "Attempting to launch without module-path..."
    Write-Host ""
    Set-Location $JYLOS_DIR
    java -jar $JAR
    exit $LASTEXITCODE
}

Write-Host "JavaFX found (version $JAVAFX_VERSION)" -ForegroundColor Green
Write-Host ""
Write-Host "Launching Jylos..."
Write-Host ""

# Change to Jylos directory so relative paths work
Set-Location $JYLOS_DIR

# Launch with module-path
$modulePathString = $MODULE_PATH -join ";"
$modulesString = $MODULES -join ","

Write-Host "Using JavaFX module-path: $modulePathString" -ForegroundColor Cyan
& java --module-path $modulePathString --add-modules $modulesString -jar $JAR

$EXIT_CODE = $LASTEXITCODE

if ($EXIT_CODE -ne 0) {
    Write-Host ""
    Write-Host "Error launching application (code: $EXIT_CODE)" -ForegroundColor Red
    Write-Host ""
    Read-Host "Press Enter to exit"
}

exit $EXIT_CODE

