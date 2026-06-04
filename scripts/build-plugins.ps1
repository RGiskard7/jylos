# Build script for Jylos plugins
# Compiles and packages plugins as JAR files in the plugins/ directory

param(
    [switch]$Clean
)

$ErrorActionPreference = "Stop"

$ProjectRoot = Split-Path -Parent $PSScriptRoot
$PluginsSource = Join-Path $ProjectRoot "plugins-source"
$JylosDir = Join-Path $ProjectRoot "jylos"
$PluginsOutput = Join-Path $JylosDir "plugins"
$JylosSrc = Join-Path (Join-Path (Join-Path $JylosDir "src") "main") "java"
$TargetDir = Join-Path (Join-Path $JylosDir "target") "classes"
$PluginInterfacePath = Join-Path (Join-Path (Join-Path (Join-Path $JylosSrc "com") "example") "jylos") "plugin"

Write-Host "Building Jylos Plugins..." -ForegroundColor Cyan
Write-Host "Source: $PluginsSource" -ForegroundColor Gray
Write-Host "Output: $PluginsOutput" -ForegroundColor Gray

# Clean if requested
if ($Clean) {
    Write-Host "Cleaning plugins directory..." -ForegroundColor Yellow
    if (Test-Path $PluginsOutput) {
        Remove-Item -Path "$PluginsOutput\*.jar" -Force
        Write-Host "Cleaned plugins directory" -ForegroundColor Green
    }
}

# Ensure plugins directory exists
if (-not (Test-Path $PluginsOutput)) {
    New-Item -ItemType Directory -Path $PluginsOutput -Force | Out-Null
    Write-Host "Created plugins directory: $PluginsOutput" -ForegroundColor Green
}

# Check if plugins source exists
if (-not (Test-Path $PluginsSource)) {
    Write-Host "ERROR: plugins-source directory not found!" -ForegroundColor Red
    Write-Host "Expected location: $PluginsSource" -ForegroundColor Red
    exit 1
}

# Get Java compiler
$JavaHome = $env:JAVA_HOME
if (-not $JavaHome) {
    # Try common locations
    $CommonJavaPaths = @(
        "C:\Program Files\Java\jdk-17",
        "C:\Program Files\Java\jdk-21",
        "C:\Program Files\Java\jdk-11"
    )
    foreach ($path in $CommonJavaPaths) {
        if (Test-Path (Join-Path $path "bin\java.exe")) {
            $JavaHome = $path
            break
        }
    }
}

if ($JavaHome) {
    $Javac = Join-Path (Join-Path $JavaHome "bin") "javac.exe"
    $Java = Join-Path (Join-Path $JavaHome "bin") "java.exe"
    $Jar = Join-Path (Join-Path $JavaHome "bin") "jar.exe"
} else {
    $Javac = "javac"
    $Java = "java"
    $Jar = "jar"
}

# Check Java
if (Test-Path $Java) {
    $oldErrorAction = $ErrorActionPreference
    $ErrorActionPreference = "SilentlyContinue"
    $javaOutput = & $Java -version 2>&1
    $ErrorActionPreference = $oldErrorAction
    if ($javaOutput) {
        $javaVersion = ($javaOutput | Select-Object -First 1).ToString()
        Write-Host "Using: $javaVersion" -ForegroundColor Gray
    } else {
        Write-Host "WARNING: Could not get Java version, but Java executable exists" -ForegroundColor Yellow
    }
} else {
    Write-Host "ERROR: Java not found! Please set JAVA_HOME or add Java to PATH" -ForegroundColor Red
    Write-Host "Tried: $Java" -ForegroundColor Yellow
    exit 1
}

# Build core classes first (needed for plugin compilation)
Write-Host "`nBuilding core classes..." -ForegroundColor Cyan
Push-Location (Join-Path $ProjectRoot "jylos")
try {
    & mvn compile -q
    if ($LASTEXITCODE -ne 0) {
        Write-Host "ERROR: Failed to compile core classes" -ForegroundColor Red
        exit 1
    }
} finally {
    Pop-Location
}

