[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateRange(0, 24)]
    [int]$Zoom,
    [Parameter(Mandatory = $true)]
    [ValidateRange(0, [int]::MaxValue)]
    [int]$X,
    [Parameter(Mandatory = $true)]
    [ValidateRange(0, [int]::MaxValue)]
    [int]$Y,
    [string]$Archive,
    [string]$Output
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
if ([string]::IsNullOrWhiteSpace($Archive)) {
    $Archive = Join-Path $repoRoot "build/offline/italy.pmtiles"
}
$Archive = (Resolve-Path -LiteralPath $Archive).Path

$maxCoordinate = [math]::Pow(2, $Zoom) - 1
if ($X -gt $maxCoordinate -or $Y -gt $maxCoordinate) {
    throw "Tile $Zoom/$X/$Y is outside the valid coordinate range 0-$maxCoordinate."
}

if ([string]::IsNullOrWhiteSpace($Output)) {
    $inspectDirectory = Join-Path $repoRoot "build/offline/inspect"
    New-Item -ItemType Directory -Force -Path $inspectDirectory | Out-Null
    $Output = Join-Path $inspectDirectory "$Zoom-$X-$Y.mvt"
} else {
    $outputParent = Split-Path -Parent $Output
    if (-not [string]::IsNullOrWhiteSpace($outputParent)) {
        New-Item -ItemType Directory -Force -Path $outputParent | Out-Null
    }
}
$Output = [System.IO.Path]::GetFullPath($Output)

$pmtiles = Get-Command pmtiles -ErrorAction SilentlyContinue
if ($null -eq $pmtiles) {
    throw "pmtiles was not found on PATH. Install the same CLI used for 'pmtiles verify'."
}

# Windows PowerShell text redirection can alter binary stdout. Copy the native
# stdout stream directly so the extracted MVT remains byte-for-byte intact.
$startInfo = New-Object System.Diagnostics.ProcessStartInfo
$startInfo.FileName = $pmtiles.Source
$startInfo.Arguments = "tile `"$Archive`" $Zoom $X $Y"
$startInfo.UseShellExecute = $false
$startInfo.RedirectStandardOutput = $true
$startInfo.RedirectStandardError = $true
$startInfo.CreateNoWindow = $true

$process = New-Object System.Diagnostics.Process
$process.StartInfo = $startInfo
if (-not $process.Start()) {
    throw "Failed to start pmtiles."
}
$outputStream = [System.IO.File]::Create($Output)
try {
    $process.StandardOutput.BaseStream.CopyTo($outputStream)
} finally {
    $outputStream.Dispose()
}
$stderr = $process.StandardError.ReadToEnd()
$process.WaitForExit()
if ($process.ExitCode -ne 0) {
    Remove-Item -LiteralPath $Output -Force -ErrorAction SilentlyContinue
    throw "pmtiles tile failed: $stderr"
}

$size = (Get-Item -LiteralPath $Output).Length
if ($size -eq 0) {
    Remove-Item -LiteralPath $Output -Force
    throw "Archive contains no tile at $Zoom/$X/$Y."
}

Write-Host "Extracted $Zoom/$X/$Y to $Output ($size bytes)."
Write-Host "Decode this raw MVT with a vector-tile inspector to examine geometry types and attributes by source layer."
