package com.tamer.honormarket;

import java.io.File;

/**
 * 开关配置：与设置界面(SettingsActivity)共用同一份 SharedPreferences。
 * Hook 侧通过 XSharedPreferences 读取。
 */
public final class TamerConfig {
    public static final String MODULE_PKG = "com.tamer.honormarket";
    public static final String PREFS_NAME = "tamer_config";
    public static final String TARGET_PKG = "com.hihonor.appmarket";
    /** 市场 launcher（resolve-activity 实测：com.hihonor.appmarket/.module.splash.Splash）。
     *  显式 ComponentName 启动（SettingsActivity）与投递钩子（ConfigRelay）共用。 */
    public static final String LAUNCHER_CLASS = "com.hihonor.appmarket.module.splash.Splash";
    /** 市场主 Activity（warm 投递落点，照抄 LineTamer 双钩子）：市场已运行时
     *  带 conf 再次拉起，系统把新 Intent 交给已存在的 MainActivity（onNewIntent），
     *  而非重建 Splash。挂 onCreate+onNewIntent 兜住 warm 路径。冷启动时其
     *  getIntent() 无 extras（Splash 不转发），deliver 自然 no-op，无害。 */
    public static final String MAIN_ACTIVITY_CLASS =
            "com.hihonor.appmarket.module.main.MainActivity";
    /** 健康类目标应用（传感器闸门默认作用对象） */
    public static final String HEALTH_PKG = "com.hihonor.health";
    /** health_packages 未配置时的兜底包名列表（逗号分隔） */
    public static final String DEFAULT_HEALTH_PACKAGES = "com.hihonor.health";
    /** 自管配置文件（POSIX 644，Hook 端兜底读取） */
    public static final String CONF_NAME = "tamer_config.conf";
    /** 宿主侧 conf（无 root 主链路，LineTamer v1.6.1 模式）：写在目标应用自己
     *  files/ 下（HOST_CONF_DIR），钩子进程在目标进程内必然可读，读序最优先。
     *  由 ConfigRelay 从启动 Intent extras 重建（规范化行格式，带 #gen 代次）。 */
    public static final String HOST_CONF_NAME = "hmt_host.conf";
    public static final String HOST_CONF_DIR = "/data/user/0/" + TARGET_PKG + "/files";

    // ===== 启动 Intent extras 键（设置页保存 → 市场 Splash.onCreate 截获）=====
    public static final String EXTRA_CONF = "hmt_conf";
    public static final String EXTRA_GEN  = "hmt_gen";
    /** 代次戳：SP long 键（不序列化进布尔 conf 文件），每次保存刷新 */
    public static final String KEY_CONF_GEN = "conf_gen";

    /** 读 SP 里的配置代次（设置页每次保存时刷新） */
    public static long confGen(android.content.SharedPreferences sp) {
        try { return sp.getLong(KEY_CONF_GEN, 0L); } catch (Throwable t) { return 0L; }
    }

    /**
     * 安全创建 XSP（v1.4.7 兜底）：LSPosed v2.2.0 标记 XSharedPreferences 弃用、
     * v2.3.0 移除（见共享引擎踩坑文档 #17）。移除后 `new XSharedPreferences` 抛
     * ClassNotFoundException——此处吞掉返回 null，使 loadForHook / refreshFromModuleSp
     * 优雅降级到 host-conf / conf 文件兜底链路，避免整个 handleLoadPackage 因 XSP 类
     * 消失而崩溃、模块自灭。
     * 注意：apexdata SP 直读主链路（readModuleSp / refreshFromModuleSp）依赖 XSP 的
     * getFile() 解析 apexdata 真实路径（市场进程异 uid、apexdata 顶层 711 不可枚举，
     * 无 XSP 则无法定位该路径）——故该主链路仅在 LSPosed < 2.3.0 生效；XSP 缺席时
     * 自动回退 host-conf 通道（#16/#17 主链路）。日志只打一次（sXspWarned）。
     */
    private static boolean sXspWarned = false;
    public static de.robv.android.xposed.XSharedPreferences safeXsp() {
        try {
            return new de.robv.android.xposed.XSharedPreferences(MODULE_PKG, PREFS_NAME);
        } catch (Throwable t) {
            if (!sXspWarned) {
                sXspWarned = true;
                de.robv.android.xposed.XposedBridge.log("[HonorMarketTamer] XSP unavailable "
                        + "(LSPosed 2.3.0+ removed?), degrading to conf-file fallback: " + t);
            }
            return null;
        }
    }

