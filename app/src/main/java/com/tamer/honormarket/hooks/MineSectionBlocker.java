package com.tamer.honormarket.hooks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodHook.MethodHookParam;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * 「我的」页板块屏蔽（MarketManageFragment / MarketManageTopAdapter）：
 *
 * 结构化部分（稳定锚点，全名类）：
 *  - MarketManageTopAdapter#onBindViewHolder 两个重载 + e0 兜底，
 *    viewType=2007(CoreTools 卡) 内隐藏 safety_check_view / clean_up_view 子块；
 *    viewType=2004(常用服务卡) 整卡隐藏。应用更新子块(app_update_card)永不动。
 *  - 独立安全检测数据(2002) 在新布局下由 NullViewHolder 渲染为 GONE，无需处理。
 *  - 资源 id 用 Resources.getIdentifier 运行时解析（LSPosed 环境反射 R$id 会失败），
 *    首次解析后缓存，含"不存在"负缓存。
 *
 * 兜底扫描部分（服务端动态卡片，文案不在 APK 里）：
 *  - onResume/onHiddenChanged/onViewCreated 后延迟扫描页面视图树，
 *    按 TextView 文案匹配「签到」「滑一滑/发现更多精彩」，
 *    隐藏其所在 RecyclerView 直接子项（内嵌网格则只隐藏格子）。
 */
public final class MineSectionBlocker {
    private static final String TAG = "[HonorMarketTamer] ";
    private static final String ADAPTER_CLS =
            "com.hihonor.appmarket.mine.adapter.MarketManageTopAdapter";
    private static final String FRAGMENT_CLS =
            "com.hihonor.appmarket.mine.MarketManageFragment";
    private static final String TARGET_PKG = "com.hihonor.appmarket";

    /** MarketManageTopAdapter 的 viewType（j45 数据类构造里写死） */
    private static final int VT_CORE_TOOLS = 2007;      // 安全检测+清理加速(+应用更新) 合体卡
    private static final int VT_COMMON_SERVICE = 2004;  // 常用服务卡
    private static final long[] SWEEP_DELAYS = { 600L, 1500L, 3000L, 5000L };

    /** 已解析的资源 id 缓存；值 null 表示解析过但不存在（负缓存） */
    private static final HashMap<String, Integer> ID_CACHE =
            new HashMap<String, Integer>();

    /** mine-hide 日志采样计数（RecyclerView 频繁重绑会导致每次 rebind 都 GONE 新视图） */
    private static final AtomicInteger sHideCount = new AtomicInteger();

    private static boolean sClean;
    private static boolean sSafety;
    private static boolean sServices;
    private static boolean sSignin;
    private static boolean sSlide;
    /** 跟随「我的」页推荐流开关：顺带隐藏加载占位文案 */
    private static boolean sMineLoading;

    private MineSectionBlocker() { }

    public static void hook(ClassLoader cl, com.tamer.honormarket.TamerConfig cfg) {
        sClean    = cfg.get(com.tamer.honormarket.TamerConfig.KEY_MINE_CLEAN, false);
        sSafety   = cfg.get(com.tamer.honormarket.TamerConfig.KEY_MINE_SAFETY, false);
        sServices = cfg.get(com.tamer.honormarket.TamerConfig.KEY_MINE_SERVICES, false);
        sSignin   = cfg.get(com.tamer.honormarket.TamerConfig.KEY_MINE_SIGNIN, false);
        sSlide    = cfg.get(com.tamer.honormarket.TamerConfig.KEY_MINE_SLIDE, false);
        sMineLoading = cfg.get(com.tamer.honormarket.TamerConfig.KEY_MINE_FEED, true);
        if (!sClean && !sSafety && !sServices && !sSignin && !sSlide && !sMineLoading) {
            XposedBridge.log(TAG + "MineSectionBlocker: all off, skip");
            return;
        }
        hookBind(cl);
        hookSweep(cl);
    }

    // ---------- 结构化：bind 时隐藏 ----------

