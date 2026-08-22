package com.tamer.honormarket.hooks;

import de.robv.android.xposed.XC_MethodHook.MethodHookParam;
import de.robv.android.xposed.XposedBridge;

/**
 * 开屏广告屏蔽（弹性签名版）。
 * 数据侧返回 null → 应用走原生无广告分支直接进主页；视图侧兜底不创建广告视图。
 */
public final class SplashAdBlocker {

    public static void hook(ClassLoader cl, com.tamer.honormarket.TamerConfig cfg) {
        if (!cfg.get(com.tamer.honormarket.TamerConfig.KEY_SPLASH_AD,
                     com.tamer.honormarket.TamerConfig.defaultValueOf(
                             com.tamer.honormarket.TamerConfig.KEY_SPLASH_AD))) {
            return;
        }

        // 1) 缓存路径挑选开屏图：d(String) → null
        HookUtil.tryHookFlex(cl,
                "com.hihonor.appmarket.main.splash.cached.CachedSplashScreenService",
                "d", 1, new HookUtil.FlexCallback() {
                    @Override
                    public void fire(MethodHookParam param) {
                        XposedBridge.log("[HonorMarketTamer] splash cached.d -> null");
                        param.setResult(null);
                    }
                });

        // 2) V3 路径 d(String,String,continuation)（该函数体 jadx 反编译失败，签名用弹性）
        HookUtil.tryHookFlex(cl, "com.hihonor.appmarket.main.splash.v3.a",
                "d", 3, new HookUtil.FlexCallback() {
                    @Override
                    public void fire(MethodHookParam param) {
                        Object last = param.args[2];
                        if (last != null && last.getClass().getName().contains("Continuation")) {
                            XposedBridge.log("[HonorMarketTamer] splash v3.d -> null");
                            param.setResult(null);
                        }
                    }
                });

        // 3) 事件路径 SplashManager.H(SplashBase)
        HookUtil.tryHookFlex(cl, "com.hihonor.appmarket.main.splash.a",
                "H", 1, new HookUtil.FlexCallback() {
                    @Override
                    public void fire(MethodHookParam param) {
                        XposedBridge.log("[HonorMarketTamer] splash event.H -> void");
                        param.setResult(null);
                    }
                });

        // 4) 视图侧兜底 SplashUtils.fg7.l(...) → null
        HookUtil.tryHookFlex(cl, "fg7", "l", 3, new HookUtil.FlexCallback() {
            @Override
            public void fire(MethodHookParam param) {
                XposedBridge.log("[HonorMarketTamer] splash view.l -> null");
                param.setResult(null);
            }
        });
    }

    private SplashAdBlocker() {}
}
