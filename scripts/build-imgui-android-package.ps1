param(
    [string]$ImGuiSourceDir = "",
    [string]$PackageId = "imgui",
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
        [Parameter(Mandatory = $true)][string]$PathValue,
        [Parameter(Mandatory = $true)][string]$Description
    )

    if (-not (Test-Path -LiteralPath $PathValue)) {
        throw "$Description not found: $PathValue"
    }

    return (Resolve-Path -LiteralPath $PathValue).Path
}

function Resolve-RepoRoot {
    return (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
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

function Copy-RequiredFile {
    param(
        [Parameter(Mandatory = $true)][string]$SourcePath,
        [Parameter(Mandatory = $true)][string]$DestinationPath
    )

    if (-not (Test-Path -LiteralPath $SourcePath -PathType Leaf)) {
        throw "Required ImGui file not found: $SourcePath"
    }

    $parent = Split-Path -Parent $DestinationPath
    if ($parent) {
        New-Item -ItemType Directory -Force -Path $parent | Out-Null
    }
    Copy-Item -LiteralPath $SourcePath -Destination $DestinationPath -Force
}

function Copy-ImGuiPackageFiles {
    param(
        [Parameter(Mandatory = $true)][string]$SourceDir,
        [Parameter(Mandatory = $true)][string]$PackageRoot
    )

    $includeDir = Join-Path $PackageRoot "include"
    $sourceOutDir = Join-Path $PackageRoot "src"
    $backendIncludeDir = Join-Path $includeDir "backends"
    $backendSourceDir = Join-Path $sourceOutDir "backends"

    New-Item -ItemType Directory -Force -Path $includeDir | Out-Null
    New-Item -ItemType Directory -Force -Path $sourceOutDir | Out-Null
    New-Item -ItemType Directory -Force -Path $backendIncludeDir | Out-Null
    New-Item -ItemType Directory -Force -Path $backendSourceDir | Out-Null

    $rootHeaders = @(
        "imconfig.h",
        "imgui.h",
        "imgui_internal.h",
        "imstb_rectpack.h",
        "imstb_textedit.h",
        "imstb_truetype.h"
    )
    foreach ($header in $rootHeaders) {
        Copy-RequiredFile `
            -SourcePath (Join-Path $SourceDir $header) `
            -DestinationPath (Join-Path $includeDir $header)
    }

    $rootSources = @(
        "imgui.cpp",
        "imgui_demo.cpp",
        "imgui_draw.cpp",
        "imgui_tables.cpp",
        "imgui_widgets.cpp"
    )
    foreach ($source in $rootSources) {
        Copy-RequiredFile `
            -SourcePath (Join-Path $SourceDir $source) `
            -DestinationPath (Join-Path $sourceOutDir $source)
    }

    $backendFiles = @(
        "imgui_impl_android.h",
        "imgui_impl_opengl3.h",
        "imgui_impl_android.cpp",
        "imgui_impl_opengl3.cpp"
    )
    foreach ($file in $backendFiles) {
        $destinationDir = if ($file.EndsWith(".h")) { $backendIncludeDir } else { $backendSourceDir }
        Copy-RequiredFile `
            -SourcePath (Join-Path $SourceDir "backends\$file") `
            -DestinationPath (Join-Path $destinationDir $file)
    }

    Copy-RequiredFile `
        -SourcePath (Join-Path $SourceDir "LICENSE.txt") `
        -DestinationPath (Join-Path $PackageRoot "LICENSE.txt")
}

function Write-PackageJson {
    param(
        [Parameter(Mandatory = $true)][string]$PathValue,
        [Parameter(Mandatory = $true)][string]$PackageIdValue,
        [Parameter(Mandatory = $true)][string]$PackageVersionValue,
        [Parameter(Mandatory = $true)][int]$PackageRevisionValue,
        [Parameter(Mandatory = $true)][string]$UpstreamVersionValue,
        [Parameter(Mandatory = $true)][string]$UpstreamTagValue,
        [Parameter(Mandatory = $true)][string]$UpstreamCommitValue
    )

    $metadata = [ordered]@{
        id = $PackageIdValue
        name = "Dear ImGui"
        version = $PackageVersionValue
        packageRevision = $PackageRevisionValue
        upstreamName = "Dear ImGui"
        upstreamVersion = $UpstreamVersionValue
        upstreamTag = $UpstreamTagValue
        upstreamCommit = $UpstreamCommitValue
        description = "Dear ImGui source and headers with Android and OpenGL ES3 backends"
        platform = "android"
        artifactType = "source"
        installType = "download"
        category = "library"
        homepage = "https://github.com/ocornut/imgui"
        license = "MIT"
        files = [ordered]@{
            include = "include"
            source = "src"
            pkgconfig = "pkgconfig/imgui.pc"
        }
    }

    $json = $metadata | ConvertTo-Json -Depth 8
    [System.IO.File]::WriteAllText($PathValue, $json + [Environment]::NewLine, [System.Text.UTF8Encoding]::new($false))
}

function Write-PkgConfigFile {
    param(
        [Parameter(Mandatory = $true)][string]$PathValue,
        [Parameter(Mandatory = $true)][string]$VersionValue
    )

    $content = @"
prefix=`${pcfiledir}/..
includedir=`${prefix}/include
sourcedir=`${prefix}/src

Name: Dear ImGui
Description: Dear ImGui source and headers with Android and OpenGL ES3 backends
Version: $VersionValue
Cflags: -I`${includedir}
"@

    [System.IO.File]::WriteAllText($PathValue, $content, [System.Text.UTF8Encoding]::new($false))
}

$repoRoot = Resolve-RepoRoot
$imguiSourceDir = Resolve-ImGuiSourceDir -Override $ImGuiSourceDir -RepoRoot $repoRoot
$versionInfo = Get-ImGuiVersionInfo -SourceDir $imguiSourceDir
$resolvedPackageVersion = if ($PackageVersion) {
    $PackageVersion
} elseif ($PackageRevision -le 1) {
    $versionInfo.Version
} else {
    "$($versionInfo.Version)-pkg.$PackageRevision"
}

if (-not $OutputDir) {
    $OutputDir = Join-Path $repoRoot "temp\build-output\imgui"
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
$archivePath = Join-Path $outputDirResolved "$artifactBaseName.tar.xz"
$shaPath = Join-Path $outputDirResolved "$artifactBaseName.sha256.txt"

if (Test-Path -LiteralPath $workRoot) {
    Remove-Item -LiteralPath $workRoot -Recurse -Force
}
New-Item -ItemType Directory -Force -Path $packageRoot | Out-Null

Copy-ImGuiPackageFiles -SourceDir $imguiSourceDir -PackageRoot $packageRoot

$pkgConfigDir = Join-Path $packageRoot "pkgconfig"
New-Item -ItemType Directory -Force -Path $pkgConfigDir | Out-Null
Write-PkgConfigFile -PathValue (Join-Path $pkgConfigDir "imgui.pc") -VersionValue $versionInfo.Version

Write-PackageJson `
    -PathValue (Join-Path $packageRoot "package.json") `
    -PackageIdValue $PackageId `
    -PackageVersionValue $resolvedPackageVersion `
    -PackageRevisionValue $PackageRevision `
    -UpstreamVersionValue $versionInfo.Version `
    -UpstreamTagValue $versionInfo.Tag `
    -UpstreamCommitValue $versionInfo.Commit

$buildInfo = @"
package_id=$PackageId
package_version=$resolvedPackageVersion
package_revision=$PackageRevision
artifact_type=source
imgui_tag=$($versionInfo.Tag)
imgui_commit=$($versionInfo.Commit)
imgui_version=$($versionInfo.Version)
"@
[System.IO.File]::WriteAllText((Join-Path $packageRoot "BUILD-INFO.txt"), $buildInfo, [System.Text.UTF8Encoding]::new($false))

if (Test-Path -LiteralPath $archivePath) {
    Remove-Item -LiteralPath $archivePath -Force
}

Push-Location $packageRoot
try {
    tar -caf $archivePath *
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to create archive: $archivePath"
    }
} finally {
    Pop-Location
}

$hash = Get-FileHash -LiteralPath $archivePath -Algorithm SHA256
$hashLine = "{0} *{1}" -f $hash.Hash.ToLowerInvariant(), [System.IO.Path]::GetFileName($archivePath)
[System.IO.File]::WriteAllText($shaPath, $hashLine + [Environment]::NewLine, [System.Text.UTF8Encoding]::new($false))

Write-Host "Built package: $archivePath"
Write-Host "Package version: $resolvedPackageVersion"
Write-Host "Upstream version: $($versionInfo.Version) ($($versionInfo.Tag))"
Write-Host "Artifact type: source"
Write-Host "ABI: none"
Write-Host "SHA256: $($hash.Hash.ToLowerInvariant())"

if (-not $KeepWorkDir) {
    Remove-Item -LiteralPath $workRoot -Recurse -Force
}
