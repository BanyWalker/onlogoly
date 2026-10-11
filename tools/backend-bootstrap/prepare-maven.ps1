param(
    [string] $MavenUserHome
)

$ErrorActionPreference = 'Stop'
$archivePath = Join-Path $PSScriptRoot 'apache-maven-3.9.16-bin.zip'
$checksumPath = "$archivePath.sha512"
$propertiesPath = Join-Path $PSScriptRoot '../../services/api/.mvn/wrapper/maven-wrapper.properties'
$properties = Get-Content -LiteralPath $propertiesPath -Raw | ConvertFrom-StringData
$expected = (Get-Content -LiteralPath $checksumPath -Raw).Trim().Split()[0].ToLowerInvariant()
$actual = (Get-FileHash -LiteralPath $archivePath -Algorithm SHA512).Hash.ToLowerInvariant()
if ($actual -ne $expected) { throw 'Local Maven archive SHA-512 verification failed.' }
if ((Get-FileHash -LiteralPath $archivePath -Algorithm SHA256).Hash.ToLowerInvariant() -ne $properties.distributionSha256Sum) {
    throw 'Local Maven archive differs from the pinned Wrapper SHA-256.'
}

if (-not $MavenUserHome) {
    $MavenUserHome = if ($env:MAVEN_USER_HOME) { $env:MAVEN_USER_HOME } else { Join-Path $env:USERPROFILE '.m2' }
}
$wrapperRoot = [IO.Path]::GetFullPath((Join-Path $MavenUserHome 'wrapper/dists/apache-maven-3.9.16'))
$urlHash = ([Security.Cryptography.SHA256]::Create().ComputeHash([Text.Encoding]::ASCII.GetBytes($properties.distributionUrl)) | ForEach-Object { $_.ToString('x2') }) -join ''
$destination = [IO.Path]::GetFullPath((Join-Path $wrapperRoot $urlHash))
if (Test-Path -LiteralPath (Join-Path $destination 'bin/mvn.cmd')) {
    Write-Output "Verified archive; Maven Wrapper cache is ready: $destination"
    exit 0
}
if (Test-Path -LiteralPath $destination) { throw 'Incomplete cache exists; refusing to overwrite it.' }

$staging = [IO.Path]::GetFullPath((Join-Path $wrapperRoot ('.unpack-' + [guid]::NewGuid().ToString('N'))))
$sourceDirectory = [IO.Path]::GetFullPath((Join-Path $staging 'apache-maven-3.9.16'))
$rootPrefix = $wrapperRoot.TrimEnd('\', '/') + [IO.Path]::DirectorySeparatorChar
foreach ($candidate in @($destination, $staging, $sourceDirectory)) {
    if (-not $candidate.StartsWith($rootPrefix, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Maven cache path escaped the intended wrapper directory.'
    }
}
New-Item -ItemType Directory -Path $wrapperRoot -Force | Out-Null
Expand-Archive -LiteralPath $archivePath -DestinationPath $staging
if (-not (Test-Path -LiteralPath (Join-Path $sourceDirectory 'bin/mvn.cmd'))) { throw 'Archive is missing Maven.' }
Move-Item -LiteralPath $sourceDirectory -Destination $destination
Remove-Item -LiteralPath $staging
Write-Output "Prepared Maven Wrapper cache: $destination"