    private static void hookBind(final ClassLoader cl) {
        if (!sClean && !sSafety && !sServices) {
            return;
        }
        try {
            Class<?> adapter = XposedHelpers.findClass(ADAPTER_CLS, cl);
            XC_MethodHook after = new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        handleBind(param.args);
                    } catch (Throwable ignored) { }
                }
            };
            // 两个 onBindViewHolder 重载都挂；e0 是 2 参重载的落点，一并挂上做双保险
            XposedBridge.hookAllMethods(adapter, "onBindViewHolder", after);
            XposedBridge.hookAllMethods(adapter, "e0", after);
            XposedBridge.log(TAG + "hooked MarketManageTopAdapter bind hooks");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "mine hookBind FAILED: " + t);
        }
    }

    private static void handleBind(Object[] args) {
        if (args == null || args.length < 1 || args[0] == null) {
            return;
        }
        Object itemObj = XposedHelpers.getObjectField(args[0], "itemView");
        if (!(itemObj instanceof android.view.View)) {
            return;
        }
        android.view.View root = (android.view.View) itemObj;
        Object vtObj = XposedHelpers.callMethod(args[0], "getItemViewType");
        if (!(vtObj instanceof Integer)) {
            return;
        }
        int vt = ((Integer) vtObj).intValue();
        if (vt == VT_CORE_TOOLS) {
            hideCoreToolsBlocks(root);
        } else if (vt == VT_COMMON_SERVICE && sServices) {
            hideOnce(root, "common-service-card");
        }
    }

    private static void hideCoreToolsBlocks(android.view.View root) {
        if (sClean) {
            hideById(root, "clean_up_view");
            hideById(root, "clean_up_icon");
            hideById(root, "clean_up_title");
            hideById(root, "clean_up_subtitle");
        }
        if (sSafety) {
            hideById(root, "safety_check_view");
            hideById(root, "safety_check_title");
            hideById(root, "safety_check_subtitle");
            hideById(root, "scan_finish_layout");
            hideById(root, "scan_finish_sub_layout");
            hideById(root, "process_view_v2");
            hideById(root, "warning_tips_imageview");
            hideById(root, "default_status");
        }
    }

    /** 运行时解析 id（带缓存与负缓存），找到且可见则 GONE */
    private static void hideById(android.view.View root, String name) {
        try {
            Integer id = idOf(root, name);
            if (id == null) {
                return;
            }
            android.view.View v = root.findViewById(id.intValue());
            if (v != null && v.getVisibility() != android.view.View.GONE) {
                v.setVisibility(android.view.View.GONE);
                int n = sHideCount.incrementAndGet();
                if (n <= 10 || n % 200 == 0) {
                    XposedBridge.log(TAG + "mine-hide " + name + " #" + n);
                }
            }
        } catch (Throwable ignored) { }
    }

    private static Integer idOf(android.view.View anyViewInApp, String name) {
        if (ID_CACHE.containsKey(name)) {
            return ID_CACHE.get(name);
        }
        try {
            int id = anyViewInApp.getResources().getIdentifier(name, "id", TARGET_PKG);
            Integer boxed = (id != 0) ? Integer.valueOf(id) : null;
            ID_CACHE.put(name, boxed);
            if (boxed == null) {
                XposedBridge.log(TAG + "mine id not found: " + name);
            }
            return boxed;
        } catch (Throwable t) {
            return null;
        }
    }

    private static void hideOnce(android.view.View v, String name) {
        if (v.getVisibility() != android.view.View.GONE) {
            v.setVisibility(android.view.View.GONE);
            int n = sHideCount.incrementAndGet();
            if (n <= 10 || n % 200 == 0) {
                XposedBridge.log(TAG + "mine-hide " + name + " #" + n);
            }
        }
    }

    // ---------- 兜底：文案扫描（服务端动态卡片）----------

    private static void hookSweep(final ClassLoader cl) {
        if (!sSignin && !sSlide && !sMineLoading) {
            return;
        }
        try {
            Class<?> frag = XposedHelpers.findClass(FRAGMENT_CLS, cl);
            XC_MethodHook after = new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        scheduleSweeps(param.thisObject);
                    } catch (Throwable ignored) { }
                }
            };
            XposedBridge.hookAllMethods(frag, "onResume", after);
            XposedBridge.hookAllMethods(frag, "onHiddenChanged", after);
            XposedBridge.hookAllMethods(frag, "onViewCreated", after);
            XposedBridge.log(TAG + "mine-sweep hooks armed on MarketManageFragment");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "mine hookSweep FAILED: " + t);
        }
    }

    private static void scheduleSweeps(final Object fragment) {
        if ((!sSignin && !sSlide && !sMineLoading) || fragment == null) {
            return;
        }
        android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
        for (int i = 0; i < SWEEP_DELAYS.length; i++) {
            h.postDelayed(new Runnable() {
                @Override
                public void run() {
                    try {
                        sweep(fragment);
                    } catch (Throwable t) {
                        XposedBridge.log(TAG + "mine-sweep err: " + t);
                    }
                }
            }, SWEEP_DELAYS[i]);
        }
    }

    private static void sweep(Object fragment) {
        Object viewObj = XposedHelpers.callMethod(fragment, "getView");
        if (!(viewObj instanceof android.view.View)) {
            return;
        }
        List<android.widget.TextView> texts = new ArrayList<android.widget.TextView>();
        collectTexts((android.view.View) viewObj, texts);
        int hidden = 0;
        for (int i = 0; i < texts.size(); i++) {
            android.widget.TextView tv = texts.get(i);
            CharSequence cs;
            try {
                cs = tv.getText();
            } catch (Throwable t) {
                continue;
            }
            if (cs == null) {
                continue;
            }
            String s = cs.toString();
            String key = matchKey(s);
            if (key == null) {
                continue;
            }
            if (hideCardOf(tv)) {
                hidden++;
                XposedBridge.log(TAG + "mine-sweep hide[" + key + "] text="
                        + shortText(s));
            }
        }
        if (hidden > 0) {
            XposedBridge.log(TAG + "mine-sweep done, hidden=" + hidden);
        }
    }

    private static String matchKey(String s) {
        if (sSignin && s.contains("签到")) {
            return "signin";
        }
        if (sSlide && (s.contains("滑一滑") || s.contains("发现更多精彩"))) {
            return "slide";
        }
        // 推荐流被拦后残留的加载占位
        if (sMineLoading && (s.contains("正在加载") || s.equals("加载中…")
                || s.equals("加载中"))) {
            return "loading";
        }
        return null;
    }

    private static void collectTexts(android.view.View v,
            List<android.widget.TextView> out) {
        if (v instanceof android.widget.TextView) {
            out.add((android.widget.TextView) v);
            return;
        }
        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            int n = g.getChildCount();
            for (int i = 0; i < n; i++) {
                android.view.View c = g.getChildAt(i);
                if (c != null) {
                    collectTexts(c, out);
                }
            }
        }
    }

    /** 找到文案所在 RecyclerView 的直接子项并隐藏；找不到 RV 就藏父容器 */
    private static boolean hideCardOf(android.widget.TextView tv) {
        android.view.View cur = tv;
        android.view.View target = null;
        for (int i = 0; i < 30 && cur != null; i++) {
            android.view.ViewParent p = cur.getParent();
            if (!(p instanceof android.view.View)) {
                break;
            }
            if (p.getClass().getName().contains("RecyclerView")) {
                target = cur;
                break;
            }
            cur = (android.view.View) p;
        }
        if (target == null) {
            android.view.ViewParent pp = tv.getParent();
            if (pp instanceof android.view.View) {
                target = (android.view.View) pp;
            } else {
                target = tv;
            }
        }
        if (target.getVisibility() == android.view.View.GONE) {
            return false;
        }
        target.setVisibility(android.view.View.GONE);
        return true;
    }

    private static String shortText(String s) {
        return s.length() > 40 ? s.substring(0, 40) : s;
    }
}
