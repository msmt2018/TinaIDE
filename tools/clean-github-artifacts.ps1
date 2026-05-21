# GitHub Artifacts 批量清理脚本
# 使用前确保已安装并登录 GitHub CLI: gh auth login

Write-Host "=== GitHub Artifacts 清理工具 ===" -ForegroundColor Cyan

# 检查是否安装了 gh
if (!(Get-Command gh -ErrorAction SilentlyContinue)) {
    Write-Host "错误: 未安装 GitHub CLI" -ForegroundColor Red
    Write-Host "请运行: winget install GitHub.cli" -ForegroundColor Yellow
    exit 1
}

# 检查是否已登录
$authStatus = gh auth status 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Host "错误: 未登录 GitHub CLI" -ForegroundColor Red
    Write-Host "请运行: gh auth login" -ForegroundColor Yellow
    exit 1
}

Write-Host "`n选择操作:" -ForegroundColor Green
Write-Host "1. 查看所有仓库的 artifacts 占用情况"
Write-Host "2. 删除指定仓库的所有 artifacts"
Write-Host "3. 删除所有仓库的所有 artifacts（危险！）"
Write-Host "4. 设置所有仓库的 artifact 保留时间为 1 天"
$choice = Read-Host "`n请输入选项 (1-4)"

switch ($choice) {
    "1" {
        Write-Host "`n正在扫描所有仓库..." -ForegroundColor Yellow
        $repos = gh repo list --limit 1000 --json nameWithOwner | ConvertFrom-Json
        
        $totalSize = 0
        foreach ($repo in $repos) {
            $repoName = $repo.nameWithOwner
            Write-Host "`n检查: $repoName" -ForegroundColor Cyan
            
            try {
                $artifacts = gh api "repos/$repoName/actions/artifacts" --jq '.artifacts' | ConvertFrom-Json
                $repoSize = 0
                
                foreach ($artifact in $artifacts) {
                    $sizeMB = [math]::Round($artifact.size_in_bytes / 1MB, 2)
                    $repoSize += $sizeMB
                    Write-Host "  - $($artifact.name): ${sizeMB}MB (创建于 $($artifact.created_at))" -ForegroundColor Gray
                }
                
                if ($repoSize -gt 0) {
                    Write-Host "  仓库总计: ${repoSize}MB" -ForegroundColor Yellow
                    $totalSize += $repoSize
                }
            } catch {
                Write-Host "  无法访问或无 artifacts" -ForegroundColor DarkGray
            }
        }
        
        Write-Host "`n=== 总计占用: ${totalSize}MB ===" -ForegroundColor Green
    }
    
    "2" {
        $repoName = Read-Host "`n请输入仓库名 (格式: 用户名/仓库名)"
        Write-Host "正在删除 $repoName 的所有 artifacts..." -ForegroundColor Yellow
        
        try {
            $artifacts = gh api "repos/$repoName/actions/artifacts" --jq '.artifacts' | ConvertFrom-Json
            $count = 0
            
            foreach ($artifact in $artifacts) {
                Write-Host "删除: $($artifact.name) (ID: $($artifact.id))" -ForegroundColor Gray
                gh api -X DELETE "repos/$repoName/actions/artifacts/$($artifact.id)"
                $count++
            }
            
            Write-Host "`n成功删除 $count 个 artifacts" -ForegroundColor Green
        } catch {
            Write-Host "错误: $_" -ForegroundColor Red
        }
    }
    
    "3" {
        $confirm = Read-Host "`n警告: 这将删除所有仓库的所有 artifacts！确认吗？(yes/no)"
        if ($confirm -ne "yes") {
            Write-Host "已取消" -ForegroundColor Yellow
            exit 0
        }
        
        Write-Host "`n正在删除所有 artifacts..." -ForegroundColor Yellow
        $repos = gh repo list --limit 1000 --json nameWithOwner | ConvertFrom-Json
        $totalCount = 0
        
        foreach ($repo in $repos) {
            $repoName = $repo.nameWithOwner
            Write-Host "`n处理: $repoName" -ForegroundColor Cyan
            
            try {
                $artifacts = gh api "repos/$repoName/actions/artifacts" --jq '.artifacts' | ConvertFrom-Json
                
                foreach ($artifact in $artifacts) {
                    Write-Host "  删除: $($artifact.name)" -ForegroundColor Gray
                    gh api -X DELETE "repos/$repoName/actions/artifacts/$($artifact.id)"
                    $totalCount++
                }
            } catch {
                Write-Host "  跳过 (无权限或无 artifacts)" -ForegroundColor DarkGray
            }
        }
        
        Write-Host "`n=== 总计删除 $totalCount 个 artifacts ===" -ForegroundColor Green
    }
    
    "4" {
        Write-Host "`n正在设置所有仓库的保留策略..." -ForegroundColor Yellow
        $repos = gh repo list --limit 1000 --json nameWithOwner | ConvertFrom-Json
        $count = 0
        
        foreach ($repo in $repos) {
            $repoName = $repo.nameWithOwner
            Write-Host "设置: $repoName" -ForegroundColor Gray
            
            try {
                # 注意：这个 API 可能需要 admin 权限
                gh api -X PUT "repos/$repoName/actions/cache/usage-policy" -f "retention_days=1"
                $count++
            } catch {
                Write-Host "  跳过 (无权限)" -ForegroundColor DarkGray
            }
        }
        
        Write-Host "`n成功设置 $count 个仓库" -ForegroundColor Green
    }
    
    default {
        Write-Host "无效选项" -ForegroundColor Red
    }
}

Write-Host "`n完成！" -ForegroundColor Green