    // ===== 总开关 =====
    public static final String KEY_MASTER = "master_enabled"; // 模块总开关

    // ===== 广告与运营（默认屏蔽）=====
    public static final String KEY_SPLASH_AD   = "block_splash_ad";        // 开屏广告
    public static final String KEY_OP_DIALOG   = "block_operation_dialog"; // 运营活动弹窗
    public static final String KEY_OP_FLOAT    = "block_operation_float";  // 运营悬浮窗
    public static final String KEY_WIDGET_TIP  = "block_widget_promote";   // 桌面卡片推广弹窗
    public static final String KEY_PERS_AD     = "block_personalized_ads"; // 个性化广告弹窗
    public static final String KEY_TOOLPAGE_ENTRY = "block_toolpage_activity_entry"; // 工具页标题栏运营活动入口(树形按钮)

    // ===== 推送与唤醒（默认屏蔽）=====
    public static final String KEY_PUSH        = "block_push";             // 推送通知
    public static final String KEY_WAKE        = "block_wake_autostart";   // 自启/广播唤醒
    public static final String KEY_SILENT_UPD  = "block_silent_update";    // 静默检查更新(默认保留)
    public static final String KEY_UPDATE_NOTIFY = "block_update_notify";  // 更新提醒通知(默认保留)
    public static final String KEY_ALL_NOTIFY  = "block_all_notifications";// 全部系统通知总闸(默认保留)

    // ===== 页面/频道屏蔽（默认不屏蔽）=====
    public static final String KEY_GAME_TAB    = "block_game_tab";      // 游戏频道
    public static final String KEY_QUICK_GAME  = "block_quick_game";    // 快游戏
    public static final String KEY_CAT_TAB     = "block_category_tab";  // 分类频道
    public static final String KEY_MINE_TAB    = "block_mine_tab";      // 我的页面
    public static final String KEY_RANK_PAGE   = "block_rank_page";     // 排行榜/热门榜
    public static final String KEY_CHILDREN    = "block_children_zone"; // 儿童模式
    public static final String KEY_CLEAN       = "block_clean_boost";   // 清理加速
    public static final String KEY_SAFETY      = "block_safety_check";  // 安全检测
    public static final String KEY_APP_MANAGE  = "block_app_manage";    // 应用管理(安装/卸载管理)
    public static final String KEY_MSG_CENTER  = "block_msg_center";    // 消息中心
    public static final String KEY_QIANGXIAN_TAB = "block_qiangxian_tab"; // 抢鲜 Tab
    public static final String KEY_APPS_TAB    = "block_apps_tab";      // 应用 Tab

    // ===== 首页搜索框（默认不屏蔽）=====
    public static final String KEY_HOME_ROLLWORD = "block_home_rollword"; // 搜索框滚动热词横幅

    // ===== 推荐流屏蔽（用户明确要求干掉，默认屏蔽）=====
    public static final String KEY_MINE_FEED   = "block_mine_feed";    // 「我的」页底部应用推荐流
    public static final String KEY_SEARCH_FEED = "block_search_feed";  // 搜索发现页应用推荐（含请求源头拦截）
    public static final String KEY_UPDATE_FEED = "block_update_recommend"; // 更新页“新应用，新用途”推荐
    public static final String KEY_DETAIL_REC  = "block_detail_recommend"; // 应用详情页「应用名+用户必备的软件推荐」
    public static final String KEY_SEARCH_AD_BOOTH = "block_search_ad_booth";   // 搜索结果页广告展位大卡
    public static final String KEY_SEARCH_MUST = "block_search_must_install";   // 搜索结果页「装机必备」横滑

    // ===== “我的”页内板块（默认不屏蔽）=====
    public static final String KEY_MINE_SLIDE    = "mine_block_slide";    // 滑一滑发现更多精彩
    public static final String KEY_MINE_SAFETY   = "mine_block_safety";   // 安全检测板块
    public static final String KEY_MINE_CLEAN    = "mine_block_clean";    // 清理加速板块
    public static final String KEY_MINE_SERVICES = "mine_block_services"; // 常用服务板块
    public static final String KEY_MINE_SIGNIN   = "mine_block_signin";   // 签到领奖入口

