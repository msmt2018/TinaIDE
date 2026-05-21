# Kotlin Serialization 已由 app/proguard-rules.pro 全局规则覆盖。

# TinaFileProvider 被 Android Manifest 反射实例化，同时构造函数显式传入
# R.xml.file_paths。Release 下必须保留类和构造路径，避免回退到
# PackageManager meta-data 解析后再次触发 FILE_PROVIDER_PATHS 缺失。
-keep class com.wuxianggujun.tinaide.storage.TinaFileProvider { *; }
