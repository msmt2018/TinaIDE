package com.termux.shared.logger;

public class Log {
    public static final int ASSERT = 0x7;
    public static final int DEBUG = 0x3;
    public static final int ERROR = 0x6;
    public static final int INFO = 0x4;
    public static final int VERBOSE = 0x2;
    public static final int WARN = 0x5;

    public static void v(String tag, String msg) { android.util.Log.v(tag, msg); }
    public static void d(String tag, String msg) { android.util.Log.d(tag, msg); }
    public static void d(String tag, Object obj) { android.util.Log.d(tag, String.valueOf(obj)); }
    public static void i(String tag, String msg) { android.util.Log.i(tag, msg); }
    public static void w(String tag, String msg) { android.util.Log.w(tag, msg); }
    public static void w(String tag, String msg, Throwable t) { android.util.Log.w(tag, msg, t); }
    public static void e(String tag, String msg) { android.util.Log.e(tag, msg); }
    public static void e(String tag, String msg, Throwable t) { android.util.Log.e(tag, msg, t); }
}
