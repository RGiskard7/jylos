@echo off
REM Jylos - Simplified launcher for Windows
REM This script automatically detects Java and JavaFX and launches the application

setlocal enabledelayedexpansion

echo.
echo ========================================
echo   Jylos - Launcher
echo ========================================
echo.

REM Get script directory and navigate to Jylos directory
set "SCRIPT_DIR=%~dp0"
set "JYLOS_DIR=%SCRIPT_DIR%..\jylos"
REM Discover the uber jar by glob so the launcher never couples to a hardcoded version.
set "JAR=%JYLOS_DIR%\target\jylos-uber.jar"
for %%f in ("%JYLOS_DIR%\target\jylos-*-uber.jar") do set "JAR=%%f"

REM Check if JAR exists
if not exist "%JAR%" (
    echo Error: JAR not found at %JAR%
    echo.
    echo Please build the project first:
    echo   .\scripts\build_all.ps1
    echo.
    echo Or manually:
    echo   cd jylos
    echo   mvn clean package -DskipTests
    echo.
    pause
    exit /b 1
)

REM Check if Java is installed
where java >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo Error: Java not found in PATH
    echo.
    echo Please install Java 17 or higher from:
    echo   https://adoptium.net/
    echo.
    echo And make sure it's added to your system PATH.
    echo.
    pause
    exit /b 1
)

echo Java found
echo.

REM Find JavaFX in Maven repository
set "M2_REPO=%USERPROFILE%\.m2\repository"
set "JAVAFX_BASE=%M2_REPO%\org\openjfx"

if not exist "%JAVAFX_BASE%" (
    echo Warning: JavaFX not found in Maven repository
    echo.
    echo Attempting to launch without module-path (may fail)...
    echo.
    cd /d "%JYLOS_DIR%"
    java -jar "%JAR%"
    exit /b !ERRORLEVEL!
)

REM Find JavaFX version (21.x.x) - check javafx-controls as reference
set "JAVAFX_VERSION="
set "CONTROLS_DIR=%JAVAFX_BASE%\javafx-controls"
if exist "%CONTROLS_DIR%" (
    REM /o-n lists newest version first so we pin the highest installed JavaFX version.
    for /f "delims=" %%d in ('dir /b /ad /o-n "%CONTROLS_DIR%\*" 2^>nul') do (
        set "JAVAFX_VERSION=%%d"
        goto :found_version
    )
)
:found_version

if "!JAVAFX_VERSION!"=="" (
    echo Warning: JavaFX 21 not found in Maven repository
    echo.
    echo Attempting to launch without module-path...
    echo.
    cd /d "%JYLOS_DIR%"
    java -jar "%JAR%"
    exit /b !ERRORLEVEL!
)

REM Build module-path - check each module individually
set "MODULE_PATH="
set "MODULES="

REM Check javafx-base
set "MODULE_DIR=%JAVAFX_BASE%\javafx-base\!JAVAFX_VERSION!"
if exist "!MODULE_DIR!" (
    set "MODULE_PATH=!MODULE_DIR!"
    set "MODULES=javafx.base"
)

REM Check javafx-controls
set "MODULE_DIR=%JAVAFX_BASE%\javafx-controls\!JAVAFX_VERSION!"
if exist "!MODULE_DIR!" (
    if "!MODULE_PATH!"=="" (
        set "MODULE_PATH=!MODULE_DIR!"
        set "MODULES=javafx.controls"
    ) else (
        set "MODULE_PATH=!MODULE_PATH!;!MODULE_DIR!"
        set "MODULES=!MODULES!,javafx.controls"
    )
)

REM Check javafx-fxml
set "MODULE_DIR=%JAVAFX_BASE%\javafx-fxml\!JAVAFX_VERSION!"
if exist "!MODULE_DIR!" (
    if "!MODULE_PATH!"=="" (
        set "MODULE_PATH=!MODULE_DIR!"
        set "MODULES=javafx.fxml"
    ) else (
        set "MODULE_PATH=!MODULE_PATH!;!MODULE_DIR!"
        set "MODULES=!MODULES!,javafx.fxml"
    )
)

REM Check javafx-graphics
set "MODULE_DIR=%JAVAFX_BASE%\javafx-graphics\!JAVAFX_VERSION!"
if exist "!MODULE_DIR!" (
    if "!MODULE_PATH!"=="" (
        set "MODULE_PATH=!MODULE_DIR!"
        set "MODULES=javafx.graphics"
    ) else (
        set "MODULE_PATH=!MODULE_PATH!;!MODULE_DIR!"
        set "MODULES=!MODULES!,javafx.graphics"
    )
)

REM Check javafx-web
set "MODULE_DIR=%JAVAFX_BASE%\javafx-web\!JAVAFX_VERSION!"
if exist "!MODULE_DIR!" (
    if "!MODULE_PATH!"=="" (
        set "MODULE_PATH=!MODULE_DIR!"
        set "MODULES=javafx.web"
    ) else (
        set "MODULE_PATH=!MODULE_PATH!;!MODULE_DIR!"
        set "MODULES=!MODULES!,javafx.web"
    )
)

if "!MODULE_PATH!"=="" (
    echo Warning: Could not find JavaFX modules
    echo.
    echo Attempting to launch without module-path...
    echo.
    cd /d "%JYLOS_DIR%"
    java -jar "%JAR%"
    exit /b !ERRORLEVEL!
)

echo JavaFX found (version !JAVAFX_VERSION!)
echo.
echo Launching Jylos...
echo.

REM Change to Jylos directory so relative paths work
cd /d "%JYLOS_DIR%"

REM Launch with module-path
java --module-path "!MODULE_PATH!" --add-modules !MODULES! -jar "%JAR%"

set "EXIT_CODE=!ERRORLEVEL!"

if !EXIT_CODE! NEQ 0 (
    echo.
    echo Error launching application (code: !EXIT_CODE!)
    echo.
    pause
)

exit /b !EXIT_CODE!