    // ===== 健康类应用（默认关；在目标健康进程内生效）=====
    public static final String KEY_HEALTH_GATE     = "health_sensor_gate"; // 传感器闸门：熄屏拒注册/注销，亮屏重放
    public static final String KEY_HEALTH_PACKAGES = "health_packages";    // 闸门作用包名（逗号分隔，字符串键）

    /** 全部开关键（设置页序列化用）——Tab 类仅保留 应用/抢鲜/游戏 */
    public static final String[] ALL_KEYS = {
        KEY_MASTER, KEY_SPLASH_AD, KEY_OP_DIALOG, KEY_OP_FLOAT, KEY_WIDGET_TIP,
        KEY_PERS_AD, KEY_TOOLPAGE_ENTRY, KEY_PUSH, KEY_WAKE, KEY_SILENT_UPD, KEY_UPDATE_NOTIFY,
        KEY_ALL_NOTIFY, KEY_GAME_TAB, KEY_QIANGXIAN_TAB, KEY_APPS_TAB,
        KEY_HOME_ROLLWORD,
        KEY_MINE_SIGNIN, KEY_MINE_SAFETY, KEY_MINE_CLEAN, KEY_MINE_SERVICES,
        KEY_MINE_SLIDE, KEY_MINE_FEED, KEY_SEARCH_FEED, KEY_UPDATE_FEED,
        KEY_DETAIL_REC, KEY_SEARCH_AD_BOOTH, KEY_SEARCH_MUST,
        KEY_HEALTH_GATE,
    };

    /** 各页面开关对应的 Activity 类名列表 */
    public static final String[][] BLOCKED_ACTIVITIES = {
        { KEY_GAME_TAB,   "com.hihonor.appmarket.module.main.minigame.QuickGameDetailActivity" },
        { KEY_GAME_TAB,   "com.hihonor.appmarket.launcher.entrance.shortcut.LauncherHotGameActivity" },
        { KEY_GAME_TAB,   "com.hihonor.appmarket.launcher.entrance.shortcut.LauncherQuickGameActivity" },
        { KEY_QUICK_GAME, "com.hihonor.appmarket.launcher.entrance.quickgame.QuickGameSettingActivity" },
        { KEY_CAT_TAB,    "com.hihonor.appmarket.main.classification.ClassificationForOverseasActivity" },
        { KEY_CAT_TAB,    "com.hihonor.appmarket.main.classification.ClassificationMoreActivity" },
        { KEY_CAT_TAB,    "com.hihonor.appmarket.main.classification.ThirdCategoryDetailActivity" },
        { KEY_MINE_TAB,   "com.hihonor.appmarket.module.mine.property.PropertyActivity" },
        { KEY_MINE_TAB,   "com.hihonor.appmarket.module.mine.property.MineGiftLIstActivity" },
        { KEY_MINE_TAB,   "com.hihonor.appmarket.module.mine.property.MineCouponActivity" },
        { KEY_MINE_TAB,   "com.hihonor.appmarket.module.mine.reserve.MyReserveActivity" },
        { KEY_MINE_TAB,   "com.hihonor.appmarket.module.mine.wishlist.WishListActivity" },
        { KEY_RANK_PAGE,  "com.hihonor.appmarket.module.common.SellingRankActivity" },
        { KEY_RANK_PAGE,  "com.hihonor.appmarket.popularapps.activity.PopularAppsSettingActivity" },
        { KEY_RANK_PAGE,  "com.hihonor.appmarket.popularapps.activity.PopularAppsSortRuleActivity" },
        { KEY_RANK_PAGE,  "com.hihonor.appmarket.module.topapps.TopAppsSettingActivity" },
        { KEY_CHILDREN,   "com.hihonor.appmarket.module.main.children.ChildrenAppActivity" },
        { KEY_CHILDREN,   "com.hihonor.appmarket.module.common.ChildrenAssemblyListActivity" },
        { KEY_CLEAN,      "com.hihonor.appmarket.biz_clean.clean.activity.CleanAccelerateActivity" },
        { KEY_CLEAN,      "com.hihonor.appmarket.biz_clean.clean.activity.DeepCleanActivity" },
        { KEY_SAFETY,     "com.hihonor.appmarket.biz_safety_check_core.activity.SafetyCheckActivityV2" },
        { KEY_SAFETY,     "com.hihonor.appmarket.biz_safety_check_core.activity.RiskAppDetailActivityV2" },
        { KEY_SAFETY,     "com.hihonor.appmarket.biz_safety_check_core.activity.SafetyIgnoreActivity" },
        { KEY_SAFETY,     "com.hihonor.appmarket.biz_safety_check_core.activity.SafetyReportActivity" },
        { KEY_SAFETY,     "com.hihonor.appmarket.biz_safety_check_core.activity.SafetySettingActivity" },
        { KEY_SAFETY,     "com.hihonor.appmarket.biz_safety_check_core.activity.UnUsedAppUninstallActivity" },
        { KEY_APP_MANAGE, "com.hihonor.appmarket.app.manage.download.InstallManagerActivity" },
        { KEY_APP_MANAGE, "com.hihonor.appmarket.app.manage.record.InstallRecordActivity" },
        { KEY_APP_MANAGE, "com.hihonor.appmarket.app.manage.uninstall.AppUninstallActivity" },
        { KEY_APP_MANAGE, "com.hihonor.appmarket.app.manage.uninstall.AppUninstallOverSeaActivity" },
        { KEY_APP_MANAGE, "com.hihonor.appmarket.archive.ui.ArchiveAppManageActivity" },
        { KEY_APP_MANAGE, "com.hihonor.appmarket.archive.ui.CanArchiveAppActivity" },
        { KEY_MSG_CENTER, "com.hihonor.appmarket.msgcenterview.MsgCenterActivityV2" },
        { KEY_MSG_CENTER, "com.hihonor.appmarket.msgcenterview.MsgInteractionActivity" },
        { KEY_MSG_CENTER, "com.hihonor.appmarket.msgcenterview.EuMsgListActivity" },
    };

