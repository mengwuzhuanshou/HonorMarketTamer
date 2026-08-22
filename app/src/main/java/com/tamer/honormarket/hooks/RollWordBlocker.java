package com.tamer.honormarket.hooks;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodHook.MethodHookParam;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * 首页（及各主页面顶部）搜索框滚动热词横幅屏蔽。
 *
 * 锚点：com.hihonor.appmarket.widgets.CommonMainTitleView（全名类，首页
 * fragment_main_menu 的 search_bar_view 就是它）。
 *  - static A(view, hotWords, assId, traceId, strategyId) 是热词数据的唯一入口，
 *    before-hook 把列表换成空 List → startHotWordsRolling 记 "data empty" 后不再轮播；
 *  - F() changeWord 是轮换出口，直接吞掉做双保险（防其它路径塞数据），顺带停掉埋点上报。
 * 搜索框本身的点击进搜索页不受影响（搜索应用永远可用）。
 */
public final class RollWordBlocker {
    private static final String TAG = "[HonorMarketTamer] ";
    private static final String CLS =
            "com.hihonor.appmarket.widgets.CommonMainTitleView";
    private static final AtomicInteger sFBlocked = new AtomicInteger();
    private static final AtomicInteger sABlocked = new AtomicInteger();

    private RollWordBlocker() { }

    public static void hook(final ClassLoader cl,
            com.tamer.honormarket.TamerConfig cfg) {
        if (!cfg.get(com.tamer.honormarket.TamerConfig.KEY_HOME_ROLLWORD, false)) {
            return;
        }
        try {
            final Class<?> clazz = XposedHelpers.findClass(CLS, cl);
            XposedBridge.hookAllMethods(clazz, "A", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        if (param.args.length == 5 && clazz.isInstance(param.args[0])) {
                            param.args[1] = new ArrayList<Object>();
                            int n = sABlocked.incrementAndGet();
                            if (n <= 8 || n % 100 == 0) {
                                XposedBridge.log(TAG + "rollword blocked data scene="
                                        + param.args[4] + " #" + n);
                            }
                        }
                    } catch (Throwable t) {
                        XposedBridge.log(TAG + "rollword A err: " + t);
                    }
                }
            });
            XposedBridge.hookAllMethods(clazz, "F", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        if (param.args.length == 0) {
                            param.setResult(null);
                            int n = sFBlocked.incrementAndGet();
                            if (n <= 8) {
                                XposedBridge.log(TAG + "rollword blocked changeWord #"
                                        + n);
                            }
                        }
                    } catch (Throwable t) {
                        XposedBridge.log(TAG + "rollword F err: " + t);
                    }
                }
            });
            XposedBridge.log(TAG + "RollWordBlocker armed on CommonMainTitleView");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "RollWordBlocker FAILED: " + t);
        }
    }
}
