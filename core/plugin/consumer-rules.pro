# ============================================================================
# LuaJava — JNI native 方法 + Lua↔Java 反射桥接
# ============================================================================
# luajava 通过 JNI 在 Lua VM 和 Java 之间桥接。
# - LuaNatives / Lua54Natives：JNI native 方法入口（全局 JNI 规则已覆盖）
# - JuaAPI：Java 反射桥接层，Lua 脚本通过 java.require("className") 反射
#   加载 Java 类并调用方法。如果类名/方法名被混淆，Lua→Java 反射会断裂。
# - JFunction：Lua 回调接口，native 侧通过 JNI 调用

# 保留 luajava 框架类名（JNI 回调 + 反射桥接）
-keep class party.iroiro.luajava.** { *; }
