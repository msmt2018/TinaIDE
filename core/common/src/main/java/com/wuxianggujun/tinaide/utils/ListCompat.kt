package com.wuxianggujun.tinaide.utils

/**
 * List 兼容性扩展函数。
 *
 * 解决 Kotlin 的 removeLast()/removeFirst() 在 Android API < 35 上
 * 因调用 Java 21 的 List 接口方法而崩溃的问题。
 */

/**
 * 移除并返回列表最后一个元素（兼容低版本 Android）
 */
fun <T> MutableList<T>.removeLastCompat(): T {
    if (isEmpty()) throw NoSuchElementException("List is empty.")
    return removeAt(lastIndex)
}

/**
 * 移除并返回列表第一个元素（兼容低版本 Android）
 */
fun <T> MutableList<T>.removeFirstCompat(): T {
    if (isEmpty()) throw NoSuchElementException("List is empty.")
    return removeAt(0)
}

/**
 * 移除列表最后一个元素，如果为空则返回 null（兼容低版本 Android）
 */
fun <T> MutableList<T>.removeLastOrNullCompat(): T? {
    return if (isEmpty()) null else removeAt(lastIndex)
}

/**
 * 移除列表第一个元素，如果为空则返回 null（兼容低版本 Android）
 */
fun <T> MutableList<T>.removeFirstOrNullCompat(): T? {
    return if (isEmpty()) null else removeAt(0)
}
