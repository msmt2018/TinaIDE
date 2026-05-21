<#
.SYNOPSIS
    TinaIDE Package Builder - Windows PowerShell 编排脚本
.DESCRIPTION
    构建 Android NDK 原生库（静态库/动态库）
    支持指定库、架构、链接类型
.PARAMETER Library
    要构建的库名称：zlib, openssl, curl, libssh2, libgit2, pcre2, sdl3, all
.PARAMETER Arch
    目标架构：arm64-v8a, armeabi-v7a, x86_64, x86, all
.PARAMETER LinkType
    链接类型：static, shared, all
.PARAMETER Mode
    构建模式：incremental (默认), rebuild, clean
.PARAMETER List
    列出可用的库和已构建的包
.EXAMPLE
    .\build-pkg.ps1 -Library zlib -Arch arm64-v8a -LinkType static
.EXAMPLE
    .\build-pkg.ps1 -Library all -Arch all -LinkType static
.EXAMPLE
    .\build-pkg.ps1 -List
#>

param(
    [Parameter(Position = 0)]
    [ValidateSet("zlib", "openssl", "curl", "libssh2", "libgit2", "pcre2", "sdl3", "all")]
    [string]$Library = "all",

    [Parameter(Position = 1)]
    [ValidateSet("arm64-v8a", "armeabi-v7a", "x86_64", "x86", "all")]
    [string]$Arch = "arm64-v8a",

    [Parameter(Position = 2)]
    [ValidateSet("static", "shared", "all")]
    [string]$LinkType = "static",

    [ValidateSet("incremental", "rebuild", "clean")]
    [string]$Mode = "incremental",

    [switch]$List,
    [switch]$Help
)

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path

# ===== 配置 =====
$IMAGE_NAME = "tinaide-pkg-builder"
$CONTAINER_NAME = "tinaide-pkg-build"
$SOURCE_VOLUME = "tinaide-pkg-source"
$OUTPUT_DIR = Join-Path $ScriptDir "output"

# 所有架构
$ALL_ARCHS = @("arm64-v8a", "armeabi-v7a", "x86_64", "x86")

# 所有库 (按依赖顺序)
$ALL_LIBS = @("zlib", "openssl", "pcre2", "libssh2", "curl", "libgit2", "sdl3")

# ===== 辅助函数 =====
function Write-Info { param($Message) Write-Host "[INFO] $Message" -ForegroundColor Cyan }
function Write-Success { param($Message) Write-Host "[SUCCESS] $Message" -ForegroundColor Green }
function Write-Warn { param($Message) Write-Host "[WARN] $Message" -ForegroundColor Yellow }
function Write-Err { param($Message) Write-Host "[ERROR] $Message" -ForegroundColor Red }

function Show-Help {
    Get-Help $MyInvocation.PSCommandPath -Detailed
    Write-Host ""
    Write-Host "可用的库:" -ForegroundColor Yellow
    $ALL_LIBS | ForEach-Object { Write-Host "  - $_" }
    Write-Host ""
    Write-Host "支持的架构:" -ForegroundColor Yellow
    $ALL_ARCHS | ForEach-Object { Write-Host "  - $_" }
    Write-Host ""
    Write-Host "依赖关系:" -ForegroundColor Yellow
    Write-Host "  libssh2  -> openssl, zlib"
    Write-Host "  libgit2  -> openssl, libssh2, zlib"
    Write-Host "  curl     -> openssl (可选)"
    Write-Host "  sdl3     -> 无依赖 (独立库)"
}

