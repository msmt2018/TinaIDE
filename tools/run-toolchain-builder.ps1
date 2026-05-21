<#
.SYNOPSIS
Start TinaIDE toolchain builder container.

.DESCRIPTION
- Build image from docker/toolchain-builder/Dockerfile
- Mount repository to /workspace
- Support detached mode, one-shot command mode, and interactive shell mode

.EXAMPLE
pwsh -File tools/run-toolchain-builder.ps1 -Detach

.EXAMPLE
pwsh -File tools/run-toolchain-builder.ps1 -NoBuild -Command "cd /workspace && bash scripts/build-and-package-android-toolchain.sh"
#>
[CmdletBinding()]
param(
  [string]$NdkVersion = 'r27',
  [string]$ImageName = 'tinaide-toolchain-builder',
  [string]$ContainerName = 'tinaide-toolchain-builder',
  [switch]$NoBuild,
  [switch]$Detach,
  [string]$Command
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Write-Step {
  param([string]$Message)
  Write-Host "[toolchain-builder] $Message" -ForegroundColor Cyan
}

function Assert-Command {
  param([string]$Name)
  if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
    throw "Missing command: $Name"
  }
}

function Invoke-DockerChecked {
  param([string[]]$DockerArgs)
  & docker @DockerArgs
  if ($LASTEXITCODE -ne 0) {
    throw "docker command failed ($LASTEXITCODE): docker $($DockerArgs -join ' ')"
  }
}

if ($Detach -and $Command) {
  throw "Cannot use -Detach and -Command together."
}

Assert-Command docker

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$dockerfile = Join-Path $repoRoot 'docker/toolchain-builder/Dockerfile'
if (-not (Test-Path -LiteralPath $dockerfile)) {
  throw "Dockerfile not found: $dockerfile"
}

$imageTag = "$($ImageName):ndk-$NdkVersion"
$volume = "${repoRoot}:/workspace"

if (-not $NoBuild) {
  Write-Step "Build image: $imageTag"
  Invoke-DockerChecked @(
    'build',
    '-t', $imageTag,
    '-f', $dockerfile,
    '--build-arg', "NDK_VERSION=$NdkVersion",
    $repoRoot
  )
} else {
  Write-Step "Skip image build (-NoBuild): $imageTag"
}

if ($Detach) {
  Write-Step "Run detached container: $ContainerName"
  if (docker ps -a --format '{{.Names}}' | Select-String -SimpleMatch -Quiet $ContainerName) {
    docker rm -f $ContainerName *> $null
  }
  Invoke-DockerChecked @(
    'run', '-d',
    '--name', $ContainerName,
    '-v', $volume,
    '-w', '/workspace',
    $imageTag,
    'bash', '-lc', 'tail -f /dev/null'
  )
  Write-Step "Container is ready: docker exec -it $ContainerName bash"
  return
}

if ($Command) {
  Write-Step "Run single command in throwaway container"
  Invoke-DockerChecked @(
    'run', '--rm',
    '-v', $volume,
    '-w', '/workspace',
    $imageTag,
    'bash', '-lc', $Command
  )
  return
}

Write-Step "Open interactive shell"
Invoke-DockerChecked @(
  'run', '--rm', '-it',
  '-v', $volume,
  '-w', '/workspace',
  $imageTag,
  'bash'
)

