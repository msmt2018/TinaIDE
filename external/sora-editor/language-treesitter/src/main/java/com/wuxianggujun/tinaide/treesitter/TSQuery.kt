package com.wuxianggujun.tinaide.treesitter

class TSQuery private constructor(private var pointer: Long, private val _errorOffset: Int, private val _errorType: TSQueryError) : TSNativeObject() {
    companion object {
        @JvmStatic fun create(language: TSLanguage, source: String): TSQuery {
            val result = nativeCreate(language.getPointer(), source)
            return TSQuery(result[0], result[1].toInt(), TSQueryError.fromInt(result[2].toInt()))
        }
        @JvmStatic private external fun nativeCreate(language: Long, source: String): LongArray
    }

    val errorOffset: Int get() = _errorOffset
    val errorType: TSQueryError get() = _errorType
    val patternCount: Int get() = nativePatternCount(pointer)
    val captureCount: Int get() = nativeCaptureCount(pointer)
    val stringCount: Int get() = nativeStringCount(pointer)
    fun getCaptureNameForId(id: Int): String = nativeGetCaptureName(pointer, id)
    fun getStartByteForPattern(patternIndex: Int): Int = nativeStartByteForPattern(pointer, patternIndex)
    fun getStringValueForId(id: Int): String = nativeGetStringValue(pointer, id)
    fun getPredicatesForPattern(patternIndex: Int): Array<TSQueryPredicateStep> {
        val data = nativePredicatesForPattern(pointer, patternIndex)
        return Array(data.size / 2) { TSQueryPredicateStep(TSQueryPredicateStep.Type.fromInt(data[it * 2]), data[it * 2 + 1]) }
    }
    override fun getPointer() = pointer
    override fun canAccess() = pointer != 0L && errorType == TSQueryError.None
    override fun close() { if (pointer != 0L) { nativeDelete(pointer); pointer = 0 } }

    private external fun nativeDelete(pointer: Long)
    private external fun nativePatternCount(pointer: Long): Int
    private external fun nativeCaptureCount(pointer: Long): Int
    private external fun nativeStringCount(pointer: Long): Int
    private external fun nativeGetCaptureName(pointer: Long, id: Int): String
    private external fun nativeStartByteForPattern(pointer: Long, patternIndex: Int): Int
    private external fun nativePredicatesForPattern(pointer: Long, patternIndex: Int): IntArray
    private external fun nativeGetStringValue(pointer: Long, id: Int): String
}

enum class TSQueryError { None, Syntax, NodeType, Field, Capture, Structure, Language;
    companion object { fun fromInt(value: Int) = entries.getOrElse(value) { None } }
}

data class TSQueryPredicateStep(val type: Type, val valueId: Int) {
    enum class Type { Done, Capture, String;
        companion object { fun fromInt(value: Int) = entries.getOrElse(value) { Done } }
    }
}
