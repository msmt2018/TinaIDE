param(
    [string]$ArchivePath = "",
    [string]$AndroidNdkDir = "",
    [int]$PackageRevision = 1,
    [string]$PackageVersion = ""
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

function Resolve-RepoRoot {
    return (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
}

function Resolve-ArchivePath {
    param(
        [string]$Override,
        [string]$RepoRoot
    )

    $candidate = if ($Override) {
        $Override
    } else {
        Join-Path $RepoRoot "app\src\main\assets\bundled_packages\sdl3.tar.xz"
    }

    if (-not (Test-Path $candidate)) {
        throw "SDL3 archive not found: $candidate"
    }

    return (Resolve-Path $candidate).Path
}

function Resolve-AndroidNdkDir {
    param([string]$Override)

    if ($Override) {
        if (-not (Test-Path $Override)) {
            throw "Android NDK directory not found: $Override"
        }
        return (Resolve-Path $Override).Path
    }

    $sdkDir = Join-Path $env:LOCALAPPDATA "Android\Sdk\ndk"
    if (-not (Test-Path $sdkDir)) {
        throw "Android NDK root not found: $sdkDir"
    }

    $latest = Get-ChildItem $sdkDir -Directory |
        Sort-Object Name -Descending |
        Select-Object -First 1

    if ($null -eq $latest) {
        throw "No Android NDK versions found under: $sdkDir"
    }

    return $latest.FullName
}

function Get-SdlPkgConfigVersion {
    param([string]$PkgConfigPath)

    if (-not (Test-Path $PkgConfigPath)) {
        throw "SDL3 pkg-config file not found: $PkgConfigPath"
    }

    $versionLine = Select-String -Path $PkgConfigPath -Pattern '^Version:\s*(.+)$' | Select-Object -First 1
    if ($null -eq $versionLine) {
        throw "Failed to parse SDL3 version from pkg-config: $PkgConfigPath"
    }

    return $versionLine.Matches[0].Groups[1].Value.Trim()
}

function Resolve-SdlAbiList {
    param(
        [string]$StagingDir,
        [string]$ReadElfPath
    )

    $libRoot = Join-Path $StagingDir "lib"
    $nestedAbiDirs = Get-ChildItem $libRoot -Directory -ErrorAction SilentlyContinue |
        Where-Object { Test-Path (Join-Path $_.FullName "libSDL3.so") } |
        Select-Object -ExpandProperty Name

    if ($nestedAbiDirs) {
        return @($nestedAbiDirs)
    }

    $rootLibrary = Join-Path $libRoot "libSDL3.so"
    if (-not (Test-Path $rootLibrary)) {
        throw "SDL3 library not found under: $libRoot"
    }

    $header = & $ReadElfPath -h $rootLibrary
    $machineLine = $header | Select-String 'Machine:\s+(.+)$' | Select-Object -First 1
    if ($null -eq $machineLine) {
        throw "Failed to detect SDL3 ABI from ELF header: $rootLibrary"
    }

    $machine = $machineLine.Matches[0].Groups[1].Value.Trim()
    switch -Regex ($machine) {
        '^AArch64$' { return @('arm64-v8a') }
        '^ARM$' { return @('armeabi-v7a') }
        'X86-64|Advanced Micro Devices X86-64' { return @('x86_64') }
        'Intel 80386' { return @('x86') }
        default { throw "Unsupported SDL3 ELF machine type: $machine" }
    }
}

function Write-SdlPackageJson {
    param(
        [string]$OutputPath,
        [string]$ResolvedPackageVersion,
        [int]$ResolvedPackageRevision,
        [string]$UpstreamVersion,
        [string[]]$AbiValues
    )

    $metadata = [ordered]@{
        id = "sdl3"
        name = "SDL3"
        version = $ResolvedPackageVersion
        packageRevision = $ResolvedPackageRevision
        upstreamName = "SDL3"
        upstreamVersion = $UpstreamVersion
        description = "Simple DirectMedia Layer 3 - Cross-platform multimedia library"
        platform = "android"
        artifactType = "shared"
        installType = "download"
        category = "library"
        homepage = "https://www.libsdl.org/"
        license = "Zlib"
        files = [ordered]@{
            include = "include/SDL3"
            lib = "lib"
            pkgconfig = "lib/pkgconfig/sdl3.pc"
        }
        abis = $AbiValues
    }

    $json = $metadata | ConvertTo-Json -Depth 6
    [System.IO.File]::WriteAllText($OutputPath, $json + [Environment]::NewLine, [System.Text.UTF8Encoding]::new($false))
}

$repoRoot = Resolve-RepoRoot
$archivePath = Resolve-ArchivePath -Override $ArchivePath -RepoRoot $repoRoot
$ndkDir = Resolve-AndroidNdkDir -Override $AndroidNdkDir
$readElfPath = Join-Path $ndkDir "toolchains\llvm\prebuilt\windows-x86_64\bin\llvm-readelf.exe"
if (-not (Test-Path $readElfPath)) {
    throw "llvm-readelf.exe not found: $readElfPath"
}

$workRoot = Join-Path $repoRoot "temp\_sdl3_metadata_repair"
$stagingDir = Join-Path $workRoot "staging"
$archiveName = [System.IO.Path]::GetFileName($archivePath)
$tempArchivePath = Join-Path $workRoot $archiveName

if (Test-Path $workRoot) {
    Remove-Item $workRoot -Recurse -Force
}
New-Item -ItemType Directory -Force -Path $stagingDir | Out-Null

tar -xf $archivePath -C $stagingDir

$upstreamVersion = Get-SdlPkgConfigVersion -PkgConfigPath (Join-Path $stagingDir "lib\pkgconfig\sdl3.pc")
$resolvedPackageVersion = if ($PackageVersion) {
    $PackageVersion
} elseif ($PackageRevision -le 1) {
    $upstreamVersion
} else {
    "$upstreamVersion-pkg.$PackageRevision"
}
$abis = Resolve-SdlAbiList -StagingDir $stagingDir -ReadElfPath $readElfPath

Write-SdlPackageJson `
    -OutputPath (Join-Path $stagingDir "package.json") `
    -ResolvedPackageVersion $resolvedPackageVersion `
    -ResolvedPackageRevision $PackageRevision `
    -UpstreamVersion $upstreamVersion `
    -AbiValues $abis

$buildInfo = @"
package_id=sdl3
package_version=$resolvedPackageVersion
package_revision=$PackageRevision
upstream_name=SDL3
upstream_version=$upstreamVersion
artifact_type=shared
abis=$($abis -join ',')
source_archive=$archiveName
"@
[System.IO.File]::WriteAllText((Join-Path $stagingDir "BUILD-INFO.txt"), $buildInfo, [System.Text.UTF8Encoding]::new($false))

Push-Location $stagingDir
try {
    if (Test-Path $tempArchivePath) {
        Remove-Item $tempArchivePath -Force
    }
    tar -caf $tempArchivePath *
} finally {
    Pop-Location
}

Move-Item -Force $tempArchivePath $archivePath
Write-Host "Updated SDL3 bundled package: $archivePath"
Write-Host "Package version: $resolvedPackageVersion"
Write-Host "Upstream version: $upstreamVersion"
Write-Host "ABIs: $($abis -join ', ')"
