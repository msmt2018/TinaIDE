$ErrorActionPreference = "SilentlyContinue"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$logFile = Join-Path $repoRoot "build/tina-toolchain/progress-watch.log"
$buildLog = Join-Path $repoRoot "build/tina-toolchain/static-build.log"
$pidFile = Join-Path $repoRoot "build/tina-toolchain/progress-watch.pid"

New-Item -ItemType Directory -Force -Path (Split-Path $logFile) | Out-Null
Set-Content -Path $pidFile -Value $PID -Encoding ascii
Add-Content -Path $logFile -Value "===== monitor started: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss') pid=$PID ====="

while ($true) {
  $ts = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
  $inspect = docker inspect --format='{{.State.Status}}|{{.State.ExitCode}}' tinaide-llvm22-static-build 2>$null
  if (-not $inspect) {
    Add-Content -Path $logFile -Value "$ts status=missing progress=unknown"
    break
  }

  $parts = $inspect -split '\|'
  $status = $parts[0]
  $exitCode = $parts[1]
  $progress = "unknown"

  if (Test-Path $buildLog) {
    $tail = Get-Content $buildLog -Tail 1200
    $m = $tail | Select-String -Pattern '\[(\d+)/(\d+)\]' | Select-Object -Last 1
    if ($m) {
      $x = [int]$m.Matches[0].Groups[1].Value
      $y = [int]$m.Matches[0].Groups[2].Value
      $p = [Math]::Round($x * 100.0 / $y, 2)
      $progress = "$x/$y ($p%)"
    } elseif ($tail.Count -gt 0) {
      $progress = "fetch_or_config"
    }
  }

  Add-Content -Path $logFile -Value "$ts status=$status exit=$exitCode progress=$progress"

  if ($status -ne "running") {
    break
  }

  Start-Sleep -Seconds 20
}

Add-Content -Path $logFile -Value "===== monitor stopped: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss') pid=$PID ====="
