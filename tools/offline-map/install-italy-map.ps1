[CmdletBinding()]
param(
    [string]$Serial
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
$sourceFile = Join-Path $repoRoot "build/offline/italy.pmtiles"
$packageId = "com.zangrcar.cngitaly"
$remoteTemporary = "/data/local/tmp/cng-italy-offline-map.pmtiles"
$appDestination = "files/maps/italy.pmtiles"

if (-not (Test-Path -LiteralPath $sourceFile -PathType Leaf)) {
    throw "Missing $sourceFile. Run build-italy-map.ps1 first."
}
$sourceSize = (Get-Item -LiteralPath $sourceFile).Length
if ($sourceSize -lt 10MB) {
    throw "Refusing to install suspiciously small PMTiles ($sourceSize bytes): $sourceFile"
}
$header = New-Object byte[] 8
$stream = [System.IO.File]::OpenRead($sourceFile)
try {
    $headerRead = $stream.Read($header, 0, $header.Length)
} finally {
    $stream.Dispose()
}
if ($headerRead -lt 8 -or [System.Text.Encoding]::ASCII.GetString($header, 0, 7) -ne "PMTiles" -or $header[7] -ne 3) {
    throw "Refusing to install a file without a valid PMTiles v3 header: $sourceFile"
}

$adbCommand = Get-Command adb -ErrorAction SilentlyContinue
if ($null -eq $adbCommand -and -not [string]::IsNullOrWhiteSpace($env:ANDROID_HOME)) {
    $candidate = Join-Path $env:ANDROID_HOME "platform-tools/adb.exe"
    if (Test-Path -LiteralPath $candidate) { $adbCommand = Get-Item $candidate }
}
if ($null -eq $adbCommand) { throw "adb was not found on PATH or under ANDROID_HOME/platform-tools." }
$adb = $adbCommand.Source
$selector = if ([string]::IsNullOrWhiteSpace($Serial)) { @() } else { @("-s", $Serial) }

function Invoke-Adb([string[]]$Arguments) {
    # Windows adb writes normal progress (including a successful push summary) to
    # stderr. Keep cmdlet errors terminating everywhere else, but capture both
    # native streams here and make adb's exit code authoritative.
    $previousErrorActionPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = "Continue"
        $output = & $adb @selector @Arguments 2>&1
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
    if ($exitCode -ne 0) {
        throw "adb $($Arguments -join ' ') failed:`n$($output -join [Environment]::NewLine)"
    }
    return $output
}

$deviceLines = @(& $adb devices | Select-Object -Skip 1 | Where-Object { $_ -match "\S+\s+device$" })
if ([string]::IsNullOrWhiteSpace($Serial) -and $deviceLines.Count -ne 1) {
    throw "Expected exactly one connected, authorized device; found $($deviceLines.Count). Use -Serial when multiple devices are connected."
}
if (-not [string]::IsNullOrWhiteSpace($Serial)) {
    $state = (& $adb @selector get-state 2>&1 | Out-String).Trim()
    if ($LASTEXITCODE -ne 0 -or $state -ne "device") {
        throw "Selected device '$Serial' is not connected and authorized (state: '$state')."
    }
}

Invoke-Adb @("shell", "run-as", $packageId, "id") | Out-Null
try {
    Write-Host "Pushing PMTiles to device temporary storage..."
    Invoke-Adb @("push", $sourceFile, $remoteTemporary) | Write-Host
    Invoke-Adb @("shell", "run-as", $packageId, "mkdir", "-p", "files/maps") | Out-Null
    Invoke-Adb @("shell", "run-as", $packageId, "cp", $remoteTemporary, "$appDestination.pending") | Out-Null
    Invoke-Adb @("shell", "run-as", $packageId, "mv", "$appDestination.pending", $appDestination) | Out-Null

    $localSize = $sourceSize
    $remoteSizeText = (Invoke-Adb @("shell", "run-as", $packageId, "stat", "-c", "%s", $appDestination) | Out-String).Trim()
    $remoteSize = 0L
    if (-not [long]::TryParse($remoteSizeText, [ref]$remoteSize)) {
        throw "Could not verify installed PMTiles size; stat returned '$remoteSizeText'."
    }
    if ($remoteSize -ne $localSize) {
        throw "Installed PMTiles size mismatch: local=$localSize, app=$remoteSize."
    }

    Invoke-Adb @("shell", "run-as", $packageId, "ls", "-lh", $appDestination) | Write-Host
    Write-Host "Installed and verified $appDestination ($remoteSize bytes)."
} finally {
    Invoke-Adb @("shell", "rm", "-f", $remoteTemporary) | Out-Null
}
