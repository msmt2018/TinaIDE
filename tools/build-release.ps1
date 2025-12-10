# TinaIDE Release Build Script
# Usage: .\tools\build-release.ps1 [-Clean] [-Install]
#   -Clean   : 清理构建缓存后再编译
#   -Install : 构建完成后通过 adb 安装到设备

param(
    [switch]$Clean,
    [switch]$Install
)

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  TinaIDE Release Build" -ForegroundColor Cyan  
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Get script directory
$scriptPath = $PSScriptRoot
if ([string]::IsNullOrEmpty($scriptPath)) {
    $scriptPath = Split-Path -Parent $MyInvocation.MyCommand.Definition
}
if ([string]::IsNullOrEmpty($scriptPath)) {
    $scriptPath = Get-Location
}

# Get project root directory
$repoRoot = Resolve-Path (Join-Path $scriptPath "..")
Write-Host "Project root: $repoRoot" -ForegroundColor DarkGray

# Change to project root
Set-Location $repoRoot

# Check signing config
$keystoreProps = Join-Path $repoRoot "keystore.properties"
if (-not (Test-Path $keystoreProps)) {
    Write-Host "Error: keystore.properties not found" -ForegroundColor Red
    exit 1
}
Write-Host "Signing config: OK" -ForegroundColor Green

# Check keystore file
$keystoreFile = Join-Path $repoRoot "keystore\release.jks"
if (-not (Test-Path $keystoreFile)) {
    Write-Host "Error: keystore\release.jks not found" -ForegroundColor Red
    exit 1
}
Write-Host "Keystore file: OK" -ForegroundColor Green
Write-Host ""

# Optional: clean build cache
if ($Clean) {
    Write-Host "Cleaning build cache..." -ForegroundColor Yellow
    .\gradlew.bat clean
    if ($LASTEXITCODE -ne 0) {
        Write-Host "Clean failed" -ForegroundColor Red
        exit $LASTEXITCODE
    }
    Write-Host ""
}

# Build Release APK
Write-Host "Building Release APK..." -ForegroundColor Cyan
Write-Host "  - Code shrinking enabled (R8)" -ForegroundColor DarkGray
Write-Host "  - Resource shrinking enabled" -ForegroundColor DarkGray
Write-Host "  - Target ABI: arm64-v8a, x86_64" -ForegroundColor DarkGray
Write-Host ""

.\gradlew.bat assembleRelease --no-daemon
if ($LASTEXITCODE -ne 0) {
    Write-Host ""
    Write-Host "Build failed! Check errors above." -ForegroundColor Red
    exit $LASTEXITCODE
}

