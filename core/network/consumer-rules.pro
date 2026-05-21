# ============================================================================
# MessagePack — Class.forName() 动态加载 buffer 实现
# ============================================================================
# msgpack-java 在 MessageBuffer.<clinit> 中通过 Class.forName() 硬编码字符串
# 加载 MessageBufferU（sun.misc.Unsafe 版本），失败后回退到安全实现。
# R8 无法追踪字符串反射引用，会将 MessageBufferU 当作死代码删除并重命名
# 其他 buffer 类名 → Class.forName() 失败 → NoClassDefFoundError。

-keep class org.msgpack.core.buffer.** { *; }