# Get classpath using Maven dependency plugin
Write-Host "Building classpath with Maven..." -ForegroundColor Gray
Push-Location (Join-Path $ProjectRoot "jylos")
try {
    # Use Maven to get all dependencies
    $mvnOutput = & mvn dependency:build-classpath -q 2>&1 | Out-String
    $mavenClasspath = ($mvnOutput -split "`n" | Where-Object { $_ -match "Dependencies classpath:" } | ForEach-Object { 
        if ($_ -match "Dependencies classpath: (.+)") { $matches[1] }
    }) | Select-Object -First 1
    
    if ($mavenClasspath) {
        $ClassPath = "$TargetDir;$mavenClasspath"
        Write-Host "Classpath built with Maven dependencies" -ForegroundColor Gray
    } else {
        # Fallback: manual classpath construction
        Write-Host "WARNING: Could not get Maven classpath, using manual construction" -ForegroundColor Yellow
        $ClassPath = $TargetDir
        $MavenRepo = Join-Path (Join-Path $env:USERPROFILE ".m2") "repository"
        
        # Add all JavaFX modules
        $JavaFXModules = @("javafx-base", "javafx-controls", "javafx-fxml", "javafx-graphics", "javafx-web")
        foreach ($module in $JavaFXModules) {
            $modulePath = Join-Path $MavenRepo "org\openjfx\$module\21"
            if (Test-Path $modulePath) {
                $jars = Get-ChildItem -Path $modulePath -Filter "*.jar" -Recurse | Select-Object -First 1
                if ($jars) {
                    $ClassPath += ";$($jars.FullName)"
                }
            }
        }
        
        # Add other dependencies
        $deps = @(
            "org\xerial\sqlite-jdbc",
            "org\commonmark\commonmark",
            "org\controlsfx\controlsfx"
        )
        foreach ($dep in $deps) {
            $depPath = Join-Path $MavenRepo $dep
            if (Test-Path $depPath) {
                $jars = Get-ChildItem -Path $depPath -Filter "*.jar" -Recurse | Select-Object -First 1
                if ($jars) {
                    $ClassPath += ";$($jars.FullName)"
                }
            }
        }
    }
} finally {
    Pop-Location
}

Write-Host "Classpath configured" -ForegroundColor Gray

# Get all plugin source files
$PluginFiles = Get-ChildItem -Path $PluginsSource -Filter "*.java" -Recurse

if ($PluginFiles.Count -eq 0) {
    Write-Host "WARNING: No plugin source files found in $PluginsSource" -ForegroundColor Yellow
    exit 0
}

Write-Host "`nFound $($PluginFiles.Count) plugin file(s)" -ForegroundColor Cyan

# Compile each plugin
$CompiledPlugins = @()