# Check output
$apkPath = Join-Path $repoRoot "app\build\outputs\apk\release\app-release.apk"
if (Test-Path $apkPath) {
    # 读取版本号
    $versionProps = Join-Path $repoRoot "version.properties"
    $versionName = "unknown"
    if (Test-Path $versionProps) {
        $content = Get-Content $versionProps
        foreach ($line in $content) {
            if ($line -match "^versionName=(.+)$") {
                $versionName = $Matches[1].Trim()
                break
            }
        }
    }

    # 重命名 APK
    $outputDir = Split-Path $apkPath -Parent
    $newApkName = "TinaIDE-$versionName.apk"
    $newApkPath = Join-Path $outputDir $newApkName
    Copy-Item -Path $apkPath -Destination $newApkPath -Force

    $apkInfo = Get-Item $newApkPath
    $sizeMB = [math]::Round($apkInfo.Length / 1MB, 2)

    Write-Host ""
    Write-Host "========================================" -ForegroundColor Green
    Write-Host "  Build Successful!" -ForegroundColor Green
    Write-Host "========================================" -ForegroundColor Green
    Write-Host ""
    Write-Host "APK: $newApkName" -ForegroundColor White
    Write-Host "Path: $newApkPath" -ForegroundColor White
    Write-Host "Size: $sizeMB MB" -ForegroundColor White
    Write-Host ""

    # 安装 APK
    if ($Install) {
        Write-Host "Installing APK via adb..." -ForegroundColor Cyan

        # 查找 adb 可执行文件
        function Resolve-AdbExecutable {
            param([string[]]$CandidateDirs)
            foreach ($dir in $CandidateDirs) {
                if ([string]::IsNullOrWhiteSpace($dir)) { continue }
                $adbPath = Join-Path $dir "adb.exe"
                if (Test-Path $adbPath) {
                    return [PSCustomObject]@{
                        Dir = $dir
                        Path = $adbPath
                    }
                }
            }
            return $null
        }

        $candidateDirs = @(
            "D:\Program Files\Microvirt\MEmu",
            "D:\Programs\Android\Sdk\platform-tools"
        )
        if ($env:ANDROID_HOME) {
            $candidateDirs += (Join-Path $env:ANDROID_HOME "platform-tools")
        }
        if ($env:ANDROID_SDK_ROOT) {
            $candidateDirs += (Join-Path $env:ANDROID_SDK_ROOT "platform-tools")
        }

        $adbInfo = Resolve-AdbExecutable -CandidateDirs $candidateDirs
        if (-not $adbInfo) {
            Write-Host "Error: adb not found in any of the expected locations:" -ForegroundColor Red
            $candidateDirs | ForEach-Object { if ($_) { Write-Host " - $_" -ForegroundColor Yellow } }
        } else {
            $adbExe = $adbInfo.Path
            $usingMemuAdb = $adbInfo.Dir -like "*Microvirt*MEmu*"
            Write-Host "Using adb from $($adbInfo.Dir)" -ForegroundColor DarkGreen

            # 检查设备连接
            $output = & $adbExe devices
            $onlineDevices = @()
            foreach ($rawLine in $output) {
                $line = $rawLine.Trim()
                if ([string]::IsNullOrWhiteSpace($line)) { continue }
                if ($line.StartsWith("List of devices")) { continue }
                if ($line.StartsWith("* daemon")) { continue }
                $parts = $line -split "\s+"
                if ($parts.Length -ge 2 -and $parts[-1] -eq "device") {
                    $onlineDevices += $parts[0]
                }
            }

            # 如果没有设备且使用 MEmu adb，尝试连接模拟器
            if ($onlineDevices.Count -eq 0 -and $usingMemuAdb) {
                Write-Host "Attempting to connect to MEmu emulators..." -ForegroundColor Yellow
                $memuPorts = @(21503, 21513, 21523, 21533, 21543, 21553)
                foreach ($port in $memuPorts) {
                    $target = "127.0.0.1:$port"
                    & $adbExe connect $target 2>&1 | Out-Null
                }
                # 重新检查设备
                $output = & $adbExe devices
                foreach ($rawLine in $output) {
                    $line = $rawLine.Trim()
                    if ([string]::IsNullOrWhiteSpace($line)) { continue }
                    if ($line.StartsWith("List of devices")) { continue }
                    if ($line.StartsWith("* daemon")) { continue }
                    $parts = $line -split "\s+"
                    if ($parts.Length -ge 2 -and $parts[-1] -eq "device") {
                        $onlineDevices += $parts[0]
                    }
                }
            }

            if ($onlineDevices.Count -eq 0) {
                Write-Host "Error: No device connected. Please connect a device or start an emulator." -ForegroundColor Red
                if ($usingMemuAdb) {
                    Write-Host "Tip: ensure MEmu has a running instance or run 'adb connect 127.0.0.1:21503'" -ForegroundColor Yellow
                }
            } else {
                Write-Host "Device found: $($onlineDevices -join ', ')" -ForegroundColor DarkGreen
                Write-Host "Installing..." -ForegroundColor DarkGray
                & $adbExe install -r $newApkPath
                if ($LASTEXITCODE -eq 0) {
                    Write-Host "Installation successful!" -ForegroundColor Green
                } else {
                    Write-Host "Installation failed. Exit code: $LASTEXITCODE" -ForegroundColor Red
                }
            }
        }
        Write-Host ""
    }

    # Open output directory
    $response = Read-Host "Open output directory? (Y/N)"
    if ($response -eq "Y" -or $response -eq "y") {
        Start-Process explorer.exe -ArgumentList $outputDir
    }
} else {
    Write-Host ""
    Write-Host "Warning: APK file not found, check build log" -ForegroundColor Yellow
}
