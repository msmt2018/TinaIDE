package com.wuxianggujun.tinaide.output

import io.github.rosemoe.sora.lang.EmptyLanguage

/**
 * 日志语言支持
 * 自动识别日志等级并高亮显示
 * 
 * 注意：当前使用 EmptyLanguage，未来可以扩展高亮功能
 */
class LogLanguage : EmptyLanguage() {
    // 继承 EmptyLanguage，提供基本的文本编辑功能
    // TODO: 未来可以实现自定义的语法高亮
}