    private final android.content.SharedPreferences sp;

    /**
     * Provider 通道覆盖层（最高优先级）：宿主进程启动后，ConfigRelay 在宿主
     * Context 就绪时经 refreshFromProvider 拉权威 SP（含 conf_gen），代次新于
     * 本进程已用配置则整体覆盖 conf 文件通道的陈旧值。文件通道（host-conf/
     * files-conf）是"市场自启时"的快照，Provider 是"此刻"的实时值，后者胜。
     */
    private final java.util.Map<String, Boolean> override =
            new java.util.concurrent.ConcurrentHashMap<String, Boolean>();
    private final java.util.Map<String, String> overrideStr =
            new java.util.concurrent.ConcurrentHashMap<String, String>();
    private volatile long overrideGen = 0L;

    /** 本进程最后一次成功加载的配置来源（排查"开关不生效"用，日志打印） */
    public static volatile String LAST_CONF_SRC = "none";

    public TamerConfig(android.content.SharedPreferences sp) { this.sp = sp; }

    public boolean get(String key, boolean def) {
        Boolean v = override.get(key);
        if (v != null) return v.booleanValue();
        try { return sp.getBoolean(key, def); } catch (Throwable t) { return def; }
    }

    /** 字符串键（如 health_packages）：conf 文件只存布尔，故 Map 侧永远落默认值；
     *  字符串仅经 XSharedPreferences / Provider 通道写入/读取。 */
    public String getStr(String key, String def) {
        String v = overrideStr.get(key);
        if (v != null) return v;
        try { return sp.getString(key, def); } catch (Throwable t) { return def; }
    }

    /** 当前生效的 Provider 覆盖代次（0 = 尚未收到 Provider 覆盖） */
    public long overrideGen() { return overrideGen; }

    /**
     * 应用一次 Provider 拉取到的配置：整体替换覆盖层（不合并——SP 是权威全量
     * 快照，合并会把 SP 里已删的键残留下来）。代次单调，旧代次永不覆盖新代次。
     */
    public void applyOverride(java.util.Map<String, Boolean> booleans,
                              java.util.Map<String, String> strings, long gen) {
        override.clear();
        override.putAll(booleans);
        overrideStr.clear();
        if (strings != null) overrideStr.putAll(strings);
        overrideGen = gen;
    }

    // ===== Provider 通道（无 root 主链路，v1.4.4，照抄 LineTamer v1.6.1）=====
    /** 设置页 ConfigProvider 的权威 URI（Hook 端用它拉 SP） */
    public static final String PROVIDER_URI = "content://" + MODULE_PKG + ".config/prefs";
    private static final long PROVIDER_MIN_INTERVAL_MS = 10_000L;
    private static final Object sProviderLock = new Object();
    private static long sLastProviderPullAt = 0L;

