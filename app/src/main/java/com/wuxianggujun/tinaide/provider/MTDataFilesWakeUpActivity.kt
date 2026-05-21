package com.wuxianggujun.tinaide.provider

import android.app.Activity
import android.os.Bundle

/**
 * MT 管理器唤醒 Activity
 *
 * 用于唤醒应用以便 MT 管理器可以访问文件提供器。
 * 此 Activity 会立即关闭，不显示任何 UI。
 */
class MTDataFilesWakeUpActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        finish()
    }
}
