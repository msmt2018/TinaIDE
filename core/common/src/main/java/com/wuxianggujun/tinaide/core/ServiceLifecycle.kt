package com.wuxianggujun.tinaide.core

/**
 * 服务生命周期接口
 *
 * 实现此接口的服务可以接收生命周期回调。
 * Koin 在创建 single 实例时会自动调用 onCreate()（通过 onClose 回调调用 onDestroy()）。
 */
interface ServiceLifecycle {
    /**
     * 服务初始化
     */
    fun onCreate()

    /**
     * 服务销毁
     */
    fun onDestroy()
}
