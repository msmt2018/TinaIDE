package com.wuxianggujun.tinaide.plugin.script

data class PluginSandboxConfig(
    val maxExecutionTimeMs: Long = 5000L,
    val maxMemoryBytes: Long = 16 * 1024 * 1024L,
    val maxStackSize: Int = 256 * 1024,
    val allowedHosts: Set<String> = emptySet(),
    val maxApiCallsPerSecond: Int = 100,
    val maxFileOpsPerMinute: Int = 60,
    val maxNetworkRequestsPerMinute: Int = 30
) {
    companion object {
        val DEFAULT = PluginSandboxConfig()

        val RESTRICTED = PluginSandboxConfig(
            maxExecutionTimeMs = 2000L,
            maxMemoryBytes = 8 * 1024 * 1024L,
            maxStackSize = 128 * 1024,
            maxApiCallsPerSecond = 50,
            maxFileOpsPerMinute = 30,
            maxNetworkRequestsPerMinute = 10
        )

        val PERMISSIVE = PluginSandboxConfig(
            maxExecutionTimeMs = 30000L,
            maxMemoryBytes = 64 * 1024 * 1024L,
            maxStackSize = 512 * 1024,
            maxApiCallsPerSecond = 500,
            maxFileOpsPerMinute = 300,
            maxNetworkRequestsPerMinute = 100
        )
    }
}

class RateLimiter(
    private val maxCalls: Int,
    private val windowMs: Long
) {
    private val timestamps = mutableListOf<Long>()

    @Synchronized
    fun tryAcquire(): Boolean {
        val now = System.currentTimeMillis()
        val windowStart = now - windowMs

        timestamps.removeAll { it < windowStart }

        if (timestamps.size >= maxCalls) {
            return false
        }

        timestamps.add(now)
        return true
    }

    @Synchronized
    fun reset() {
        timestamps.clear()
    }
}
