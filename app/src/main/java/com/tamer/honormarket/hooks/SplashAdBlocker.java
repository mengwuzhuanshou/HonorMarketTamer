package com.tamer.honormarket.hooks;

import de.robv.android.xposed.XC_MethodHook.MethodHookParam;
import de.robv.android.xposed.XposedBridge;

/**
 * 开屏广告屏蔽（弹性签名版，多版本适配）。
 * 数据侧返回 null → 应用走原生无广告分支直接进主页；视图侧兜底不创建广告视图。
 *
 * 版本适配（2026-08-30，适配市场 16.1.7.303）：
 * - 保留钩子 1~4：16.1.6.302 实测有效的四点（老版本兼容）。
 * - 新增钩子 5~7：16.1.7.303 的三处漂移——
 *   ① 设备 AB ab_splash/request_splash=1 后走 V3 实时路径，老钩子 2 的
 *      continuation 类名检查（contains("Continuation")）永不匹配
 *      （实参是协程自身 SplashManager$startSplashFromInitByV3$1），补充无条件拦截；
 *   ② 缓存路径数据入口新增无参 CachedSplashScreenService#e()（getSplashScreenData），
 *      拦它可同时覆盖图标预取缓存分支；
 *   ③ 广告视图工厂 fg7.l → kg7.l（混淆名 fg7 在新版本被复用成无关 lambda 类）。
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

        // 4) 视图侧兜底 SplashUtils.fg7.l(...) → null（老版本；16.1.7.303 中 fg7 已被混淆器
        //    复用为无关 lambda 类，此钩子自动落空，由钩子 7 接管）
        HookUtil.tryHookFlex(cl, "fg7", "l", 3, new HookUtil.FlexCallback() {
            @Override
            public void fire(MethodHookParam param) {
                XposedBridge.log("[HonorMarketTamer] splash view.l -> null");
                param.setResult(null);
            }
        });

        // ===== 以下为 16.1.7.303 适配钩子（老版本缺失对应类/方法时 hookAll 自动落空） =====

        // 5) V3 数据入口修正版：v3.a.d(String,String,continuation)。实参 continuation 是
        //    协程自身（SplashManager$startSplashFromInitByV3$1），老钩子 2 的类名检查
        //    永不匹配（见类注释①）；该类为 V3 开屏专用服务，3 参 d 即数据抓取入口，
        //    直接无条件置 null（协程空分支原生收尾：C(0)+K() 进主页）。
        HookUtil.tryHookFlex(cl, "com.hihonor.appmarket.main.splash.v3.a",
                "d", 3, new HookUtil.FlexCallback() {
            @Override
            public void fire(MethodHookParam param) {
                XposedBridge.log("[HonorMarketTamer] splash v3.d fixed -> null");
                param.setResult(null);
            }
        });

        // 6) 缓存路径新数据入口：CachedSplashScreenService.e()（0 参 getSplashScreenData）。
        //    v3 关闭时 startSplashFromInitByCached → CachedSplashViewModel.c → e()；
        //    e() 内部：预取缓存空则 d("0")（钩子 1 已拦），缓存命中则走图标预取协程
        //    （钩子 1 管不到）——在 e() 源头置 null 一并覆盖两条分支（见类注释②）。
        HookUtil.tryHookFlex(cl,
                "com.hihonor.appmarket.main.splash.cached.CachedSplashScreenService",
                "e", 0, new HookUtil.FlexCallback() {
            @Override
            public void fire(MethodHookParam param) {
                XposedBridge.log("[HonorMarketTamer] splash cached.e -> null");
                param.setResult(null);
            }
        });

        // 7) 视图侧新工厂：kg7.l(Activity,SplashBase,SplashManager) → null。老 fg7.l 的
        //    换名版（签名一致），是 cached/v3/event 三条路径共同终点（见类注释③）；
        //    置 null 后 this.m=null，应用按「无广告」收尾并按原有延时移除默认开屏。
        //    注意：老版本 16.1.6.302 里 kg7 是同名协程 continuation 类（无 l 方法），
        //    hookAllMethods 找不到 l 会自然落空，不会误伤。
        HookUtil.tryHookFlex(cl, "kg7", "l", 3, new HookUtil.FlexCallback() {
            @Override
            public void fire(MethodHookParam param) {
                XposedBridge.log("[HonorMarketTamer] splash view kg7.l -> null");
                param.setResult(null);
            }
        });

        // ===== 16.1.8.305 适配 =====
        // 8) 视图侧工厂再次换位置/换名：ad splash 视图工厂从混淆短名 kg7 挪到
        //    全名类 com.hihonor.appmarket.main.splash.utils.a，方法名 o(Activity,SplashBase,a)
        //    → AdSplashScreen（dex 调用图实证：全 dex 里唯一返回 AdSplashScreen 的工厂）。
        //    它是 cached/v3/event 三条路径共同的视图终点；置 null 后应用按「无广告」收尾。
        //    钩子 5（v3.a#d 数据源头）仍是主拦截，本钩子为视图侧兜底。
        //    老版本无此类/方法时 hookAll 自然落空，不误伤。
        HookUtil.tryHookFlex(cl,
                "com.hihonor.appmarket.main.splash.utils.a", "o", 3,
                new HookUtil.FlexCallback() {
                    @Override
                    public void fire(MethodHookParam param) {
                        XposedBridge.log("[HonorMarketTamer] splash view utils.a.o -> null");
                        param.setResult(null);
                    }
                });
    }

    private SplashAdBlocker() {}
}
