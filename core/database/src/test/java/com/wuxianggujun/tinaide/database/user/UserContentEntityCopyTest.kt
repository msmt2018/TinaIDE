package com.wuxianggujun.tinaide.database.user

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class UserContentEntityCopyTest {

    @Test
    fun favoriteEntity_copyShouldKeepPrimaryIdentityAndAllowSyncTransition() {
        val unsynced = FavoriteEntity(
            id = "fav-1",
            pluginId = "plugin-a",
            name = "Plugin A",
            description = "old",
            iconUrl = null,
            category = "tool",
            tags = """["cpp"]""",
            latestVersion = "1.0.0",
            addedAt = "2026-01-01T00:00:00Z",
            synced = false
        )

        val synced = unsynced.copy(description = "new", synced = true)

        assertThat(synced.id).isEqualTo("fav-1")
        assertThat(synced.pluginId).isEqualTo("plugin-a")
        assertThat(synced.description).isEqualTo("new")
        assertThat(synced.synced).isTrue()
    }

    @Test
    fun downloadHistoryEntity_copyShouldPreserveItemIdentityWhenMarkingUnsynced() {
        val downloaded = DownloadHistoryEntity(
            id = "download-1",
            itemType = "plugin",
            itemId = "plugin-a",
            version = "1.0.0",
            fileSize = 2048L,
            downloadedAt = "2026-01-01T00:00:00Z",
            synced = true
        )

        val unsynced = downloaded.copy(version = "1.1.0", synced = false)

        assertThat(unsynced.id).isEqualTo("download-1")
        assertThat(unsynced.itemType).isEqualTo("plugin")
        assertThat(unsynced.itemId).isEqualTo("plugin-a")
        assertThat(unsynced.version).isEqualTo("1.1.0")
        assertThat(unsynced.fileSize).isEqualTo(2048L)
        assertThat(unsynced.synced).isFalse()
    }
}
