package com.tamer.honormarket.ui;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
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

        // 无 root 配置通道（v1.4.4，LineTamer v1.6.1 模式）：对目标宿主显式
        // 授权读配置 Provider（部分 OEM 把三方 Provider 的 exported 强制视为
        // false，运行时 grantUriPermission 是唯一通路）。每次打开设置页都重授
        // 一次兜底；授权写入 urigrants.xml 可跨重启。
        com.tamer.honormarket.ConfigProvider.ensureGrant(getApplicationContext());

        // 无 root 主链路（v1.4.6）：打开设置页即把权威 SP chmod 644（apexdata
        // 真实路径，getFilePath 解析），确保市场冷启/ onResume 直读可读——覆盖
        // 新装机 SP 仍为默认 660 的场景。toggle 时也会再 chmod 一次（见 handler）。
        makeWorldReadable();

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
                + "附加目标：com.hihonor.health（运动健康，仅传感器闸门）\n"
                + "“搜索应用”与“应用更新”始终保留，不受开关影响。\n"
                + "拨动开关即后台同步：市场在后台完成配置投递，本页面保持前台、不打断你。\n"
                + "（健康传感器闸门需手动强制停止运动健康）");
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
        addSwitch(TamerConfig.KEY_TOOLPAGE_ENTRY, "屏蔽工具页活动入口", "Block tool-page activity entries", "应用更新/安装管理等页标题栏的运营活动按钮（如“耀耀农场”树形图标）不再显示");

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
        addSwitch(TamerConfig.KEY_SEARCH_AD_BOOTH, "屏蔽搜索结果广告展位", "Block search-result ad booth", "移除搜索结果第一位的广告大卡（带“广告”标签）");
        addSwitch(TamerConfig.KEY_SEARCH_MUST, "屏蔽搜索页装机必备", "Hide search must-install strip", "移除搜索结果底部的“装机必备”横滑推荐");

        // ===== 健康类应用（默认关；在健康进程内生效）=====
        section("健康应用传感器闸门（默认关）", "Health sensor gate (off by default)");
        addSwitch(TamerConfig.KEY_HEALTH_GATE, "熄屏停采运动健康传感器", "Stop Health sensors at screen-off",
                "熄屏时注销/拒绝运动健康的加速度计与计步器会话，亮屏按原参数重放；"
                        + "桌面步数卡片数据不丢（传感器中枢硬件继续累计），熄屏锻炼会停采。"
                        + "作用对象：com.hihonor.health；改动后需强制停止运动健康");

        // ===== 手动同步（与拨开关同链路，显式"立即再投一次"）=====
        // 主链路 = 后台组件启动：拨开关已自动带 conf+gen 后台拉起市场、钩子写
        // host-conf、REORDER_TO_FRONT 留本设置页在前台。本按钮 = 同链路再触发一次，
        // 供"刚拨完想立刻确认市场已吃到新配置"时手动点。
        addSpace(dp * 12);
        Button syncNow = new Button(this);
        syncNow.setText("立即后台同步配置到市场");
        syncNow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                launchTargetWithConfig(syncConfigFile());
            }
        });
        root.addView(syncNow, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView syncHint = new TextView(this);
        syncHint.setText("拨开关即自动后台同步；本按钮用于手动再投一次。");
        syncHint.setTextColor(Color.parseColor("#999999"));
        syncHint.setTextSize(11);
        syncHint.setPadding(0, dp * 4, 0, 0);
        root.addView(syncHint, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        addSpace(dp * 20);
        TextView foot = new TextView(this);
        foot.setText("⚠ 本模块由 AI 生成，请自行评估风险。/ This module is AI-generated; use at your own discretion.\n\n"
                + "配置文件：/data/data/" + TamerConfig.MODULE_PKG
                + "/shared_prefs/" + TamerConfig.PREFS_NAME + ".xml\n"
                + "若开关不生效：1) LSPosed 中启用本模块并勾选作用域“荣耀应用市场”"
                + "（健康应用传感器闸门需同时勾选“运动健康”作用域）；"
                + "2) 强制停止对应应用后重新打开。\n"
                + "If switches don't take effect: enable the module in LSPosed, "
                + "select the \"Honor Market\" scope (and the \"Honor Health\" scope "
                + "for the sensor gate), then force-stop and reopen the app.");
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
                // commit()（同步）而非 apply()（异步）：确保 SP 落盘【先于】
                // makeWorldReadable 的 chmod——apply 异步刷盘会在 chmod 之后重置
                // 文件权限为 660，市场（异 uid）就读不到。conf_gen 也并入同一次
                // 同步 commit（launchTargetWithConfig 不再异步写 SP，避免再重置权限）。
                long gen = System.currentTimeMillis();
                getSp().edit()
                        .putBoolean(key, isChecked)
                        .putLong(TamerConfig.KEY_CONF_GEN, gen)
                        .commit();
                makeWorldReadable();
                // 无 root 主链路（v1.4.6）：SP 已 chmod 644，市场冷启 loadForHook
                // 直读该 SP 即拿最新配置；warm 时 onResume 钩子 refreshFromModuleSp
                // 重读覆盖。下方 launch 仅用于"把市场带到前台给用户看"，配置投递
                // 已不依赖它的 extras（OEM smart-launch/snapshot 会跳过 Splash.onCreate
                // 且不转发 extras）。REORDER_TO_FRONT 把本设置任务提回前台——拨开关
                // 时用户留在设置页、市场不抢前台。Provider 通道保留为 stock 冗余。
                launchTargetWithConfig(syncConfigFile());
                Toast.makeText(SettingsActivity.this,
                        "已保存，市场后台同步中", Toast.LENGTH_SHORT).show();
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

    /** 每次保存盖新代次（conf_gen = 当前毫秒，单调递增）。市场进程经 Provider
     *  拉到 SP 后比对 gen：新于本进程已用配置才覆盖——这是"不拉起也能让新配置
     *  生效"的判定依据。不盖代次则市场永远认为配置没变、跳过覆盖。 */
    private void stampGen() {
        try {
            getSp().edit().putLong(TamerConfig.KEY_CONF_GEN,
                    System.currentTimeMillis()).apply();
        } catch (Throwable ignored) {}
    }

    /** 写自管配置文件（POSIX 644）并返回 conf 文本（供启动 Intent 携带）：
     *  副本1 = 应用自身 files 目录；副本2 = LSPosed apexdata prefs 同级目录
     * （与 XSharedPreferences 的 xml 同目录）；
     *  副本3 = /data/local/tmp —— 纯开发期兜底（root 只作开发兜底，运行时链路
     *  不依赖它；模块进程在 APEX mount namespace 内看不到 su，此写失败无害）。
     *  无 root 主链路（LineTamer v1.6.1）：返回的 conf 文本随启动 Intent 交给
     *  ConfigRelay 写入市场自己的 files/hmt_host.conf，见 launchTargetWithConfig。 */
    private String syncConfigFile() {
        StringBuilder sb = new StringBuilder();
        for (String key : TamerConfig.ALL_KEYS) {
            sb.append(key).append('=')
              .append(getSp().getBoolean(key, TamerConfig.defaultValueOf(key)))
              .append('\n');
        }
        byte[] data;
        try {
            data = sb.toString().getBytes("UTF-8");
        } catch (Throwable e) { return null; }

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

        // 副本3（开发兜底）：root 写入全局固定路径。运行时主链路不依赖此副本
        // （模块进程在 APEX mount namespace 内无 su，写失败无害）。
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
            android.util.Log.w("HonorMarketTamer", "global conf sync skipped (dev-only): " + t);
        }
        return new String(data, java.nio.charset.Charset.forName("UTF-8"));
    }

    /** 无 root 主链路（照抄 LineTamer v1.6.1 / BiliTamer 通用引擎）：带配置+代次的
     *  启动 Intent 拉起市场。显式 ComponentName 到 launcher（Splash），附
     *  NEW_TASK|CLEAR_TOP|SINGLE_TOP，携 hmt_conf/hmt_gen（gen 走 long extra）。
     *  ConfigRelay 截获 Splash.onCreate（冷/热均落此，见其 javadoc），规范化重建后
     *  写市场自己 files/ 下的 host-conf——宿主进程必然可读，OEM 封 Provider/
     *  uri grant/media 都封不住组件启动。每次保存盖新代次（SP conf_gen）。 */
    private void launchTargetWithConfig(String confText) {
        if (confText == null) return;
        try {
            // gen 已由 toggle handler 同步 commit 进 SP conf_gen；此处只读取、
            // 不再异步写 SP（异步 apply 会在 makeWorldReadable 的 chmod 之后
            // 重置 SP 文件权限为 660，市场异 uid 读不到）。
            long gen = TamerConfig.confGen(getSp());
            android.content.Intent li = null;
            // 显式 ComponentName 启动：不经 PM 查询，绕开 Android 11+ 包可见性
            // （getLaunchIntentForPackage 对不可见包返回 null——模块未声明 <queries>）
            try {
                li = new android.content.Intent(android.content.Intent.ACTION_MAIN);
                li.addCategory(android.content.Intent.CATEGORY_LAUNCHER);
                li.setComponent(new android.content.ComponentName(
                        TamerConfig.TARGET_PKG, TamerConfig.LAUNCHER_CLASS));
            } catch (Throwable ignored) {}
            if (li == null) {
                li = getPackageManager().getLaunchIntentForPackage(TamerConfig.TARGET_PKG);
            }
            if (li == null) {
                android.util.Log.w("HonorMarketTamer", "no launch intent for market");
                return;
            }
            // NEW_TASK|SINGLE_TOP（不带 CLEAR_TOP）：实机实测——带 CLEAR_TOP
            // （0x16000000）时 warm 市场新建的 Splash 实例卡在 INITIALIZING、
            // onCreate 不完成、extras 投不进；去掉 CLEAR_TOP（0x12000000）后
            // Splash.onCreate 正常触发、携 extras 投递成功。REORDER_TO_FRONT
            // 随后把本设置任务提回前台。
            li.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                    | android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP);
            li.putExtra(TamerConfig.EXTRA_CONF, confText);
            // gen 代次走 long（putExtra(String,long)→Intent，描述符已实证；引擎同款）
            li.putExtra(TamerConfig.EXTRA_GEN, gen);
            startActivity(li);
            android.util.Log.i("HonorMarketTamer", "conf launch gen=" + gen);
            // 抢回前台：市场 Splash 只需在其自身 task 内 onCreate 投递 conf（onCreate
            // 必在 onStart 前触发、与前台无关），不必占前台。立刻把设置任务
            // REORDER_TO_FRONT 提回前台——用户拨开关时留在设置页，市场只在后台
            // 完成投递。REORDER_TO_FRONT 不重建已存在的设置任务（开关状态不丢）。
            try {
                android.content.Intent self = new android.content.Intent();
                self.setComponent(new android.content.ComponentName(
                        TamerConfig.MODULE_PKG, "com.tamer.honormarket.ui.SettingsActivity"));
                self.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                        | android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(self);
            } catch (Throwable ignored) { }
        } catch (Throwable t) {
            android.util.Log.e("HonorMarketTamer", "conf launch ex", t);
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

    /** 把 SP 文件设为全局可读——市场进程（异 uid）靠直接读它拿到"此刻"的最新
     *  配置（无 root 主链路，v1.4.6）。取 SP 真实路径（apexdata 重定向后）三条路：
     *  ① interface getFilePath()（stock/较新框架有，OEM 无→NoSuchMethodError，被吞）；
     *  ② 反射 SharedPreferencesImpl.mFile（OEM 框架实测：私有 File 字段，指向真实
     *     apexdata 路径）；③ 常规 dataDir/shared_prefs（stock 设备兜底）。apexdata
     *  目录链默认 711（other 可穿行），只需叶子文件 chmod 644。注：曾有的 su 兜底
     *  分支经实测无效已删除（v1.3.2）。 */
    private void makeWorldReadable() {
        java.io.File real = resolveSpFile();
        if (real != null && real.isFile()) {
            try { android.system.Os.chmod(real.getAbsolutePath(), 0644); return; }
            catch (Throwable ignored) { }
        }
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

    /** 解析本模块 SP 文件的真实物理路径（apexdata 重定向后）。优先 interface
     *  getFilePath()；OEM 框架该接口无此方法（NoSuchMethodError）则反射读
     *  SharedPreferencesImpl 的私有 mFile 字段（实测指向 apexdata 真实路径）；
     *  都失败返回 null（由调用方走常规 dataDir 兜底）。 */
    private java.io.File resolveSpFile() {
        try {
            java.io.File f = getSp().getFilePath();
            if (f != null && f.isFile()) return f;
        } catch (Throwable ignored) { }
        try {
            java.lang.reflect.Field mf = getSp().getClass().getDeclaredField("mFile");
            mf.setAccessible(true);
            Object o = mf.get(getSp());
            if (o instanceof java.io.File) {
                java.io.File f = (java.io.File) o;
                if (f.isFile()) return f;
            }
        } catch (Throwable ignored) { }
        return null;
    }
}