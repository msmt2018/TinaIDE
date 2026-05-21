package com.wuxianggujun.tinaide.core.logging

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LogExportPolicyTest {

    @Test
    fun `manual server upload includes project and runtime diagnostics after confirmation`() {
        val policy = LogExportPolicy.from(LogExportProfile.SERVER_UPLOAD)

        assertThat(policy.includes(LogBundleSource.BUILD_LOGS)).isTrue()
        assertThat(policy.includes(LogBundleSource.ROOTFS_PACKAGE_MANAGER_LOGS)).isTrue()
        assertThat(policy.includes(LogBundleSource.PROOT_LOGS)).isTrue()
        val consentRequiredPrivacyClasses = setOf(LogExportPrivacyClass.USER_PROJECT, LogExportPrivacyClass.USER_RUNTIME)
        assertThat(policy.sources.map { it.privacyClass }.toSet().intersect(consentRequiredPrivacyClasses))
            .containsExactly(LogExportPrivacyClass.USER_PROJECT, LogExportPrivacyClass.USER_RUNTIME)
    }

    @Test
    fun `server upload keeps app process logcat without global fallback`() {
        val policy = LogExportPolicy.from(LogExportProfile.SERVER_UPLOAD)

        assertThat(policy.includes(LogBundleSource.LOGCAT)).isTrue()
        assertThat(policy.logcatEntryName).isEqualTo("app_process_logcat.txt")
        assertThat(policy.allowGlobalLogcatFallback).isFalse()
        assertThat(policy.includeUserRuntimeTombstones).isFalse()
    }

    @Test
    fun `local export keeps complete user controlled diagnostics`() {
        val policy = LogExportPolicy.from(LogExportProfile.LOCAL_EXPORT)

        assertThat(LogBundleSource.values().all { policy.includes(it) }).isTrue()
        assertThat(policy.logcatEntryName).isEqualTo("logcat.txt")
        assertThat(policy.allowGlobalLogcatFallback).isTrue()
        assertThat(policy.includeUserRuntimeTombstones).isTrue()
    }

    @Test
    fun `manifest records privacy gate and excluded server sources`() {
        val policy = LogExportPolicy.from(LogExportProfile.SERVER_UPLOAD)
        val manifest = LogExportManifestBuilder.build(policy, generatedAt = "2026-04-29 10:00:00")

        assertThat(manifest).contains("profile=SERVER_UPLOAD")
        assertThat(manifest).contains("global_logcat_fallback=false")
        assertThat(manifest).contains("include_user_runtime_tombstones=false")
        assertThat(manifest).contains("requires_explicit_user_consent=true")
        assertThat(manifest).contains("allowed_privacy_classes=EXPORT_METADATA, APP_DIAGNOSTIC, HOST_CRASH, USER_PROJECT, USER_RUNTIME")
        assertThat(manifest).contains("consent_required_privacy_classes=USER_PROJECT, USER_RUNTIME")
        assertThat(manifest).contains("excluded_privacy_classes=NONE")
        assertThat(manifest).contains("- name=BUILD_LOGS")
        assertThat(manifest).contains("  included=true")
    }
}
