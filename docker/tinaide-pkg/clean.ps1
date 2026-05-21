<#
.SYNOPSIS
    清理 TinaIDE Package Builder 资源
.DESCRIPTION
    清理 Docker 镜像、容器、volume 和输出目录
#>

param(
    [switch]$All,      # 清理所有资源
    [switch]$Output,   # 只清理输出
    [switch]$Docker,   # 只清理 Docker 资源
    [switch]$Force     # 强制删除
)

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$OUTPUT_DIR = Join-Path $ScriptDir "output"

$IMAGE_NAME = "tinaide-pkg-builder"
$SOURCE_VOLUME = "tinaide-pkg-source"

function Write-Info { param($Message) Write-Host "[INFO] $Message" -ForegroundColor Cyan }
function Write-Success { param($Message) Write-Host "[SUCCESS] $Message" -ForegroundColor Green }

if (-not $All -and -not $Output -and -not $Docker) {
    $All = $true
}

if ($All -or $Output) {
    if (Test-Path $OUTPUT_DIR) {
        Write-Info "删除输出目录: $OUTPUT_DIR"
        Remove-Item -Recurse -Force $OUTPUT_DIR
        Write-Success "输出目录已清理"
    }
}

if ($All -or $Docker) {
    Write-Info "清理 Docker 资源..."

    # 停止并删除容器
    $containers = docker ps -aq --filter "ancestor=$IMAGE_NAME" 2>$null
    if ($containers) {
        docker rm -f $containers 2>$null
        Write-Info "已删除容器"
    }

    # 删除 volume
    if (docker volume ls -q --filter "name=$SOURCE_VOLUME") {
        docker volume rm $SOURCE_VOLUME 2>$null
        Write-Info "已删除 volume: $SOURCE_VOLUME"
    }

    # 删除镜像
    if ($Force -or $All) {
        if (docker images -q $IMAGE_NAME) {
            docker rmi $IMAGE_NAME 2>$null
            Write-Info "已删除镜像: $IMAGE_NAME"
        }
    }

    Write-Success "Docker 资源已清理"
}

Write-Success "清理完成"
