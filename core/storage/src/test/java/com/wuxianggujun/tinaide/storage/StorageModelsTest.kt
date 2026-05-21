package com.wuxianggujun.tinaide.storage

import com.google.common.truth.Truth.assertThat
import com.wuxianggujun.tinaide.storage.db.ProjectLocationEntity
import org.junit.Test

class StorageModelsTest {

    @Test
    fun requestRuntime_shouldComparePermissionArraysByContent() {
        val first = StoragePermissionRequest.RequestRuntime(
            arrayOf("android.permission.READ_EXTERNAL_STORAGE", "android.permission.WRITE_EXTERNAL_STORAGE")
        )
        val second = StoragePermissionRequest.RequestRuntime(
            arrayOf("android.permission.READ_EXTERNAL_STORAGE", "android.permission.WRITE_EXTERNAL_STORAGE")
        )
        val differentOrder = StoragePermissionRequest.RequestRuntime(
            arrayOf("android.permission.WRITE_EXTERNAL_STORAGE", "android.permission.READ_EXTERNAL_STORAGE")
        )

        assertThat(first).isEqualTo(second)
        assertThat(first.hashCode()).isEqualTo(second.hashCode())
        assertThat(first).isNotEqualTo(differentOrder)
    }

    @Test
    fun projectLocationEntity_shouldRoundTripDomainModel() {
        val location = ProjectLocation(
            projectId = "project-id",
            projectDirName = "Demo",
            sourceRootPath = "/storage/projects/Demo",
            registered = 123L
        )

        val entity = ProjectLocationEntity.fromDomainModel(location)
        val roundTripped = entity.toDomainModel()

        assertThat(entity.id).isEqualTo(0L)
        assertThat(entity.projectId).isEqualTo("project-id")
        assertThat(entity.projectDirName).isEqualTo("Demo")
        assertThat(entity.sourceRootPath).isEqualTo("/storage/projects/Demo")
        assertThat(entity.registered).isEqualTo(123L)
        assertThat(roundTripped).isEqualTo(location)
    }

    @Test
    fun legacyPendingSourceRootPrefix_shouldBeStableForMigrationSentinel() {
        assertThat(ProjectLocationEntity.LEGACY_PENDING_SOURCE_ROOT_PREFIX)
            .isEqualTo("__legacy_pending__/")
    }
}
