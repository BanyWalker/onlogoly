param(
    [string]$SourceDirectory = (Join-Path $PSScriptRoot '../../services/api/data/workspaces/ENV-OM-001'),
    [string]$DataDirectory = (Join-Path $PSScriptRoot '../../services/workspace-data'),
    [string]$WorkspaceId = 'ENV-OM-001'
)

$ErrorActionPreference = 'Stop'
if ($WorkspaceId -notmatch '^[A-Za-z0-9_][A-Za-z0-9_.-]{0,127}$') {
    throw 'Invalid workspace ID'
}
$sourcePath = (Resolve-Path -LiteralPath $SourceDirectory).Path
$dataPath = [System.IO.Path]::GetFullPath($DataDirectory)
$destinationPath = Join-Path $dataPath "workspaces/$WorkspaceId"
if (-not (Test-Path -LiteralPath (Join-Path $sourcePath 'object-model.json') -PathType Leaf)) {
    throw "Missing source model: $sourcePath"
}
if (Test-Path -LiteralPath $destinationPath) {
    throw "Destination already exists; refusing to overwrite: $destinationPath"
}
if ($dataPath.StartsWith($sourcePath + [System.IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Destination must be outside the source workspace'
}
$migrationId = (Get-Date -Format 'yyyyMMdd-HHmmss') + '-' + [Guid]::NewGuid().ToString('N')
$backupPath = Join-Path $dataPath "migration-backups/$migrationId/$WorkspaceId"
$stagingPath = Join-Path $dataPath ".migration-$migrationId"
New-Item -ItemType Directory -Path $backupPath, $stagingPath, (Split-Path $destinationPath -Parent) -Force | Out-Null
$sourceFiles = @(Get-ChildItem -LiteralPath $sourcePath -File -Recurse -Force)
foreach ($file in $sourceFiles) {
    $relativePath = $file.FullName.Substring($sourcePath.Length).TrimStart([char[]]'\/')
    foreach ($copyRoot in @($backupPath, $stagingPath)) {
        $copyPath = Join-Path $copyRoot $relativePath
        New-Item -ItemType Directory -Path (Split-Path $copyPath -Parent) -Force | Out-Null
        Copy-Item -LiteralPath $file.FullName -Destination $copyPath
        if ((Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash -ne (Get-FileHash -LiteralPath $copyPath -Algorithm SHA256).Hash) {
            throw "Copy checksum mismatch: $relativePath"
        }
    }
}
Move-Item -LiteralPath $stagingPath -Destination $destinationPath
Write-Output "Copied and verified $($sourceFiles.Count) files: $destinationPath"
Write-Output "Migration backup: $backupPath"
Write-Output 'The source is preserved. Start the backend using the new directory and verify it before removing old runtime files.'
