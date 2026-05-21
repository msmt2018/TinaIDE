@file:Suppress("DEPRECATION")

package com.gyf.immersionbar.ktx

import android.app.Dialog
import android.app.DialogFragment as LegacyDialogFragment
import android.app.Fragment as LegacyFragment
import android.view.View
import com.gyf.immersionbar.ImmersionBar

// 兼容 framework Fragment API，单独隔离废弃类型，避免污染 AndroidX 扩展告警。

@JvmOverloads
inline fun LegacyFragment.immersionBar(
    isOnly: Boolean = false,
    block: ImmersionBar.() -> Unit
) = ImmersionBar.with(this, isOnly).apply { block(this) }.init()

@JvmOverloads
inline fun LegacyDialogFragment.immersionBar(
    isOnly: Boolean = false,
    block: ImmersionBar.() -> Unit
) = ImmersionBar.with(this, isOnly).apply { block(this) }.init()

@JvmOverloads
inline fun LegacyFragment.immersionBar(
    dialog: Dialog,
    isOnly: Boolean = false,
    block: ImmersionBar.() -> Unit
) = activity?.run { ImmersionBar.with(this, dialog, isOnly).apply { block(this) }.init() } ?: Unit

@JvmOverloads
fun LegacyFragment.immersionBar(isOnly: Boolean = false) = immersionBar(isOnly) { }

@JvmOverloads
fun LegacyDialogFragment.immersionBar(isOnly: Boolean = false) = immersionBar(isOnly) { }

@JvmOverloads
fun LegacyFragment.immersionBar(dialog: Dialog, isOnly: Boolean = false) =
    immersionBar(dialog, isOnly) {}

@JvmOverloads
fun LegacyFragment.destroyImmersionBar(dialog: Dialog, isOnly: Boolean = false) =
    activity?.run { ImmersionBar.destroy(this, dialog, isOnly) } ?: Unit

// 状态栏扩展
val LegacyFragment.statusBarHeight get() = ImmersionBar.getStatusBarHeight(this)

// 导航栏扩展
val LegacyFragment.navigationBarHeight get() = ImmersionBar.getNavigationBarHeight(this)
val LegacyFragment.navigationBarWidth get() = ImmersionBar.getNavigationBarWidth(this)

// ActionBar扩展
val LegacyFragment.actionBarHeight get() = ImmersionBar.getActionBarHeight(this)

// 是否有导航栏
val LegacyFragment.hasNavigationBar get() = ImmersionBar.hasNavigationBar(this)

// 是否有刘海屏
val LegacyFragment.hasNotchScreen get() = ImmersionBar.hasNotchScreen(this)

// 获得刘海屏高度
val LegacyFragment.notchHeight get() = ImmersionBar.getNotchHeight(this)

// 导航栏是否在底部
val LegacyFragment.isNavigationAtBottom get() = ImmersionBar.isNavigationAtBottom(this)

// 是否是全面屏手势
val LegacyFragment.isGesture get() = ImmersionBar.isGesture(this)

// statusBarView扩展
fun LegacyFragment.fitsStatusBarView(view: View) = ImmersionBar.setStatusBarView(this, view)

// titleBar扩展
fun LegacyFragment.fitsTitleBar(vararg view: View) = ImmersionBar.setTitleBar(this, *view)

fun LegacyFragment.fitsTitleBarMarginTop(vararg view: View) =
    ImmersionBar.setTitleBarMarginTop(this, *view)

// 隐藏状态栏
fun LegacyFragment.hideStatusBar() = activity?.run { ImmersionBar.hideStatusBar(window) } ?: Unit

// 显示状态栏
fun LegacyFragment.showStatusBar() = activity?.run { ImmersionBar.showStatusBar(window) } ?: Unit

// 解决顶部与布局重叠问题，不可逆
fun LegacyFragment.setFitsSystemWindows() = ImmersionBar.setFitsSystemWindows(this)
