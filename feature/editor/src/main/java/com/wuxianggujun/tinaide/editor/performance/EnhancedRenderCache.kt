package com.wuxianggujun.tinaide.editor.performance

import android.util.LruCache

/**
 * 增强的渲染缓存
 * 
 * 优化点：
 * 1. 使用 LruCache 替代固定大小列表
 * 2. 根据设备内存动态调整缓存大小
 * 3. 预加载可见区域附近的行
 * 4. 智能缓存淘汰策略
 */
class EnhancedRenderCache(
    private val maxMemoryKb: Int = getDefaultCacheSize()
) {
    
    companion object {
        /**
         * 根据设备内存计算默认缓存大小
         */
        fun getDefaultCacheSize(): Int {
            val runtime = Runtime.getRuntime()
            val maxMemory = runtime.maxMemory() / 1024 // KB
            
            // 使用最大内存的 1/16 作为缓存
            return (maxMemory / 16).toInt().coerceIn(512, 4096)
        }
    }
    
    data class MeasureCacheItem(
        val line: Int,
        val widths: FloatArray?,
        val timestamp: Long
    ) {
        // 估算内存占用（字节）
        fun estimateSize(): Int {
            return 16 + (widths?.size ?: 0) * 4 // 对象头 + float数组
        }
    }
    
    data class SpanCacheItem(
        val line: Int,
        val spans: Any?, // 实际类型根据你的实现
        val timestamp: Long
    ) {
        fun estimateSize(): Int = 64 // 简化估算
    }
    
    // 测量缓存 - 使用 LruCache
    private val measureCache = object : LruCache<Int, MeasureCacheItem>(maxMemoryKb) {
        override fun sizeOf(key: Int, value: MeasureCacheItem): Int {
            return value.estimateSize() / 1024 // 转换为 KB
        }
    }
    
    // 语法高亮缓存
    private val spanCache = object : LruCache<Int, SpanCacheItem>(maxMemoryKb / 2) {
        override fun sizeOf(key: Int, value: SpanCacheItem): Int {
            return value.estimateSize() / 1024
        }
    }
    
    /**
     * 获取或创建测量缓存
     */
    fun getOrCreateMeasureCache(line: Int): MeasureCacheItem {
        return measureCache.get(line) ?: MeasureCacheItem(
            line = line,
            widths = null,
            timestamp = System.currentTimeMillis()
        ).also {
            measureCache.put(line, it)
        }
    }
    
    /**
     * 查询测量缓存
     */
    fun queryMeasureCache(line: Int): MeasureCacheItem? {
        return measureCache.get(line)
    }
    
    /**
     * 预加载可见区域附近的行
     */
    fun preloadVisibleRange(firstVisible: Int, lastVisible: Int, lineCount: Int) {
        val preloadStart = maxOf(0, firstVisible - 20)
        val preloadEnd = minOf(lineCount - 1, lastVisible + 20)
        
        for (line in preloadStart..preloadEnd) {
            // 如果缓存中没有，创建一个占位符
            if (measureCache.get(line) == null) {
                measureCache.put(line, MeasureCacheItem(line, null, System.currentTimeMillis()))
            }
        }
    }
    
    /**
     * 更新插入操作
     */
    fun updateForInsertion(startLine: Int, endLine: Int) {
        if (startLine == endLine) return
        
        val deltaLines = endLine - startLine
        val snapshot = measureCache.snapshot()
        
        // 清空缓存并重新插入（调整行号）
        measureCache.evictAll()
        
        snapshot.forEach { (line, item) ->
            val newLine = if (line >= startLine) line + deltaLines else line
            measureCache.put(newLine, item.copy(line = newLine))
        }
    }
    
    /**
     * 更新删除操作
     */
    fun updateForDeletion(startLine: Int, endLine: Int) {
        if (startLine == endLine) return
        
        val deltaLines = endLine - startLine
        val snapshot = measureCache.snapshot()
        
        measureCache.evictAll()
        
        snapshot.forEach { (line, item) ->
            when {
                line < startLine -> measureCache.put(line, item)
                line >= endLine -> {
                    val newLine = line - deltaLines
                    measureCache.put(newLine, item.copy(line = newLine))
                }
                // 删除范围内的行不重新插入
            }
        }
    }
    
    /**
     * 重置缓存
     */
    fun reset() {
        measureCache.evictAll()
        spanCache.evictAll()
    }
    
    /**
     * 获取缓存统计信息
     */
    fun getCacheStats(): CacheStats {
        return CacheStats(
            measureCacheSize = measureCache.size(),
            measureCacheHitCount = measureCache.hitCount(),
            measureCacheMissCount = measureCache.missCount(),
            spanCacheSize = spanCache.size(),
            spanCacheHitCount = spanCache.hitCount(),
            spanCacheMissCount = spanCache.missCount()
        )
    }
    
    data class CacheStats(
        val measureCacheSize: Int,
        val measureCacheHitCount: Int,
        val measureCacheMissCount: Int,
        val spanCacheSize: Int,
        val spanCacheHitCount: Int,
        val spanCacheMissCount: Int
    ) {
        val measureHitRate: Float
            get() = if (measureCacheHitCount + measureCacheMissCount == 0) 0f
                    else measureCacheHitCount.toFloat() / (measureCacheHitCount + measureCacheMissCount)
        
        val spanHitRate: Float
            get() = if (spanCacheHitCount + spanCacheMissCount == 0) 0f
                    else spanCacheHitCount.toFloat() / (spanCacheHitCount + spanCacheMissCount)
    }
}
