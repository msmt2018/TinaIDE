package com.wuxianggujun.tinaide.ui.activity

import com.google.common.truth.Truth.assertThat
import com.wuxianggujun.tinaide.core.crash.CrashUploadState
import com.wuxianggujun.tinaide.core.crash.CrashUploadStatusTextResolver
import com.wuxianggujun.tinaide.core.i18n.Strings
import org.junit.Test

class CrashActivityUploadStatusTest {

    @Test
    fun crashUploadStatusTextResolver_shouldUseCalmColorForNormalStates() {
        val queued = CrashUploadStatusTextResolver.resolve(CrashUploadState.Status.QUEUED)
        val uploaded = CrashUploadStatusTextResolver.resolve(CrashUploadState.Status.UPLOADED)

        assertThat(queued.messageRes).isEqualTo(Strings.crash_upload_status_queued)
        assertThat(queued.isAttention).isFalse()
        assertThat(uploaded.messageRes).isEqualTo(Strings.crash_upload_status_uploaded)
        assertThat(uploaded.isAttention).isFalse()
    }

    @Test
    fun crashUploadStatusTextResolver_shouldUseAttentionColorForRetryFailedAndSkipped() {
        val retry = CrashUploadStatusTextResolver.resolve(CrashUploadState.Status.RETRY_PENDING)
        val failed = CrashUploadStatusTextResolver.resolve(CrashUploadState.Status.FAILED)
        val skipped = CrashUploadStatusTextResolver.resolve(CrashUploadState.Status.SKIPPED)

        assertThat(retry.messageRes).isEqualTo(Strings.crash_upload_status_retry_pending)
        assertThat(failed.messageRes).isEqualTo(Strings.crash_upload_status_failed)
        assertThat(skipped.messageRes).isEqualTo(Strings.crash_upload_status_skipped)
        assertThat(retry.isAttention).isTrue()
        assertThat(failed.isAttention).isTrue()
        assertThat(skipped.isAttention).isTrue()
    }
}
