package com.tamer.honormarket;

import com.tamer.honormarket.hooks.ActivityBlocker;
import com.tamer.honormarket.hooks.ConfigRelay;
import com.tamer.honormarket.hooks.DialogBlocker;
import com.tamer.honormarket.hooks.HealthSensorGate;
import com.tamer.honormarket.hooks.MineSectionBlocker;
import com.tamer.honormarket.hooks.PushBlocker;
import com.tamer.honormarket.hooks.RecommendFeedBlocker;
import com.tamer.honormarket.hooks.RollWordBlocker;
import com.tamer.honormarket.hooks.SearchAdBlocker;
import com.tamer.honormarket.hooks.SplashAdBlocker;
import com.tamer.honormarket.hooks.TabStripper;
import com.tamer.honormarket.hooks.ToolPageEntryBlocker;
import com.tamer.honormarket.hooks.WakeBlocker;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * 荣耀应用市场净化 - LSPosed 模块入口
 * 主目标：com.hihonor.appmarket
 * 原则：搜索应用与应用更新永远可用；其余功能均可通过开关屏蔽。
 * 附加目标：com.hihonor.health（默认关）——HealthSensorGate 传感器闸门，
 *          由 health_sensor_gate 开关 + health_packages 包名列表独立控制。
 */
public class MainHook implements IXposedHookLoadPackage {

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpp) {
        // 自证日志：直接进系统 logcat，不依赖 LSPosed 日志页
        android.util.Log.i("HonorMarketTamer", "handleLoadPackage: " + lpp.packageName
                + " process=" + lpp.processName + " pid=" + android.os.Process.myPid());

        // ---- 健康类应用传感器闸门（默认关；health_packages 列表）----
        // 在目标健康进程内收 SensorManager 注册口：熄屏拒注册/注销已注册会话，
        // 亮屏按原参数重放（详见 HealthSensorGate）。移植自 QQTamer，逻辑不变。
        if (!TamerConfig.TARGET_PKG.equals(lpp.packageName)) {
            // safeXsp：LSPosed 2.3.0+ 移除 XSP 时返回 null（降级 conf 文件兜底），不崩钩子。
            XSharedPreferences hxsp = TamerConfig.safeXsp();
            final TamerConfig hcfg = TamerConfig.loadForHook(hxsp);
            if (hcfg.get(TamerConfig.KEY_MASTER, true)
                    && hcfg.get(TamerConfig.KEY_HEALTH_GATE, false)) {
                final String pkgs = hcfg.getStr(TamerConfig.KEY_HEALTH_PACKAGES,
                        TamerConfig.DEFAULT_HEALTH_PACKAGES);
                if (inCsv(pkgs, lpp.packageName)) {
                    final ClassLoader hcl = lpp.classLoader;
                    XposedBridge.log("[HonorMarketTamer] HealthSensorGate target pkg="
                            + lpp.packageName + " process=" + lpp.processName);
                    safe("HealthSensorGate", new Thunk() {
                        public void run() { HealthSensorGate.install(hcl, hcfg); }
                    });
                }
            }
            return;
        }
        writeAliveMarker();
        // safeXsp：LSPosed 2.3.0+ 移除 XSP 时返回 null（apexdata 直读降级 conf 文件兜底）。
        XSharedPreferences xsp = TamerConfig.safeXsp();
        TamerConfig cfg = TamerConfig.loadForHook(xsp);

        // 配置中继（无 root 主链路）：常驻安装、不受总开关门控——master 当前
        // 关闭时，用户从设置页拨回后也需要这条通道把新配置送达宿主文件。
        // v1.4.4 起双通道：① Provider（市场自启时拉设置页权威 SP，不拉起）
        // ② launch extras（兜底）。setActiveCfg 把本进程配置实例交给中继，
        // Provider 拉取成功即覆盖它（Blocker 与中继共享同一实例，即时生效）。
        ConfigRelay.setActiveCfg(cfg);
        safe("ConfigRelay", new Thunk() { public void run() { ConfigRelay.hook(lpp.classLoader); } });

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
                + " update=" + cfg.get(TamerConfig.KEY_UPDATE_FEED, true) + "]"
                + " sad[booth=" + cfg.get(TamerConfig.KEY_SEARCH_AD_BOOTH, true)
                + " must=" + cfg.get(TamerConfig.KEY_SEARCH_MUST, true) + "]"
                + " toolpage=" + cfg.get(TamerConfig.KEY_TOOLPAGE_ENTRY, true)
                + " confSrc=" + TamerConfig.LAST_CONF_SRC);

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
        safe("SearchAdBlocker", new Thunk() { public void run() { SearchAdBlocker.hook(lppFinal.classLoader, cfgFinal); } });
        safe("ToolPageEntryBlocker", new Thunk() { public void run() { ToolPageEntryBlocker.hook(lppFinal.classLoader, cfgFinal); } });
    }

    interface Thunk { void run() throws Throwable; }

    /** 逗号分隔包名列表匹配（trim + 忽略空段） */
    private static boolean inCsv(String csv, String pkg) {
        if (csv == null || pkg == null) return false;
        for (String t : csv.split(",")) {
            if (pkg.equals(t.trim())) return true;
        }
        return false;
    }

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
