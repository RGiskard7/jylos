$ErrorActionPreference = "Stop"

$RootDir = Split-Path -Parent $PSScriptRoot
$EditorDir = Join-Path $RootDir "jylos/editor-web"

if (-not (Get-Command npm -ErrorAction SilentlyContinue)) {
    throw "npm is required to rebuild the CodeMirror editor bundle."
}

Push-Location $EditorDir
try {
    npm ci
    npm run build
} finally {
    Pop-Location
}
