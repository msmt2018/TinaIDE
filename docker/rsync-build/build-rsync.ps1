# TinaIDE Rsync Docker Builder
# 使用 Docker 编译支持 16KB 页面对齐的 Android rsync

param(
    [string]$Architecture = "arm64-v8a",
    [switch]$AllArchitectures,
    [switch]$Clean,
    [switch]$NoBuildCache,
    [string]$RsyncVersion = "v3.4.0"
)

$ErrorActionPreference = "Stop"

# 架构映射
$archMap = @{
    "arm64-v8a" = "aarch64-linux-android"
    "armeabi-v7a" = "armv7a-linux-androideabi"
    "x86_64" = "x86_64-linux-android"
    "x86" = "i686-linux-android"
}

$imageName = "tinaide-rsync-builder"
$containerName = "tinaide-rsync-build"
$scriptDir = $PSScriptRoot
$projectRoot = Split-Path (Split-Path $scriptDir -Parent) -Parent
$outputDir = Join-Path $projectRoot "app\src\main\jniLibs"
$srcVolume = "tinaide-rsync-src"

function Write-Header {
    param([string]$Text)
    Write-Host ""
    Write-Host "========================================" -ForegroundColor Cyan
    Write-Host $Text -ForegroundColor Cyan
    Write-Host "========================================" -ForegroundColor Cyan
    Write-Host ""
}

function Build-DockerImage {
    Write-Header "构建 Docker 镜像"
    
    $buildArgs = @(
        "build",
        "-t", $imageName,
        "-f", "Dockerfile"
    )
    
    if ($NoBuildCache) {
        $buildArgs += "--no-cache"
    }
    
    $buildArgs += "."
    
    Write-Host "执行: docker $($buildArgs -join ' ')" -ForegroundColor Yellow
    
    Push-Location $scriptDir
    try {
        & docker $buildArgs
        if ($LASTEXITCODE -ne 0) {
            throw "Docker 镜像构建失败"
        }
        Write-Host "✓ Docker 镜像构建成功" -ForegroundColor Green
    } finally {
        Pop-Location
    }
}

function Build-RsyncForArch {
    param(
        [string]$Arch,
        [string]$Target
    )
    
    Write-Header "构建 Rsync for $Arch"
    
    Write-Host "架构: $Arch" -ForegroundColor Cyan
    Write-Host "目标: $Target" -ForegroundColor Cyan
    Write-Host "版本: $RsyncVersion" -ForegroundColor Cyan
    Write-Host ""
    
    # 创建输出目录
    $archOutputDir = Join-Path $outputDir $Arch
    New-Item -ItemType Directory -Force -Path $archOutputDir | Out-Null
    
    # 运行 Docker 容器
    $dockerArgs = @(
        "run",
        "--rm",
        "--name", "$containerName-$Arch",
        "-v", "${srcVolume}:/build/src",
        "-v", "${archOutputDir}:/output/$Arch",
        "-e", "TARGET_ARCH=$Target",
        "-e", "RSYNC_VERSION=$RsyncVersion",
        $imageName
    )
    
    Write-Host "执行: docker $($dockerArgs -join ' ')" -ForegroundColor Yellow
    Write-Host ""
    
    & docker $dockerArgs
    
    if ($LASTEXITCODE -ne 0) {
        Write-Error "构建失败: $Arch"
        return $false
    }
    
    # 验证输出
    $outputFile = Join-Path $archOutputDir "librsync.so"
    if (Test-Path $outputFile) {
        Write-Host ""
        Write-Host "✓ 构建成功: $outputFile" -ForegroundColor Green
        
        # 显示文件大小
        $fileSize = (Get-Item $outputFile).Length
        $fileSizeKB = [math]::Round($fileSize / 1KB, 2)
        Write-Host "  文件大小: $fileSizeKB KB" -ForegroundColor Gray
        
        return $true
    } else {
        Write-Error "输出文件不存在: $outputFile"
        return $false
    }
}

