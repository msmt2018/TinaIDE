package com.wuxianggujun.tinaide.core.crash

import com.google.common.truth.Truth.assertThat
import com.wuxianggujun.tinaide.core.i18n.Strings
import org.junit.Test

class CrashUploadStatusTextResolverTest {

    @Test
    fun resolve_shouldMapPassiveStatusesWithoutAttentionFlag() {
        assertThat(CrashUploadStatusTextResolver.resolve(CrashUploadState.Status.NONE))
            .isEqualTo(CrashUploadStatusTextSpec(Strings.crash_upload_status_none, false))
        assertThat(CrashUploadStatusTextResolver.resolve(CrashUploadState.Status.QUEUED))
            .isEqualTo(CrashUploadStatusTextSpec(Strings.crash_upload_status_queued, false))
        assertThat(CrashUploadStatusTextResolver.resolve(CrashUploadState.Status.UPLOADING))
            .isEqualTo(CrashUploadStatusTextSpec(Strings.crash_upload_status_uploading, false))
        assertThat(CrashUploadStatusTextResolver.resolve(CrashUploadState.Status.UPLOADED))
            .isEqualTo(CrashUploadStatusTextSpec(Strings.crash_upload_status_uploaded, false))
    }

    @Test
    fun resolve_shouldMarkSkippedRetryAndFailedAsAttention() {
        assertThat(CrashUploadStatusTextResolver.resolve(CrashUploadState.Status.SKIPPED))
            .isEqualTo(CrashUploadStatusTextSpec(Strings.crash_upload_status_skipped, true))
        assertThat(CrashUploadStatusTextResolver.resolve(CrashUploadState.Status.RETRY_PENDING))
            .isEqualTo(CrashUploadStatusTextSpec(Strings.crash_upload_status_retry_pending, true))
        assertThat(CrashUploadStatusTextResolver.resolve(CrashUploadState.Status.FAILED))
            .isEqualTo(CrashUploadStatusTextSpec(Strings.crash_upload_status_failed, true))
    }
}
