package com.tamer.honormarket.hooks;

import com.tamer.honormarket.TamerConfig;
import de.robv.android.xposed.XC_MethodHook.MethodHookParam;
import de.robv.android.xposed.XposedBridge;

/**
 * 配置中继（无 root 主链路，照抄 LineTamer v1.6.1 / BiliTamer 通用引擎）。
 *
 * 通道：设置页保存后带 hmt_conf/hmt_gen 拉起市场（显式 ComponentName 到 launcher
 * Splash + NEW_TASK|CLEAR_TOP|SINGLE_TOP），此处截获 launcher Activity 的
 * onCreate（冷启动读 getIntent）与 onNewIntent（运行中重投递读 getArg(0)），
 * 解析后写入市场自有 files/hmt_host.conf（带 #gen 代次行），成为钩子侧读序首位
 * （TamerConfig.readConfFile）。此后配置闭环不再依赖跨进程读 SP 或 root 副本——
 * OEM 封 Provider/uri grant/media 都封不住组件启动。
 *
 * 挂载点（引擎双钩子，只钩 launcher）：
 * 市场 launcher = .module.splash.Splash（resolve-activity 实测，singleTop，转发
 * MainActivity 后即 finish()）。Splash 转发时 new Intent 只拷贝 showCnAgreement /
 * isFromSplash 等键、【不转发原始 extras】，故 hmt_conf/hmt_gen 只存在于 Splash
 * 的 getIntent()，MainActivity 永远收不到（resume-probe 实测 hasConf=false）。
 * 带 CLEAR_TOP|SINGLE_TOP 拉起已运行市场时系统会新建 Splash 实例（旧实例已 finish），
 * 其 onCreate 携 extra 触发——故冷/热两条路径都落在 Splash.onCreate（实机 dumpsys +
 * logcat 双证）。onNewIntent 按引擎一并挂上（Splash 继承 Activity 空实现，实际不触发，
 * 挂之无害，保持与引擎一致）。
 *
 * 双通道（v1.4.4 起）：
 * ① Provider 通道（主，不拉起）：宿主 Context 就绪（Splash.onCreate）即经
 *    ConfigProvider 跨应用 IPC 拉设置页权威 SP，代次新则覆盖本进程配置——
 *    设置页拨开关【不必拉起市场】，市场自启时自动拿到最新配置。
 * ② launch 通道（兜底，保留）：设置页带 extras 拉起市场时截获写入 host-conf，
 *    文件通道读序仍最优先，Provider 拉取失败时自动回退到它。
 *
 * 代次协议（引擎同款）：写入侧单调保护——携带旧 gen 的在途 Intent 不覆盖新代次文件。
 * 本钩子不受总开关门控：master 关闭时用户拨回，也需此通道把新配置送达。
 */
public final class ConfigRelay {

    private static volatile TamerConfig sActiveCfg;

    /** MainHook 在 handleLoadPackage 里把本次进程的配置实例交给中继；
     *  Provider 拉取成功后覆盖它（与所有 Blocker 共享同一实例，即时生效）。 */
    public static void setActiveCfg(TamerConfig cfg) {
        sActiveCfg = cfg;
    }

