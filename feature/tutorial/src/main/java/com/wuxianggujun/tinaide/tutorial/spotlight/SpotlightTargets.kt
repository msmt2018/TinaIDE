package com.wuxianggujun.tinaide.tutorial.spotlight

/**
 * Spotlight 引导目标 ID 常量
 *
 * 统一管理，避免散落的字符串导致引导目标对不上。
 */
object SpotlightTargets {
    const val BOTTOM_NAV_PROJECT = "bottom_nav_project"
    const val BOTTOM_NAV_MARKET = "bottom_nav_market"
    const val BOTTOM_NAV_TUTORIAL = "bottom_nav_tutorial"
    const val BOTTOM_NAV_PROFILE = "bottom_nav_profile"

    /**
     * 项目页右下角 FAB（展开菜单的主按钮）
     *
     * 目前命名历史原因沿用 "fab_new_project"，但实际对应的是“项目操作菜单”入口。
     */
    const val FAB_PROJECT_ACTIONS = "fab_new_project"

    /** 项目页 FAB 展开菜单中的“新建项目”入口 */
    const val FAB_MENU_NEW_PROJECT = "fab_menu_new_project"
}
