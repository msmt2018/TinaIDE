package com.wuxianggujun.tinaide.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FeedbackRequest(
    val category: String,
    val title: String,
    val content: String,
    @SerialName("device_info")
    val deviceInfo: FeedbackDeviceInfo? = null,
    @SerialName("related_log_id")
    val relatedLogId: String? = null
)

@Serializable
data class FeedbackDeviceInfo(
    val model: String,
    val brand: String,
    @SerialName("android_version")
    val androidVersion: String,
    @SerialName("app_version")
    val appVersion: String,
    val abi: String,
    @SerialName("sdk_int")
    val sdkInt: Int
)

@Serializable
data class FeedbackResponse(
    val id: String,
    val category: String,
    val title: String,
    val content: String,
    val status: String,
    @SerialName("created_at")
    val createdAt: String
)