    /**
     * 宿主 Context 就绪时经标准 ContentResolver IPC 拉设置页权威 SP（含
     * conf_gen）。幂等 + 节流（10s）：市场进程内多处就绪回调只拉一次；拉取
     * 失败静默回退到文件通道（host-conf 仍是有效兜底）。
     */
    public static void refreshFromProvider(android.content.Context ctx, TamerConfig cfg) {
        if (ctx == null || cfg == null) return;
        synchronized (sProviderLock) {
            long now = System.currentTimeMillis();
            if (now - sLastProviderPullAt < PROVIDER_MIN_INTERVAL_MS) return;
            sLastProviderPullAt = now;
        }
        try {
            // content:// URI 对只实现 openFile（返回 PFD）的 Provider，
            // openInputStream 由框架把 PFD 包成流——跨应用 binder IPC，
            // 不依赖 LSPosed 任何特性。
            pullViaStream(ctx, cfg);
        } catch (Throwable t) {
            android.util.Log.i("HonorMarketTamer", "provider pull failed: " + t);
        }
    }

    /**
     * 运行中（warm）市场直读设置页权威 SP 并覆盖本进程配置（无 root 主链路，v1.4.6）。
     * 与 refreshFromProvider 同款节流/代次协议，但读的是 SP 文件本身而非被封的
     * Provider IPC：市场侧 XSharedPreferences 解析到 apexdata 重定向后真实路径，
     * 设置页每次保存同步写 SP + chmod 644，故读到的是"此刻"最新配置。代次新则
     * applyOverride（与所有 Blocker 共享同一 cfg 实例，即时生效、无需重启市场）。
     * 每次重建 XSP（轻量），不跨调用缓存——apexdata uuid 路径由 LSPosed 实时解析。
     */
    public static void refreshFromModuleSp(TamerConfig cfg) {
        if (cfg == null) return;
        synchronized (sProviderLock) {
            long now = System.currentTimeMillis();
            if (now - sLastProviderPullAt < PROVIDER_MIN_INTERVAL_MS) return;
            sLastProviderPullAt = now;
        }
        try {
            de.robv.android.xposed.XSharedPreferences xsp = safeXsp();
            // XSP 被移除（LSPosed 2.3.0+）时 warm 直读主链路不可用：静默跳过（不每 10s
            // 打错误日志），配置改由冷启动 conf 文件 / host-conf 兜底链路提供。
            if (xsp == null) return;
            java.io.File xf = xsp.getFile();
            if (xf == null || !xf.isFile() || !xf.canRead()) return;
            java.io.InputStream in = new java.io.FileInputStream(xf);
            java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int n;
            while ((n = in.read(chunk)) > 0) buf.write(chunk, 0, n);
            in.close();
            String xml = new String(buf.toByteArray(), "UTF-8");
            java.util.Map<String, Object> parsed = parseSpXml(xml, PREFS_NAME);
            @SuppressWarnings("unchecked")
            java.util.Map<String, Boolean> booleans =
                    (java.util.Map<String, Boolean>) parsed.get("booleans");
            long gen = (Long) parsed.get("gen");
            if (booleans == null || booleans.isEmpty()) return;
            if (gen > cfg.overrideGen()) {
                cfg.applyOverride(booleans, null, gen);
                LAST_CONF_SRC = "moduleSpLive(gen=" + gen + ")";
                de.robv.android.xposed.XposedBridge.log("[HonorMarketTamer] moduleSp live override gen="
                        + gen + " keys=" + booleans.size());
            }
        } catch (Throwable t) {
            de.robv.android.xposed.XposedBridge.log("[HonorMarketTamer] refreshFromModuleSp failed: " + t);
        }
    }

