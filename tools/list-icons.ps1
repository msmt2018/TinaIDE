# Extract all unique Icons.XXX.YYY patterns from Kotlin source files
$patterns = @()
Get-ChildItem -Path "app/src" -Recurse -Filter "*.kt" | ForEach-Object {
    $content = Get-Content $_.FullName -Raw
    $matches = [regex]::Matches($content, 'Icons\.(\w+)\.(\w+)')
    foreach ($m in $matches) {
        $patterns += $m.Value
    }
}

$unique = $patterns | Sort-Object -Unique
Write-Output "Total unique icon references: $($unique.Count)"
Write-Output ""

# Group by category
$grouped = @{}
foreach ($p in $unique) {
    $parts = $p -split '\.'
    $category = $parts[1]
    if (-not $grouped.ContainsKey($category)) { $grouped[$category] = @() }
    $grouped[$category] += $parts[2]
}

foreach ($cat in ($grouped.Keys | Sort-Object)) {
    $icons = $grouped[$cat] | Sort-Object
    Write-Output "=== Icons.$cat ($($icons.Count) icons) ==="
    foreach ($icon in $icons) {
        Write-Output "  Icons.$cat.$icon"
    }
    Write-Output ""
}
