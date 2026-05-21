# 终端模块仅保留 JNI / XML 反射构造相关规则。

# Termux JNI
-keep class com.termux.terminal.JNI { *; }
-keepclasseswithmembers class com.termux.view.** {
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}