    /** openInputStream 读 Provider SP XML（binder IPC，跨应用标准通路） */
    private static void pullViaStream(android.content.Context ctx, TamerConfig cfg)
            throws Exception {
        java.io.InputStream in = ctx.getContentResolver()
                .openInputStream(android.net.Uri.parse(PROVIDER_URI));
        if (in == null) {
            android.util.Log.w("HonorMarketTamer", "provider stream null");
            return;
        }
        java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int n;
        while ((n = in.read(chunk)) > 0) buf.write(chunk, 0, n);
        in.close();
        String xml = new String(buf.toByteArray(), "UTF-8");
        java.util.Map<String, Object> parsed = parseSpXml(xml, PREFS_NAME);
        @SuppressWarnings("unchecked")
        java.util.Map<String, Boolean> booleans =
                (java.util.Map<String, Boolean>) parsed.get("booleans");
        @SuppressWarnings("unchecked")
        java.util.Map<String, String> strings =
                (java.util.Map<String, String>) parsed.get("strings");
        long gen = (Long) parsed.get("gen");
        if (booleans == null || booleans.isEmpty()) return;
        if (gen > cfg.overrideGen()) {
            cfg.applyOverride(booleans, strings, gen);
            LAST_CONF_SRC = "provider(gen=" + gen + ")";
            de.robv.android.xposed.XposedBridge.log("[HonorMarketTamer] provider override applied gen="
                    + gen + " keys=" + booleans.size());
        }
    }

    /**
     * 解析 SP XML（只取布尔键 + 字符串键 + conf_gen）。SP XML 形如：
     *   <map><boolean name="master_enabled" value="true"/>...
     *        <string name="health_packages">com.hihonor.health</string></map>
     * conf_gen 是 long，XML 里以 <long> 存；老 SP（无 long）则无 conf_gen 键，
     * 此时 gen 取 0（不会覆盖已有配置）。
     */
    public static java.util.Map<String, Object> parseSpXml(String xml, String name) {
        java.util.Map<String, Boolean> booleans = new java.util.HashMap<String, Boolean>();
        java.util.Map<String, String> strings = new java.util.HashMap<String, String>();
        long gen = 0L;
        if (xml != null) {
            java.util.regex.Matcher mb = java.util.regex.Pattern
                    .compile("<boolean name=\"([^\"]+)\" value=\"(true|false)\"")
                    .matcher(xml);
            while (mb.find()) booleans.put(mb.group(1), "true".equals(mb.group(2)));
            java.util.regex.Matcher ms = java.util.regex.Pattern
                    .compile("<string name=\"([^\"]+)\"[^>]*>([^<]*)</string>")
                    .matcher(xml);
            while (ms.find()) strings.put(ms.group(1), ms.group(2));
            java.util.regex.Matcher mg = java.util.regex.Pattern
                    .compile("<long name=\"" + KEY_CONF_GEN + "\" value=\"(\\d+)\"")
                    .matcher(xml);
            if (mg.find()) {
                try { gen = Long.parseLong(mg.group(1)); } catch (Throwable ignored) {}
            }
        }
        java.util.Map<String, Object> out = new java.util.HashMap<String, Object>();
        out.put("booleans", booleans);
        out.put("strings", strings);
        out.put("gen", gen);
        return out;
    }

    /** 默认值表：与 SettingsActivity 保持一致 */
    public static boolean defaultValueOf(String key) {
        switch (key) {
            case KEY_MASTER:
            case KEY_SPLASH_AD:
            case KEY_OP_DIALOG:
            case KEY_OP_FLOAT:
            case KEY_WIDGET_TIP:
            case KEY_PERS_AD:
            case KEY_TOOLPAGE_ENTRY:
            case KEY_PUSH:
            case KEY_WAKE:
            case KEY_MINE_FEED:
            case KEY_SEARCH_FEED:
            case KEY_UPDATE_FEED:
            case KEY_DETAIL_REC:
            case KEY_SEARCH_AD_BOOTH:
            case KEY_SEARCH_MUST:
                return true;
            default:
                return false;
        }
    }

    /** Hook 侧加载配置（v1.4.6 无 root 主链路）：
     *  ① 模块权威 SP 直读（市场侧 XSP 已解析到 apexdata 重定向后真实路径，设置页
     *     每次保存同步写 SP + chmod 644）——最实时，且不依赖"市场被正确启动"，
     *     不受 OEM smart-launch/snapshot 拦截 onCreate 的影响；
     *  ② conf 文件（host-conf 等，兄弟项目旧链路，保留为 stock 设备/SP 不可读时兜底）；
     *  ③ XSharedPreferences 兜底。
     */
    public static TamerConfig loadForHook(de.robv.android.xposed.XSharedPreferences xsp) {
        java.util.Map<String, Boolean> sp = readModuleSp(xsp);
        if (sp != null) {
            return new TamerConfig(new MapBackedPrefs(sp));
        }
        java.util.Map<String, Boolean> m = readConfFile(xsp);
        if (m != null) {
            return new TamerConfig(new MapBackedPrefs(m));
        }
        // XSP 被移除（xsp==null）且无 conf 文件：返回空表（全部默认值），避免持有 null 的
        // XspBackedPrefs 在每次 get() 上 NPE 崩钩子（XspBackedPrefs.getBoolean 的 xsp 调用
        // 不在 ensure() 的 try 内）。
        if (xsp == null) {
            return new TamerConfig(new MapBackedPrefs(new java.util.HashMap<String, Boolean>()));
        }
        return new TamerConfig(new XspBackedPrefs(xsp));
    }

