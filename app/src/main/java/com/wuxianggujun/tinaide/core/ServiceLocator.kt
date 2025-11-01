package com.wuxianggujun.tinaide.core

/**
 * 轻量级依赖注入容器
 * 用于管理应用中各个服务的生命周期和依赖关系
 */
object ServiceLocator {
    private val services = mutableMapOf<Class<*>, Any>()
    
    /**
     * 注册服务实例
     */
    fun <T : Any> register(serviceClass: Class<T>, instance: T) {
        services[serviceClass] = instance
    }
    
    /**
     * 获取服务实例
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> get(serviceClass: Class<T>): T {
        return services[serviceClass] as? T
            ?: throw IllegalStateException("Service ${serviceClass.simpleName} not registered")
    }
    
    /**
     * 检查服务是否已注册
     */
    fun <T : Any> isRegistered(serviceClass: Class<T>): Boolean {
        return services.containsKey(serviceClass)
    }
    
    /**
     * 清除所有服务（用于测试或重置）
     */
    fun clear() {
        services.clear()
    }
}

/**
 * Kotlin 扩展函数，简化服务注册
 */
inline fun <reified T : Any> ServiceLocator.register(instance: T) {
    register(T::class.java, instance)
}

/**
 * Kotlin 扩展函数，简化服务获取
 */
inline fun <reified T : Any> ServiceLocator.get(): T {
    return get(T::class.java)
}
