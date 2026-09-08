package com.tamer.honormarket.hooks;

import java.util.concurrent.atomic.AtomicInteger;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodHook.MethodHookParam;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * 搜索结果页净化（SearchAppActivity 结果流，两个独立开关）：
 *
 * 数据锚点：AssSearchResultAdapter（搜索结果 GridView search_base_list 的装配适配器，
 * 全名类不混淆）。SearchResultLogicHandler 把服务端返回的 BaseAssInfo 混合列表写进
 * 适配器只有三个入口（16.1.8.301 实测，jadx 行号 584/910/964）：
 *   - w1(List,boolean)  首屏结果（showSearchResultData）
 *   - G0(List,boolean)  上拉加载更多（showSearchResultMoreData）
 *   - A0(List)          父类 CommAssAdapter 的整表替换（showSearchResultDataForBuild 重建）
 * 三个入口都在进适配器【之前】对传入列表原地过滤：
 *   1. 广告展位卡（结果第一位的大卡，带「广告」标签，layout zy_focus_booth_item）：
 *      条目类型 FocusBoothAppListInfo（@Keep Gson bean，全名类不混淆）→ 整条删除。
 *   2. 装机必备（ass_title 标题 + zy_discover 横滑图标列表；标题条目为 AssTitleInfo，
 *      横滑条目为 AssImageAppInfos → ImageHorScrollListHolder，两者都继承 BaseAssInfo
 *      且 Holder 渲染时直接读 getTitleName()）：getTitleName()/getDynamicTitle()
 *      含「装机必备」→ 删除，标题与横滑一同消失。
 *
 * 只动传入 List 的内容（Iterator.remove），不替换列表对象、不改控制流、不触发重请求
 * ——G0/w1 之后向 list 追加 load-more 尾条的逻辑照常运行，无死循环。
 * A0 挂在父类 CommAssAdapter 上，用 thisObject instanceof AssSearchResultAdapter
 * 限定只对搜索结果适配器实例生效——首页/详情页等共用 CommAssAdapter 的页面不受影响
 * （老教训：通用适配器绝不能按类全局拦）。
 */
public final class SearchAdBlocker {
    private static final String TAG = "[HonorMarketTamer] ";
    private static final String SEARCH_ADAPTER =
            "com.hihonor.appmarket.search.adapter.AssSearchResultAdapter";
    private static final String COMM_ASS_ADAPTER =
            "com.hihonor.appmarket.module.main.adapter.CommAssAdapter";
    private static final String BOOTH_BEAN =
            "com.hihonor.appmarket.card.bean.FocusBoothAppListInfo";

    private static final AtomicInteger sBoothHit = new AtomicInteger();
    private static final AtomicInteger sMustHit = new AtomicInteger();
    /** 列表构成 dump 次数：前几轮打印全部条目类名+标题，用于核对过滤覆盖面 */
    private static final AtomicInteger sDump = new AtomicInteger();

    private static boolean sAdBooth;
    private static boolean sMust;

    private SearchAdBlocker() { }

    public static void hook(final ClassLoader cl,
            com.tamer.honormarket.TamerConfig cfg) {
        sAdBooth = cfg.get(com.tamer.honormarket.TamerConfig.KEY_SEARCH_AD_BOOTH, true);
        sMust = cfg.get(com.tamer.honormarket.TamerConfig.KEY_SEARCH_MUST, true);
        if (!sAdBooth && !sMust) {
            XposedBridge.log(TAG + "SearchAdBlocker skipped (both switches off)");
            return;
        }
        try {
            final Class<?> adapterCls = XposedHelpers.findClass(SEARCH_ADAPTER, cl);
            Class<?> booth = null;
            try {
                booth = XposedHelpers.findClass(BOOTH_BEAN, cl);
            } catch (Throwable ignored) {
                // Bean 缺失只影响广告卡过滤，装机必备按标题过滤不受影响
            }
            final Class<?> boothCls = booth;

            XC_MethodHook sink = new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        if (param.args.length < 1
                                || !(param.args[0] instanceof java.util.List)) {
                            return;
                        }
                        // A0 挂在父类上：限定只处理搜索结果适配器实例
                        if (param.thisObject != null
                                && !adapterCls.isInstance(param.thisObject)) {
                            return;
                        }
                        scrub((java.util.List) param.args[0], boothCls);
                    } catch (Throwable t) {
                        XposedBridge.log(TAG + "search-ad scrub err: " + t);
                    }
                }
            };

            XposedBridge.hookAllMethods(adapterCls, "w1", sink);   // 首屏
            XposedBridge.hookAllMethods(adapterCls, "G0", sink);   // 加载更多
            Class<?> comm = XposedHelpers.findClass(COMM_ASS_ADAPTER, cl);
            XposedBridge.hookAllMethods(comm, "A0", sink);         // 重建路径（父类，scoped）
            XposedBridge.log(TAG + "SearchAdBlocker armed on AssSearchResultAdapter"
                    + " w1/G0 + CommAssAdapter A0(scoped) booth=" + sAdBooth
                    + " must=" + sMust);
        } catch (Throwable t) {
            XposedBridge.log(TAG + "SearchAdBlocker FAILED: " + t);
        }
    }

    /** 原地过滤：广告展位卡条目 / 标题含「装机必备」的装配条目 */
    private static void scrub(java.util.List list, Class<?> boothCls) {
        if (list == null || list.isEmpty()) {
            return;
        }
        boolean dump = sDump.incrementAndGet() <= 6;
        StringBuilder sb = dump ? new StringBuilder() : null;
        int booth = 0;
        int must = 0;
        java.util.Iterator it = list.iterator();
        while (it.hasNext()) {
            Object item = it.next();
            if (item == null) {
                continue;
            }
            String cls = item.getClass().getName();
            String title = titleOf(item);
            if (sb != null) {
                sb.append("\n  ").append(shortName(cls)).append(" title=").append(title);
            }
            if (sAdBooth && boothCls != null && boothCls.isInstance(item)) {
                it.remove();
                booth++;
            } else if (sMust && title != null && title.contains("装机必备")) {
                it.remove();
                must++;
            }
        }
        if (booth > 0) {
            int n = sBoothHit.addAndGet(booth);
            if (n <= 10 || n % 50 == 0) {
                XposedBridge.log(TAG + "search-ad booth removed x" + booth
                        + " (total #" + n + ")");
            }
        }
        if (must > 0) {
            int n = sMustHit.addAndGet(must);
            if (n <= 10 || n % 50 == 0) {
                XposedBridge.log(TAG + "search must-install removed x" + must
                        + " (total #" + n + ")");
            }
        }
        if (sb != null) {
            XposedBridge.log(TAG + "search list scan #" + sDump.get()
                    + " size=" + list.size() + sb);
        }
    }

    /** BaseAssInfo.getTitleName() / getDynamicTitle()，取不到返回 null */
    private static String titleOf(Object item) {
        try {
            Object v = XposedHelpers.callMethod(item, "getTitleName");
            if (v instanceof String && ((String) v).length() > 0) {
                return (String) v;
            }
        } catch (Throwable ignored) { }
        try {
            Object v = XposedHelpers.callMethod(item, "getDynamicTitle");
            if (v instanceof String && ((String) v).length() > 0) {
                return (String) v;
            }
        } catch (Throwable ignored) { }
        return null;
    }

    private static String shortName(String cls) {
        int i = cls.lastIndexOf('.');
        return i >= 0 ? cls.substring(i + 1) : cls;
    }
}