    public static void hook(ClassLoader cl) {
        // onCreate（冷启动落点）：读 getIntent()。argc=1（Bundle）。
        HookUtil.tryHookFlex(cl, TamerConfig.LAUNCHER_CLASS, "onCreate", 1, new HookUtil.FlexCallback() {
            @Override
            public void fire(MethodHookParam param) {
                Object act = param.thisObject;
                if (act instanceof android.content.Context) {
                    // 宿主 Context 就绪点：捕获 applicationContext 供 Provider 拉取
                    HostCtx.set((android.content.Context) act);
                }
                // 无 root 主链路（v1.4.6）：直读设置页权威 SP（apexdata 644）
                TamerConfig.refreshFromModuleSp(sActiveCfg);
                // Provider 通道（冗余，stock 设备可用）
                TamerConfig.refreshFromProvider(HostCtx.get(), sActiveCfg);
                android.content.Intent it = (act instanceof android.app.Activity)
                        ? ((android.app.Activity) act).getIntent() : null;
                deliver(it);
            }
        });
        // onNewIntent（运行中重投递落点）：读传入的 Intent（args[0]），与引擎一致。
        HookUtil.tryHookFlex(cl, TamerConfig.LAUNCHER_CLASS, "onNewIntent", 1, new HookUtil.FlexCallback() {
            @Override
            public void fire(MethodHookParam param) {
                TamerConfig.refreshFromModuleSp(sActiveCfg);
                TamerConfig.refreshFromProvider(HostCtx.get(), sActiveCfg);
                deliver(param.args != null && param.args.length > 0 ? param.args[0] : null);
            }
        });
        // ===== warm 投递兜底（照抄 LineTamer 双钩子）=====
        // 市场已运行（MainActivity 在栈顶）时再次带 conf 拉起，系统把新 Intent
        // 交给已存在的 MainActivity 而非重建 Splash。挂 MainActivity 的 onCreate +
        // onNewIntent 兜住 warm 路径。冷启动时 MainActivity.getIntent() 无 extras
        // （Splash 转发不拷原始 extras），deliver 早退 no-op，无害。
        HookUtil.tryHookFlex(cl, TamerConfig.MAIN_ACTIVITY_CLASS, "onCreate", 1, new HookUtil.FlexCallback() {
            @Override
            public void fire(MethodHookParam param) {
                Object act = param.thisObject;
                if (act instanceof android.content.Context) {
                    HostCtx.set((android.content.Context) act);
                }
                TamerConfig.refreshFromModuleSp(sActiveCfg);
                android.content.Intent it = (act instanceof android.app.Activity)
                        ? ((android.app.Activity) act).getIntent() : null;
                deliver(it);
            }
        });
        HookUtil.tryHookFlex(cl, TamerConfig.MAIN_ACTIVITY_CLASS, "onNewIntent", 1, new HookUtil.FlexCallback() {
            @Override
            public void fire(MethodHookParam param) {
                TamerConfig.refreshFromModuleSp(sActiveCfg);
                deliver(param.args != null && param.args.length > 0 ? param.args[0] : null);
            }
        });
        // ===== warm 主触发点（v1.4.6）=====
        // 市场已运行、用户拨完开关再打开市场时，任何 Activity 进前台都会触发
        // onResume——此刻直读设置页权威 SP（apexdata 644）并代次比较覆盖本进程
        // 配置。不依赖"市场被正确启动携 extras"（OEM smart-launch/snapshot 会
        // 跳过 Splash.onCreate 且 Splash 转发不拷 extras），是最稳的 warm 通路。
        // 节流 10s（refreshFromModuleSp 内部），多 Activity 串联触发无害。
        HookUtil.tryHook(cl, "android.app.Activity", "onResume", new Object[0],
                new de.robv.android.xposed.XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(
                            de.robv.android.xposed.XC_MethodHook.MethodHookParam param) {
                        TamerConfig.refreshFromModuleSp(sActiveCfg);
                    }
                });
    }

    /** 解析投递 extras → 代次比较 → 落盘宿主副本（引擎 deliver 的 HMT 对应物）。 */
    private static void deliver(Object intentObj) {
        try {
            if (!(intentObj instanceof android.content.Intent)) return;
            android.content.Intent it = (android.content.Intent) intentObj;
            if (!it.hasExtra(TamerConfig.EXTRA_CONF)) return;
            String conf = it.getStringExtra(TamerConfig.EXTRA_CONF);
            if (conf == null || conf.length() == 0) return;
            long gen = it.getLongExtra(TamerConfig.EXTRA_GEN, 0L);

            // 规范化重建：仅保留 ALL_KEYS 白名单内的键，值必须 true/false；
            // 传输形态（extras 原文）不落入副本。
            StringBuilder sb = new StringBuilder();
            if (gen > 0) sb.append("#gen=").append(gen).append('\n');
            java.util.Map<String, Boolean> parsed =
                    new java.util.HashMap<String, Boolean>();
            java.util.HashSet<String> known = new java.util.HashSet<String>();
            for (String k : TamerConfig.ALL_KEYS) known.add(k);
            int kept = 0;
            for (String line : conf.split("\n")) {
                int i = line.indexOf('=');
                if (i <= 0) continue;
                String key = line.substring(0, i).trim();
                String val = line.substring(i + 1).trim();
                if (!known.contains(key)) continue;
                if (!"true".equals(val) && !"false".equals(val)) continue;
                sb.append(key).append('=').append(val).append('\n');
                parsed.put(key, Boolean.valueOf(val));
                kept++;
            }
            if (kept == 0) return;

            // 单调保护：旧 gen 的在途 Intent 不覆盖新代次文件
            long fileGen = readGen(hostFile());
            if (gen > 0 && gen < fileGen) {
                XposedBridge.log("[HonorMarketTamer] conf relay skipped: gen="
                        + gen + " < file gen=" + fileGen);
                return;
            }

            java.io.File f = hostFile();
            java.io.FileOutputStream fos = new java.io.FileOutputStream(f);
            try {
                fos.write(sb.toString().getBytes("UTF-8"));
                fos.getFD().sync();
            } finally {
                fos.close();
            }
            // 关键：运行中市场的本进程配置仍是旧快照（进程启动时读的文件），
            // 只落盘 host-conf 要等下次冷启才生效。这里同步更新 in-memory 覆盖层
            // （与 Provider 通道共用 applyOverride），开关即时生效、无需重启市场。
            if (sActiveCfg != null && gen > sActiveCfg.overrideGen()) {
                sActiveCfg.applyOverride(parsed, null, gen);
            }
            XposedBridge.log("[HonorMarketTamer] conf delivered via launch intent, gen="
                    + gen + " keys=" + kept
                    + " inMem=" + (sActiveCfg != null ? "applied" : "noCfg"));
        } catch (Throwable t) {
            XposedBridge.log("[HonorMarketTamer] conf delivery failed: " + t);
        }
    }

    private static java.io.File hostFile() {
        return new java.io.File(TamerConfig.HOST_CONF_DIR, TamerConfig.HOST_CONF_NAME);
    }

    /** 读既有 host-conf 的 #gen 行（不存在/无 gen 行 → 0，旧格式兜底分支） */
    private static long readGen(java.io.File f) {
        if (!f.isFile() || !f.canRead()) return 0L;
        try {
            java.io.BufferedReader br = new java.io.BufferedReader(
                    new java.io.InputStreamReader(new java.io.FileInputStream(f), "UTF-8"));
            try {
                String line = br.readLine();
                while (line != null) {
                    if (line.startsWith("#gen=")) {
                        try { return Long.parseLong(line.substring(5).trim()); }
                        catch (Throwable t) { return 0L; }
                    }
                    line = br.readLine();
                }
            } finally {
                br.close();
            }
        } catch (Throwable ignored) {}
        return 0L;
    }

    private ConfigRelay() {}
}
