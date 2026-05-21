package com.wuxianggujun.tinaide.storage

import android.content.Intent

sealed class StoragePermissionRequest {
    object AlreadyGranted : StoragePermissionRequest()
    data class OpenSettings(val intent: Intent) : StoragePermissionRequest()
    data class RequestRuntime(val permissions: Array<String>) : StoragePermissionRequest() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is RequestRuntime) return false
            return permissions.contentEquals(other.permissions)
        }

        override fun hashCode(): Int = permissions.contentHashCode()
    }
    object NoActionNeeded : StoragePermissionRequest()
}
