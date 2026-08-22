package com.tamer.honormarket.hooks;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodHook.MethodHookParam;
import de.robv.android.xposed.XposedBridge;

/**
 * 自启/广播唤醒屏蔽：
 * - PowerConnectedReceiver.onReceive（插电拉活+广告跟踪）
 * - WifiStateChangeReceiver.onReceive（基类 hook 即覆盖全部 8 个空子类）
 * - BootController.J（被动启动总入口，W1/W2/W3 最终都汇聚于此）
 * - WakeMarketProvider.call（外部应用 content:// 唤醒）
 * 静默更新相关组件由 KEY_SILENT_UPD 单独控制（默认保留，保证"应用更新"可用）。
 */
public final class WakeBlocker {

    public static void hook(ClassLoader cl, final com.tamer.honormarket.TamerConfig cfg) {
        XC_MethodHook noop = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                XposedBridge.log("[HonorMarketTamer] suppressed wake: "
                        + param.thisObject.getClass().getName());
                param.setResult(null);
            }
        };

        if (cfg.get(com.tamer.honormarket.TamerConfig.KEY_WAKE,
                    com.tamer.honormarket.TamerConfig.defaultValueOf(
                            com.tamer.honormarket.TamerConfig.KEY_WAKE))) {
            HookUtil.tryHook(cl, "com.hihonor.appmarket.receiver.PowerConnectedReceiver",
                    "onReceive", new Object[]{
                            "android.content.Context", "android.content.Intent"}, noop);

            // 基类一个点覆盖 WifiConnectReceiver / ...1 / ...2 / WithoutConditions... 全部子类
            HookUtil.tryHook(cl, "com.hihonor.appmarket.receiver.wifi.WifiStateChangeReceiver",
                    "onReceive", new Object[]{
                            "android.content.Context", "android.content.Intent"}, noop);

            // 被动启动总入口（suspend 静态方法，3 参），before 返回 null 即不拉活
            HookUtil.tryHookAll(cl, "com.hihonor.appmarket.boot.BootController", "J",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            if (param.args != null && param.args.length == 3) {
                                XposedBridge.log("[HonorMarketTamer] BootController.J suppressed");
                                param.setResult(null);
                            }
                        }
                    });

            // 外部唤醒 Provider
            HookUtil.tryHook(cl, "com.hihonor.appmarket.boot.provider.WakeMarketProvider",
                    "call", new Object[]{"java.lang.String", "java.lang.String",
                            "android.os.Bundle"},
                    HookUtil.returnValue(null));
            HookUtil.tryHook(cl, "com.hihonor.appmarket.boot.provider.WakeMarketProvider",
                    "onCreate", new Object[0], HookUtil.returnValue(false));
        }

        if (cfg.get(com.tamer.honormarket.TamerConfig.KEY_SILENT_UPD,
                    com.tamer.honormarket.TamerConfig.defaultValueOf(
                            com.tamer.honormarket.TamerConfig.KEY_SILENT_UPD))) {
            // 静默更新检查闹钟
            HookUtil.tryHook(cl, "com.hihonor.appmarket.slientcheck.checkupdate.au.AuCheckReceiver",
                    "onReceive", new Object[]{
                            "android.content.Context", "android.content.Intent"}, noop);
            HookUtil.tryHook(cl, "com.hihonor.appmarket.slientcheck.alarmwake.AlarmCheckReceiver",
                    "onReceive", new Object[]{
                            "android.content.Context", "android.content.Intent"}, noop);
            // 夜间电量检测（静默更新条件）
            HookUtil.tryHook(cl, "com.hihonor.appmarket.slientcheck.BatteryReceiver",
                    "onReceive", new Object[]{
                            "android.content.Context", "android.content.Intent"}, noop);
            // 静默更新唤醒 Job
            HookUtil.tryHook(cl,
                    "com.hihonor.appmarket.slientcheck.checkupdate.au.freeze.SilentUpdateWakeJobService",
                    "onStartJob", new Object[]{"android.app.job.JobParameters"},
                    HookUtil.returnValue(false));
            // 资源预下载 Job（WiFi+充电）
            HookUtil.tryHook(cl, "com.hihonor.predownload.job.PreDownloadJob",
                    "onStartJob", new Object[]{"android.app.job.JobParameters"},
                    HookUtil.returnValue(false));
        }
        // 注意：WaitWifiJob 故意不拦截 —— 它同时负责用户手动下载的 WiFi 续传，
        // 拦截会破坏正常下载体验。
    }

    private WakeBlocker() {}
}
