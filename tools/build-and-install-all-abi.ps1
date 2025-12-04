Param(
    [ValidateSet("debug", "release")]
    [string]$Variant = "debug"
)

$ErrorActionPreference = "Stop"

$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = Resolve-Path (Join-Path $scriptRoot "..")

function Invoke-GradleTask {
    param(
        [Parameter(Mandatory = $true)][string]$Task,
        [switch]$WarnOnly
    )
    Write-Host "Executing Gradle task: $Task" -ForegroundColor DarkCyan
    & ./gradlew $Task
    if ($LASTEXITCODE -ne 0) {
        if ($WarnOnly) {
            Write-Host "Gradle task failed (${Task}) but script will continue. See Gradle output above for details." -ForegroundColor Yellow
        } else {
            Write-Host "Gradle task failed: $Task" -ForegroundColor Red
            exit $LASTEXITCODE
        }
    }
}

function Ensure-HostFlatc {
    $setupScript = Join-Path $scriptRoot "setup-flatc.ps1"
    if (-not (Test-Path $setupScript)) {
        Write-Host "setup-flatc.ps1 not found at $setupScript" -ForegroundColor Yellow
        return
    }

    $flatcDir = Join-Path $repoRoot "external/flatbuffers-prebuilt"
    $hostDescription = [System.Runtime.InteropServices.RuntimeInformation]::OSDescription
    Write-Host "Ensuring host flatc is available..." -ForegroundColor Cyan
    try {
        & $setupScript | Out-Null
    } catch {
        Write-Host "Failed to bootstrap flatc via setup script" -ForegroundColor Red
        throw
    }
}

Ensure-HostFlatc

function Clean-NativeOutputs {
    Write-Host "Cleaning previous native build outputs..." -ForegroundColor Cyan
    $cleanTasks = @(
        ":app:externalNativeBuildCleanDebug",
        ":app:externalNativeBuildCleanRelease"
    )
    foreach ($task in $cleanTasks) {
        Invoke-GradleTask -Task $task -WarnOnly
    }
    $pathsToRemove = @(
        (Join-Path $repoRoot "app/.cxx"),
        (Join-Path $repoRoot "app/build/intermediates/cmake")
    )
    foreach ($path in $pathsToRemove) {
        if (Test-Path $path) {
            Write-Host "Removing $path" -ForegroundColor DarkGray
            Remove-Item -Recurse -Force $path -ErrorAction SilentlyContinue
        }
    }
}

Clean-NativeOutputs

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
    Write-Host "adb executable not found in any of the expected locations:" -ForegroundColor Red
    $candidateDirs | ForEach-Object { if ($_){ Write-Host " - $_" -ForegroundColor Yellow } }
    exit 1
}
$adbDir = $adbInfo.Dir
$adbExe = $adbInfo.Path
$script:usingMemuAdb = $adbDir -like "*Microvirt*MEmu*"
Write-Host "Using adb from $adbDir" -ForegroundColor DarkGreen
if (-not ($env:Path -split ";" | ForEach-Object { $_.Trim() } | Where-Object { $_ -eq $adbDir })) {
    $env:Path = "$adbDir;$env:Path"
}

function Get-AdbDevices {
    $output = & $adbExe devices
    if ($LASTEXITCODE -ne 0) {
        Write-Host "Failed to query adb devices." -ForegroundColor Red
        exit $LASTEXITCODE
    }
    $entries = @()
    foreach ($rawLine in $output) {
        $line = $rawLine.Trim()
        if ([string]::IsNullOrWhiteSpace($line)) { continue }
        if ($line.StartsWith("List of devices")) { continue }
        if ($line.StartsWith("* daemon")) { continue }
        $parts = $line -split "\s+"
        if ($parts.Length -ge 2) {
            $entries += [PSCustomObject]@{
                Id    = $parts[0]
                State = $parts[-1]
            }
        }
    }
    return $entries
}

function Try-ConnectMemuEmulators {
    if (-not $script:usingMemuAdb) {
        return
    }
    $memuPorts = @(21503, 21513, 21523, 21533, 21543, 21553)
    Write-Host "Attempting to connect to running MEmu instances..." -ForegroundColor Yellow
    foreach ($port in $memuPorts) {
        $target = "127.0.0.1:$port"
        $output = & $adbExe connect $target 2>&1
        if ($output) {
            $output | ForEach-Object {
                Write-Host "adb connect $target -> $_" -ForegroundColor DarkGray
            }
        }
    }
}

function Ensure-AdbDeviceAvailable {
    Write-Host "Checking connected adb devices..." -ForegroundColor Cyan
    $devices = Get-AdbDevices
    $onlineDevices = $devices | Where-Object { $_.State -eq "device" }

    if ((($onlineDevices | Measure-Object).Count -eq 0) -and $script:usingMemuAdb) {
        Try-ConnectMemuEmulators
        $devices = Get-AdbDevices
        $onlineDevices = $devices | Where-Object { $_.State -eq "device" }
    }

    if (-not $onlineDevices -or $onlineDevices.Count -eq 0) {
        if ($devices.Count -gt 0) {
            $states = ($devices | ForEach-Object { "$($_.Id) [$($_.State)]" }) -join ", "
            Write-Host "adb detected devices but none are online: $states" -ForegroundColor Red
        } else {
            Write-Host "No adb devices detected. Please connect a device or start an emulator." -ForegroundColor Red
            if ($script:usingMemuAdb) {
                Write-Host "Tip: ensure the MEmu multi-instance manager has a running device or manually run 'adb connect 127.0.0.1:21503' before retrying." -ForegroundColor Yellow
            }
        }
        exit 1
    }
    $ids = $onlineDevices | ForEach-Object { $_.Id }
    Write-Host "Using adb device(s): $($ids -join ', ')" -ForegroundColor DarkGreen
}

Ensure-AdbDeviceAvailable

function Get-GradleTask {
    param([string]$variant)
    $capitalized = $variant.Substring(0,1).ToUpper() + $variant.Substring(1)
    return "assemble${capitalized}AllAbi"
}

$gradleTask = Get-GradleTask -variant $Variant
Write-Host "Running Gradle task: $gradleTask" -ForegroundColor Cyan
Invoke-GradleTask -Task $gradleTask

$apkName = "app-$Variant.apk"
$apkPath = Join-Path "app/build/outputs/apk/$Variant" $apkName
if (-not (Test-Path $apkPath)) {
    Write-Host "APK not found: $apkPath" -ForegroundColor Red
    exit 1
}

Write-Host "Installing $apkPath via adb..." -ForegroundColor Cyan
& $adbExe install -r $apkPath | Out-Host
if ($LASTEXITCODE -ne 0) {
    Write-Host "adb install failed. See output above." -ForegroundColor Red
    exit $LASTEXITCODE
}

Write-Host "Launching com.wuxianggujun.tinaide/.ui.ProjectManagerActivity" -ForegroundColor Cyan
& $adbExe shell am start -n com.wuxianggujun.tinaide/.ui.ProjectManagerActivity | Out-Host
if ($LASTEXITCODE -ne 0) {
    Write-Host "Failed to launch application via adb." -ForegroundColor Yellow
}

Write-Host "Build and install completed." -ForegroundColor Green
