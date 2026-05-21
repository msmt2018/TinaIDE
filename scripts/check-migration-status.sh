#!/bin/bash

# 批量组件迁移脚本
# 将原生 Material3 组件替换为 Tina 设计系统组件

echo "开始批量迁移组件..."

# P0 文件列表
P0_FILES=(
    "app/src/main/java/com/wuxianggujun/tinaide/ui/compose/screens/main/market/MyPublishScreen.kt"
    "app/src/main/java/com/wuxianggujun/tinaide/ui/compose/screens/main/MainActivityDialogs.kt"
    "app/src/main/java/com/wuxianggujun/tinaide/ui/compose/screens/main/MainScreen.kt"
)

# P1 文件列表
P1_FILES=(
    "app/src/main/java/com/wuxianggujun/tinaide/ui/compose/components/EditorCommandDialogs.kt"
    "app/src/main/java/com/wuxianggujun/tinaide/ui/compose/components/UnsavedChangesOnExitDialog.kt"
    "app/src/main/java/com/wuxianggujun/tinaide/ui/compose/components/UnsavedFileDialog.kt"
    "app/src/main/java/com/wuxianggujun/tinaide/ui/compose/components/NewFileDialog.kt"
    "app/src/main/java/com/wuxianggujun/tinaide/ui/compose/components/BookmarksContent.kt"
)

# P2 文件列表
P2_FILES=(
    "app/src/main/java/com/wuxianggujun/tinaide/ui/compose/components/GitRemoteDialog.kt"
    "app/src/main/java/com/wuxianggujun/tinaide/ui/compose/components/GitSyncDialog.kt"
    "app/src/main/java/com/wuxianggujun/tinaide/ui/compose/screens/main/MainActivityGitDialogs.kt"
)

# P3 文件列表
P3_FILES=(
    "feature/settings/src/main/java/com/wuxianggujun/tinaide/ui/compose/components/LspToolchainInstallDialog.kt"
    "feature/settings/src/main/java/com/wuxianggujun/tinaide/ui/compose/components/PluginPermissionDialog.kt"
    "feature/settings/src/main/java/com/wuxianggujun/tinaide/ui/compose/screens/settings/sections/AiSettingsSection.kt"
    "feature/settings/src/main/java/com/wuxianggujun/tinaide/ui/compose/screens/settings/sections/DeveloperOptionsSection.kt"
    "feature/settings/src/main/java/com/wuxianggujun/tinaide/ui/compose/screens/settings/sections/KeyboardSettingsSection.kt"
    "feature/settings/src/main/java/com/wuxianggujun/tinaide/ui/compose/screens/settings/sections/PluginLogScreen.kt"
)

# P4 文件列表
P4_FILES=(
    "feature/login/src/main/java/com/wuxianggujun/tinaide/ui/activity/LoginActivity.kt"
    "feature/settings/src/main/java/com/wuxianggujun/tinaide/settings/OpenSourceLicensesActivity.kt"
    "feature/packages/src/main/java/com/wuxianggujun/tinaide/ui/compose/screens/packages/InstallHistoryScreen.kt"
)

# 合并所有文件
ALL_FILES=("${P0_FILES[@]}" "${P1_FILES[@]}" "${P2_FILES[@]}" "${P3_FILES[@]}" "${P4_FILES[@]}")

# 统计
total=0
migrated=0

for file in "${ALL_FILES[@]}"; do
    if [ -f "$file" ]; then
        total=$((total + 1))
        echo "检查文件: $file"

        # 检查是否包含需要迁移的组件
        if grep -q "import androidx.compose.material3.AlertDialog$\|import androidx.compose.material3.Button$\|import androidx.compose.material3.OutlinedButton$\|import androidx.compose.material3.TextButton$" "$file"; then
            echo "  ✓ 需要迁移"
            migrated=$((migrated + 1))
        else
            echo "  - 已迁移或无需迁移"
        fi
    else
        echo "文件不存在: $file"
    fi
done

echo ""
echo "统计结果:"
echo "  总文件数: $total"
echo "  需要迁移: $migrated"
echo "  已完成: $((total - migrated))"
