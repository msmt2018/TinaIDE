package com.wuxianggujun.tinaide.editor.bookmark

/**
 * 书签交互反馈通道
 *
 * 用于把“应该提示用户”的事件从底层编辑器手势中抽离出来，避免直接依赖 UI 实现。
 */
fun interface BookmarkFeedback {
    fun onInvalidBookmarkLine()
}

