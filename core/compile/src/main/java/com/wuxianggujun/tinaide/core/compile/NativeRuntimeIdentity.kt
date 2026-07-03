package com.wuxianggujun.tinaide.core.compile

/**
 * Minimal identity for the native Android sysroot/runtime selected for a build.
 *
 * Keep this small so build artifacts, CMake cache, compile_commands, and clangd
 * reuse all invalidate on the same facts.
 */
data class NativeRuntimeIdentity(
    val sysrootProfileId: String?,
    val sysrootApiLevel: Int,
) {
    val cmakeProfileId: String
        get() = profileIdForCMake(sysrootProfileId)

    companion object {
        const val CMAKE_SYSROOT_PROFILE_ID_KEY = "TINA_SYSROOT_PROFILE_ID"
        const val CMAKE_SYSROOT_API_LEVEL_KEY = "TINA_SYSROOT_API_LEVEL"

        val DEFAULT = NativeRuntimeIdentity(
            sysrootProfileId = null,
            sysrootApiLevel = MakeCommandOverrides.DEFAULT_SYSROOT_API_LEVEL,
        )

        fun from(options: BuildOptions): NativeRuntimeIdentity = NativeRuntimeIdentity(
            sysrootProfileId = options.sysrootProfileId?.trim()?.takeIf { it.isNotEmpty() },
            sysrootApiLevel = options.sysrootApiLevel,
        )

        fun profileIdForCMake(value: String?): String =
            value?.trim()?.takeIf { it.isNotEmpty() } ?: "<active>"
    }
}
