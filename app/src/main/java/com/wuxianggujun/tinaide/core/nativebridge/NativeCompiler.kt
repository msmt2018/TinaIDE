package com.wuxianggujun.tinaide.core.nativebridge

object NativeCompiler {
    external fun getClangVersion(): String
    external fun syntaxCheck(sysroot: String, srcPath: String, target: String, isCxx: Boolean): String

    /**
     * 使用 clang in-process 编译单个源文件为目标文件（.o）。
     * 返回空字符串表示成功；非空字符串为诊断输出/错误信息。
     */
    external fun emitObj(
        sysroot: String,
        srcPath: String,
        objOut: String,
        target: String,
        isCxx: Boolean,
        flags: Array<String>,
        includeDirs: Array<String>
    ): String
}