    /**
     * 直读设置页权威 SP（无 root 主链路，v1.4.6）。市场进程内的 XSharedPreferences
     * 已解析到 apexdata 重定向后的真实路径（实机实测 xsp.getFile()=
     * /data/misc/apexdata/{uuid}/prefs/com.tamer.honormarket/tamer_config.xml、
     * canRead=true）；设置页每次拨开关同步写该 SP 并 chmod 644，故本方法读到的是
     * "此刻"的最新配置。解析出布尔键即用之（含 conf_gen 但此处不依赖代次——SP 是
     * 设置页唯一权威写入方，单调由写入侧保证）。不可读/解析为空则返回 null，交回
     * readConfFile 兜底（stock 设备 SP 在常规路径、同样经 getFilePath chmod 可读）。
     */
    private static java.util.Map<String, Boolean> readModuleSp(
            de.robv.android.xposed.XSharedPreferences xsp) {
        if (xsp == null) return null; // XSP 被移除（LSPosed 2.3.0+）：直读主链路不可用，静默交回 conf 兜底
        try {
            java.io.File xf = xsp.getFile();
            if (xf == null || !xf.isFile() || !xf.canRead()) return null;
            java.io.InputStream in = new java.io.FileInputStream(xf);
            java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int n;
            while ((n = in.read(chunk)) > 0) buf.write(chunk, 0, n);
            in.close();
            String xml = new String(buf.toByteArray(), "UTF-8");
            java.util.Map<String, Object> parsed = parseSpXml(xml, PREFS_NAME);
            @SuppressWarnings("unchecked")
            java.util.Map<String, Boolean> booleans =
                    (java.util.Map<String, Boolean>) parsed.get("booleans");
            if (booleans == null || booleans.isEmpty()) return null;
            LAST_CONF_SRC = "moduleSp(" + xf.getAbsolutePath() + ")";
            de.robv.android.xposed.XposedBridge.log("[HonorMarketTamer] confSrc="
                    + xf.getAbsolutePath() + " keys=" + booleans.size());
            return booleans;
        } catch (Throwable t) {
            de.robv.android.xposed.XposedBridge.log("[HonorMarketTamer] readModuleSp failed: " + t);
            return null;
        }
    }

    /**
     * 候选顺序（v1.4.2 起调整，LineTamer v1.6.1 无 root 主链路）：
     * ① 宿主 host-conf（市场自己 files/ 下，钩子从启动 extras 重建、每次启动最新）
     * ② 模块自身 files/conf（设置页随开关实时重写）
     * ③ apexdata prefs 同级 conf
     * ④ /data/local/tmp/conf —— 纯开发期兜底（root 只在开发时用），运行时不再写它，
     *    停写后永不刷新，必须垫底。
     * 首个可读且非空者胜出；host-conf 的 #gen 行由写入侧保证单调，读侧无需比代次。
     */
    private static java.util.Map<String, Boolean> readConfFile(
            de.robv.android.xposed.XSharedPreferences xsp) {
        java.util.List<String> candidates = new java.util.ArrayList<String>();
        candidates.add(HOST_CONF_DIR + "/" + HOST_CONF_NAME);
        candidates.add("/data/user/0/" + MODULE_PKG + "/files/" + CONF_NAME);
        try {
            java.io.File xf = xsp.getFile();
            if (xf != null && xf.getParentFile() != null) {
                candidates.add(new java.io.File(xf.getParentFile(), CONF_NAME).getAbsolutePath());
            }
        } catch (Throwable ignored) {}
        candidates.add("/data/local/tmp/" + CONF_NAME);
        for (String p : candidates) {
            try {
                java.io.File f = new java.io.File(p);
                if (!f.isFile() || !f.canRead()) continue;
                java.util.Map<String, Boolean> m = new java.util.HashMap<String, Boolean>();
                java.io.BufferedReader br = new java.io.BufferedReader(
                        new java.io.InputStreamReader(new java.io.FileInputStream(f), "UTF-8"));
                String line;
                while ((line = br.readLine()) != null) {
                    int i = line.indexOf('=');
                    if (i <= 0) continue;
                    // 严格布尔：非 true/false 的值整键跳过、绝不落默认 false——
                    // 一次传输损坏的副本曾把 master 解析成 false 导致模块整体自灭。
                    String raw = line.substring(i + 1).trim();
                    if (!"true".equals(raw) && !"false".equals(raw)) continue;
                    m.put(line.substring(0, i).trim(),
                          "true".equals(raw));
                }
                br.close();
                if (!m.isEmpty()) {
                    LAST_CONF_SRC = "file(" + p + ")";
                    de.robv.android.xposed.XposedBridge.log("[HonorMarketTamer] confSrc="
                            + p + " keys=" + m.size());
                    return m;
                }
            } catch (Throwable ignored) {}
        }
        return null;
    }

