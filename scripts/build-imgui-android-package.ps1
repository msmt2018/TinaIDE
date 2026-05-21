param(
    [string]$ImGuiSourceDir = "",
    [string]$AndroidSdkDir = "",
    [string]$AndroidNdkDir = "",
    [string[]]$Abis = @("arm64-v8a"),
    [int]$ApiLevel = 21,
    [string]$AndroidStl = "c++_static",
    [string]$PackageId = "imgui-android-gl3",
    [string]$LibraryName = "imgui_android_gl3",
    [string]$PackageVersion = "",
    [int]$PackageRevision = 1,
    [string]$OutputDir = "",
    [switch]$IncludeVersionInFileName,
    [switch]$KeepWorkDir
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

function Resolve-ExistingPath {
    param(
        [Parameter(Mandatory = $true)]
        [string]$PathValue,
        [Parameter(Mandatory = $true)]
        [string]$Description
    )

    if (-not (Test-Path $PathValue)) {
        throw "$Description not found: $PathValue"
    }

    return (Resolve-Path $PathValue).Path
}

function Resolve-RepoRoot {
    return (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
}

function Resolve-AndroidSdkDir {
    param([string]$Override)

    if ($Override) {
        return Resolve-ExistingPath -PathValue $Override -Description "Android SDK directory"
    }

    $candidates = @(
        $env:ANDROID_SDK_ROOT,
        $env:ANDROID_HOME,
        (Join-Path $env:LOCALAPPDATA "Android\Sdk")
    ) | Where-Object { $_ }

    foreach ($candidate in $candidates) {
        if (Test-Path $candidate) {
            return (Resolve-Path $candidate).Path
        }
    }

    throw "Android SDK directory not found. Pass -AndroidSdkDir explicitly."
}

function Resolve-AndroidNdkDir {
    param(
        [string]$Override,
        [string]$SdkDir
    )

    if ($Override) {
        return Resolve-ExistingPath -PathValue $Override -Description "Android NDK directory"
    }

    $ndkRoot = Join-Path $SdkDir "ndk"
    if (-not (Test-Path $ndkRoot)) {
        throw "Android NDK root not found under SDK: $ndkRoot"
    }

    $latest = Get-ChildItem $ndkRoot -Directory |
        Sort-Object Name -Descending |
        Select-Object -First 1

    if ($null -eq $latest) {
        throw "No Android NDK versions found under: $ndkRoot"
    }

    return $latest.FullName
}

function Resolve-AndroidCMakeBinDir {
    param([string]$SdkDir)

    $cmakeRoot = Join-Path $SdkDir "cmake"
    if (-not (Test-Path $cmakeRoot)) {
        throw "Android SDK CMake directory not found: $cmakeRoot"
    }

    $latest = Get-ChildItem $cmakeRoot -Directory |
        Sort-Object Name -Descending |
        Select-Object -First 1

    if ($null -eq $latest) {
        throw "No Android SDK CMake versions found under: $cmakeRoot"
    }

    $binDir = Join-Path $latest.FullName "bin"
    if (-not (Test-Path (Join-Path $binDir "cmake.exe"))) {
        throw "cmake.exe not found under: $binDir"
    }
    if (-not (Test-Path (Join-Path $binDir "ninja.exe"))) {
        throw "ninja.exe not found under: $binDir"
    }

    return $binDir
}

function Resolve-ImGuiSourceDir {
    param(
        [string]$Override,
        [string]$RepoRoot
    )

    if ($Override) {
        return Resolve-ExistingPath -PathValue $Override -Description "ImGui source directory"
    }

    $defaultDir = Join-Path $RepoRoot "temp\imgui-v1.92.6"
    return Resolve-ExistingPath -PathValue $defaultDir -Description "ImGui source directory"
}

function Get-ImGuiVersionInfo {
    param([string]$SourceDir)

    $tag = ""
    try {
        $tag = (& git -C $SourceDir describe --tags --abbrev=0).Trim()
    } catch {
        $tag = ""
    }

    if (-not $tag) {
        throw "Failed to read git tag from ImGui source directory: $SourceDir"
    }

    $version = $tag.TrimStart("v")
    $commit = (& git -C $SourceDir rev-parse HEAD).Trim()

    return @{
        Tag = $tag
        Version = $version
        Commit = $commit
    }
}

function Write-PackageJson {
    param(
        [string]$PathValue,
        [string]$PackageIdValue,
        [string]$PackageVersionValue,
        [int]$PackageRevisionValue,
        [string]$UpstreamNameValue,
        [string]$UpstreamVersionValue,
        [string]$UpstreamTagValue,
        [string]$UpstreamCommitValue,
        [string[]]$AbiValues
    )

    $metadata = [ordered]@{
        id = $PackageIdValue
        name = "Dear ImGui Android OpenGL3"
        version = $PackageVersionValue
        packageRevision = $PackageRevisionValue
        upstreamName = $UpstreamNameValue
        upstreamVersion = $UpstreamVersionValue
        upstreamTag = $UpstreamTagValue
        upstreamCommit = $UpstreamCommitValue
        description = "Dear ImGui shared library with official Android and OpenGL ES3 backends"
        platform = "android"
        installType = "download"
        category = "library"
        homepage = "https://github.com/ocornut/imgui"
        license = "MIT"
        files = [ordered]@{
            include = "include"
            lib = "lib"
            pkgconfig = "pkgconfig/$PackageIdValue.pc"
        }
        abis = $AbiValues
    }

    $json = $metadata | ConvertTo-Json -Depth 6
    [System.IO.File]::WriteAllText($PathValue, $json + [Environment]::NewLine, [System.Text.UTF8Encoding]::new($false))
}

function Write-PkgConfigFile {
    param(
        [string]$PathValue,
        [string]$VersionValue,
        [string]$LibraryNameValue,
        [string]$AbiValue,
        [string]$PrefixExpression
    )

    $content = @"
prefix=$PrefixExpression
exec_prefix=`${prefix}
libdir=`${prefix}/lib/$AbiValue
includedir=`${prefix}/include

Name: Dear ImGui Android OpenGL3
Description: Dear ImGui shared library with official Android and OpenGL ES3 backends
Version: $VersionValue
Libs: -L`${libdir} -l$LibraryNameValue
Libs.private: -landroid -lEGL -lGLESv3 -llog
Cflags: -I`${includedir}
"@

    [System.IO.File]::WriteAllText($PathValue, $content, [System.Text.UTF8Encoding]::new($false))
}

function Copy-Headers {
    param(
        [string]$SourceDir,
        [string]$IncludeDir
    )

    $rootHeaders = @(
        "imconfig.h",
        "imgui.h",
        "imgui_internal.h",
        "imstb_rectpack.h",
        "imstb_textedit.h",
        "imstb_truetype.h"
    )

    New-Item -ItemType Directory -Force -Path $IncludeDir | Out-Null
    $backendIncludeDir = Join-Path $IncludeDir "backends"
    New-Item -ItemType Directory -Force -Path $backendIncludeDir | Out-Null

    foreach ($header in $rootHeaders) {
        Copy-Item (Join-Path $SourceDir $header) (Join-Path $IncludeDir $header) -Force
    }

    Copy-Item (Join-Path $SourceDir "backends\imgui_impl_android.h") (Join-Path $backendIncludeDir "imgui_impl_android.h") -Force
    Copy-Item (Join-Path $SourceDir "backends\imgui_impl_opengl3.h") (Join-Path $backendIncludeDir "imgui_impl_opengl3.h") -Force
    Copy-Item (Join-Path $SourceDir "LICENSE.txt") (Join-Path (Split-Path $IncludeDir -Parent) "LICENSE.txt") -Force
}

$repoRoot = Resolve-RepoRoot
$sdkDir = Resolve-AndroidSdkDir -Override $AndroidSdkDir
$ndkDir = Resolve-AndroidNdkDir -Override $AndroidNdkDir -SdkDir $sdkDir
$cmakeBinDir = Resolve-AndroidCMakeBinDir -SdkDir $sdkDir
$imguiSourceDir = Resolve-ImGuiSourceDir -Override $ImGuiSourceDir -RepoRoot $repoRoot
$versionInfo = Get-ImGuiVersionInfo -SourceDir $imguiSourceDir
$resolvedPackageVersion = if ($PackageVersion) {
    $PackageVersion
} elseif ($PackageRevision -le 1) {
    $versionInfo.Version
} else {
    "$($versionInfo.Version)-pkg.$PackageRevision"
}

$cmakeExe = Join-Path $cmakeBinDir "cmake.exe"
$ninjaExe = Join-Path $cmakeBinDir "ninja.exe"
$toolchainFile = Join-Path $ndkDir "build\cmake\android.toolchain.cmake"
$cMakeProjectDir = Join-Path $repoRoot "scripts\cmake\imgui-android-gl3"

if (-not (Test-Path $toolchainFile)) {
    throw "Android CMake toolchain not found: $toolchainFile"
}

if (-not $OutputDir) {
    $OutputDir = Join-Path $repoRoot "temp\build-output\imgui-android-gl3"
}

$outputDirResolved = $OutputDir
New-Item -ItemType Directory -Force -Path $outputDirResolved | Out-Null
$outputDirResolved = (Resolve-Path $outputDirResolved).Path

$workRoot = Join-Path $repoRoot "temp\_imgui_package_work\$PackageId-$($versionInfo.Version)"
$packageRoot = Join-Path $workRoot "package"
$artifactBaseName = if ($IncludeVersionInFileName) {
    "$PackageId-$resolvedPackageVersion"
} else {
    $PackageId
}
$zipPath = Join-Path $outputDirResolved "$artifactBaseName.zip"
$shaPath = Join-Path $outputDirResolved "$artifactBaseName.sha256.txt"

if (Test-Path $workRoot) {
    Remove-Item $workRoot -Recurse -Force
}
New-Item -ItemType Directory -Force -Path $packageRoot | Out-Null

$includeDir = Join-Path $packageRoot "include"
Copy-Headers -SourceDir $imguiSourceDir -IncludeDir $includeDir

$rootPkgConfigDir = Join-Path $packageRoot "pkgconfig"
New-Item -ItemType Directory -Force -Path $rootPkgConfigDir | Out-Null

foreach ($abi in $Abis) {
    $abiBuildDir = Join-Path $workRoot "build\$abi"
    $abiLibDir = Join-Path $packageRoot "lib\$abi"
    $abiPkgConfigDir = Join-Path $abiLibDir "pkgconfig"
    New-Item -ItemType Directory -Force -Path $abiLibDir | Out-Null
    New-Item -ItemType Directory -Force -Path $abiPkgConfigDir | Out-Null

    & $cmakeExe `
        -S $cMakeProjectDir `
        -B $abiBuildDir `
        -G Ninja `
        "-DCMAKE_MAKE_PROGRAM=$ninjaExe" `
        "-DCMAKE_TOOLCHAIN_FILE=$toolchainFile" `
        "-DANDROID_ABI=$abi" `
        "-DANDROID_PLATFORM=android-$ApiLevel" `
        "-DANDROID_STL=$AndroidStl" `
        "-DCMAKE_BUILD_TYPE=Release" `
        "-DIMGUI_SOURCE_DIR=$imguiSourceDir"

    if ($LASTEXITCODE -ne 0) {
        throw "CMake configure failed for ABI: $abi"
    }

    & $cmakeExe --build $abiBuildDir --config Release
    if ($LASTEXITCODE -ne 0) {
        throw "CMake build failed for ABI: $abi"
    }

    $builtLibrary = Join-Path $abiBuildDir "lib$LibraryName.so"
    if (-not (Test-Path $builtLibrary)) {
        throw "Built library not found for ABI ${abi}: $builtLibrary"
    }

    Copy-Item $builtLibrary (Join-Path $abiLibDir "lib$LibraryName.so") -Force

    $abiPcPath = Join-Path $abiPkgConfigDir "$PackageId.pc"
    Write-PkgConfigFile -PathValue $abiPcPath -VersionValue $versionInfo.Version -LibraryNameValue $LibraryName -AbiValue $abi -PrefixExpression '`${pcfiledir}/../../..'

    if ($abi -eq $Abis[0]) {
        Write-PkgConfigFile -PathValue (Join-Path $rootPkgConfigDir "$PackageId.pc") -VersionValue $versionInfo.Version -LibraryNameValue $LibraryName -AbiValue $abi -PrefixExpression '`${pcfiledir}/..'
    }
}

Write-PackageJson `
    -PathValue (Join-Path $packageRoot "package.json") `
    -PackageIdValue $PackageId `
    -PackageVersionValue $resolvedPackageVersion `
    -PackageRevisionValue $PackageRevision `
    -UpstreamNameValue "Dear ImGui" `
    -UpstreamVersionValue $versionInfo.Version `
    -UpstreamTagValue $versionInfo.Tag `
    -UpstreamCommitValue $versionInfo.Commit `
    -AbiValues $Abis

$buildInfo = @"
package_id=$PackageId
package_version=$resolvedPackageVersion
package_revision=$PackageRevision
library_name=$LibraryName
imgui_tag=$($versionInfo.Tag)
imgui_commit=$($versionInfo.Commit)
imgui_version=$($versionInfo.Version)
abis=$($Abis -join ',')
android_api_level=$ApiLevel
android_stl=$AndroidStl
android_ndk=$ndkDir
"@
[System.IO.File]::WriteAllText((Join-Path $packageRoot "BUILD-INFO.txt"), $buildInfo, [System.Text.UTF8Encoding]::new($false))

if (Test-Path $zipPath) {
    Remove-Item $zipPath -Force
}

Compress-Archive -Path (Join-Path $packageRoot "*") -DestinationPath $zipPath -CompressionLevel Optimal

$hash = Get-FileHash -Path $zipPath -Algorithm SHA256
$hashLine = "{0} *{1}" -f $hash.Hash.ToLowerInvariant(), [System.IO.Path]::GetFileName($zipPath)
[System.IO.File]::WriteAllText($shaPath, $hashLine + [Environment]::NewLine, [System.Text.UTF8Encoding]::new($false))

Write-Host "Built package: $zipPath"
Write-Host "Package version: $resolvedPackageVersion"
Write-Host "Upstream version: $($versionInfo.Version) ($($versionInfo.Tag))"
Write-Host "SHA256: $($hash.Hash.ToLowerInvariant())"

if (-not $KeepWorkDir) {
    Remove-Item $workRoot -Recurse -Force
}
