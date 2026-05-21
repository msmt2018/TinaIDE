[CmdletBinding()]
param(
    [string]$ApkPath,
    [ValidateSet("debug", "release")]
    [string]$Variant = "debug",
    [ValidateSet("arm64", "x86_64")]
    [string]$Abi = "arm64",
    [int]$TopLargeFiles = 20,
    [double]$LargeFileThresholdMB = 1
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

Add-Type -AssemblyName System.IO.Compression.FileSystem

function Resolve-RepoRoot {
    $root = Resolve-Path (Join-Path $PSScriptRoot "..")
    return $root.Path
}

function Resolve-ApkPath {
    param(
        [string]$ApkPath,
        [string]$RepoRoot,
        [string]$Variant,
        [string]$Abi
    )

    if ($ApkPath) {
        $full = $ApkPath
        if (-not [System.IO.Path]::IsPathRooted($full)) {
            $full = Join-Path $RepoRoot $full
        }
        return $full
    }

    $abiDir = if ($Abi -eq "arm64") { "arm64" } else { "x86_64" }
    $searchRoot = Join-Path $RepoRoot (Join-Path "app/build/outputs/apk" (Join-Path $abiDir $Variant))
    if (-not (Test-Path $searchRoot)) {
        throw "APK output directory not found: $searchRoot (run build first, or pass -ApkPath)"
    }

    $apk = Get-ChildItem -Path $searchRoot -Recurse -Filter "*.apk" -File |
        Where-Object { $_.Name -notlike "*-unsigned.apk" -and $_.Name -notlike "*.dm" } |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1

    if (-not $apk) {
        throw "No APK found under: $searchRoot (pass -ApkPath to specify)"
    }

    return $apk.FullName
}

function Format-MiB([long]$bytes) {
    return ("{0:N2} MiB" -f ($bytes / 1MB))
}

function Write-Section([string]$title) {
    Write-Host ""
    Write-Host "=== $title ===" -ForegroundColor Yellow
}

$repoRoot = Resolve-RepoRoot
$apkFullPath = Resolve-ApkPath -ApkPath $ApkPath -RepoRoot $repoRoot -Variant $Variant -Abi $Abi

if (-not (Test-Path $apkFullPath)) {
    Write-Host "APK not found: $apkFullPath" -ForegroundColor Red
    exit 1
}

$apkItem = Get-Item $apkFullPath
Write-Host "Analyzing APK: $($apkItem.FullName)" -ForegroundColor Cyan
Write-Host ("Total APK size: {0}" -f (Format-MiB $apkItem.Length)) -ForegroundColor White

$zip = [System.IO.Compression.ZipFile]::OpenRead($apkItem.FullName)
try {
    $entries = $zip.Entries

    Write-Section "Size by Category"
    $cats = @{}
    foreach ($e in $entries) {
        $cat = "Other"
        if ($e.FullName -like "*.dex") { $cat = "DEX" }
        elseif ($e.FullName -like "lib/*") { $cat = "Native-SO" }
        elseif ($e.FullName -like "assets/*") { $cat = "Assets" }
        elseif ($e.FullName -like "res/*") { $cat = "Resources" }
        elseif ($e.FullName -like "META-INF/*") { $cat = "META-INF" }

        if (-not $cats.ContainsKey($cat)) { $cats[$cat] = 0L }
        $cats[$cat] += [int64]$e.Length
    }

    $cats.GetEnumerator() | Sort-Object Value -Descending | ForEach-Object {
        Write-Host ("  {0,-12} {1,10}" -f $_.Key, (Format-MiB $_.Value))
    }

    Write-Section "DEX Files"
    $dex = $entries | Where-Object { $_.FullName -like "*.dex" } | Sort-Object Length -Descending
    if (-not $dex) {
        Write-Host "  (none)"
    } else {
        $dex | ForEach-Object {
            Write-Host ("  {0,-25} {1,10}" -f $_.FullName, (Format-MiB $_.Length))
        }
    }

    $thresholdBytes = [long]($LargeFileThresholdMB * 1MB)
    Write-Section ("Large Files (>{0} MiB, top {1})" -f $LargeFileThresholdMB, $TopLargeFiles)
    $large = $entries |
        Where-Object { $_.Length -gt $thresholdBytes } |
        Sort-Object Length -Descending |
        Select-Object -First $TopLargeFiles

    if (-not $large) {
        Write-Host "  (none)"
    } else {
        $large | ForEach-Object {
            Write-Host ("  {0,-65} {1,10}" -f $_.FullName, (Format-MiB $_.Length))
        }
    }

    Write-Section "Native Libraries"
    $libs = $entries | Where-Object { $_.FullName -like "lib/*" } | Sort-Object Length -Descending
    if (-not $libs) {
        Write-Host "  (none)"
    } else {
        $libs | ForEach-Object {
            Write-Host ("  {0,-65} {1,10:N0} KiB" -f $_.FullName, ($_.Length / 1KB))
        }
    }
} finally {
    $zip.Dispose()
}
