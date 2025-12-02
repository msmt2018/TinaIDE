Param(
  [string[]]$Abi,
  [int]$ApiLevel = 28,
  [string]$BuildOutputRoot = 'docker/llvm-build/build-output',
  [string]$AppJniLibs = 'app/src/main/jniLibs',
  [string]$AppAssetsSysroot = 'app/src/main/assets/sysroot',
  [ValidateSet('none','zip','mirror')]
  [string]$SysrootMode = 'zip'
)

$validAbis = @('arm64-v8a','x86_64')
$abiList = if (-not $Abi -or $Abi.Count -eq 0) { $validAbis } else { $Abi }
foreach ($entry in $abiList) {
  if ($validAbis -notcontains $entry) {
    throw "Unsupported ABI '$entry'. Valid values: $($validAbis -join ', ')"
  }
}

function Invoke-SyncSingleAbi {
  param(
    [string]$AbiValue
  )
  Write-Host "== Sync LLVM artifacts for ABI=$AbiValue ==" -ForegroundColor Cyan
  $args = @(
    '-File', (Join-Path '.' 'tools/sync-llvm-build.ps1'),
    '-Abi', $AbiValue,
    '-ApiLevel', $ApiLevel,
    '-BuildOutputRoot', $BuildOutputRoot,
    '-AppJniLibs', $AppJniLibs,
    '-AppAssetsSysroot', $AppAssetsSysroot,
    '-SysrootMode', $SysrootMode
  )
  & pwsh -NoLogo @args
  if ($LASTEXITCODE -ne 0) {
    throw "sync-llvm-build.ps1 failed for ABI=$AbiValue (exit $LASTEXITCODE)"
  }
}

function Rename-SysrootZip {
  param(
    [string]$AbiValue
  )
  $assetsRoot = Split-Path -Parent $AppAssetsSysroot
  $targetZip = Join-Path $assetsRoot ("sysroot-$AbiValue.zip")
  $defaultZip = Join-Path $assetsRoot 'sysroot.zip'
  if (Test-Path $defaultZip) {
    if (Test-Path $targetZip) { Remove-Item -Force $targetZip }
    Move-Item -Force $defaultZip $targetZip
    Write-Host "Renamed $defaultZip -> $targetZip" -ForegroundColor Green
  } elseif (Test-Path $targetZip) {
    Write-Host "Sysroot archive already present -> $targetZip" -ForegroundColor DarkGray
  }
}

foreach ($abi in $abiList) {
  Invoke-SyncSingleAbi -AbiValue $abi
  if ($SysrootMode -eq 'zip') {
    Rename-SysrootZip -AbiValue $abi
  }
}

Write-Host "== Completed multi-ABI sync ==" -ForegroundColor Cyan
