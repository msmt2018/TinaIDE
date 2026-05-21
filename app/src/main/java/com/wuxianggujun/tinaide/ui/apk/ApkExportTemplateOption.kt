package com.wuxianggujun.tinaide.ui.apk

import com.wuxianggujun.tinaide.core.apkbuilder.ApkTemplateType
import java.io.File

data class ApkExportTemplateOption(
    val id: String,
    val label: String,
    val templateType: ApkTemplateType,
    val templateFile: File? = null
)