foreach ($pluginFile in $PluginFiles) {
    $pluginName = $pluginFile.BaseName
    
    # Skip package-info.java
    if ($pluginName -eq "package-info") {
        continue
    }
    
    Write-Host "`nBuilding plugin: $pluginName..." -ForegroundColor Cyan
    
    # Create temp directory for this plugin
    $TempDir = Join-Path $env:TEMP "jylos-plugin-$pluginName"
    if (Test-Path $TempDir) {
        Remove-Item -Path $TempDir -Recurse -Force
    }
    New-Item -ItemType Directory -Path $TempDir -Force | Out-Null
    
    # Copy ONLY this plugin's source file (preserve package structure)
    $SourcePackageDir = Join-Path $PluginsSource "com\example\jylos\plugin\builtin"
    $PluginPackageDir = Join-Path $TempDir "com\example\jylos\plugin\builtin"
    
    # Ensure destination directory exists
    if (-not (Test-Path $PluginPackageDir)) {
        New-Item -ItemType Directory -Path $PluginPackageDir -Force | Out-Null
    }
    
    $PluginSourceFile = $null
    if (Test-Path $SourcePackageDir) {
        # Copy from plugins-source/com/example/jylos/plugin/builtin
        $PluginSourceFile = Join-Path $SourcePackageDir "$pluginName.java"
    } else {
        # Fallback: copy from plugins-source root (old structure)
        $PluginSourceFile = Join-Path $PluginsSource "$pluginName.java"
    }
    
    if (-not (Test-Path $PluginSourceFile)) {
        Write-Host "  ERROR: Plugin source file not found: $PluginSourceFile" -ForegroundColor Red
        Remove-Item -Path $TempDir -Recurse -Force
        continue
    }
    
    # Copy the file (ensure destination directory exists)
    try {
        Copy-Item -Path $PluginSourceFile -Destination $PluginPackageDir -Force -ErrorAction Stop
    } catch {
        Write-Host "  ERROR: Failed to copy file: $_" -ForegroundColor Red
        Remove-Item -Path $TempDir -Recurse -Force
        continue
    }
    
    # Verify file was copied
    $CopiedFile = Join-Path $PluginPackageDir "$pluginName.java"
    if (-not (Test-Path $CopiedFile)) {
        Write-Host "  ERROR: Plugin source file not found after copy: $CopiedFile" -ForegroundColor Red
        Write-Host "  Source: $PluginSourceFile" -ForegroundColor Yellow
        Write-Host "  Destination: $PluginPackageDir" -ForegroundColor Yellow
        Remove-Item -Path $TempDir -Recurse -Force
        continue
    }
    
    # Compile ONLY this plugin file
    $SourcePaths = @($CopiedFile)
    
    Write-Host "  Compiling..." -ForegroundColor Gray
    # Use UTF-8 encoding to avoid character encoding issues
    # Temporarily ignore warnings (they're not fatal)
    $oldErrorAction = $ErrorActionPreference
    $ErrorActionPreference = "SilentlyContinue"
    $compileOutput = & $Javac --release 17 -encoding UTF-8 -cp $ClassPath -d $TempDir $SourcePaths 2>&1
    $ErrorActionPreference = $oldErrorAction
    
    $hasErrors = $false
    if ($compileOutput) {
        $compileOutput | ForEach-Object {
            $line = $_.ToString()
            if ($line -match "error:") {
                Write-Host "  ERROR: $line" -ForegroundColor Red
                $hasErrors = $true
            } elseif ($line -match "warning:") {
                # Ignore warnings (they're not fatal)
                Write-Host "  Warning: $line" -ForegroundColor Yellow
            }
        }
    }
    
    # Check if compilation succeeded by looking for the .class file
    $PluginClass = Get-ChildItem -Path $TempDir -Filter "$pluginName.class" -Recurse | Select-Object -First 1
    if ($hasErrors -or (-not $PluginClass)) {
        Write-Host "  ERROR: Failed to compile $pluginName" -ForegroundColor Red
        Remove-Item -Path $TempDir -Recurse -Force
        continue
    }
    
    # Plugin class already found during compilation check
    
    # Get full class name
    $RelativePath = $PluginClass.FullName.Substring($TempDir.Length + 1)
    $ClassName = $RelativePath -replace "\\", "." -replace "\.class$", ""
    
    Write-Host "  Plugin class: $ClassName" -ForegroundColor Gray
    
    # Create JAR
    $JarName = "$pluginName.jar"
    $JarPath = Join-Path $PluginsOutput $JarName
    
    Write-Host "  Creating JAR: $JarName..." -ForegroundColor Gray
    
    # Create manifest
    $ManifestFile = Join-Path $TempDir "MANIFEST.MF"
    $ManifestContent = @"
Manifest-Version: 1.0
Plugin-Class: $ClassName
Created-By: Jylos Plugin Builder

"@
    Set-Content -Path $ManifestFile -Value $ManifestContent
    
    # Verify compiled classes exist (including inner classes)
    $PluginClassDir = Join-Path $TempDir "com\example\jylos\plugin\builtin"
    $AllClassFiles = Get-ChildItem -Path $PluginClassDir -Filter "*.class" -ErrorAction SilentlyContinue
    if ($AllClassFiles) {
        $InnerClasses = @($AllClassFiles | Where-Object { $_.Name -match '\$' })
        if ($InnerClasses.Count -gt 0) {
            Write-Host "  Found inner classes: $($InnerClasses.Count)" -ForegroundColor Gray
        }
    }
    
    # Remove source files from temp dir (we only want .class files in JAR)
    Get-ChildItem -Path $TempDir -Filter "*.java" -Recurse | Remove-Item -Force
    
    # Build JAR (include only plugin classes, not dependencies)
    Push-Location $TempDir
    try {
        # Use -C to change directory and include only the 'com' package structure
        # This ensures we include only plugin classes, not any dependency classes
        & $Jar cfm $JarPath $ManifestFile -C $TempDir com
        if ($LASTEXITCODE -eq 0) {
            # Verify JAR contains the main class and inner classes
            $jarContents = & $Jar tf $JarPath 2>&1 | Where-Object { $_ -match "\.class$" }
            $pluginClasses = $jarContents | Where-Object { $_ -match "builtin.*\.class$" }
            $hasMainClass = $pluginClasses | Where-Object { $_ -match "$pluginName\.class$" }
            $hasInnerClasses = $pluginClasses | Where-Object { $_ -match '\$.*\.class$' }
            
            if ($hasMainClass) {
                Write-Host "  [OK] Created: $JarName" -ForegroundColor Green
                $innerCount = @($hasInnerClasses).Count
                if ($innerCount -gt 0) {
                    Write-Host "    (includes $innerCount inner class(es))" -ForegroundColor Gray
                }
                $CompiledPlugins += $pluginName
            } else {
                Write-Host "  ERROR: JAR created but main class not found" -ForegroundColor Red
                Write-Host "    JAR contents: $($pluginClasses -join ', ')" -ForegroundColor Yellow
            }
        } else {
            Write-Host "  ERROR: Failed to create JAR" -ForegroundColor Red
        }
    } finally {
        Pop-Location
        Remove-Item -Path $TempDir -Recurse -Force
    }
}

Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "Plugin Build Summary" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Built: $($CompiledPlugins.Count) plugin(s)" -ForegroundColor Green
foreach ($plugin in $CompiledPlugins) {
    Write-Host "  - $plugin.jar" -ForegroundColor Gray
}
Write-Host "`nPlugins location: $PluginsOutput" -ForegroundColor Cyan
Write-Host "Done!" -ForegroundColor Green
