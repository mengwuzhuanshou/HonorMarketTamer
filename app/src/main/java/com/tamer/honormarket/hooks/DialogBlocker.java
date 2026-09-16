package com.tamer.honormarket.hooks;

import de.robv.android.xposed.XC_MethodHook.MethodHookParam;
import de.robv.android.xposed.XposedBridge;

/** 运营弹窗/悬浮窗/推广弹窗屏蔽（弹性签名版） */
public final class DialogBlocker {

    private static final String ADD_WIDGET_DIALOG =
            "com.hihonor.appmarket.base.widget.card.dialog.AddWidgetDialogFragment";
    private static final String PERS_ADS_DIALOG =
            "com.hihonor.appmarket.dialog.personalizedads.PersonalizedAdsDialogFragment";

    public static void hook(final ClassLoader cl, final com.tamer.honormarket.TamerConfig cfg) {
        if (cfg.get(com.tamer.honormarket.TamerConfig.KEY_OP_DIALOG,
                    com.tamer.honormarket.TamerConfig.defaultValueOf(
                            com.tamer.honormarket.TamerConfig.KEY_OP_DIALOG))) {
            // 【16.1.8.305 适配】运营弹窗显示入口从 i0 挪到 j0。
            //  - 16.1.7 及以前：i0(3参) 是弹窗内容装配/显示路径；
            //  - 16.1.8.305：i0 变成 i0(int,View)（2 参，点按上报+dismiss 路径），
            //    显示入口是 j0(FragmentActivity,OperationVO,Runnable)（3 参，
            //    MainFrameFragment.reShowOperationDialog / u2 调用，内部 a0(activity) 弹框）。
            // 两版都按 3 参拦：老 i0(3) 与 新 j0(3)；新版 i0 已 2 参 → 老钩子自然落空。
            HookUtil.tryHookFlex(cl, "com.hihonor.appmarket.operation.ui.OperationDialog",
                    "j0", 3, new HookUtil.FlexCallback() {
                        @Override
                        public void fire(MethodHookParam param) {
                            XposedBridge.log("[HonorMarketTamer] block OperationDialog.j0 (show)");
                            param.setResult(null);
                        }
                    });
            HookUtil.tryHookFlex(cl, "com.hihonor.appmarket.operation.ui.OperationDialog",
                    "i0", 3, new HookUtil.FlexCallback() {
                        @Override
                        public void fire(MethodHookParam param) {
                            XposedBridge.log("[HonorMarketTamer] block OperationDialog.i0");
                            param.setResult(null);
                        }
                    });
        }

        if (cfg.get(com.tamer.honormarket.TamerConfig.KEY_OP_FLOAT,
                    com.tamer.honormarket.TamerConfig.defaultValueOf(
                            com.tamer.honormarket.TamerConfig.KEY_OP_FLOAT))) {
            HookUtil.tryHookFlex(cl,
                    "com.hihonor.appmarket.main.features.operfloating.OperationFloatingWindowManager",
                    "b", 2, new HookUtil.FlexCallback() {
                        @Override
                        public void fire(MethodHookParam param) {
                            XposedBridge.log("[HonorMarketTamer] block op-float event");
                            param.setResult(null);
                        }
                    });
            HookUtil.tryHookFlex(cl,
                    "com.hihonor.appmarket.operation.widget.floatingwindow.window.a",
                    "k", 6, new HookUtil.FlexCallback() {
                        @Override
                        public void fire(MethodHookParam param) {
                            XposedBridge.log("[HonorMarketTamer] block float window.k");
                            param.setResult(null);
                        }
                    });
        }

        boolean widgetTip = cfg.get(com.tamer.honormarket.TamerConfig.KEY_WIDGET_TIP,
                com.tamer.honormarket.TamerConfig.defaultValueOf(
                        com.tamer.honormarket.TamerConfig.KEY_WIDGET_TIP));
        boolean persAd = cfg.get(com.tamer.honormarket.TamerConfig.KEY_PERS_AD,
                com.tamer.honormarket.TamerConfig.defaultValueOf(
                        com.tamer.honormarket.TamerConfig.KEY_PERS_AD));
        if (widgetTip || persAd) {
            HookUtil.tryHookFlex(cl,
                    "com.hihonor.appmarket.widgets.dialog.BaseUikitDialogFragment",
                    "c0", 2, new HookUtil.FlexCallback() {
                        @Override
                        public void fire(MethodHookParam param) {
                            String cls = param.thisObject.getClass().getName();
                            if (cls.equals(ADD_WIDGET_DIALOG)
                                    && cfg.get(com.tamer.honormarket.TamerConfig.KEY_WIDGET_TIP, true)) {
                                XposedBridge.log("[HonorMarketTamer] block AddWidgetDialogFragment");
                                param.setResult(null);
                            } else if (cls.equals(PERS_ADS_DIALOG)
                                    && cfg.get(com.tamer.honormarket.TamerConfig.KEY_PERS_AD, true)) {
                                XposedBridge.log("[HonorMarketTamer] block PersonalizedAdsDialogFragment");
                                param.setResult(null);
                            }
                        }
                    });
        }
    }

    private DialogBlocker() {}
}
