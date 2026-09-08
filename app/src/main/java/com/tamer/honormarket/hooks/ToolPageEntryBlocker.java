package com.tamer.honormarket.hooks;

import java.util.concurrent.atomic.AtomicInteger;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodHook.MethodHookParam;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * 工具页「运营活动入口」按钮屏蔽（标题栏树形图标 → 耀耀农场等 H5 活动页）。
 *
 * 锚点：operation.toolpage.ToolPageActivityEntryView（FrameLayout 子类，活动入口
 * 专用视图，全名类不混淆）。构造器初始 setVisibility(GONE)；唯一显示路径是 b(String)：
 * ToolPageActivityManager 查到活动配置 → setVisibility(VISIBLE) + 加载活动图标 +
 * setOnClickListener（点击经 DispatchModuleManager 打开活动 H5，应用更新页实开为
 * WebViewCommonActivity「耀耀农场·天天浇水领水果」）。
 * addView 进标题栏后调用方必调 b("页类型")——使用方：应用更新 / 安装管理 / 安装记录 /
 * 卸载管理 / 清理加速 / 安全检测，全部是同类运营入口，一并屏蔽。
 *
 * 手法：before-hook 吞掉 b(String)（setResult(null)；方法为 void、调用方不消费返回值、
 * 吞掉不影响 initView 后续逻辑），视图停在构造器初始的 GONE 态：永不显示、不绑定点击、
 * 不请求活动图标。相比「显示后补压 GONE」无时序竞态、无多轮扫描、无死循环。
 * 不拦活动 H5 页本身（WebViewCommonActivity 是通用容器，全拦会误伤正常页面）——
 * 入口没了，页面自然打不开。
 */
public final class ToolPageEntryBlocker {
    private static final String TAG = "[HonorMarketTamer] ";
    private static final String ENTRY_VIEW =
            "com.hihonor.appmarket.operation.toolpage.ToolPageActivityEntryView";

    private static final AtomicInteger sHit = new AtomicInteger();

    private ToolPageEntryBlocker() { }

    public static void hook(final ClassLoader cl,
            com.tamer.honormarket.TamerConfig cfg) {
        if (!cfg.get(com.tamer.honormarket.TamerConfig.KEY_TOOLPAGE_ENTRY, true)) {
            XposedBridge.log(TAG + "ToolPageEntryBlocker skipped (switch off)");
            return;
        }
        try {
            Class<?> clazz = XposedHelpers.findClass(ENTRY_VIEW, cl);
            XposedBridge.hookAllMethods(clazz, "b", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        // b(String) 单参；吞掉 = 保持构造器初始 GONE，永不绑定点击
                        if (param.args.length == 1) {
                            param.setResult(null);
                            int n = sHit.incrementAndGet();
                            if (n <= 10 || n % 50 == 0) {
                                XposedBridge.log(TAG
                                        + "toolpage activity entry killed #" + n);
                            }
                        }
                    } catch (Throwable t) {
                        XposedBridge.log(TAG + "toolpage entry err: " + t);
                    }
                }
            });
            XposedBridge.log(TAG + "ToolPageEntryBlocker armed on "
                    + "ToolPageActivityEntryView#b");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "ToolPageEntryBlocker FAILED: " + t);
        }
    }
}
