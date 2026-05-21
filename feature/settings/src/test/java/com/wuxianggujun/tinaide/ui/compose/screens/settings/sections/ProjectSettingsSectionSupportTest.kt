package com.wuxianggujun.tinaide.ui.compose.screens.settings.sections

import com.google.common.truth.Truth.assertThat
import com.wuxianggujun.tinaide.core.i18n.Strings
import com.wuxianggujun.tinaide.project.ProjectApkExportType
import org.junit.Test

class ProjectSettingsSectionSupportTest {

    @Test
    fun resolveAutoSaveIntervalText_shouldCoverPresetAndCustomValues() {
        assertThat(
            ProjectSettingsSectionSupport.resolveAutoSaveIntervalText(0)
        ).isEqualTo(ProjectSettingsTextSpec(Strings.interval_off))
        assertThat(
            ProjectSettingsSectionSupport.resolveAutoSaveIntervalText(30)
        ).isEqualTo(ProjectSettingsTextSpec(Strings.interval_30s))
        assertThat(
            ProjectSettingsSectionSupport.resolveAutoSaveIntervalText(60)
        ).isEqualTo(ProjectSettingsTextSpec(Strings.interval_60s))
        assertThat(
            ProjectSettingsSectionSupport.resolveAutoSaveIntervalText(300)
        ).isEqualTo(ProjectSettingsTextSpec(Strings.interval_5min))
        assertThat(
            ProjectSettingsSectionSupport.resolveAutoSaveIntervalText(90)
        ).isEqualTo(
            ProjectSettingsTextSpec(
                labelRes = Strings.project_autosave_interval_seconds,
                formatArgs = listOf(90)
            )
        )
    }

    @Test
    fun buildAutoSaveIntervalOptions_shouldKeepDialogOrder() {
        assertThat(
            ProjectSettingsSectionSupport.buildAutoSaveIntervalOptions()
        ).containsExactly(
            ProjectSettingsOptionSpec(value = 0, labelRes = Strings.interval_off),
            ProjectSettingsOptionSpec(value = 30, labelRes = Strings.interval_30s),
            ProjectSettingsOptionSpec(value = 60, labelRes = Strings.interval_60s),
            ProjectSettingsOptionSpec(value = 300, labelRes = Strings.interval_5min)
        ).inOrder()
    }

    @Test
    fun resolveProjectAvailabilityAndName_shouldHandleNullAndBlank() {
        assertThat(
            ProjectSettingsSectionSupport.hasProjectOpened(null)
        ).isFalse()
        assertThat(
            ProjectSettingsSectionSupport.hasProjectOpened("   ")
        ).isFalse()
        assertThat(
            ProjectSettingsSectionSupport.hasProjectOpened("/workspace/demo")
        ).isTrue()

        assertThat(
            ProjectSettingsSectionSupport.resolveCurrentProjectName(null, "未打开")
        ).isEqualTo("未打开")
        assertThat(
            ProjectSettingsSectionSupport.resolveCurrentProjectName("Demo", "未打开")
        ).isEqualTo("Demo")
    }

    @Test
    fun resolveCollectionSummary_shouldSwitchBetweenEmptyAndCount() {
        assertThat(
            ProjectSettingsSectionSupport.resolveCollectionSummary(0)
        ).isEqualTo(ProjectSettingsTextSpec(Strings.settings_project_native_paths_empty))
        assertThat(
            ProjectSettingsSectionSupport.resolveCollectionSummary(-1)
        ).isEqualTo(ProjectSettingsTextSpec(Strings.settings_project_native_paths_empty))
        assertThat(
            ProjectSettingsSectionSupport.resolveCollectionSummary(3)
        ).isEqualTo(
            ProjectSettingsTextSpec(
                labelRes = Strings.settings_project_native_paths_count,
                formatArgs = listOf(3)
            )
        )
    }

