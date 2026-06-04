param()
$ErrorActionPreference = 'Stop'

$Root = Split-Path -Parent $PSScriptRoot
$Pom = Join-Path $Root "jylos/pom.xml"

Write-Host "== Jylos Storage Matrix Gate =="
Write-Host "Root: $Root"
Write-Host ""

Write-Host "[1/3] SQLite contract/integration tests..."
mvn -f $Pom "-Dtest=SQLiteDBIntegrationTest,NoteDAOSQLiteTest,FolderDAOSQLiteContractTest" test

Write-Host ""
Write-Host "[2/3] FileSystem contract tests..."
mvn -f $Pom "-Dtest=FileSystemDAOContractTest,NoteDAOFileSystemTest" test

Write-Host ""
Write-Host "[3/3] Manual storage smoke (required)"
Write-Host "- Run app in SQLite mode and validate core flows"
Write-Host "- Run app in FileSystem mode and validate same flows"
Write-Host "- Confirm parity in: create/edit/save, trash/restore, folders/subfolders, tags"
Write-Host ""
Write-Host "Storage matrix gate completed successfully."
