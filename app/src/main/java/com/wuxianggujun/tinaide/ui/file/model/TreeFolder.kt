package com.wuxianggujun.tinaide.ui.file.model

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.appcompat.content.res.AppCompatResources
import com.wuxianggujun.tinaide.R
import java.io.File

class TreeFolder(file: File) : TreeFile(file) {

    override fun getIcon(context: Context): Drawable? {
        return AppCompatResources.getDrawable(context, R.drawable.ic_folder)
    }
}