function Show-List {
    Write-Info "可用的库:"
    $ALL_LIBS | ForEach-Object { Write-Host "  - $_" }

    Write-Host ""
    Write-Info "已构建的包:"

    if (Test-Path $OUTPUT_DIR) {
        Get-ChildItem -Path $OUTPUT_DIR -Recurse -Filter "*.tar.xz" | ForEach-Object {
            $size = "{0:N2} KB" -f ($_.Length / 1KB)
            $relativePath = $_.FullName.Replace($OUTPUT_DIR, "").TrimStart("\", "/")
            Write-Host "  $relativePath ($size)"
        }
    }
    else {
        Write-Host "  (无)"
    }
}

# ===== Docker 操作 =====
function Ensure-DockerImage {
    Write-Info "检查 Docker 镜像..."

    $imageExists = docker images -q $IMAGE_NAME 2>$null

    if ($Mode -eq "rebuild" -or -not $imageExists) {
        Write-Info "构建 Docker 镜像 $IMAGE_NAME ..."
        docker build -t $IMAGE_NAME $ScriptDir

        if ($LASTEXITCODE -ne 0) {
            Write-Err "Docker 镜像构建失败"
            exit 1
        }
        Write-Success "Docker 镜像构建完成"
    }
    else {
        Write-Info "使用已有镜像: $IMAGE_NAME"
    }
}

function Ensure-SourceVolume {
    $volumeExists = docker volume ls -q --filter "name=$SOURCE_VOLUME"

    if ($Mode -eq "clean" -and $volumeExists) {
        Write-Info "清理源码 volume..."
        docker volume rm $SOURCE_VOLUME 2>$null
    }

    if (-not (docker volume ls -q --filter "name=$SOURCE_VOLUME")) {
        Write-Info "创建源码 volume: $SOURCE_VOLUME"
        docker volume create $SOURCE_VOLUME
    }
}

function Run-Build {
    param(
        [string]$Lib,
        [string]$Architecture,
        [string]$Link
    )

    Write-Info "=========================================="
    Write-Info "构建: $Lib ($Architecture, $Link)"
    Write-Info "=========================================="

    # 确保输出目录存在
    $libOutputDir = Join-Path $OUTPUT_DIR $Lib $Architecture
    if (-not (Test-Path $libOutputDir)) {
        New-Item -ItemType Directory -Path $libOutputDir -Force | Out-Null
    }

    # 运行容器
    $cmd = @(
        "docker", "run", "--rm",
        "-v", "${SOURCE_VOLUME}:/build/src",
        "-v", "${OUTPUT_DIR}:/output",
        $IMAGE_NAME,
        "/build/build.sh", $Lib, $Architecture, $Link
    )

    Write-Info "执行: $($cmd -join ' ')"

    & $cmd[0] $cmd[1..($cmd.Length - 1)]

    if ($LASTEXITCODE -ne 0) {
        Write-Err "构建失败: $Lib ($Architecture, $Link)"
        return $false
    }

    Write-Success "构建完成: $Lib ($Architecture, $Link)"
    return $true
}

# ===== 主程序 =====
function Main {
    if ($Help) {
        Show-Help
        return
    }

    if ($List) {
        Show-List
        return
    }

    Write-Info "TinaIDE Package Builder"
    Write-Info "======================="
    Write-Info "库: $Library"
    Write-Info "架构: $Arch"
    Write-Info "链接类型: $LinkType"
    Write-Info "模式: $Mode"

    # 清理模式
    if ($Mode -eq "clean") {
        Write-Info "执行清理..."

        # 删除输出目录
        if (Test-Path $OUTPUT_DIR) {
            Remove-Item -Recurse -Force $OUTPUT_DIR
            Write-Info "已删除输出目录"
        }

        # 删除 Docker 资源
        docker rm -f $CONTAINER_NAME 2>$null
        docker volume rm $SOURCE_VOLUME 2>$null
        docker rmi $IMAGE_NAME 2>$null

        Write-Success "清理完成"
        return
    }

    # 确保 Docker 环境
    Ensure-DockerImage
    Ensure-SourceVolume

    # 确保输出目录
    if (-not (Test-Path $OUTPUT_DIR)) {
        New-Item -ItemType Directory -Path $OUTPUT_DIR -Force | Out-Null
    }

    # 展开参数
    $targetLibs = if ($Library -eq "all") { $ALL_LIBS } else { @($Library) }
    $targetArchs = if ($Arch -eq "all") { $ALL_ARCHS } else { @($Arch) }
    $targetLinks = if ($LinkType -eq "all") { @("static", "shared") } else { @($LinkType) }

    # 计数
    $total = $targetLibs.Count * $targetArchs.Count * $targetLinks.Count
    $current = 0
    $success = 0
    $failed = 0

    $startTime = Get-Date

    # 构建循环
    foreach ($lib in $targetLibs) {
        foreach ($arch in $targetArchs) {
            foreach ($link in $targetLinks) {
                $current++
                Write-Info "进度: $current / $total"

                if (Run-Build -Lib $lib -Architecture $arch -Link $link) {
                    $success++
                }
                else {
                    $failed++
                }
            }
        }
    }

    $elapsed = (Get-Date) - $startTime

    Write-Host ""
    Write-Info "=========================================="
    Write-Info "构建完成"
    Write-Info "=========================================="
    Write-Info "成功: $success"
    Write-Info "失败: $failed"
    Write-Info "耗时: $($elapsed.ToString('hh\:mm\:ss'))"
    Write-Info "输出目录: $OUTPUT_DIR"

    # 列出生成的包
    Write-Host ""
    Show-List
}

Main
