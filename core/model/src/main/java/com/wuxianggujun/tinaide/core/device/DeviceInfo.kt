package com.wuxianggujun.tinaide.core.device

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DeviceInfo(
    /** 设备 ID */
    @SerialName("device_id")
    val deviceId: String,

    /** 设备型号 */
    val model: String,

    /** 系统版本 */
    @SerialName("os_version")
    val osVersion: String,

    /** 应用版本 */
    @SerialName("app_version")
    val appVersion: String
)