function Clean-All {
    Write-Header "清理构建产物"
    
    # 停止并删除容器
    Write-Host "停止容器..." -ForegroundColor Yellow
    docker ps -a --filter "name=$containerName" --format "{{.Names}}" | ForEach-Object {
        docker stop $_ 2>$null
        docker rm $_ 2>$null
    }
    
    # 删除镜像
    Write-Host "删除镜像..." -ForegroundColor Yellow
    docker rmi $imageName 2>$null
    
    # 删除 volume
    Write-Host "删除源码 volume..." -ForegroundColor Yellow
    docker volume rm $srcVolume 2>$null
    
    # 删除输出文件
    Write-Host "删除输出文件..." -ForegroundColor Yellow
    foreach ($arch in $archMap.Keys) {
        $archOutputDir = Join-Path $outputDir $arch
        $outputFile = Join-Path $archOutputDir "librsync.so"
        if (Test-Path $outputFile) {
            Remove-Item $outputFile -Force
            Write-Host "  删除: $outputFile" -ForegroundColor Gray
        }
    }
    
    Write-Host ""
    Write-Host "✓ 清理完成" -ForegroundColor Green
}

function Show-Summary {
    param([hashtable]$Results)
    
    Write-Header "构建总结"
    
    $success = 0
    $failed = 0
    
    foreach ($arch in $Results.Keys) {
        if ($Results[$arch]) {
            Write-Host "✓ $arch" -ForegroundColor Green
            $success++
        } else {
            Write-Host "✗ $arch" -ForegroundColor Red
            $failed++
        }
    }
    
    Write-Host ""
    Write-Host "成功: $success" -ForegroundColor Green
    Write-Host "失败: $failed" -ForegroundColor Red
    Write-Host ""
    
    if ($failed -eq 0) {
        Write-Host "🎉 所有架构构建成功！" -ForegroundColor Green
        Write-Host ""
        Write-Host "下一步:" -ForegroundColor Yellow
        Write-Host "1. 在 build.gradle.kts 中移除或注释 Maven 依赖" -ForegroundColor Gray
        Write-Host "2. 构建 APK 并测试" -ForegroundColor Gray
        Write-Host "3. 在 Android 15+ 设备上验证" -ForegroundColor Gray
    }
}

# ============================================
# 主执行流程
# ============================================

Write-Header "TinaIDE Rsync Docker Builder"

Write-Host "项目根目录: $projectRoot" -ForegroundColor Gray
Write-Host "输出目录: $outputDir" -ForegroundColor Gray
Write-Host "Rsync 版本: $RsyncVersion" -ForegroundColor Gray
Write-Host ""

# 检查 Docker
try {
    docker --version | Out-Null
} catch {
    Write-Error "Docker 未安装或未运行。请先安装 Docker Desktop。"
    exit 1
}

# 清理模式
if ($Clean) {
    Clean-All
    exit 0
}

# 构建 Docker 镜像
Build-DockerImage

# 创建源码 volume（如果不存在）
$volumeExists = docker volume ls --format "{{.Name}}" | Select-String -Pattern "^$srcVolume$"
if (-not $volumeExists) {
    Write-Host "创建源码 volume: $srcVolume" -ForegroundColor Yellow
    docker volume create $srcVolume | Out-Null
}

# 构建
if ($AllArchitectures) {
    Write-Host "构建所有架构..." -ForegroundColor Cyan
    Write-Host ""
    
    $results = @{}
    foreach ($arch in $archMap.Keys) {
        $results[$arch] = Build-RsyncForArch -Arch $arch -Target $archMap[$arch]
    }
    
    Show-Summary -Results $results
    
    if ($results.Values -contains $false) {
        exit 1
    }
} else {
    # 构建单个架构
    if (-not $archMap.ContainsKey($Architecture)) {
        Write-Error "未知架构: $Architecture. 可用选项: $($archMap.Keys -join ', ')"
        exit 1
    }
    
    $target = $archMap[$Architecture]
    if (Build-RsyncForArch -Arch $Architecture -Target $target) {
        Write-Host ""
        Write-Host "🎉 构建完成！" -ForegroundColor Green
        Write-Host ""
        Write-Host "输出文件: $outputDir\$Architecture\librsync.so" -ForegroundColor Cyan
        Write-Host ""
        Write-Host "下一步:" -ForegroundColor Yellow
        Write-Host "1. 在 build.gradle.kts 中移除或注释 Maven 依赖" -ForegroundColor Gray
        Write-Host "2. 构建 APK 并测试" -ForegroundColor Gray
    } else {
        exit 1
    }
}
