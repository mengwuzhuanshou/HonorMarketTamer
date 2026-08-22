package com.tamer.honormarket.ui;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.tamer.honormarket.TamerConfig;

/**
 * 模块设置界面（纯代码 UI，无资源依赖）。
 * 开关写入本应用私有 SharedPreferences(tamer_config.xml)，
 * 并尽力 chmod 为全局可读，供 Hook 侧 XSharedPreferences 读取。
 */
public class SettingsActivity extends Activity {

    private LinearLayout root;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        float den = getResources().getDisplayMetrics().density;
        final int dp = Math.max(1, Math.round(den));

        ScrollView scroll = new ScrollView(this);
        scroll.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        scroll.setBackgroundColor(Color.parseColor("#FAFAFA"));

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp * 20, dp * 24, dp * 20, dp * 40);
        scroll.addView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);

        // 标题
        TextView title = new TextView(this);
        title.setText("荣耀市场净化");
        title.setTextColor(Color.parseColor("#111111"));
        title.setTextSize(22 * den / den); // 22sp
        title.setTextSize(22);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);
        addSpace(dp * 4);

        TextView sub = new TextView(this);
        sub.setText("目标应用：com.hihonor.appmarket（荣耀应用市场）\n"
                + "“搜索应用”与“应用更新”始终保留，不受开关影响。\n"
                + "修改任意开关后，请在 LSPosed 中强制停止应用市场后重新打开。");
        sub.setTextColor(Color.parseColor("#666666"));
        sub.setTextSize(13);
        root.addView(sub);
        addSpace(dp * 16);

        // ===== 广告与运营（默认屏蔽）=====
        section("广告与运营", "Ads & Promotions");
        addSwitch(TamerConfig.KEY_SPLASH_AD,  "屏蔽开屏广告", "Block splash ads", "启动时不再展示开屏商业广告");
        addSwitch(TamerConfig.KEY_OP_DIALOG,  "屏蔽运营活动弹窗", "Block promo dialogs", "首页弹出的运营/活动推广 Dialog");
        addSwitch(TamerConfig.KEY_OP_FLOAT,   "屏蔽运营悬浮窗", "Block floating promo windows", "页面角落的悬浮推广小窗");
        addSwitch(TamerConfig.KEY_WIDGET_TIP, "屏蔽桌面卡片推广弹窗", "Block widget-promo dialogs", "“添加桌面小组件/卡片”推荐弹窗");
        addSwitch(TamerConfig.KEY_PERS_AD,    "屏蔽个性化推荐广告弹窗", "Block personalized ad dialogs", "个性化广告说明与推荐弹窗");

        // ===== 推送与唤醒（默认屏蔽）=====
        section("推送与唤醒", "Push & Wake-up");
        addSwitch(TamerConfig.KEY_PUSH,       "屏蔽其它推送通知", "Block marketing push", "活动/运营/营销类推送；应用更新类自动放行");
        addSwitch(TamerConfig.KEY_WAKE,       "屏蔽自启与广播唤醒", "Block auto-start & wake-ups", "电源/Wi-Fi/装包广播等唤醒入口全部静默");
        addSwitch(TamerConfig.KEY_SILENT_UPD, "屏蔽静默更新检查", "Block silent update checks", "默认关闭（保留静默更新能力）；开启后仅手动检查更新");
        addSwitch(TamerConfig.KEY_UPDATE_NOTIFY, "屏蔽应用更新提醒", "Block update reminders", "默认关闭（保留更新提醒）；开启后不再提醒可更新应用");

        // ===== 底部 Tab 屏蔽（默认保留）=====
        section("底部 Tab 屏蔽（默认保留，按需打开）", "Bottom tabs (kept by default)");
        addSwitch(TamerConfig.KEY_GAME_TAB,   "屏蔽游戏 Tab", "Hide Games tab", "移除底栏“游戏”频道及游戏页面");
        addSwitch(TamerConfig.KEY_APPS_TAB,   "屏蔽应用 Tab", "Hide Apps tab", "移除底栏“应用”频道");
        addSwitch(TamerConfig.KEY_QIANGXIAN_TAB, "屏蔽抢鲜 Tab", "Hide Early-Access tab", "移除底栏“抢鲜”频道");

        // ===== 「我的」页板块屏蔽（默认保留）=====
        section("「我的」页板块屏蔽（默认保留，按需打开）", "\"Mine\" page cards (kept by default)");
        addSwitch(TamerConfig.KEY_MINE_SIGNIN,   "屏蔽签到领奖", "Hide check-in card", "隐藏“我的”页签到领奖入口卡片");
        addSwitch(TamerConfig.KEY_MINE_SAFETY,   "屏蔽安全检测", "Hide security-scan card", "隐藏“我的”页安全检测板块");
        addSwitch(TamerConfig.KEY_MINE_CLEAN,    "屏蔽清理加速", "Hide cleaner card", "隐藏“我的”页清理加速板块");
        addSwitch(TamerConfig.KEY_MINE_SERVICES, "屏蔽常用服务", "Hide common-services card", "隐藏“我的”页常用服务板块");
        addSwitch(TamerConfig.KEY_MINE_SLIDE,    "屏蔽滑一滑·发现更多精彩", "Hide swipe banner", "隐藏“我的”页滑动推广横幅");

        // ===== 首页搜索框 =====
        section("首页搜索框", "Home search box");
        addSwitch(TamerConfig.KEY_HOME_ROLLWORD, "屏蔽搜索框滚动横幅", "Block rolling search banner", "移除首页顶部搜索框内滚动的热词推广");

        // ===== 推荐流屏蔽（默认屏蔽）=====
        section("推荐流屏蔽（默认开启，可关闭恢复）", "Feed blocking (on by default, reversible)");
        addSwitch(TamerConfig.KEY_MINE_FEED,   "屏蔽「我的」页推荐流", "Hide Mine-page app feed", "移除“我的”页面底部整条应用推荐流");
        addSwitch(TamerConfig.KEY_SEARCH_FEED, "屏蔽搜索发现页推荐", "Block search-page recommendations", "点搜索框进入后的推荐不再展示，请求一并拦截，输入框无需等待");
        addSwitch(TamerConfig.KEY_UPDATE_FEED, "屏蔽更新页“新应用，新用途”", "Hide \"New apps, new uses\" on Updates page", "应用更新页面内嵌的新应用推荐不再展示");
        addSwitch(TamerConfig.KEY_DETAIL_REC,  "屏蔽应用详情页软件推荐", "Hide detail-page recommendations", "移除详情页“应用名＋用户必备的软件推荐”板块");

        addSpace(dp * 20);
        TextView foot = new TextView(this);
        foot.setText("⚠ 本模块由 AI 生成，请自行评估风险。/ This module is AI-generated; use at your own discretion.\n\n"
                + "配置文件：/data/data/" + TamerConfig.MODULE_PKG
                + "/shared_prefs/" + TamerConfig.PREFS_NAME + ".xml\n"
                + "若开关不生效：1) LSPosed 中启用本模块并勾选作用域“荣耀应用市场”；"
                + "2) 重启应用市场。\n"
                + "If switches don't take effect: enable the module in LSPosed, "
                + "select the \"Honor Market\" scope, then force-stop and reopen the market.");
        foot.setTextColor(Color.parseColor("#999999"));
        foot.setTextSize(12);
        root.addView(foot);

        // 初始同步一次自管配置文件
        syncConfigFile();
    }

    private void section(String text, String textEn) {
        float den = getResources().getDisplayMetrics().density;
        int dp = Math.max(1, Math.round(den));
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(Color.parseColor("#1E88E5"));
        tv.setTextSize(15);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setPadding(0, dp * 8, 0, 0);
        root.addView(tv);
        TextView tve = new TextView(this);
        tve.setText(textEn);
        tve.setTextColor(Color.parseColor("#7FA6D9"));
        tve.setTextSize(11);
        tve.setPadding(0, 0, 0, dp * 8);
        root.addView(tve);
    }

    private void addSwitch(final String key, String title, String titleEn, String desc) {
        float den = getResources().getDisplayMetrics().density;
        final int dp = Math.max(1, Math.round(den));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp * 10, 0, dp * 10);

        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);

        TextView t1 = new TextView(this);
        t1.setText(title);
        t1.setTextColor(Color.parseColor("#222222"));
        t1.setTextSize(16);
        textCol.addView(t1);

        TextView ten = new TextView(this);
        ten.setText(titleEn);
        ten.setTextColor(Color.parseColor("#AAAAAA"));
        ten.setTextSize(11);
        textCol.addView(ten);

        TextView t2 = new TextView(this);
        t2.setText(desc);
        t2.setTextColor(Color.parseColor("#888888"));
        t2.setTextSize(12);
        textCol.addView(t2);

        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        row.addView(textCol, textLp);

        Switch sw = new Switch(this);
        boolean def = TamerConfig.defaultValueOf(key);
        sw.setChecked(getSp().getBoolean(key, def));
        sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                getSp().edit().putBoolean(key, isChecked).apply();
                makeWorldReadable();
                syncConfigFile();
                Toast.makeText(SettingsActivity.this,
                        "已保存，重启应用市场后生效", Toast.LENGTH_SHORT).show();
            }
        });
        row.addView(sw, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(row, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void addSpace(int px) {
        View v = new View(this);
        root.addView(v, new ViewGroup.LayoutParams(1, px));
    }

    private android.content.SharedPreferences getSp() {
        return getSharedPreferences(TamerConfig.PREFS_NAME, MODE_PRIVATE);
    }

    /** 写自管配置文件（POSIX 644）：
     *  副本1 = 应用自身 files 目录；副本2 = LSPosed apexdata prefs 同级目录
     * （与 XSharedPreferences 的 xml 同目录，目标进程确定可读） */
    private void syncConfigFile() {
        StringBuilder sb = new StringBuilder();
        for (String key : TamerConfig.ALL_KEYS) {
            sb.append(key).append('=')
              .append(getSp().getBoolean(key, TamerConfig.defaultValueOf(key)))
              .append('\n');
        }
        byte[] data;
        try {
            data = sb.toString().getBytes("UTF-8");
        } catch (Throwable e) { return; }

        // 副本1：自身 files 目录
        try {
            writeConf(new java.io.File(getFilesDir(), TamerConfig.CONF_NAME), data);
        } catch (Throwable ignored) {}

        // 副本2：apexdata prefs 同级
        try {
            de.robv.android.xposed.XSharedPreferences xsp = new de.robv.android.xposed.XSharedPreferences(
                    TamerConfig.MODULE_PKG, TamerConfig.PREFS_NAME);
            java.io.File xf = xsp.getFile();
            if (xf != null && xf.getParentFile() != null) {
                writeConf(new java.io.File(xf.getParentFile(), TamerConfig.CONF_NAME), data);
            }
        } catch (Throwable ignored) {}

        // 副本3（关键）：root 写入全局固定路径，目标进程必然可读
        try {
            java.io.File tmp = new java.io.File(getFilesDir(), "tamer_global.tmp");
            writeConf(tmp, data);
            ProcessBuilder pb = new ProcessBuilder("su", "-c",
                    "cat '" + tmp.getAbsolutePath() + "' > /data/local/tmp/"
                            + TamerConfig.CONF_NAME
                            + " && chmod 644 /data/local/tmp/" + TamerConfig.CONF_NAME);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            int rc = p.waitFor();
            android.util.Log.i("HonorMarketTamer", "global conf sync rc=" + rc);
            tmp.delete();
        } catch (Throwable t) {
            android.util.Log.e("HonorMarketTamer", "global conf sync ex", t);
        }
    }

    private static void writeConf(java.io.File f, byte[] data) {
        try {
            java.io.FileOutputStream fos = new java.io.FileOutputStream(f);
            fos.write(data);
            fos.getFD().sync();
            fos.close();
            android.system.Os.chmod(f.getAbsolutePath(), 0644);
        } catch (Throwable ignored) {
        }
    }

    /** 把 prefs 文件设为全局可读（自身权限直接 chmod；未重定向时有效）。
     *  注：曾有的 su 兜底分支经实测无效已删除（v1.3.2），配置同步走
     *  syncConfigFile 的三副本策略，不依赖本方法。 */
    private void makeWorldReadable() {
        try {
            android.content.pm.ApplicationInfo ai = getApplicationInfo();
            if (ai != null && ai.dataDir != null) {
                java.io.File prefsDir = new java.io.File(ai.dataDir, "shared_prefs");
                java.io.File f = new java.io.File(prefsDir, TamerConfig.PREFS_NAME + ".xml");
                if (f.exists()) {
                    android.system.Os.chmod(ai.dataDir, 0751);
                    android.system.Os.chmod(prefsDir.getAbsolutePath(), 0755);
                    android.system.Os.chmod(f.getAbsolutePath(), 0644);
                }
            }
        } catch (Throwable ignored) {
        }
    }
}