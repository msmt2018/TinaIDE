package com.wuxianggujun.tinaide.ui.compose.screens.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wuxianggujun.tinaide.core.i18n.Strings
import com.wuxianggujun.tinaide.ui.compose.components.TinaAlertDialog
import com.wuxianggujun.tinaide.ui.compose.components.TinaDialogCard
import com.wuxianggujun.tinaide.ui.compose.components.TinaDialogContentColumn
import com.wuxianggujun.tinaide.ui.compose.components.TinaDialogTitleText
import com.wuxianggujun.tinaide.ui.compose.components.TinaPrimaryButton
import com.wuxianggujun.tinaide.ui.compose.components.TinaSingleChoiceDialog
import com.wuxianggujun.tinaide.ui.compose.components.TinaTextButton
import com.wuxianggujun.tinaide.ui.compose.screens.settings.SettingsViewModel
import com.wuxianggujun.tinaide.ui.compose.screens.settings.components.SettingsCard
import com.wuxianggujun.tinaide.ui.compose.screens.settings.components.SettingsCategoryTitle
import com.wuxianggujun.tinaide.ui.compose.screens.settings.components.SettingsClickableItem
import com.wuxianggujun.tinaide.ui.compose.screens.settings.components.SettingsDisplayItem
import com.wuxianggujun.tinaide.ui.compose.screens.settings.components.SettingsSwitchItem

