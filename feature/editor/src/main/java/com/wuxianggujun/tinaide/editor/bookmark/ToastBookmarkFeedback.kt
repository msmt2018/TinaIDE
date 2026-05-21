package com.wuxianggujun.tinaide.editor.bookmark

import android.content.Context
import android.widget.Toast
import com.wuxianggujun.tinaide.core.i18n.Strings
import com.wuxianggujun.tinaide.core.i18n.strOr

class ToastBookmarkFeedback(
    private val context: Context
) : BookmarkFeedback {
    override fun onInvalidBookmarkLine() {
        Toast.makeText(
            context,
            Strings.toast_bookmark_ignored_blank_line.strOr(context),
            Toast.LENGTH_SHORT
        ).show()
    }
}


