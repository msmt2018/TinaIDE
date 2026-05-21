package com.wuxianggujun.tinaide.project

import android.content.Context
import java.io.File
import timber.log.Timber

/**
 * 项目创建服务
 *
 * 统一封装项目目录创建、模板安装和失败清理逻辑，
 * 供项目列表、新建向导等入口复用。
 */
object ProjectCreationService {
    private const val TAG = "ProjectCreation"

    fun createProject(
        context: Context,
        projectRoot: File,
        projectName: String,
        templateType: ProjectTemplateInstaller.TemplateType = ProjectTemplateInstaller.TemplateType.CPP_SINGLE_FILE,
        cppStandard: CppStandard = CppStandard.DEFAULT,
        ndkApiLevel: AndroidApiLevel? = null
    ): ProjectCreationResult {
        return createProject(
            context = context,
            projectRoot = projectRoot,
            projectName = projectName,
            templateSpec = ProjectTemplateSpec.Asset(templateType),
            cppStandard = cppStandard,
            ndkApiLevel = ndkApiLevel
        )
    }

    fun createProject(
        context: Context,
        projectRoot: File,
        projectName: String,
        templateSpec: ProjectTemplateSpec,
        cppStandard: CppStandard = CppStandard.DEFAULT,
        ndkApiLevel: AndroidApiLevel? = null
    ): ProjectCreationResult {
        val normalizedName = projectName.trim()
        if (normalizedName.isBlank()) {
            return ProjectCreationResult.Failure(ProjectCreationFailure.EMPTY_NAME)
        }

        if (projectRoot.path.isBlank()) {
            return ProjectCreationResult.Failure(
                reason = ProjectCreationFailure.PROJECT_ROOT_UNAVAILABLE,
                detail = projectRoot.path
            )
        }

        if (!projectRoot.exists() && !projectRoot.mkdirs()) {
            return ProjectCreationResult.Failure(
                reason = ProjectCreationFailure.PROJECT_ROOT_UNAVAILABLE,
                detail = projectRoot.absolutePath
            )
        }

        if (!projectRoot.isDirectory) {
            return ProjectCreationResult.Failure(
                reason = ProjectCreationFailure.PROJECT_ROOT_UNAVAILABLE,
                detail = projectRoot.absolutePath
            )
        }

        val projectDir = File(projectRoot, normalizedName)
        if (projectDir.exists()) {
            return ProjectCreationResult.Failure(
                reason = ProjectCreationFailure.ALREADY_EXISTS,
                detail = projectDir.absolutePath
            )
        }

        if (!projectDir.mkdirs()) {
            return ProjectCreationResult.Failure(
                reason = ProjectCreationFailure.CREATE_DIRECTORY_FAILED,
                detail = projectDir.absolutePath
            )
        }

        val templateInstalled = runCatching {
            when (templateSpec) {
                is ProjectTemplateSpec.Asset -> ProjectTemplateInstaller.install(
                    context = context,
                    destDir = projectDir,
                    projectName = normalizedName,
                    type = templateSpec.type,
                    cppStandard = cppStandard,
                    ndkApiLevel = ndkApiLevel
                )
                is ProjectTemplateSpec.Zip -> ProjectTemplateInstaller.install(
                    destDir = projectDir,
                    projectName = normalizedName,
                    templateSpec = templateSpec,
                    cppStandard = cppStandard,
                    ndkApiLevel = ndkApiLevel
                )
            }
        }.onFailure { throwable ->
            Timber.tag(TAG).e(throwable, "Create project failed: %s", normalizedName)
        }.getOrDefault(false)

        if (!templateInstalled) {
            cleanupFailedProject(projectDir)
            return ProjectCreationResult.Failure(
                reason = ProjectCreationFailure.TEMPLATE_INSTALL_FAILED,
                detail = projectDir.absolutePath
            )
        }

        return ProjectCreationResult.Success(projectDir)
    }

    private fun cleanupFailedProject(projectDir: File) {
        runCatching {
            if (projectDir.exists()) {
                projectDir.deleteRecursively()
            }
        }.onFailure { throwable ->
            Timber.tag(TAG).w(throwable, "Failed to cleanup project dir: %s", projectDir.absolutePath)
        }
    }
}

sealed interface ProjectCreationResult {
    data class Success(val projectDir: File) : ProjectCreationResult
    data class Failure(
        val reason: ProjectCreationFailure,
        val detail: String? = null
    ) : ProjectCreationResult
}

enum class ProjectCreationFailure {
    EMPTY_NAME,
    PROJECT_ROOT_UNAVAILABLE,
    ALREADY_EXISTS,
    CREATE_DIRECTORY_FAILED,
    TEMPLATE_INSTALL_FAILED
}
