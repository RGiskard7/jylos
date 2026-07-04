<#
.SYNOPSIS
  Builds the Jylos Windows .exe installer.

.DESCRIPTION
  Thin wrapper over package-windows.ps1 -Type exe. Requires the WiX Toolset
  (jpackage uses WiX for both .exe and .msi installers). See docs/PACKAGING.md.

.EXAMPLE
  .\scripts\package-windows-exe.ps1
#>
& (Join-Path $PSScriptRoot 'package-windows.ps1') -Type exe
exit $LASTEXITCODE
