package com.wuxianggujun.tinaide.ui.compose.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Source
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wuxianggujun.tinaide.core.i18n.Strings

/**
 * 搜索范围枚举
 */
enum class SearchScope {
    PROJECT, // 整个项目
    CURRENT_FILE, // 当前文件
    OPEN_FILES, // 打开的文件
    CUSTOM_DIRECTORY // 自定义目录
}

/**
 * 搜索范围选择器组件
 */
@Composable
fun SearchScopeSelector(
    currentScope: SearchScope,
    onScopeChange: (SearchScope) -> Unit,
    customDirectory: String?,
    onSelectCustomDirectory: () -> Unit,
    hasCurrentFile: Boolean = false,
    hasOpenFiles: Boolean = false,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        // 当前选中的范围显示
        FilterChip(
            selected = true,
            onClick = { expanded = true },
            label = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = getScopeIcon(currentScope),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = when (currentScope) {
                            SearchScope.PROJECT -> stringResource(Strings.search_scope_project)
                            SearchScope.CURRENT_FILE -> stringResource(Strings.search_scope_current_file)
                            SearchScope.OPEN_FILES -> stringResource(Strings.search_scope_open_files)
                            SearchScope.CUSTOM_DIRECTORY -> customDirectory?.substringAfterLast('/')
                                ?: stringResource(Strings.search_scope_custom)
                        }
                    )
                }
            }
        )

        // 下拉菜单
        TinaDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            // 整个项目
            ScopeMenuItem(
                scope = SearchScope.PROJECT,
                currentScope = currentScope,
                icon = Icons.Default.Source,
                label = stringResource(Strings.search_scope_project),
                onClick = {
                    onScopeChange(SearchScope.PROJECT)
                    expanded = false
                }
            )

            // 当前文件（仅在有当前文件时显示）
            if (hasCurrentFile) {
                ScopeMenuItem(
                    scope = SearchScope.CURRENT_FILE,
                    currentScope = currentScope,
                    icon = Icons.AutoMirrored.Filled.InsertDriveFile,
                    label = stringResource(Strings.search_scope_current_file),
                    onClick = {
                        onScopeChange(SearchScope.CURRENT_FILE)
                        expanded = false
                    }
                )
            }

            // 打开的文件（仅在有打开文件时显示）
            if (hasOpenFiles) {
                ScopeMenuItem(
                    scope = SearchScope.OPEN_FILES,
                    currentScope = currentScope,
                    icon = Icons.Default.FolderOpen,
                    label = stringResource(Strings.search_scope_open_files),
                    onClick = {
                        onScopeChange(SearchScope.OPEN_FILES)
                        expanded = false
                    }
                )
            }

            TinaDropdownMenuDivider()

            ScopeMenuItem(
                scope = SearchScope.CUSTOM_DIRECTORY,
                currentScope = currentScope,
                icon = Icons.Default.Folder,
                label = stringResource(Strings.search_scope_custom),
                supportingText = customDirectory,
                onClick = {
                    onSelectCustomDirectory()
                    expanded = false
                }
            )
        }
    }
}

@Composable
private fun ScopeMenuItem(
    scope: SearchScope,
    currentScope: SearchScope,
    icon: ImageVector,
    label: String,
    supportingText: String? = null,
    onClick: () -> Unit
) {
    val isSelected = scope == currentScope

    TinaDropdownMenuItem(
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = label,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                    supportingText?.let { secondaryText ->
                        Text(
                            text = secondaryText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        onClick = onClick,
        leadingIcon = if (isSelected) {
            {
                RadioButton(
                    selected = true,
                    onClick = null,
                    modifier = Modifier.size(20.dp)
                )
            }
        } else {
            null
        }
    )
}

private fun getScopeIcon(scope: SearchScope): ImageVector = when (scope) {
    SearchScope.PROJECT -> Icons.Default.Source
    SearchScope.CURRENT_FILE -> Icons.AutoMirrored.Filled.InsertDriveFile
    SearchScope.OPEN_FILES -> Icons.Default.FolderOpen
    SearchScope.CUSTOM_DIRECTORY -> Icons.Default.Folder
}

/**
 * 文件包含/排除模式输入组件
 */
@Composable
fun FilePatternInput(
    includePattern: String,
    onIncludePatternChange: (String) -> Unit,
    excludePattern: String,
    onExcludePatternChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // 包含模式
        OutlinedTextField(
            value = includePattern,
            onValueChange = onIncludePatternChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(Strings.search_include_pattern)) },
            placeholder = { Text("*.kt, *.java") },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 排除模式
        OutlinedTextField(
            value = excludePattern,
            onValueChange = onExcludePatternChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(Strings.search_exclude_pattern)) },
            placeholder = { Text("build/*, .git/*") },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall
        )
    }
}
