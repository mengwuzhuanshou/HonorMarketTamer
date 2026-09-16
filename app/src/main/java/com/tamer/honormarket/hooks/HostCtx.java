package com.tamer.honormarket.hooks;

import android.content.Context;

/**
 * 宿主（市场）Context 持有器。
 *
 * 不能用 de.robv.android.xposed.AndroidAppHelper：LSPosed 注入会把模块引用的
 * API 类改名（实测变成乱码包名.AndroidAppHelper），直接 ClassNotFoundError。
 * 改为从市场 Splash#onCreate 的 thisObject（Activity 即 Context）捕获应用上下文。
 * 持有的是 applicationContext，供 Provider 通道拉配置用。
 */
public final class HostCtx {
    private static volatile Context appCtx;

    public static void set(Context context) {
        if (context != null && appCtx == null) {
            try {
                appCtx = context.getApplicationContext();
            } catch (Throwable t) {
                appCtx = context;
            }
        }
    }

    public static Context get() {
        return appCtx;
    }

    private HostCtx() {}
}
