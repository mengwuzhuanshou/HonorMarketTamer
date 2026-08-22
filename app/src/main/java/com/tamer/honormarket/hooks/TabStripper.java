package com.tamer.honormarket.hooks;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodHook.MethodHookParam;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * 底部 Tab / “我的”页板块过滤 v3。
 *
 * 关键教训：
 *  1) v1 在 onRenderStart 等单点删除页面，与按全量列表建好的 Fragment 并行数组错位
 *     —— “我的”内容串到“抢鲜”。必须让所有下游读到同一份过滤后的数据。
 *  2) defpackage.uf3 存在同名类冲突（多 dex），findClass 拿到的不是 fb5 用的那个；
 *     必须在运行时从 fb5.d 的参数上取真实 FrameInfoBO 类，再按签名定位其
 *     “读列表”与“写列表”方法挂钩。
 *
 * 实现：fb5.d(bo, cb) beforeHook 时：
 *  - 立即对本次调用做 m(e()) 过滤（覆盖当前帧）
 *  - 在 bo 的真实类上定位 List read() / write(List) 并挂长期钩子（覆盖后续动态刷新）
 */
public final class TabStripper {

    private static volatile boolean sInventoryLogged = false;
    private static volatile boolean sBoundHooks = false;

    public static void hook(final ClassLoader cl, final com.tamer.honormarket.TamerConfig cfg) {
        final TabRules rules = new TabRules(cfg);
        if (!rules.hasAnyTabRule() && !rules.hasAnyMineSectionRule()) {
            XposedBridge.log("[HonorMarketTamer] TabStripper: no rules, skip");
            return;
        }

        HookUtil.FlexCallback entry = new HookUtil.FlexCallback() {
            @Override
            public void fire(MethodHookParam param) {
                try {
                    Object bo = param.args[0];
                    if (bo == null) return;
                    bindPersistentHooks(bo, rules);
                    // 本次调用立即过滤
                    List<?> cur = readList(bo);
                    if (cur != null) {
                        List<Object> filtered = filterPages(cur, rules, "d()");
                        if (filtered != null) {
                            writeList(bo, filtered);
                        }
                    }
                } catch (Throwable t) {
                    XposedBridge.log("[HonorMarketTamer] TabStripper(d) failed: " + t);
                }
            }
        };
        // 锚点：fb5.a 的唯一实现者（全名类，无同名冲突）。
        // 其 d(uf3,List,AL,AL,int[],AL) 是菜单/Fragment/统计并行数组的统一交付点，
        // 在此做“同索引一致删除”，天然保持所有下游对齐；g 菜单、i 默认索引配合修正。
        java.lang.Class<?> cbCls;
        try {
            cbCls = XposedHelpers.findClass(
                    "com.hihonor.appmarket.main.frame.fragments.MainFrameFragmentTemp$a", cl);
        } catch (Throwable t) {
            XposedBridge.log("[HonorMarketTamer] anchor class not found: " + t);
            return;
        }

        // d(...)：六参并行数组一致删除
        XC_MethodHook dHook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                try {
                    Object[] a = param.args;
                    if (a == null || a.length < 6 || !(a[1] instanceof List)) return;
                    List<?> pages = (List<?>) a[1];
                    logInventory(pages);
                    boolean[] drop = new boolean[pages.size()];
                    int dropCount = 0;
                    for (int i = 0; i < pages.size(); i++) {
                        Object p = pages.get(i);
                        boolean d;
                        try { d = rules.isTabBlocked(p); } catch (Throwable t) { d = false; }
                        drop[i] = d;
                        if (d) {
                            dropCount++;
                            XposedBridge.log("[HonorMarketTamer] tab dropped: " + pageDesc(p));
                        }
                        if (rules.isMineSectionTarget(p)) filterMineSections(p, rules);
                    }
                    if (dropCount == 0 || dropCount >= pages.size()) return;

                    java.util.ArrayList<Object> np = new java.util.ArrayList<>();
                    java.util.ArrayList<Object> n1 = new java.util.ArrayList<>();
                    java.util.ArrayList<Object> n2 = new java.util.ArrayList<>();
                    ArrayList<Object> n3 = new ArrayList<>();
                    ArrayList<Object> n5 = new ArrayList<>();
                    int[] oldArr = (a[4] instanceof int[]) ? (int[]) a[4] : null;
                    int[] narr = oldArr != null ? new int[oldArr.length - dropCount] : null;
                    int w = 0;
                    for (int i = 0; i < pages.size(); i++) {
                        if (drop[i]) continue;
                        np.add(pages.get(i));
                        if (a[2] instanceof java.util.List && ((java.util.List<?>) a[2]).size() == pages.size())
                            n1.add(((java.util.List<Object>) a[2]).get(i));
                        if (a[3] instanceof java.util.List && ((java.util.List<?>) a[3]).size() == pages.size())
                            n2.add(((java.util.List<Object>) a[3]).get(i));
                        if (narr != null && oldArr.length == pages.size()) narr[w] = oldArr[i];
                        if (a[5] instanceof java.util.List && ((java.util.List<?>) a[5]).size() == pages.size())
                            n3.add(((java.util.List<Object>) a[5]).get(i));
                        w++;
                    }
                    a[1] = np;
                    if (a[2] instanceof java.util.List && n1.size() == np.size()) a[2] = n1;
                    if (a[3] instanceof java.util.List && n2.size() == np.size()) a[3] = n2;
                    if (narr != null && narr.length == np.size()) a[4] = narr;
                    if (a[5] instanceof java.util.List && n3.size() == np.size()) a[5] = n3;
                    sKeptCount.set(np.size());
                    XposedBridge.log("[HonorMarketTamer] tabs " + pages.size()
                            + " -> " + np.size());
                } catch (Throwable t) {
                    XposedBridge.log("[HonorMarketTamer] d-consistent failed: " + t);
                }
            }
        };
        XposedBridge.hookAllMethods(cbCls, "d", dHook);

        // g(uf3, List)：底栏菜单列表，原地过滤；并借 args[0] 绑定真实 FrameInfoBO 读写钩子
        XC_MethodHook gHook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                try {
                    if (param.args == null || param.args.length < 2
                            || !(param.args[1] instanceof List)) return;
                    if (param.args[0] != null) bindFrameBoHooks(param.args[0], rules);
                    List<Object> list = (List<Object>) param.args[1];
                    logInventory(list);
                    Iterator<Object> it = list.iterator();
                    boolean changed = false;
                    while (it.hasNext()) {
                        Object p = it.next();
                        boolean d;
                        try { d = rules.isTabBlocked(p); } catch (Throwable t) { d = false; }
                        if (d) { it.remove(); changed = true;
                            XposedBridge.log("[HonorMarketTamer] menu dropped: " + pageDesc(p)); }
                        if (rules.isMineSectionTarget(p)) filterMineSections(p, rules);
                    }
                    if (changed) sKeptCount.set(list.size());
                } catch (Throwable t) {
                    XposedBridge.log("[HonorMarketTamer] g failed: " + t);
                }
            }
        };
        XposedBridge.hookAllMethods(cbCls, "g", gHook);

        // i(int defaultIdx, ...)：默认选中索引，按删除后的规模收敛
        XC_MethodHook iHook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                try {
                    if (param.args == null || param.args.length < 1) return;
                    if (!(param.args[0] instanceof Integer)) return;
                    Integer kept = sKeptCount.get();
                    if (kept == null || kept <= 0) return;
                    int idx = (Integer) param.args[0];
                    if (idx >= kept) {
                        param.args[0] = kept - 1;
                        XposedBridge.log("[HonorMarketTamer] default tab idx " + idx
                                + " -> " + (kept - 1));
                    }
                } catch (Throwable t) {
                    XposedBridge.log("[HonorMarketTamer] i failed: " + t);
                }
            }
        };
        XposedBridge.hookAllMethods(cbCls, "i", iHook);

        XposedBridge.log("[HonorMarketTamer] anchored on MainFrameFragmentTemp$a");
    }

    private static final java.util.concurrent.atomic.AtomicInteger sKeptCount =
            new java.util.concurrent.atomic.AtomicInteger(-1);

    /**
     * 从活对象上拿真实 FrameInfoBO 类，给它的“读列表/写列表”方法挂持久钩子。
     * 这样缓存命中路径（isCache=true 时跳过整帧重建）读到的也永远是过滤后的数据，
     * 彻底消除“菜单已滤、Fragment 全量”的串台。
     */
    private static void bindFrameBoHooks(final Object bo, final TabRules rules) {
        if (sBoBound) return;
        synchronized (TabStripper.class) {
            if (sBoBound) return;
            sBoBound = true;
            try {
                final Class<?> cls = bo.getClass();
                final String rName = findListGetter(cls);
                final String wName = findListSetter(cls);
                XposedBridge.log("[HonorMarketTamer] frame-bo=" + cls.getName()
                        + " read=" + rName + " write=" + wName);
                if (wName != null) {
                    XposedBridge.hookAllMethods(cls, wName, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            try {
                                if (param.args == null || param.args.length < 1
                                        || !(param.args[0] instanceof List)) return;
                                List<Object> f = filterPages((List<?>) param.args[0], rules, "W");
                                if (f != null) param.args[0] = f;
                            } catch (Throwable t) {
                                XposedBridge.log("[HonorMarketTamer] W failed: " + t);
                            }
                        }
                    });
                }
                if (rName != null) {
                    XposedBridge.hookAllMethods(cls, rName, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            try {
                                Object res = param.getResult();
                                if (!(res instanceof List)) return;
                                List<Object> f = filterPages((List<?>) res, rules, "R");
                                if (f != null) param.setResult(f);
                            } catch (Throwable t) {
                                XposedBridge.log("[HonorMarketTamer] R failed: " + t);
                            }
                        }
                    });
                }
            } catch (Throwable t) {
                XposedBridge.log("[HonorMarketTamer] bindFrameBo failed: " + t);
            }
        }
    }

    private static volatile boolean sBoBound = false;

    /** 在真实 FrameInfoBO 类上挂 read/write 长期钩子（仅一次） */
    private static void bindPersistentHooks(final Object bo, final TabRules rules) {
        if (sBoundHooks) return;
        synchronized (TabStripper.class) {
            if (sBoundHooks) return;
            sBoundHooks = true;
            try {
                final Class<?> cls = bo.getClass();
                final String readName = findListGetter(cls);
                final String writeName = findListSetter(cls);
                XposedBridge.log("[HonorMarketTamer] frame-bo=" + cls.getName()
                        + " read=" + readName + " write=" + writeName);
                if (readName == null && writeName == null) return;

                if (writeName != null) {
                    XC_MethodHook w = new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            try {
                                if (param.args == null || param.args.length < 1
                                        || !(param.args[0] instanceof List)) return;
                                List<Object> filtered =
                                        filterPages((List<?>) param.args[0], rules, "W");
                                if (filtered != null) param.args[0] = filtered;
                            } catch (Throwable t) {
                                XposedBridge.log("[HonorMarketTamer] W failed: " + t);
                            }
                        }
                    };
                    XposedBridge.hookAllMethods(cls, writeName, w);
                }
                if (readName != null) {
                    XC_MethodHook r = new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            try {
                                Object res = param.getResult();
                                if (!(res instanceof List)) return;
                                List<Object> filtered =
                                        filterPages((List<?>) res, rules, "R");
                                if (filtered != null) param.setResult(filtered);
                            } catch (Throwable t) {
                                XposedBridge.log("[HonorMarketTamer] R failed: " + t);
                            }
                        }
                    };
                    XposedBridge.hookAllMethods(cls, readName, r);
                }
            } catch (Throwable t) {
                XposedBridge.log("[HonorMarketTamer] bind failed: " + t);
            }
        }
    }

    /** 找无参且返回 List 的读方法名 */
    private static String findListGetter(Class<?> cls) {
        for (java.lang.reflect.Method m : cls.getDeclaredMethods()) {
            if (m.getParameterTypes().length == 0
                    && List.class.isAssignableFrom(m.getReturnType())) {
                return m.getName();
            }
        }
        return null;
    }

    /** 找单参 List 的写方法名 */
    private static String findListSetter(Class<?> cls) {
        for (java.lang.reflect.Method m : cls.getDeclaredMethods()) {
            if (m.getParameterTypes().length == 1
                    && List.class.isAssignableFrom(m.getParameterTypes()[0])) {
                return m.getName();
            }
        }
        return null;
    }

    private static List<?> readList(Object bo) {
        try {
            Object r = XposedHelpers.callMethod(bo, findListGetter(bo.getClass()));
            return r instanceof List ? (List<?>) r : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private static void writeList(Object bo, List<?> v) {
        try {
            XposedHelpers.callMethod(bo, findListSetter(bo.getClass()), v);
        } catch (Throwable ignored) {}
    }

    /** 返回 null 表示无需改动；否则返回过滤后的新列表 */
    private static List<Object> filterPages(List<?> orig, TabRules rules, String via) {
        if (orig == null || orig.isEmpty()) return null;
        logInventory(orig);
        List<Object> out = null;
        for (int i = 0; i < orig.size(); i++) {
            Object page = orig.get(i);
            boolean drop;
            try {
                drop = rules.isTabBlocked(page);
            } catch (Throwable t) {
                drop = false;
            }
            if (drop) {
                if (out == null) {
                    out = new ArrayList<>();
                    for (int j = 0; j < i; j++) out.add(orig.get(j));
                }
                XposedBridge.log("[HonorMarketTamer] tab dropped via " + via + ": "
                        + pageDesc(page));
            } else if (out != null) {
                out.add(page);
            }
            if (rules.isMineSectionTarget(page)) {
                filterMineSections(page, rules);
            }
        }
        if (out != null && out.isEmpty()) return null; // 防全删白屏
        return out;
    }

    @SuppressWarnings("unchecked")
    private static void filterMineSections(Object minePage, TabRules rules) {
        try {
            Object subObj = XposedHelpers.callMethod(minePage, "getSubMenu");
            if (!(subObj instanceof List)) return;
            List<Object> subs = (List<Object>) subObj;
            Iterator<Object> it = subs.iterator();
            boolean changed = false;
            while (it.hasNext()) {
                Object sub = it.next();
                String name;
                try {
                    Object n = XposedHelpers.callMethod(sub, "getPageName");
                    name = n == null ? "" : String.valueOf(n);
                } catch (Throwable t) {
                    continue;
                }
                if (rules.isMineSectionBlocked(name)) {
                    it.remove();
                    changed = true;
                    XposedBridge.log("[HonorMarketTamer] mine section dropped: " + name);
                }
            }
            if (changed) {
                try { XposedHelpers.callMethod(minePage, "setSubMenu", subs); } catch (Throwable ignored) {}
            }
        } catch (Throwable t) {
            XposedBridge.log("[HonorMarketTamer] mine section filter failed: " + t);
        }
    }

    private static void logInventory(List<?> pages) {
        if (sInventoryLogged) return;
        sInventoryLogged = true;
        StringBuilder sb = new StringBuilder("[HonorMarketTamer] page inventory:");
        for (Object p : pages) {
            sb.append(" {").append(pageDesc(p)).append("}");
        }
        XposedBridge.log(sb.toString());
    }

    private static String pageDesc(Object page) {
        String name = "?";
        int type = -1;
        long id = -1;
        try {
            Object n = XposedHelpers.callMethod(page, "getPageName");
            if (n != null) name = String.valueOf(n);
        } catch (Throwable ignored) {}
        try {
            Object t = XposedHelpers.callMethod(page, "getPageType");
            if (t instanceof Integer) type = (Integer) t;
        } catch (Throwable ignored) {}
        try {
            Object i = XposedHelpers.callMethod(page, "getPageId");
            if (i instanceof Number) id = ((Number) i).longValue();
        } catch (Throwable ignored) {}
        return "name=" + name + ",type=" + type + ",id=" + id;
    }

    /** 规则集：每次进程冷加载时固化一次（仅 应用/抢鲜/游戏 三个 Tab 开关） */
    private static final class TabRules {
        final boolean blkGame, blkApps, blkQiangxian;

        TabRules(com.tamer.honormarket.TamerConfig cfg) {
            blkGame      = on(cfg, com.tamer.honormarket.TamerConfig.KEY_GAME_TAB);
            blkApps      = on(cfg, com.tamer.honormarket.TamerConfig.KEY_APPS_TAB);
            blkQiangxian = on(cfg, com.tamer.honormarket.TamerConfig.KEY_QIANGXIAN_TAB);
        }

        private static boolean on(com.tamer.honormarket.TamerConfig cfg, String key) {
            return cfg.get(key, com.tamer.honormarket.TamerConfig.defaultValueOf(key));
        }

        boolean hasAnyTabRule() {
            return blkGame || blkApps || blkQiangxian;
        }

        boolean hasAnyMineSectionRule() {
            return false;
        }

        boolean isTabBlocked(Object page) {
            Integer type = intOf(page, "getPageType");
            String name = strOf(page, "getPageName");
            if (blkGame && match(type, name, 6, "游戏")) return true;
            if (blkApps && match(type, name, 7, "应用")) return true;
            if (blkQiangxian && name != null && name.contains("抢鲜")) return true;
            return false;
        }

        boolean isMineSectionTarget(Object page) {
            return false; // 已按需求移除“我的”页/板块屏蔽
        }

        boolean isMineSectionBlocked(String name) {
            return false;
        }

        private static boolean match(Integer type, String name, int wantType, String wantName) {
            if (type != null && type == wantType) return true;
            return name != null && name.equals(wantName);
        }

        private static Integer intOf(Object o, String m) {
            try {
                Object r = XposedHelpers.callMethod(o, m);
                return r instanceof Integer ? (Integer) r : null;
            } catch (Throwable t) { return null; }
        }

        private static String strOf(Object o, String m) {
            try {
                Object r = XposedHelpers.callMethod(o, m);
                return r == null ? null : String.valueOf(r);
            } catch (Throwable t) { return null; }
        }
    }
}