    @Test
    fun summarizeFlagValue_shouldTrimEllipsizeAndFallbackToEmptyText() {
        assertThat(
            ProjectSettingsSectionSupport.summarizeFlagValue("   ", "空")
        ).isEqualTo("空")
        assertThat(
            ProjectSettingsSectionSupport.summarizeFlagValue("  -Wall  ", "空")
        ).isEqualTo("-Wall")
        assertThat(
            ProjectSettingsSectionSupport.summarizeFlagValue(
                "-DDEBUG -Winvalid-pch -fvisibility=hidden -Winvalid-pch",
                "空"
            )
        ).isEqualTo("-DDEBUG -Winvalid-pch -fvisibility=hidden -Wi...")
    }

    @Test
    fun parsePathLines_shouldTrimFilterAndDeduplicate() {
        assertThat(
            ProjectSettingsSectionSupport.parsePathLines(
                " include \n\nlib\ninclude\n  runtime  \n"
            )
        ).containsExactly("include", "lib", "runtime").inOrder()
    }

    @Test
    fun normalizeFlagInput_shouldCollapseNonBlankLinesIntoSingleLine() {
        assertThat(
            ProjectSettingsSectionSupport.normalizeFlagInput("   ")
        ).isEmpty()
        assertThat(
            ProjectSettingsSectionSupport.normalizeFlagInput(
                "  -Wall \n\n -Winvalid-pch \n  -DDEBUG  "
            )
        ).isEqualTo("-Wall -Winvalid-pch -DDEBUG")
    }

    @Test
    fun resolveProjectApkExportTypeLabelAndOptions_shouldCoverAllKnownValues() {
        assertThat(
            ProjectSettingsSectionSupport.resolveProjectApkExportTypeLabelRes(
                ProjectApkExportType.NATIVE_ACTIVITY
            )
        ).isEqualTo(Strings.apk_builder_template_native)
        assertThat(
            ProjectSettingsSectionSupport.resolveProjectApkExportTypeLabelRes(
                ProjectApkExportType.SDL3
            )
        ).isEqualTo(Strings.apk_builder_template_sdl3)
        assertThat(
            ProjectSettingsSectionSupport.resolveProjectApkExportTypeLabelRes(
                ProjectApkExportType.TERMINAL
            )
        ).isEqualTo(Strings.apk_builder_template_terminal)
        assertThat(
            ProjectSettingsSectionSupport.resolveProjectApkExportTypeLabelRes(
                ProjectApkExportType.DISABLED
            )
        ).isEqualTo(Strings.settings_project_apk_export_disabled)
        assertThat(
            ProjectSettingsSectionSupport.resolveProjectApkExportTypeLabelRes(null)
        ).isEqualTo(Strings.settings_project_apk_export_not_detected)

        assertThat(
            ProjectSettingsSectionSupport.buildProjectApkExportTypeOptions()
        ).containsExactly(
            ProjectSettingsOptionSpec(
                value = ProjectApkExportType.NATIVE_ACTIVITY.name,
                labelRes = Strings.apk_builder_template_native
            ),
            ProjectSettingsOptionSpec(
                value = ProjectApkExportType.SDL3.name,
                labelRes = Strings.apk_builder_template_sdl3
            ),
            ProjectSettingsOptionSpec(
                value = ProjectApkExportType.TERMINAL.name,
                labelRes = Strings.apk_builder_template_terminal
            ),
            ProjectSettingsOptionSpec(
                value = ProjectApkExportType.DISABLED.name,
                labelRes = Strings.settings_project_apk_export_disabled
            )
        ).inOrder()

        assertThat(
            ProjectSettingsSectionSupport.resolveProjectApkExportType(
                ProjectApkExportType.TERMINAL.name
            )
        ).isEqualTo(ProjectApkExportType.TERMINAL)
        assertThat(
            ProjectSettingsSectionSupport.resolveProjectApkExportType(
                ProjectApkExportType.SDL3.name
            )
        ).isEqualTo(ProjectApkExportType.SDL3)
        assertThat(
            ProjectSettingsSectionSupport.resolveProjectApkExportType("unknown")
        ).isNull()
    }
}