    /** 内存映射版 SharedPreferences 适配器 */
    private static final class MapBackedPrefs implements android.content.SharedPreferences {
        private final java.util.Map<String, Boolean> map;
        MapBackedPrefs(java.util.Map<String, Boolean> map) { this.map = map; }
        @Override public boolean getBoolean(String key, boolean defValue) {
            Boolean v = map.get(key);
            return v == null ? defValue : v.booleanValue();
        }
        @Override public int getInt(String key, int defValue) { return defValue; }
        @Override public String getString(String key, String defValue) { return defValue; }
        @Override public android.content.SharedPreferences.Editor edit() { throw new UnsupportedOperationException(); }
        @Override public java.util.Map<String, ?> getAll() { throw new UnsupportedOperationException(); }
        @Override public long getLong(String key, long defValue) { throw new UnsupportedOperationException(); }
        @Override public float getFloat(String key, float defValue) { throw new UnsupportedOperationException(); }
        @Override public java.util.Set<String> getStringSet(String key, java.util.Set<String> defValues) { throw new UnsupportedOperationException(); }
        @Override public boolean contains(String key) { return map.containsKey(key); }
        @Override public java.io.File getFilePath() { return null; }
        @Override public void registerOnSharedPreferenceChangeListener(android.content.SharedPreferences.OnSharedPreferenceChangeListener listener) {}
        @Override public void unregisterOnSharedPreferenceChangeListener(android.content.SharedPreferences.OnSharedPreferenceChangeListener listener) {}
    }

    /** 把 XSharedPreferences 适配成 SharedPreferences 接口 */
    private static final class XspBackedPrefs implements android.content.SharedPreferences {
        private final de.robv.android.xposed.XSharedPreferences xsp;
        XspBackedPrefs(de.robv.android.xposed.XSharedPreferences xsp) { this.xsp = xsp; }
        private void ensure() { try { xsp.reload(); } catch (Throwable ignored) {} }
        @Override public boolean getBoolean(String key, boolean defValue) {
            ensure(); return xsp.getBoolean(key, defValue);
        }
        @Override public int getInt(String key, int defValue) { ensure(); return xsp.getInt(key, defValue); }
        @Override public String getString(String key, String defValue) { ensure(); return xsp.getString(key, defValue); }
        @Override public android.content.SharedPreferences.Editor edit() { throw new UnsupportedOperationException(); }
        @Override public java.util.Map<String, ?> getAll() { throw new UnsupportedOperationException(); }
        @Override public long getLong(String key, long defValue) { throw new UnsupportedOperationException(); }
        @Override public float getFloat(String key, float defValue) { throw new UnsupportedOperationException(); }
        @Override public java.util.Set<String> getStringSet(String key, java.util.Set<String> defValues) { throw new UnsupportedOperationException(); }
        @Override public boolean contains(String key) { throw new UnsupportedOperationException(); }
        @Override public java.io.File getFilePath() { return null; }
        @Override public void registerOnSharedPreferenceChangeListener(android.content.SharedPreferences.OnSharedPreferenceChangeListener listener) {}
        @Override public void unregisterOnSharedPreferenceChangeListener(android.content.SharedPreferences.OnSharedPreferenceChangeListener listener) {}
    }

    private TamerConfig() { this.sp = null; }
}
