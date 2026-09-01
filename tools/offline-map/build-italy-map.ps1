[CmdletBinding()]
param(
    [ValidateSet("Italy", "NordEst")]
    [string]$Region = "Italy",
    [string]$BasemapsRef = "a50c699adc60a45c899971b1e11275e61f13bfbf"
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

function Require-Command([string]$Name) {
    $command = Get-Command $Name -ErrorAction SilentlyContinue
    if ($null -eq $command) {
        throw "Required command '$Name' was not found on PATH."
    }
    return $command.Source
}

$git = Require-Command "git"
$java = Require-Command "java"
$maven = Require-Command "mvn"

$javaVersionText = (& $java --version | Select-Object -First 1) -join ""
if ($javaVersionText -notmatch '^(?:openjdk |java )?(?:version ")?(?<major>\d+)') {
    throw "Could not determine Java version from: $javaVersionText"
}
if ([int]$Matches.major -lt 21) {
    throw "Java 21 or newer is required; found: $javaVersionText"
}

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
$offlineBuild = Join-Path $repoRoot "build/offline"
$cacheDirectory = Join-Path $offlineBuild "cache"
$basemapsDirectory = Join-Path $cacheDirectory "basemaps"
$outputFile = Join-Path $offlineBuild "italy.pmtiles"
$area = if ($Region -eq "NordEst") { "italy/nord-est" } else { "italy" }
$sourceUrl = if ($Region -eq "NordEst") {
    "https://download.geofabrik.de/europe/italy/nord-est-latest.osm.pbf"
} else {
    "https://download.geofabrik.de/europe/italy-latest.osm.pbf"
}

New-Item -ItemType Directory -Force -Path $cacheDirectory | Out-Null

if (-not (Test-Path -LiteralPath (Join-Path $basemapsDirectory ".git"))) {
    Write-Host "Cloning official protomaps/basemaps profile..."
    & $git clone https://github.com/protomaps/basemaps.git $basemapsDirectory
    if ($LASTEXITCODE -ne 0) { throw "Failed to clone protomaps/basemaps." }
}

Write-Host "Updating basemaps checkout and selecting pinned revision $BasemapsRef..."
& $git -C $basemapsDirectory fetch --depth 1 origin $BasemapsRef
if ($LASTEXITCODE -ne 0) { throw "Failed to fetch basemaps revision $BasemapsRef." }
& $git -C $basemapsDirectory checkout --detach --force FETCH_HEAD
if ($LASTEXITCODE -ne 0) { throw "Failed to check out basemaps revision $BasemapsRef." }

$tilesDirectory = Join-Path $basemapsDirectory "tiles"
Write-Host "Building the official Protomaps Basemap Planetiler profile..."
Push-Location $tilesDirectory
try {
    & $maven clean package -DskipTests
    if ($LASTEXITCODE -ne 0) { throw "Maven failed to build the basemap Planetiler JAR." }

    $jars = @(Get-ChildItem -LiteralPath (Join-Path $tilesDirectory "target") -Filter "*-with-deps.jar")
    if ($jars.Count -ne 1) {
        throw "Expected exactly one *-with-deps.jar, found $($jars.Count)."
    }

    New-Item -ItemType Directory -Force -Path $offlineBuild | Out-Null
    if (Test-Path -LiteralPath $outputFile) {
        Remove-Item -LiteralPath $outputFile -Force
    }

    Write-Host "Generating $Region directly with Planetiler."
    Write-Host "OSM source: $sourceUrl"
    & $java -jar $jars[0].FullName --download --force "--area=$area" "--output=$outputFile"
    if ($LASTEXITCODE -ne 0) { throw "Planetiler basemap generation failed." }
} finally {
    Pop-Location
}

if (-not (Test-Path -LiteralPath $outputFile -PathType Leaf)) {
    throw "Planetiler completed without producing $outputFile."
}
$outputSize = (Get-Item -LiteralPath $outputFile).Length
if ($outputSize -lt 10MB) {
    throw "Generated PMTiles is suspiciously small ($outputSize bytes); refusing to use it."
}

Write-Host "Created $outputFile ($([Math]::Round($outputSize / 1MB, 1)) MiB)."
Write-Host "This archive was generated directly by the official Protomaps Planetiler profile; pmtiles extract was not used."

$pmtiles = Get-Command "pmtiles" -ErrorAction SilentlyContinue
if ($null -eq $pmtiles) {
    Write-Warning "pmtiles CLI not found; install it and run 'pmtiles show ... --metadata' plus 'pmtiles verify ...' before phone testing."
} else {
    Write-Host "Inspecting generated PMTiles metadata..."
    & $pmtiles.Source show $outputFile --metadata
    if ($LASTEXITCODE -ne 0) { throw "pmtiles show failed for $outputFile." }

    Write-Host "Verifying generated PMTiles archive..."
    & $pmtiles.Source verify $outputFile
    if ($LASTEXITCODE -ne 0) { throw "pmtiles verify failed for $outputFile." }
}
