# Script to safely remove the installers directory
# This handles cases where jpackage created very long paths that Windows can't delete normally

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Jylos - Cleanup Installers" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

$installersPath = Join-Path (Get-Location) "Jylos\target\installers"

if (-not (Test-Path $installersPath)) {
    Write-Host "Installers directory not found. Nothing to clean." -ForegroundColor Green
    exit 0
}

Write-Host "Found installers directory: $installersPath" -ForegroundColor Yellow
Write-Host "Attempting to remove (this may take a moment)..." -ForegroundColor Yellow
Write-Host ""

try {
    # Method 1: Try normal deletion first
    Remove-Item -Path $installersPath -Recurse -Force -ErrorAction Stop
    Write-Host "Successfully removed installers directory!" -ForegroundColor Green
} catch {
    Write-Host "Normal deletion failed. Trying robocopy method..." -ForegroundColor Yellow
    
    # Method 2: Use robocopy to delete (handles long paths better)
    $emptyDir = Join-Path $env:TEMP "empty-$(Get-Random)"
    New-Item -ItemType Directory -Path $emptyDir -Force | Out-Null
    
    try {
        # Robocopy with /MIR mirrors the empty directory to the target, effectively deleting it
        # /R:0 = retry 0 times, /W:0 = wait 0 seconds between retries
        $robocopyArgs = @(
            $emptyDir,
            $installersPath,
            "/MIR",
            "/R:0",
            "/W:0",
            "/NFL",  # No File List
            "/NDL",  # No Directory List
            "/NJH",  # No Job Header
            "/NJS"   # No Job Summary
        )
        
        Write-Host "Using robocopy to delete (this may take a moment)..." -ForegroundColor Yellow
        $process = Start-Process -FilePath "robocopy.exe" -ArgumentList $robocopyArgs -Wait -PassThru -NoNewWindow -WindowStyle Hidden
        
        # Robocopy exit codes: 0-7 are success codes
        # 0 = No files copied, 1 = Files copied successfully
        if ($process.ExitCode -le 7) {
            # Try to remove the now-empty directory
            Start-Sleep -Seconds 1
            Remove-Item -Path $installersPath -Force -ErrorAction SilentlyContinue
            Remove-Item -Path $emptyDir -Force -ErrorAction SilentlyContinue
            
            if (-not (Test-Path $installersPath)) {
                Write-Host "Successfully removed installers directory using robocopy!" -ForegroundColor Green
            } else {
                Write-Host "Partially removed. Some files may remain." -ForegroundColor Yellow
                Write-Host "You may need to restart your computer to fully remove the directory." -ForegroundColor Yellow
            }
        } else {
            Write-Host "Robocopy method failed. Exit code: $($process.ExitCode)" -ForegroundColor Red
            Write-Host ""
            Write-Host "The directory has a recursive structure that's too deep to delete easily." -ForegroundColor Yellow
            Write-Host ""
            Write-Host "Recommended solutions:" -ForegroundColor Cyan
            Write-Host "  1. Restart your computer, then try deleting again" -ForegroundColor Yellow
            Write-Host "  2. Use 7-Zip File Manager:" -ForegroundColor Yellow
            Write-Host "     - Right-click the 'installers' folder in 7-Zip" -ForegroundColor Yellow
            Write-Host "     - Select 'Delete' (7-Zip handles long paths better)" -ForegroundColor Yellow
            Write-Host "  3. Enable Windows long path support (requires Admin):" -ForegroundColor Yellow
            Write-Host "     New-ItemProperty -Path 'HKLM:\SYSTEM\CurrentControlSet\Control\FileSystem' -Name 'LongPathsEnabled' -Value 1 -PropertyType DWORD -Force" -ForegroundColor Cyan
            Write-Host "     Then restart PowerShell and try again" -ForegroundColor Cyan
        }
    } catch {
        Write-Host "Robocopy method also failed: $_" -ForegroundColor Red
        Write-Host ""
        Write-Host "The directory structure is too complex. Try restarting your computer first." -ForegroundColor Yellow
    } finally {
        # Clean up empty directory
        if (Test-Path $emptyDir) {
            Remove-Item -Path $emptyDir -Force -ErrorAction SilentlyContinue
        }
    }
}

Write-Host ""

