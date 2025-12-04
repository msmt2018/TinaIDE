package com.wuxianggujun.tinaide.ui.file.model

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.appcompat.content.res.AppCompatResources
import com.wuxianggujun.tinaide.R
import java.io.File

open class TreeFile(val file: File) {

    companion object {
        fun fromFile(file: File): TreeFile {
            return if (file.isDirectory) {
                TreeFolder(file)
            } else {
                when (file.extension.lowercase()) {
                    "java" -> TreeJavaFile(file)
                    "kt" -> TreeKotlinFile(file)
                    "cpp", "cc", "cxx" -> TreeCppFile(file)
                    "c" -> TreeCFile(file)
                    "h", "hpp" -> TreeHeaderFile(file)
                    "xml" -> TreeXmlFile(file)
                    else -> TreeFile(file)
                }
            }
        }
    }

    open fun getIcon(context: Context): Drawable? {
        return AppCompatResources.getDrawable(context, R.drawable.ic_file_default)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TreeFile) return false
        return file == other.file
    }

    override fun hashCode(): Int {
        return file.hashCode()
    }
}
