package com.wuxianggujun.tinaide.database.user

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class UserContentEntitiesTest {

    @Test
    fun favoriteEntity_shouldDefaultToSynced() {
        val entity = FavoriteEntity(
            id = "fav-1",
            pluginId = "plugin",
            name = "Plugin",
            description = null,
            iconUrl = null,
            category = "tool",
            tags = """["cpp"]""",
            latestVersion = "1.0.0",
            addedAt = "2026-01-01T00:00:00Z"
        )

        assertThat(entity.synced).isTrue()
        assertThat(entity.tags).isEqualTo("""["cpp"]""")
    }

    @Test
    fun downloadHistoryEntity_shouldPreserveOptionalFieldsAndDefaultSynced() {
        val entity = DownloadHistoryEntity(
            id = "download-1",
            itemType = "plugin",
            itemId = "plugin-id",
            version = null,
            fileSize = null,
            downloadedAt = "2026-01-01T00:00:00Z"
        )

        assertThat(entity.synced).isTrue()
        assertThat(entity.version).isNull()
        assertThat(entity.fileSize).isNull()
    }
}
