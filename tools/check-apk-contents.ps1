[CmdletBinding()]
param(
    [string]$ApkPath,
    [ValidateSet("debug", "release")]
    [string]$Variant = "debug",
    [ValidateSet("arm64", "x86_64")]
    [string]$Abi = "arm64",
    [string[]]$Find = @()
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

function Print-PrefixEntries {
    param(
        [System.Collections.IEnumerable]$Entries,
        [string]$Prefix
    )

    $list = $Entries | Where-Object { $_.FullName -like "$Prefix*" } | Sort-Object Length -Descending
    if (-not $list) {
        Write-Host "  (none)" -ForegroundColor DarkGray
        return
    }

    $list | ForEach-Object {
        Write-Host ("  {0,-70} {1,10}" -f $_.FullName, (Format-MiB $_.Length))
    }
}

$repoRoot = Resolve-RepoRoot
$apkFullPath = Resolve-ApkPath -ApkPath $ApkPath -RepoRoot $repoRoot -Variant $Variant -Abi $Abi

if (-not (Test-Path $apkFullPath)) {
    Write-Host "APK not found: $apkFullPath" -ForegroundColor Red
    exit 1
}

$apkItem = Get-Item $apkFullPath
Write-Host "Checking APK: $($apkItem.FullName)" -ForegroundColor Cyan
Write-Host ("Total APK size: {0}" -f (Format-MiB $apkItem.Length)) -ForegroundColor White

$zip = [System.IO.Compression.ZipFile]::OpenRead($apkItem.FullName)
try {
    $entries = $zip.Entries

    Write-Section "Assets Summary"
    $assetsEntries = $entries | Where-Object { $_.FullName -like "assets/*" }
    $assetsSize = ($assetsEntries | Measure-Object -Property Length -Sum).Sum
    Write-Host ("Total assets: {0} files, {1}" -f $assetsEntries.Count, (Format-MiB $assetsSize)) -ForegroundColor White

    Write-Section "Assets: tina-toolchain"
    Print-PrefixEntries -Entries $entries -Prefix "assets/tina-toolchain/"

    Write-Section "Assets: android-sysroot"
    Print-PrefixEntries -Entries $entries -Prefix "assets/android-sysroot/"

    Write-Section "Assets: proot"
    Print-PrefixEntries -Entries $entries -Prefix "assets/proot/"

    Write-Section "Native Libraries Summary"
    $libEntries = $entries | Where-Object { $_.FullName -like "lib/*" }
    $libSize = ($libEntries | Measure-Object -Property Length -Sum).Sum
    Write-Host ("Total libs: {0} files, {1}" -f $libEntries.Count, (Format-MiB $libSize)) -ForegroundColor White

    $abiLibDir = if ($Abi -eq "arm64") { "arm64-v8a" } else { "x86_64" }
    Write-Host ("ABI libs: lib/{0}/*" -f $abiLibDir) -ForegroundColor DarkGray
    $entries | Where-Object { $_.FullName -like ("lib/{0}/*" -f $abiLibDir) } | Sort-Object Length -Descending | ForEach-Object {
        Write-Host ("  {0,-70} {1,10:N0} KiB" -f $_.FullName, ($_.Length / 1KB))
    }

    if ($Find -and $Find.Count -gt 0) {
        Write-Section "Search"
        foreach ($p in $Find) {
            if ([string]::IsNullOrWhiteSpace($p)) { continue }
            Write-Host ("Pattern: {0}" -f $p) -ForegroundColor Green
            $matches = $entries | Where-Object { $_.FullName -match ([regex]::Escape($p)) } | Sort-Object Length -Descending
            if (-not $matches) {
                Write-Host "  (no matches)" -ForegroundColor DarkGray
                continue
            }
            $matches | ForEach-Object {
                Write-Host ("  {0,-70} {1,10}" -f $_.FullName, (Format-MiB $_.Length))
            }
        }
    }
} finally {
    $zip.Dispose()
}
