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
    /** 自管配置文件（POSIX 644，Hook 端兜底读取） */
    public static final String CONF_NAME = "tamer_config.conf";

    // ===== 总开关 =====
    public static final String KEY_MASTER = "master_enabled"; // 模块总开关

    // ===== 广告与运营（默认屏蔽）=====
    public static final String KEY_SPLASH_AD   = "block_splash_ad";        // 开屏广告
    public static final String KEY_OP_DIALOG   = "block_operation_dialog"; // 运营活动弹窗
    public static final String KEY_OP_FLOAT    = "block_operation_float";  // 运营悬浮窗
    public static final String KEY_WIDGET_TIP  = "block_widget_promote";   // 桌面卡片推广弹窗
    public static final String KEY_PERS_AD     = "block_personalized_ads"; // 个性化广告弹窗

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

    // ===== “我的”页内板块（默认不屏蔽）=====
    public static final String KEY_MINE_SLIDE    = "mine_block_slide";    // 滑一滑发现更多精彩
    public static final String KEY_MINE_SAFETY   = "mine_block_safety";   // 安全检测板块
    public static final String KEY_MINE_CLEAN    = "mine_block_clean";    // 清理加速板块
    public static final String KEY_MINE_SERVICES = "mine_block_services"; // 常用服务板块
    public static final String KEY_MINE_SIGNIN   = "mine_block_signin";   // 签到领奖入口

    /** 全部开关键（设置页序列化用）——Tab 类仅保留 应用/抢鲜/游戏 */
    public static final String[] ALL_KEYS = {
        KEY_MASTER, KEY_SPLASH_AD, KEY_OP_DIALOG, KEY_OP_FLOAT, KEY_WIDGET_TIP,
        KEY_PERS_AD, KEY_PUSH, KEY_WAKE, KEY_SILENT_UPD, KEY_UPDATE_NOTIFY,
        KEY_ALL_NOTIFY, KEY_GAME_TAB, KEY_QIANGXIAN_TAB, KEY_APPS_TAB,
        KEY_HOME_ROLLWORD,
        KEY_MINE_SIGNIN, KEY_MINE_SAFETY, KEY_MINE_CLEAN, KEY_MINE_SERVICES,
        KEY_MINE_SLIDE, KEY_MINE_FEED, KEY_SEARCH_FEED, KEY_UPDATE_FEED,
        KEY_DETAIL_REC,
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

    public TamerConfig(android.content.SharedPreferences sp) { this.sp = sp; }

    public boolean get(String key, boolean def) {
        try { return sp.getBoolean(key, def); } catch (Throwable t) { return def; }
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
            case KEY_PUSH:
            case KEY_WAKE:
            case KEY_MINE_FEED:
            case KEY_SEARCH_FEED:
            case KEY_UPDATE_FEED:
            case KEY_DETAIL_REC:
                return true;
            default:
                return false;
        }
    }

    /** Hook 侧加载配置：conf 文件为最高优先级（设置页每次切换同步写入、无延迟），
     *  其次 XSharedPreferences（lspd 异步同步可能滞后） */
    public static TamerConfig loadForHook(de.robv.android.xposed.XSharedPreferences xsp) {
        java.util.Map<String, Boolean> m = readConfFile(xsp);
        if (m != null) {
            return new TamerConfig(new MapBackedPrefs(m));
        }
        return new TamerConfig(new XspBackedPrefs(xsp));
    }

    /**
     * 候选顺序（v1.3.3 起调整）：自身目录两份由设置页随开关实时重写、永远最新，
     * 放前面；/data/local/tmp 只有拿过 root 才会被写入且之后不再刷新，
     * 排最前会让「撤权后继续拨开关」的设备读到陈旧配置，故降级为末位兜底。
     */
    private static java.util.Map<String, Boolean> readConfFile(
            de.robv.android.xposed.XSharedPreferences xsp) {
        java.util.List<String> candidates = new java.util.ArrayList<String>();
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
                if (!m.isEmpty()) return m;
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
        @Override public void registerOnSharedPreferenceChangeListener(android.content.SharedPreferences.OnSharedPreferenceChangeListener listener) {}
        @Override public void unregisterOnSharedPreferenceChangeListener(android.content.SharedPreferences.OnSharedPreferenceChangeListener listener) {}
    }

    private TamerConfig() { this.sp = null; }
}