@Composable
internal fun ProjectSettingsSection(viewModel: SettingsViewModel) {
    val state by viewModel.uiState.collectAsState()
    var showNewProjectLocationDialog by remember { mutableStateOf(false) }
    var showAutoSaveDialog by remember { mutableStateOf(false) }
    var showApkExportDialog by remember { mutableStateOf(false) }
    var editingPathType by remember { mutableStateOf<NativeDependencyPathType?>(null) }
    var editingBuildFlagType by remember { mutableStateOf<NativeBuildFlagType?>(null) }

    Spacer(modifier = Modifier.height(8.dp))

    // "自动保存 / 备份 / 新项目默认源位置" 在语义上是全局偏好；
    // 当用户从项目列表菜单进入"指定项目"模式时不应渲染，避免误导。
    if (!state.isTargetProjectMode) {
        // 自动保存设置
        SettingsCategoryTitle(stringResource(Strings.settings_cat_auto_save))

        SettingsCard {
            val autoSaveDisplayName = resolveProjectSettingsText(
                ProjectSettingsSectionSupport.resolveAutoSaveIntervalText(state.projectAutoSaveInterval)
            )
            SettingsClickableItem(
                title = stringResource(Strings.settings_auto_save_interval),
                value = autoSaveDisplayName,
                onClick = { showAutoSaveDialog = true },
                showDivider = false
            )
        }

        // 备份设置
        SettingsCategoryTitle(stringResource(Strings.settings_cat_backup))

        SettingsCard {
            SettingsSwitchItem(
                title = stringResource(Strings.settings_auto_backup),
                subtitle = stringResource(Strings.settings_auto_backup_desc),
                checked = state.projectAutoBackup,
                onCheckedChange = { viewModel.setProjectAutoBackup(it) },
                showDivider = false
            )
        }

        SettingsCategoryTitle(stringResource(Strings.settings_cat_new_project))
        SettingsCard {
            SettingsClickableItem(
                title = stringResource(Strings.settings_new_project_default_source_location),
                subtitle = stringResource(Strings.settings_new_project_default_source_location_desc),
                value = stringResource(
                    ProjectSettingsSectionSupport.resolveNewProjectSourceLocationLabelRes(
                        state.newProjectDefaultSourceLocation
                    )
                ),
                onClick = { showNewProjectLocationDialog = true },
                showDivider = false
            )
        }
    }

    // 三块"项目专属"类目（项目总览 / 原生依赖路径 / 原生构建标志）
    // 只有在存在可操作项目时才有意义：
    //   - MainActivity 齿轮进入 → 会话项目
    //   - 项目列表菜单 → 项目设置 → 覆盖模式指向点击的项目
    //   - 主页齿轮进入（无会话） → 整块不渲染，避免出现一串"无可用"占位
    val hasProjectContext = ProjectSettingsSectionSupport.hasProjectOpened(
        state.currentProjectRootPath
    )

    if (hasProjectContext) {
        SettingsCategoryTitle(stringResource(Strings.settings_cat_project))
        SettingsCard {
            val currentProjectValue = ProjectSettingsSectionSupport.resolveCurrentProjectName(
                currentProjectName = state.currentProjectName,
                unavailableValue = stringResource(Strings.settings_project_native_paths_no_project_value)
            )
            SettingsDisplayItem(
                title = stringResource(Strings.settings_project_native_paths_current_project),
                value = currentProjectValue
            )
            SettingsClickableItem(
                title = stringResource(Strings.settings_project_apk_export_type),
                subtitle = stringResource(Strings.settings_project_apk_export_type_desc),
                value = stringResource(
                    ProjectSettingsSectionSupport.resolveProjectApkExportTypeLabelRes(
                        state.projectApkExportType
                    )
                ),
                onClick = { showApkExportDialog = true },
                showDivider = !state.isTargetProjectMode
            )
            // 覆盖模式下没有 buildDir，无法自动检测 APK 导出类型；该按钮不渲染。
            if (!state.isTargetProjectMode) {
                SettingsClickableItem(
                    title = stringResource(Strings.settings_project_apk_export_redetect),
                    subtitle = stringResource(Strings.settings_project_apk_export_redetect_desc),
                    onClick = viewModel::redetectProjectApkExportType,
                    showDivider = false
                )
            }
        }

        SettingsCategoryTitle(stringResource(Strings.settings_cat_native_dependency_paths))
        SettingsCard {
            val includeValue = resolveProjectSettingsText(
                ProjectSettingsSectionSupport.resolveCollectionSummary(
                    state.projectNativeIncludeDirs.size
                )
            )
            val libraryValue = resolveProjectSettingsText(
                ProjectSettingsSectionSupport.resolveCollectionSummary(
                    state.projectNativeLibraryDirs.size
                )
            )
            val runtimeValue = resolveProjectSettingsText(
                ProjectSettingsSectionSupport.resolveCollectionSummary(
                    state.projectNativeRuntimeDirs.size
                )
            )

            SettingsClickableItem(
                title = stringResource(NativeDependencyPathType.INCLUDE.titleRes),
                subtitle = stringResource(NativeDependencyPathType.INCLUDE.subtitleRes),
                value = includeValue,
                onClick = { editingPathType = NativeDependencyPathType.INCLUDE }
            )
            SettingsClickableItem(
                title = stringResource(NativeDependencyPathType.LIBRARY.titleRes),
                subtitle = stringResource(NativeDependencyPathType.LIBRARY.subtitleRes),
                value = libraryValue,
                onClick = { editingPathType = NativeDependencyPathType.LIBRARY }
            )
            SettingsClickableItem(
                title = stringResource(NativeDependencyPathType.RUNTIME.titleRes),
                subtitle = stringResource(NativeDependencyPathType.RUNTIME.subtitleRes),
                value = runtimeValue,
                onClick = { editingPathType = NativeDependencyPathType.RUNTIME },
                showDivider = false
            )
        }

        SettingsCategoryTitle(stringResource(Strings.settings_cat_native_build_flags))
        SettingsCard {
            val emptyText = stringResource(Strings.settings_project_native_paths_empty)
            val cFlagsValue = ProjectSettingsSectionSupport.summarizeFlagValue(
                state.projectNativeCFlags,
                emptyText
            )
            val cppFlagsValue = ProjectSettingsSectionSupport.summarizeFlagValue(
                state.projectNativeCppFlags,
                emptyText
            )
            val ldFlagsValue = ProjectSettingsSectionSupport.summarizeFlagValue(
                state.projectNativeLdFlags,
                emptyText
            )
            val ldLibsValue = ProjectSettingsSectionSupport.summarizeFlagValue(
                state.projectNativeLdLibs,
                emptyText
            )
            val cmakeArgsValue = resolveProjectSettingsText(
                ProjectSettingsSectionSupport.resolveCollectionSummary(
                    state.projectNativeCMakeArgs.size
                )
            )

            SettingsClickableItem(
                title = stringResource(NativeBuildFlagType.CFLAGS.titleRes),
                subtitle = stringResource(NativeBuildFlagType.CFLAGS.subtitleRes),
                value = cFlagsValue,
                onClick = { editingBuildFlagType = NativeBuildFlagType.CFLAGS }
            )
            SettingsClickableItem(
                title = stringResource(NativeBuildFlagType.CXXFLAGS.titleRes),
                subtitle = stringResource(NativeBuildFlagType.CXXFLAGS.subtitleRes),
                value = cppFlagsValue,
                onClick = { editingBuildFlagType = NativeBuildFlagType.CXXFLAGS }
            )
            SettingsClickableItem(
                title = stringResource(NativeBuildFlagType.LDFLAGS.titleRes),
                subtitle = stringResource(NativeBuildFlagType.LDFLAGS.subtitleRes),
                value = ldFlagsValue,
                onClick = { editingBuildFlagType = NativeBuildFlagType.LDFLAGS }
            )
            SettingsClickableItem(
                title = stringResource(NativeBuildFlagType.LDLIBS.titleRes),
                subtitle = stringResource(NativeBuildFlagType.LDLIBS.subtitleRes),
                value = ldLibsValue,
                onClick = { editingBuildFlagType = NativeBuildFlagType.LDLIBS }
            )
            SettingsClickableItem(
                title = stringResource(NativeBuildFlagType.CMAKE_ARGS.titleRes),
                subtitle = stringResource(NativeBuildFlagType.CMAKE_ARGS.subtitleRes),
                value = cmakeArgsValue,
                onClick = { editingBuildFlagType = NativeBuildFlagType.CMAKE_ARGS },
                showDivider = false
            )
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    if (showNewProjectLocationDialog) {
        TinaSingleChoiceDialog(
            title = stringResource(Strings.settings_new_project_default_source_location),
            options = ProjectSettingsSectionSupport.buildNewProjectSourceLocationOptions()
                .map { it.value to stringResource(it.labelRes) },
            selectedValue = state.newProjectDefaultSourceLocation.value,
            onSelected = { value ->
                ProjectSettingsSectionSupport.resolveNewProjectSourceLocation(value)
                    ?.let(viewModel::setNewProjectDefaultSourceLocation)
                showNewProjectLocationDialog = false
            },
            onDismiss = { showNewProjectLocationDialog = false }
        )
    }

    if (showAutoSaveDialog) {
        val options = ProjectSettingsSectionSupport.buildAutoSaveIntervalOptions()
        TinaSingleChoiceDialog(
            title = stringResource(Strings.settings_auto_save_interval),
            options = options.map { it.value.toString() to stringResource(it.labelRes) },
            selectedValue = state.projectAutoSaveInterval.toString(),
            onSelected = { value ->
                viewModel.setProjectAutoSaveInterval(value.toInt())
                showAutoSaveDialog = false
            },
            onDismiss = { showAutoSaveDialog = false }
        )
    }

    if (showApkExportDialog) {
        TinaSingleChoiceDialog(
            title = stringResource(Strings.settings_project_apk_export_type),
            options = ProjectSettingsSectionSupport.buildProjectApkExportTypeOptions()
                .map { it.value to stringResource(it.labelRes) },
            selectedValue = state.projectApkExportType?.name,
            onSelected = { value ->
                ProjectSettingsSectionSupport.resolveProjectApkExportType(value)
                    ?.let(viewModel::updateProjectApkExportType)
                showApkExportDialog = false
            },
            onDismiss = { showApkExportDialog = false }
        )
    }

    val activePathType = editingPathType
    if (activePathType != null) {
        val currentPaths = when (activePathType) {
            NativeDependencyPathType.INCLUDE -> state.projectNativeIncludeDirs
            NativeDependencyPathType.LIBRARY -> state.projectNativeLibraryDirs
            NativeDependencyPathType.RUNTIME -> state.projectNativeRuntimeDirs
        }
        NativeDependencyPathEditorDialog(
            title = stringResource(activePathType.titleRes),
            initialPaths = currentPaths,
            onConfirm = { updatedPaths ->
                when (activePathType) {
                    NativeDependencyPathType.INCLUDE -> {
                        viewModel.updateProjectNativeDependencyPaths(
                            includeDirs = updatedPaths,
                            libraryDirs = state.projectNativeLibraryDirs,
                            runtimeDirs = state.projectNativeRuntimeDirs
                        )
                    }

                    NativeDependencyPathType.LIBRARY -> {
                        viewModel.updateProjectNativeDependencyPaths(
                            includeDirs = state.projectNativeIncludeDirs,
                            libraryDirs = updatedPaths,
                            runtimeDirs = state.projectNativeRuntimeDirs
                        )
                    }

                    NativeDependencyPathType.RUNTIME -> {
                        viewModel.updateProjectNativeDependencyPaths(
                            includeDirs = state.projectNativeIncludeDirs,
                            libraryDirs = state.projectNativeLibraryDirs,
                            runtimeDirs = updatedPaths
                        )
                    }
                }
                editingPathType = null
            },
            onDismiss = { editingPathType = null },
            hintText = stringResource(Strings.settings_project_native_paths_edit_hint),
            placeholderText = stringResource(Strings.settings_project_native_paths_edit_placeholder)
        )
    }

    val activeBuildFlagType = editingBuildFlagType
    if (activeBuildFlagType != null) {
        if (activeBuildFlagType == NativeBuildFlagType.CMAKE_ARGS) {
            NativeDependencyPathEditorDialog(
                title = stringResource(activeBuildFlagType.titleRes),
                initialPaths = state.projectNativeCMakeArgs,
                onConfirm = { updatedArgs ->
                    viewModel.updateProjectNativeBuildFlags(
                        cFlags = state.projectNativeCFlags,
                        cppFlags = state.projectNativeCppFlags,
                        ldFlags = state.projectNativeLdFlags,
                        ldLibs = state.projectNativeLdLibs,
                        cmakeArgs = updatedArgs
                    )
                    editingBuildFlagType = null
                },
                onDismiss = { editingBuildFlagType = null },
                hintText = stringResource(Strings.settings_project_native_cmake_args_edit_hint),
                placeholderText = stringResource(Strings.settings_project_native_cmake_args_edit_placeholder)
            )
        } else {
            val initialValue = when (activeBuildFlagType) {
                NativeBuildFlagType.CFLAGS -> state.projectNativeCFlags
                NativeBuildFlagType.CXXFLAGS -> state.projectNativeCppFlags
                NativeBuildFlagType.LDFLAGS -> state.projectNativeLdFlags
                NativeBuildFlagType.LDLIBS -> state.projectNativeLdLibs
                NativeBuildFlagType.CMAKE_ARGS -> ""
            }
            NativeBuildFlagEditorDialog(
                title = stringResource(activeBuildFlagType.titleRes),
                initialValue = initialValue,
                onConfirm = { updatedValue ->
                    val normalizedValue = ProjectSettingsSectionSupport.normalizeFlagInput(
                        updatedValue
                    )
                    when (activeBuildFlagType) {
                        NativeBuildFlagType.CFLAGS -> viewModel.updateProjectNativeBuildFlags(
                            cFlags = normalizedValue,
                            cppFlags = state.projectNativeCppFlags,
                            ldFlags = state.projectNativeLdFlags,
                            ldLibs = state.projectNativeLdLibs,
                            cmakeArgs = state.projectNativeCMakeArgs
                        )

                        NativeBuildFlagType.CXXFLAGS -> viewModel.updateProjectNativeBuildFlags(
                            cFlags = state.projectNativeCFlags,
                            cppFlags = normalizedValue,
                            ldFlags = state.projectNativeLdFlags,
                            ldLibs = state.projectNativeLdLibs,
                            cmakeArgs = state.projectNativeCMakeArgs
                        )

                        NativeBuildFlagType.LDFLAGS -> viewModel.updateProjectNativeBuildFlags(
                            cFlags = state.projectNativeCFlags,
                            cppFlags = state.projectNativeCppFlags,
                            ldFlags = normalizedValue,
                            ldLibs = state.projectNativeLdLibs,
                            cmakeArgs = state.projectNativeCMakeArgs
                        )

                        NativeBuildFlagType.LDLIBS -> viewModel.updateProjectNativeBuildFlags(
                            cFlags = state.projectNativeCFlags,
                            cppFlags = state.projectNativeCppFlags,
                            ldFlags = state.projectNativeLdFlags,
                            ldLibs = normalizedValue,
                            cmakeArgs = state.projectNativeCMakeArgs
                        )

                        NativeBuildFlagType.CMAKE_ARGS -> Unit
                    }
                    editingBuildFlagType = null
                },
                onDismiss = { editingBuildFlagType = null }
            )
        }
    }
}

@Composable
private fun resolveProjectSettingsText(spec: ProjectSettingsTextSpec): String = if (spec.formatArgs.isEmpty()) {
    stringResource(spec.labelRes)
} else {
    stringResource(spec.labelRes, *spec.formatArgs.toTypedArray())
}

@Composable
private fun NativeDependencyPathEditorDialog(
    title: String,
    initialPaths: List<String>,
    onConfirm: (List<String>) -> Unit,
    onDismiss: () -> Unit,
    hintText: String,
    placeholderText: String
) {
    var inputText by remember(title, initialPaths) {
        mutableStateOf(initialPaths.joinToString(separator = "\n"))
    }

    TinaAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            TinaDialogTitleText(title)
        },
        text = {
            TinaDialogContentColumn {
                TinaDialogCard(
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    Text(
                        text = hintText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 180.dp, max = 320.dp),
                    singleLine = false,
                    minLines = 6,
                    maxLines = 12,
                    placeholder = {
                        Text(placeholderText)
                    }
                )
            }
        },
        confirmButton = {
            TinaPrimaryButton(
                text = stringResource(Strings.btn_confirm),
                onClick = {
                    onConfirm(ProjectSettingsSectionSupport.parsePathLines(inputText))
                }
            )
        },
        dismissButton = {
            TinaTextButton(
                text = stringResource(Strings.btn_cancel),
                onClick = onDismiss
            )
        }
    )
}

@Composable
private fun NativeBuildFlagEditorDialog(
    title: String,
    initialValue: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var inputText by remember(title, initialValue) { mutableStateOf(initialValue) }

    TinaAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            TinaDialogTitleText(title)
        },
        text = {
            TinaDialogContentColumn {
                TinaDialogCard(
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    Text(
                        text = stringResource(Strings.settings_project_native_flags_edit_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 140.dp, max = 280.dp),
                    singleLine = false,
                    minLines = 4,
                    maxLines = 10,
                    placeholder = {
                        Text(stringResource(Strings.settings_project_native_flags_edit_placeholder))
                    }
                )
            }
        },
        confirmButton = {
            TinaPrimaryButton(
                text = stringResource(Strings.btn_confirm),
                onClick = { onConfirm(inputText) }
            )
        },
        dismissButton = {
            TinaTextButton(
                text = stringResource(Strings.btn_cancel),
                onClick = onDismiss
            )
        }
    )
}
