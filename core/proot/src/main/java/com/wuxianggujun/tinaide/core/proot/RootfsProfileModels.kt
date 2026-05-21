package com.wuxianggujun.tinaide.core.proot

import kotlinx.serialization.Serializable

@Serializable
enum class RootfsSourceType {
    LINUX_DISTRO,
}

@Serializable
enum class RootfsPackageManager {
    APK,
    APT,
    PACMAN,
    DNF,
    UNKNOWN,
}

@Serializable
data class RootfsProfile(
    val id: String,
    val displayName: String,
    val distroId: String,
    val distroName: String,
    val rootfsPath: String,
    val sourceType: RootfsSourceType,
    val packageManager: RootfsPackageManager = RootfsPackageManager.UNKNOWN,
    val shellPath: String = DEFAULT_SHELL_PATH,
    val createdAt: Long,
    val updatedAt: Long,
) {
    companion object {
        const val DEFAULT_SHELL_PATH = "/bin/sh"
    }
}

@Serializable
data class RootfsProfilesState(
    val activeProfileId: String,
    val profiles: List<RootfsProfile>,
)
