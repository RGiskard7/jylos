<#
Run script for Jylos (Windows PowerShell)
Usage: .\scripts\run_all.ps1

This script launches Jylos with JavaFX module-path configuration if needed.
#>
param()

Set-StrictMode -Version Latest

$root = Split-Path -Parent $PSScriptRoot
Push-Location (Join-Path $root 'jylos')

$jar = Join-Path (Get-Location) 'target\jylos-2.0.0-uber.jar'
if (Test-Path $jar) {
    Write-Host "Launching Jylos..." -ForegroundColor Green
    Write-Host "JAR: $jar" -ForegroundColor Cyan
    
    # Attempt to find JavaFX modules in Maven repository
    $m2Repo = Join-Path $env:USERPROFILE '.m2\repository'
    $javafxModules = @()
    
    if (Test-Path $m2Repo) {
        # javafx.web requires javafx.media, so we need to include it
        $modulesToFind = @('javafx-base', 'javafx-controls', 'javafx-fxml', 'javafx-graphics', 'javafx-media', 'javafx-web')
        
        foreach ($moduleName in $modulesToFind) {
            # Find version directories (e.g., 21, 21.0.0, etc.)
            $versionDirs = Get-ChildItem -Path "$m2Repo\org\openjfx\$moduleName" -Directory -ErrorAction SilentlyContinue |
                Where-Object { $_.Name -match '^[0-9]' } |
                Sort-Object { [version]($_.Name) } -Descending
            
            if ($versionDirs) {
                # Take the first matching version directory
                $versionDir = $versionDirs[0].FullName
                # Find the actual JAR file (not -sources.jar or -javadoc.jar)
                $jarFile = Get-ChildItem -Path $versionDir -Filter "$moduleName-*.jar" -ErrorAction SilentlyContinue | 
                    Where-Object { $_.Name -notmatch '-(sources|javadoc)\.jar$' } | 
                    Select-Object -First 1
                
                if ($jarFile) {
                    # Use the JAR file path directly (Java module-path accepts individual JAR files)
                    # This avoids Java scanning the directory and picking up -sources.jar files
                    if ($jarFile.FullName -notin $javafxModules) {
                        $javafxModules += $jarFile.FullName
                    }
                }
            }
        }
    }
    
    if ($javafxModules.Count -gt 0) {
        $modulePath = $javafxModules -join ';'
        Write-Host "Using JavaFX module-path: $modulePath" -ForegroundColor Cyan
        & java --module-path $modulePath --add-modules javafx.base,javafx.controls,javafx.fxml,javafx.graphics,javafx.media,javafx.web -jar $jar
    } else {
        Write-Host "JavaFX modules not found. Attempting standard JAR launch..." -ForegroundColor Yellow
        & java -jar $jar
    }
    
    if ($LASTEXITCODE -ne 0) {
        Write-Warning "JAR launch failed. Attempting via Maven exec:java..."
        & mvn -f 'pom.xml' exec:java "-Dexec.mainClass=com.example.jylos.Main" 2>&1
    }
} else {
    Write-Host "Packaged JAR not found at $jar" -ForegroundColor Red
    Write-Host "Attempting to run via Maven..." -ForegroundColor Yellow
    & mvn -f 'pom.xml' exec:java "-Dexec.mainClass=com.example.jylos.Main" 2>&1
}

Pop-Location
