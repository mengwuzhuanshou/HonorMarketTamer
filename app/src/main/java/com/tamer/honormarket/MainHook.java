package com.tamer.honormarket;

import com.tamer.honormarket.hooks.ActivityBlocker;
import com.tamer.honormarket.hooks.DialogBlocker;
import com.tamer.honormarket.hooks.MineSectionBlocker;
import com.tamer.honormarket.hooks.PushBlocker;
import com.tamer.honormarket.hooks.RecommendFeedBlocker;
import com.tamer.honormarket.hooks.RollWordBlocker;
import com.tamer.honormarket.hooks.SplashAdBlocker;
import com.tamer.honormarket.hooks.TabStripper;
import com.tamer.honormarket.hooks.WakeBlocker;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * 荣耀应用市场净化 - LSPosed 模块入口
 * 目标：com.hihonor.appmarket
 * 原则：搜索应用与应用更新永远可用；其余功能均可通过开关屏蔽。
 */
public class MainHook implements IXposedHookLoadPackage {

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpp) {
        // 自证日志：直接进系统 logcat，不依赖 LSPosed 日志页
        android.util.Log.i("HonorMarketTamer", "handleLoadPackage: " + lpp.packageName
                + " process=" + lpp.processName + " pid=" + android.os.Process.myPid());
        if (!TamerConfig.TARGET_PKG.equals(lpp.packageName)) {
            return;
        }
        writeAliveMarker();
        XSharedPreferences xsp = new XSharedPreferences(TamerConfig.MODULE_PKG, TamerConfig.PREFS_NAME);
        TamerConfig cfg = TamerConfig.loadForHook(xsp);

        // 启动自报配置状态，便于排查“开关不生效”
        try {
            java.io.File pf = xsp.getFile();
            XposedBridge.log("[HonorMarketTamer] prefs=" + (pf == null ? "null" : pf.getPath())
                    + " canRead=" + (pf != null && pf.canRead()));
        } catch (Throwable ignored) {}
        XposedBridge.log("[HonorMarketTamer] effective: master=" + cfg.get(TamerConfig.KEY_MASTER, true)
                + " splashAd=" + cfg.get(TamerConfig.KEY_SPLASH_AD, true)
                + " opDialog=" + cfg.get(TamerConfig.KEY_OP_DIALOG, true)
                + " push=" + cfg.get(TamerConfig.KEY_PUSH, true)
                + " wake=" + cfg.get(TamerConfig.KEY_WAKE, true)
                + " gameTab=" + cfg.get(TamerConfig.KEY_GAME_TAB, false)
                + " rollword=" + cfg.get(TamerConfig.KEY_HOME_ROLLWORD, false)
                + " mine[signin=" + cfg.get(TamerConfig.KEY_MINE_SIGNIN, false)
                + " safety=" + cfg.get(TamerConfig.KEY_MINE_SAFETY, false)
                + " clean=" + cfg.get(TamerConfig.KEY_MINE_CLEAN, false)
                + " services=" + cfg.get(TamerConfig.KEY_MINE_SERVICES, false)
                + " slide=" + cfg.get(TamerConfig.KEY_MINE_SLIDE, false) + "]"
                + " feed[mine=" + cfg.get(TamerConfig.KEY_MINE_FEED, true)
                + " search=" + cfg.get(TamerConfig.KEY_SEARCH_FEED, true)
                + " update=" + cfg.get(TamerConfig.KEY_UPDATE_FEED, true) + "]");

        if (!cfg.get(TamerConfig.KEY_MASTER, true)) {
            XposedBridge.log("[HonorMarketTamer] 模块已通过总开关禁用");
            return;
        }
        XposedBridge.log("[HonorMarketTamer] hooked into " + lpp.packageName
                + " process=" + lpp.processName);

        // 每个 Blocker 独立容错，互不影响。
        // 注意：必须用匿名内部类而非 Lambda——老版 dalvik-dx 对 invokedynamic
        // 的脱糖产物（call_site_ids）会被 ART 校验器拒收（VerifyError: Bad call site id）。
        final XC_LoadPackage.LoadPackageParam lppFinal = lpp;
        final com.tamer.honormarket.TamerConfig cfgFinal = cfg;
        safe("ActivityBlocker", new Thunk() { public void run() { ActivityBlocker.hook(lppFinal.classLoader, cfgFinal); } });
        safe("SplashAdBlocker", new Thunk() { public void run() { SplashAdBlocker.hook(lppFinal.classLoader, cfgFinal); } });
        safe("TabStripper",     new Thunk() { public void run() { TabStripper.hook(lppFinal.classLoader, cfgFinal); } });
        safe("PushBlocker",     new Thunk() { public void run() { PushBlocker.hook(lppFinal.classLoader, cfgFinal); } });
        safe("WakeBlocker",     new Thunk() { public void run() { WakeBlocker.hook(lppFinal.classLoader, cfgFinal); } });
        safe("DialogBlocker",   new Thunk() { public void run() { DialogBlocker.hook(lppFinal.classLoader, cfgFinal); } });
        safe("MineSectionBlocker", new Thunk() { public void run() { MineSectionBlocker.hook(lppFinal.classLoader, cfgFinal); } });
        safe("RollWordBlocker",    new Thunk() { public void run() { RollWordBlocker.hook(lppFinal.classLoader, cfgFinal); } });
        safe("RecommendFeedBlocker", new Thunk() { public void run() { RecommendFeedBlocker.hook(lppFinal.classLoader, cfgFinal); } });
    }

    interface Thunk { void run() throws Throwable; }

    /** 在目标应用自己的 files 目录写存活标记，供无 logcat 条件下肉眼确认 */
    private static void writeAliveMarker() {
        try {
            java.io.File dir = new java.io.File("/data/data/" + TamerConfig.TARGET_PKG + "/files");
            java.io.File f = new java.io.File(dir, "honormarket_tamer_alive.txt");
            java.io.FileWriter w = new java.io.FileWriter(f, true);
            w.write("loaded at " + new java.util.Date() + " pid=" + android.os.Process.myPid() + "\n");
            w.close();
        } catch (Throwable t) {
            android.util.Log.i("HonorMarketTamer", "marker write failed: " + t);
        }
    }

    private static void safe(String name, Thunk t) {
        try {
            t.run();
        } catch (Throwable tr) {
            XposedBridge.log("[HonorMarketTamer] " + name + " init failed: " + tr);
        }
    }
}
