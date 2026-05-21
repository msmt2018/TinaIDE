package com.wuxianggujun.tinaide.project

import android.content.Context
import com.wuxianggujun.tinaide.core.i18n.Strings
import com.wuxianggujun.tinaide.core.i18n.strOr
import java.io.File

sealed interface ProjectTemplateSpec {
    val buildSystem: ProjectBuildSystem
    val primaryLanguage: ProjectLanguage
    val isNdkTemplate: Boolean

    data class Asset(
        val type: ProjectTemplateInstaller.TemplateType
    ) : ProjectTemplateSpec {
        override val buildSystem: ProjectBuildSystem = type.buildSystem
        override val primaryLanguage: ProjectLanguage = type.primaryLanguage
        override val isNdkTemplate: Boolean = type.isNdkTemplate
    }

    data class Zip(
        val id: String,
        val zipFile: File,
        override val buildSystem: ProjectBuildSystem,
        override val primaryLanguage: ProjectLanguage = ProjectLanguage.CPP,
        override val isNdkTemplate: Boolean = false
    ) : ProjectTemplateSpec
}

data class ProjectTemplateOption(
    val id: String,
    val displayName: String,
    val description: String,
    val spec: ProjectTemplateSpec,
    val isRecommended: Boolean = false
)

object BuiltInProjectTemplates {
    val defaultTemplateId: String = "builtin:${ProjectTemplateInstaller.TemplateType.CPP_SINGLE_FILE.name}"

    fun createOptions(context: Context): List<ProjectTemplateOption> {
        return listOf(
            createBuiltInOption(
                context = context,
                type = ProjectTemplateInstaller.TemplateType.CPP_SINGLE_FILE,
                displayName = Strings.template_cpp_single.strOr(context),
                description = Strings.template_desc_cpp_single.strOr(context),
                isRecommended = true
            ),
            createBuiltInOption(
                context = context,
                type = ProjectTemplateInstaller.TemplateType.CMAKE_EXECUTABLE,
                displayName = Strings.template_cmake_exe.strOr(context),
                description = Strings.template_desc_cmake_exe.strOr(context)
            ),
            createBuiltInOption(
                context = context,
                type = ProjectTemplateInstaller.TemplateType.CMAKE_LIBRARY,
                displayName = Strings.template_cmake_lib.strOr(context),
                description = Strings.template_desc_cmake_lib.strOr(context)
            ),
            createBuiltInOption(
                context = context,
                type = ProjectTemplateInstaller.TemplateType.MAKE_EXECUTABLE,
                displayName = Strings.template_make_exe.strOr(context),
                description = Strings.template_desc_make_exe.strOr(context)
            ),
            createBuiltInOption(
                context = context,
                type = ProjectTemplateInstaller.TemplateType.NDK_SHARED_LIBRARY,
                displayName = Strings.template_ndk_shared_lib.strOr(context),
                description = Strings.template_desc_ndk_shared_lib.strOr(context)
            )
        )
    }

    private fun createBuiltInOption(
        context: Context,
        type: ProjectTemplateInstaller.TemplateType,
        displayName: String,
        description: String,
        isRecommended: Boolean = false
    ): ProjectTemplateOption {
        return ProjectTemplateOption(
            id = "builtin:${type.name}",
            displayName = displayName,
            description = description,
            spec = ProjectTemplateSpec.Asset(type),
            isRecommended = isRecommended
        )
    }
}
