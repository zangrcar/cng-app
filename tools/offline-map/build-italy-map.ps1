[CmdletBinding()]
param(
    [ValidateSet("Italy", "NordEst", "LjubljanaTest")]
    [string]$Region = "Italy",
    [string]$BasemapsRef = "a50c699adc60a45c899971b1e11275e61f13bfbf",
    [switch]$Rebuild
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

function Format-FileSize([long]$Bytes) {
    return "{0:N1} MiB" -f ($Bytes / 1MB)
}

function Assert-FileSignature([string]$Label, [string]$Path, [string]$Kind) {
    $stream = [System.IO.File]::OpenRead($Path)
    try {
        $header = New-Object byte[] 16
        $read = $stream.Read($header, 0, $header.Length)
    } finally {
        $stream.Dispose()
    }

    $valid = switch ($Kind) {
        "Zip" { $read -ge 4 -and $header[0] -eq 0x50 -and $header[1] -eq 0x4B -and $header[2] -in @(0x03, 0x05, 0x07) -and $header[3] -in @(0x04, 0x06, 0x08) }
        "Gzip" { $read -ge 2 -and $header[0] -eq 0x1F -and $header[1] -eq 0x8B }
        "SQLite" { $read -ge 16 -and [System.Text.Encoding]::ASCII.GetString($header, 0, 16) -eq "SQLite format 3`0" }
        "OsmPbf" {
            if ($read -lt 12) { $false } else {
                $blobHeaderLength = ([int]$header[0] -shl 24) -bor ([int]$header[1] -shl 16) -bor ([int]$header[2] -shl 8) -bor [int]$header[3]
                $blobHeaderLength -ge 8 -and $blobHeaderLength -le 64KB -and
                    [System.Text.Encoding]::ASCII.GetString($header, 4, 12) -match "OSMHeader"
            }
        }
        "Pmtiles" { $read -ge 8 -and [System.Text.Encoding]::ASCII.GetString($header, 0, 7) -eq "PMTiles" -and $header[7] -eq 3 }
        default { throw "Unknown file signature kind '$Kind'." }
    }
    if (-not $valid) {
        throw "$Label has an invalid $Kind header: $Path"
    }
}

function Invoke-DownloadAttempt(
    [string]$Label,
    [string]$Url,
    [string]$Partial,
    [long]$MinimumBytes
) {
    $bits = Get-Command Start-BitsTransfer -ErrorAction SilentlyContinue
    $curl = Get-Command curl.exe -ErrorAction SilentlyContinue

    if ((-not (Test-Path -LiteralPath $Partial)) -and $null -ne $bits) {
        Write-Host "Downloading ${Label} with BITS from $Url"
        try {
            Start-BitsTransfer -Source $Url -Destination $Partial -Description "CNG Italy: $Label" -ErrorAction Stop
        } catch {
            Write-Warning "BITS failed for ${Label}: $($_.Exception.Message)"
            return $false
        }
    } else {
        if ($null -eq $curl) {
            Write-Warning "curl.exe is unavailable for downloading/resuming $Label."
            return $false
        }
        $partialSize = if (Test-Path -LiteralPath $Partial -PathType Leaf) {
            (Get-Item -LiteralPath $Partial).Length
        } else {
            0L
        }
        Write-Host "Downloading/resuming ${Label} with curl.exe from $(Format-FileSize $partialSize): $Url"
        & $curl.Source --location --fail --retry 10 --retry-delay 5 --retry-all-errors --continue-at - --output $Partial $Url
        if ($LASTEXITCODE -ne 0) {
            Write-Warning "curl.exe failed downloading $Label from $Url."
            return $false
        }
    }

    return (Test-Path -LiteralPath $Partial -PathType Leaf) -and
        (Get-Item -LiteralPath $Partial).Length -ge $MinimumBytes
}

function Ensure-DownloadedFile(
    [string]$Label,
    [string]$Url,
    [string]$FallbackUrl,
    [string]$Destination,
    [long]$MinimumBytes,
    [string]$Signature
) {
    if (Test-Path -LiteralPath $Destination -PathType Leaf) {
        $existingSize = (Get-Item -LiteralPath $Destination).Length
        if ($existingSize -ge $MinimumBytes) {
            Assert-FileSignature -Label $Label -Path $Destination -Kind $Signature
            Write-Host "Reusing ${Label}: $(Format-FileSize $existingSize)"
            return
        }
        Write-Warning "$Label cache is too small ($(Format-FileSize $existingSize)); downloading a replacement."
    }

    $parent = Split-Path -Parent $Destination
    New-Item -ItemType Directory -Force -Path $parent | Out-Null
    $partial = "$Destination.partial"

    try {
        $downloaded = Invoke-DownloadAttempt -Label $Label -Url $Url -Partial $partial -MinimumBytes $MinimumBytes
        if (-not $downloaded -and -not [string]::IsNullOrWhiteSpace($FallbackUrl)) {
            Write-Warning "Primary Geofabrik download failed; trying official ext2 mirror."
            $downloaded = Invoke-DownloadAttempt -Label $Label -Url $FallbackUrl -Partial $partial -MinimumBytes $MinimumBytes
        }
        if (-not $downloaded) {
            throw "All configured download hosts failed. Partial file retained at $partial."
        }

        if (-not (Test-Path -LiteralPath $partial -PathType Leaf)) {
            throw "Download completed without creating $partial."
        }
        $downloadedSize = (Get-Item -LiteralPath $partial).Length
        if ($downloadedSize -lt $MinimumBytes) {
            throw "$Label download is too small ($(Format-FileSize $downloadedSize), minimum $(Format-FileSize $MinimumBytes)). Partial file retained at $partial."
        }

        Assert-FileSignature -Label $Label -Path $partial -Kind $Signature

        Move-Item -LiteralPath $partial -Destination $Destination -Force
        Write-Host "Downloaded ${Label}: $(Format-FileSize $downloadedSize)"
    } catch {
        throw "Failed to prepare ${Label} from ${Url}. $($_.Exception.Message)"
    }
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
$stagedOutputFile = Join-Path $offlineBuild "italy.building.pmtiles"
if ([System.IO.Path]::GetExtension($stagedOutputFile) -ne ".pmtiles") {
    throw "Planetiler staging output must end in .pmtiles: $stagedOutputFile"
}
$area = switch ($Region) {
    "NordEst" { "nord-est" }
    "LjubljanaTest" { "ljubljana-test" }
    default { "italy" }
}
$sourceUrl = switch ($Region) {
    "NordEst" { "https://download.geofabrik.de/europe/italy/nord-est-latest.osm.pbf" }
    "LjubljanaTest" { "https://download.bbbike.org/osm/bbbike/Ljubljana/Ljubljana.osm.pbf" }
    default { "https://download.geofabrik.de/europe/italy-latest.osm.pbf" }
}
$sourceFallbackUrl = switch ($Region) {
    "NordEst" { "https://download-ext2.geofabrik.de/europe/italy/nord-est-latest.osm.pbf" }
    "LjubljanaTest" { "" }
    default { "https://download-ext2.geofabrik.de/europe/italy-latest.osm.pbf" }
}
$osmMinimumBytes = switch ($Region) {
    "NordEst" { 100MB }
    "LjubljanaTest" { 10MB }
    default { 500MB }
}

New-Item -ItemType Directory -Force -Path $cacheDirectory | Out-Null

if (-not (Test-Path -LiteralPath (Join-Path $basemapsDirectory ".git"))) {
    Write-Host "Cloning official protomaps/basemaps profile..."
    & $git clone https://github.com/protomaps/basemaps.git $basemapsDirectory
    if ($LASTEXITCODE -ne 0) { throw "Failed to clone protomaps/basemaps." }
}

Write-Host "Selecting pinned basemaps revision $BasemapsRef..."
& $git -C $basemapsDirectory cat-file -e "$BasemapsRef^{commit}" 2>$null
if ($LASTEXITCODE -ne 0) {
    Write-Host "Pinned revision is not cached; fetching it from the official repository..."
    & $git -C $basemapsDirectory fetch --depth 1 origin $BasemapsRef
    if ($LASTEXITCODE -ne 0) { throw "Failed to fetch basemaps revision $BasemapsRef." }
}
& $git -C $basemapsDirectory checkout --detach --force $BasemapsRef
if ($LASTEXITCODE -ne 0) { throw "Failed to check out basemaps revision $BasemapsRef." }

$resolvedBasemapsRef = (& $git -C $basemapsDirectory rev-parse HEAD | Out-String).Trim()
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($resolvedBasemapsRef)) {
    throw "Failed to resolve the checked-out basemaps revision."
}

$tilesDirectory = Join-Path $basemapsDirectory "tiles"
$sourcesDirectory = Join-Path $tilesDirectory "data/sources"
$sourceDefinitions = @(
    @{ Label = "$Region OSM"; Url = $sourceUrl; FallbackUrl = $sourceFallbackUrl; Destination = (Join-Path $sourcesDirectory "$area.osm.pbf"); MinimumBytes = $osmMinimumBytes; Signature = "OsmPbf" },
    @{ Label = "Natural Earth"; Url = "https://naciscdn.org/naturalearth/packages/natural_earth_vector.gpkg.zip"; Destination = (Join-Path $sourcesDirectory "natural_earth_vector.gpkg.zip"); MinimumBytes = 10MB; Signature = "Zip" },
    @{ Label = "water polygons"; Url = "https://osmdata.openstreetmap.de/download/water-polygons-split-3857.zip"; Destination = (Join-Path $sourcesDirectory "water-polygons-split-3857.zip"); MinimumBytes = 10MB; Signature = "Zip" },
    @{ Label = "land polygons"; Url = "https://osmdata.openstreetmap.de/download/land-polygons-split-3857.zip"; Destination = (Join-Path $sourcesDirectory "land-polygons-split-3857.zip"); MinimumBytes = 10MB; Signature = "Zip" },
    @{ Label = "landcover"; Url = "https://r2-public.protomaps.com/datasets/daylight-landcover.gpkg"; Destination = (Join-Path $sourcesDirectory "daylight-landcover.gpkg"); MinimumBytes = 10MB; Signature = "SQLite" },
    @{ Label = "QRank"; Url = "https://qrank.toolforge.org/download/qrank.csv.gz"; Destination = (Join-Path $sourcesDirectory "qrank.csv.gz"); MinimumBytes = 10MB; Signature = "Gzip" },
    @{ Label = "PGF encoding"; Url = "https://wipfli.github.io/pgf-encoding/pgf-encoding.zip"; Destination = (Join-Path $sourcesDirectory "pgf-encoding.zip"); MinimumBytes = 100KB; Signature = "Zip" }
)

foreach ($source in $sourceDefinitions) {
    Ensure-DownloadedFile @source
}

$targetDirectory = Join-Path $tilesDirectory "target"
$revisionMarker = Join-Path $targetDirectory "cng-basemaps-revision.txt"
$jars = @(Get-ChildItem -LiteralPath $targetDirectory -Filter "*-with-deps.jar" -ErrorAction SilentlyContinue)
$builtRevision = if (Test-Path -LiteralPath $revisionMarker -PathType Leaf) {
    (Get-Content -LiteralPath $revisionMarker -Raw).Trim()
} else {
    ""
}
$shouldBuildJar = $Rebuild -or $jars.Count -ne 1 -or $builtRevision -ne $resolvedBasemapsRef

$published = $false
try {
    Push-Location $tilesDirectory
    try {
        if ($shouldBuildJar) {
            Write-Host "Building the official Protomaps Basemap Planetiler profile..."
            & $maven clean package -DskipTests
            if ($LASTEXITCODE -ne 0) { throw "Maven failed to build the basemap Planetiler JAR." }
            $jars = @(Get-ChildItem -LiteralPath $targetDirectory -Filter "*-with-deps.jar")
            if ($jars.Count -eq 1) {
                Set-Content -LiteralPath $revisionMarker -Value $resolvedBasemapsRef -NoNewline
            }
        } else {
            Write-Host "Reusing Planetiler JAR built for basemaps revision $resolvedBasemapsRef."
        }

        if ($jars.Count -ne 1) {
            throw "Expected exactly one *-with-deps.jar, found $($jars.Count)."
        }

        New-Item -ItemType Directory -Force -Path $offlineBuild | Out-Null
        if (Test-Path -LiteralPath $stagedOutputFile) {
            Remove-Item -LiteralPath $stagedOutputFile -Force
        }

        Write-Host "All source files available locally."
        Write-Host "Starting Planetiler with automatic downloads disabled."
        & $java -jar $jars[0].FullName --force "--area=$area" "--output=$stagedOutputFile"
        if ($LASTEXITCODE -ne 0) { throw "Planetiler basemap generation failed." }
    } finally {
        Pop-Location
    }

    if (-not (Test-Path -LiteralPath $stagedOutputFile -PathType Leaf)) {
        throw "Planetiler completed without producing $stagedOutputFile."
    }
    $outputSize = (Get-Item -LiteralPath $stagedOutputFile).Length
    if ($outputSize -lt 10MB) {
        throw "Generated PMTiles is suspiciously small ($outputSize bytes); refusing to use it."
    }
    Assert-FileSignature -Label "Generated PMTiles" -Path $stagedOutputFile -Kind "Pmtiles"

    Write-Host "Generated staged archive ($([Math]::Round($outputSize / 1MB, 1)) MiB)."
    Write-Host "This archive was generated directly by the official Protomaps Planetiler profile; pmtiles extract was not used."

    $pmtiles = Get-Command "pmtiles" -ErrorAction SilentlyContinue
    if ($null -eq $pmtiles) {
        Write-Warning "pmtiles CLI not found; install it and run 'pmtiles show ... --metadata' plus 'pmtiles verify ...' before phone testing."
    } else {
        Write-Host "Inspecting generated PMTiles metadata..."
        $metadataOutput = (& $pmtiles.Source show $stagedOutputFile --metadata 2>&1 | Out-String)
        if ($LASTEXITCODE -ne 0) { throw "pmtiles show failed for $outputFile." }
        Write-Host $metadataOutput.TrimEnd()
        $requiredLayers = @("earth", "water", "roads", "boundaries", "places")
        $missingLayers = @($requiredLayers | Where-Object { $metadataOutput -notmatch ('(?i)["'']id["'']\s*:\s*["'']' + [regex]::Escape($_) + '["'']') })
        if ($missingLayers.Count -gt 0) {
            throw "Generated PMTiles metadata is missing Android style layer(s): $($missingLayers -join ', ')."
        }
        Write-Host "Confirmed Android-required vector layers: $($requiredLayers -join ', ')."

        Write-Host "Verifying generated PMTiles archive..."
        & $pmtiles.Source verify $stagedOutputFile
        if ($LASTEXITCODE -ne 0) { throw "pmtiles verify failed for $outputFile." }
    }

    Move-Item -LiteralPath $stagedOutputFile -Destination $outputFile -Force
    $published = $true
    Write-Host "Created canonical archive $outputFile ($([Math]::Round($outputSize / 1MB, 1)) MiB)."
} finally {
    if (-not $published -and (Test-Path -LiteralPath $stagedOutputFile)) {
        Remove-Item -LiteralPath $stagedOutputFile -Force
    }
}
