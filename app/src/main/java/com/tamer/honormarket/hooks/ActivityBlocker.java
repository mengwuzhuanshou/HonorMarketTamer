package com.tamer.honormarket.hooks;

import java.util.HashSet;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * 页面级屏蔽：按开关拦截对应 Activity 的创建（直接 finish）。
 * 覆盖：游戏/快游戏、分类、排行榜、儿童模式、清理加速、安全检测、应用管理、消息中心。
 */
public final class ActivityBlocker {

    public static void hook(final ClassLoader cl, final com.tamer.honormarket.TamerConfig cfg) {
        final Set<String> blocked = new HashSet<>();
        for (String[] entry : com.tamer.honormarket.TamerConfig.BLOCKED_ACTIVITIES) {
            if (cfg.get(entry[0], com.tamer.honormarket.TamerConfig.defaultValueOf(entry[0]))) {
                blocked.add(entry[1]);
            }
        }
        if (blocked.isEmpty()) {
            XposedBridge.log("[HonorMarketTamer] ActivityBlocker: nothing to block");
            return;
        }

        XC_MethodHook kill = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) {
                String name = param.thisObject.getClass().getName();
                if (blocked.contains(name)) {
                    XposedBridge.log("[HonorMarketTamer] block activity: " + name);
                    try {
                        XposedHelpers.callMethod(param.thisObject, "finish");
                    } catch (Throwable ignored) {}
                }
            }
        };

        // onCreate 与 onResume 双保险：部分页面在 onCreate 后仍会短暂显示
        HookUtil.tryHook(cl, "android.app.Activity", "onCreate",
                new Object[]{"android.os.Bundle"}, kill);
        HookUtil.tryHook(cl, "android.app.Activity", "onResume", new Object[0], kill);
    }

    private ActivityBlocker() {}
}
