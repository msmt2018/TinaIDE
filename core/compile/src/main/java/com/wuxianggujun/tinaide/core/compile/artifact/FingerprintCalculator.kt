package com.wuxianggujun.tinaide.core.compile.artifact

import com.wuxianggujun.tinaide.core.compile.BuildOptions
import com.wuxianggujun.tinaide.core.compile.strategy.BuildContext

/**
 * 鏋勫缓鎸囩汗璁＄畻鍣細鏍规嵁 [BuildContext.options] 涓?[ArtifactSpec] 鐢熸垚
 * [BuildFingerprint]銆? */
class FingerprintCalculator {

    fun compute(ctx: BuildContext, spec: ArtifactSpec): BuildFingerprint {
        val options = ctx.options
        return BuildFingerprint(
            compilerType = options.compilerType.name,
            compilerPath = resolveCompilerPath(options),
            toolchainId = options.toolchainId,
            sysrootProfileId = options.sysrootProfileId,
            sysrootApiLevel = options.sysrootApiLevel,
            buildType = options.buildType.name,
            cmakeBuildType = options.cmakeBuildType.cmakeValue,
            cmakeGenerator = options.cmakeGenerator.cmakeValue,
            cFlags = options.nativeCFlags.trim(),
            cppFlags = options.nativeCppFlags.trim(),
            ldFlags = options.nativeLdFlags.trim(),
            ldLibs = options.nativeLdLibs.trim(),
            cmakeExtraArgs = options.nativeCMakeArgs.joinToString(" "),
            cppStandard = options.cppStandard?.trim(),
            optimizationLevel = options.optimizationLevel.trim(),
            generateDebugInfo = options.generateDebugInfo,
            preferSharedLibraryForRun = options.preferSharedLibraryForRun,
            parallelJobs = options.parallelJobs,
            resolvedRunMode = options.resolvedRunMode.name,
            artifactKind = spec.kind.name,
            expectedOutputPath = normalizePath(spec.expectedPath, ctx.buildDir),
            trackedInputsHash = TrackedInputHasher.hashFiles(spec.sources, ctx.projectRoot),
            reconfigureInputsHash = spec.reconfigureSources.takeIf { it.isNotEmpty() }
                ?.let { TrackedInputHasher.hashFiles(it, ctx.projectRoot) },
            extraEnvHash = null,
        )
    }

    /**
     * 瑙ｆ瀽鍑哄疄闄呭叆鎸囩汗鐨?compiler 璺緞銆?     */
    private fun resolveCompilerPath(options: BuildOptions): String = when (options.compilerType.name) {
        "CUSTOM" -> buildString {
            append("custom:")
            append(options.customCCompiler.orEmpty())
            append("|")
            append(options.customCppCompiler.orEmpty())
        }
        else -> "${options.compilerType.name.lowercase()}:${options.toolchainId?.trim()?.takeIf { it.isNotEmpty() } ?: "active"}"
    }

    private fun normalizePath(file: java.io.File, baseDir: java.io.File): String =
        file.absoluteFile.relativeToOrSelf(baseDir.absoluteFile).path.replace(java.io.File.separatorChar, '/')
}
