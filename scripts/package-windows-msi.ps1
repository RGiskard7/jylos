<#
.SYNOPSIS
  Builds the Jylos Windows .msi installer.

.DESCRIPTION
  Thin wrapper over package-windows.ps1 -Type msi. Requires the WiX Toolset
  (jpackage uses WiX for both .exe and .msi installers). See docs/PACKAGING.md.

.EXAMPLE
  .\scripts\package-windows-msi.ps1
#>
& (Join-Path $PSScriptRoot 'package-windows.ps1') -Type msi
exit $LASTEXITCODE
