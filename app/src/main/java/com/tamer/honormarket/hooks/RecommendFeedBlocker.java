package com.tamer.honormarket.hooks;

import java.util.concurrent.atomic.AtomicInteger;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodHook.MethodHookParam;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * 推荐流屏蔽（三个页面，各自独立开关）：
 *
 * 1. 「我的」页：
 *  - 请求源头 MarketManageViewModel#o(...)（老桥）/ #n(...)（16.1.8.301 起真实请求核心
 *    R232 唯一网络出口）直接吞掉 → 零流量、无响应循环、无加载闪烁；
 *  - 响应汇聚点（方法名随版本漂移 V1→W1→Z1，均 4 参 BaseResp）：置空 assemblyList 的
 *    成功空响应，覆盖网络 + 缓存两条路径（缓存里的旧豆包/百度行也不渲染）；
 *  - 残留「正在加载」类占位文案由 MineSectionBlocker 的页面扫描隐藏。
 *
 * 2. 搜索发现页（点搜索框进入）：
 *  - 【v1.3.0 请求源头】SearchActivationViewModel#o(long,int,int,String,String,Continuation)
 *    是首屏与加载更多共用的唯一网络出口（内部同步 execute R008 装配列表），
 *    before-hook 直接返回 errorCode=-1 的空 BaseResp——完全复刻 App 自身“请求失败”
 *    分支（VM 内部 execute 异常时就是这么构造的），下游 t()/u1()/A1 对该形态全链路
 *    判空安全且自带收尾：零流量、协程立即完成、loading 瞬间消失、输入框即刻可用；
 *  - A1(setActiveData) 置空卡片列表；再在渲染适配器 AssSearchActivationAdapter#w1
 *    （@Nullable、空安全、唯一数据入口）做兜底，覆盖缓存等其它上游路径。热搜词/历史不受影响。
 *
 * 3. 更新页“新应用，新用途”：
 *  - access$setRecommendResponseData / setRecommendResponseData 把 resp 置 null，
 *    应用自身空列表分支会收尾并隐藏推荐区。
 *
 * 4.【v1.3.0】应用详情页「应用名+用户必备的软件推荐」（AppIntroductionFragment 底部
 *   ConcatAdapter 的 AppDetailRecommendAdapter 区块）。两个数据进料口一起掐：
 *  - AppDetailRecommendViewModel#h(...)：唯一请求入口（MultiAssemblyDataReq），
 *    主详情页 AppDetailsActivity.D0 尾部 / 介绍 Tab W0() / 半屏 AppDetailRecommendFragment /
 *    分发详情 DispatchAppRecommendViewModel(继承) / 签到详情 SignDetailActivity 全走这里；
 *    返回 Job 仅被可空调用方使用，setResult(null) 安全；
 *  - AppDetailsActivity$requestMiddle$1$1#invokeSuspend：inner-detail 预取桥，把预取 resp
 *    直接 setValue 进同一 LiveData；整体吞掉（预取管理器本身不动，不影响详情页加载速度）。
 *   无响应则区块（含标题）根本不渲染，RecommendAdapter 空态零高度。
 */
public final class RecommendFeedBlocker {
    private static final String TAG = "[HonorMarketTamer] ";
    private static final String MINE_FRAG =
            "com.hihonor.appmarket.mine.MarketManageFragment";
    private static final String MINE_VM =
            "com.hihonor.appmarket.mine.viewmodel.MarketManageViewModel";
    private static final String SEARCH_FRAG =
            "com.hihonor.appmarket.search.fragment.SearchActivationFragment";
    private static final String SEARCH_ADAPTER =
            "com.hihonor.appmarket.search.adapter.AssSearchActivationAdapter";
    private static final String UPDATE_ACT =
            "com.hihonor.appmarket.appupdate.UpdateManagerActivity";
    private static final String UPDATE_DISCOVER =
            "com.hihonor.appmarket.appupdate.adapter.DiscoverMoreAdapter";
    /** 搜索发现页请求源头 VM（首屏+加载更多共用唯一网络出口 o(...)） */
    private static final String SEARCH_VM =
            "com.hihonor.appmarket.search.model.SearchActivationViewModel";
    /** 详情页推荐请求入口 VM（子类 DispatchAppRecommendViewModel 继承同一方法） */
    private static final String DETAIL_REC_VM =
            "com.hihonor.appmarket.base.details.recommend.AppDetailRecommendViewModel";
    /** 详情页 inner-detail 预取 → 推荐 LiveData 的桥接协程（jadx 真实类名，全包名唯一） */
    private static final String DETAIL_MIDDLE_BRIDGE =
            "com.hihonor.appmarket.app.details.activity.AppDetailsActivity$requestMiddle$1$1";

    private static final AtomicInteger sReqBlocked = new AtomicInteger();
    /** 16.1.8.301 起：真实请求核心 n(...) 的命中计数 */
    private static final AtomicInteger sReqCoreBlocked = new AtomicInteger();
    /** 16.1.8.301 起：响应汇聚点 W1(...) 的命中计数 */
    private static final AtomicInteger sW1Hit = new AtomicInteger();
    private static final AtomicInteger sSearchBlocked = new AtomicInteger();
    private static final AtomicInteger sSearchSrcBlocked = new AtomicInteger();
    private static final AtomicInteger sDetailRecBlocked = new AtomicInteger();
    /** 低频路径的命中计数：仅用于日志采样（前几条全打，之后按比例抽样），不参与逻辑 */
    private static final AtomicInteger sV1Hit = new AtomicInteger();
    private static final AtomicInteger sA1Hit = new AtomicInteger();
    private static final AtomicInteger sUpdBridgeHit = new AtomicInteger();
    private static final AtomicInteger sUpdRespHit = new AtomicInteger();
    private static final AtomicInteger sDiscoverHit = new AtomicInteger();

    private static boolean sMineFeed;
    private static boolean sSearchFeed;
    private static boolean sUpdateFeed;
    private static boolean sDetailRec;

    private RecommendFeedBlocker() { }

    public static void hook(final ClassLoader cl,
            com.tamer.honormarket.TamerConfig cfg) {
        sMineFeed   = cfg.get(com.tamer.honormarket.TamerConfig.KEY_MINE_FEED, true);
        sSearchFeed = cfg.get(com.tamer.honormarket.TamerConfig.KEY_SEARCH_FEED, true);
        sUpdateFeed = cfg.get(com.tamer.honormarket.TamerConfig.KEY_UPDATE_FEED, true);
        sDetailRec  = cfg.get(com.tamer.honormarket.TamerConfig.KEY_DETAIL_REC, true);
        if (sMineFeed) {
            hookMineReq(cl);       // 老：o(...) 请求桥（16.1.8 起已降级为合成桥，保留双保险）
            hookMineReqCore(cl);   // 新：n(...) 真实请求核心（16.1.8.301 R232 唯一网络出口）
            hookMine(cl);          // 老：V1(resp,z,z2,z3) 缓存路径（16.1.8 起改名，保留存档）
            hookMineResp(cl);      // 响应汇聚点 Z1/W1/V1（4 参 BaseResp，16.1.8.305 真身=Z1）
        }
        if (sSearchFeed) {
            hookSearchSource(cl);
            hookSearch(cl);
            hookSearchAdapter(cl);
        }
        if (sUpdateFeed) {
            hookUpdate(cl);
            hookUpdateDiscover(cl);
        }
        if (sDetailRec) {
            hookDetailRecommend(cl);
            hookDetailMiddleBridge(cl);
        }
    }

    /** 「我的」页请求源头：吞掉 realRequestRecommend 核心，请求零流量 */
    private static void hookMineReq(final ClassLoader cl) {
        try {
            Class<?> vm = XposedHelpers.findClass(MINE_VM, cl);
            XposedBridge.hookAllMethods(vm, "o", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        if (param.args.length == 4) {
                            param.setResult(null);
                            int n = sReqBlocked.incrementAndGet();
                            if (n <= 10 || n % 50 == 0) {
                                XposedBridge.log(TAG + "feed-block mine request #" + n);
                            }
                        }
                    } catch (Throwable t) {
                        XposedBridge.log(TAG + "feed-block mine-req err: " + t);
                    }
                }
            });
            XposedBridge.log(TAG + "RecommendFeedBlocker armed on MarketManageViewModel#o");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "RecommendFeedBlocker mine-vm FAILED: " + t);
        }
    }

    /**
     * 【16.1.8.301 适配】「我的」页真实请求核心：MarketManageViewModel#n(MutableLiveData,int,String,boolean)。
     *
     * 16.1.8 起服务端/客户端把 realRequestRecommend 的出口从 o(...) 挪到 n(...)：
     *  - o(...) 降级为 Kotlin default-args 合成桥 static synthetic o(VM,LiveData,int,int)，只转调 n；
     *  - 首屏 t("R232") / 刷新 q(...) 经桥进 n，但上滑加载更多 s(offset,"51") 在 str!=null 时
     *    【直接调 n(...)"51"】，完全绕过老 o 钩子 → 老钩子只杀桥、拦不住无限 load-more，
     *    于是「我的」页底部豆包/百度单行应用推荐流（single_line_recommend_root）复活且可无限滚动。
     *
     * n(...) 是首屏/刷新/加载更多三条路径共用的唯一网络出口（内部 new MultiAssemblyDataReq
     * setRecommendCode("R232") 后 BaseViewModel.request 发请求）。before-hook setResult(null)
     * → 请求零流量、协程不启动、无响应 → 下游 RecommendAdapter 无数据可绑，feed 整段不渲染。
     * 与老 o 钩子同形态（都只 setResult(null)、不改返回值结构），幂等、无回调、不死循环。
     */
    private static void hookMineReqCore(final ClassLoader cl) {
        try {
            Class<?> vm = XposedHelpers.findClass(MINE_VM, cl);
            XposedBridge.hookAllMethods(vm, "n", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        // n(MutableLiveData, int offset, String reqSrc, boolean) = 4 参
                        if (param.args.length == 4) {
                            param.setResult(null);
                            int n = sReqCoreBlocked.incrementAndGet();
                            if (n <= 10 || n % 50 == 0) {
                                XposedBridge.log(TAG + "mine-feed req-core killed #" + n);
                            }
                        }
                    } catch (Throwable t) {
                        XposedBridge.log(TAG + "mine-feed req-core err: " + t);
                    }
                }
            });
            XposedBridge.log(TAG + "RecommendFeedBlocker armed on MarketManageViewModel#n (req core)");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "RecommendFeedBlocker mine-req-core FAILED: " + t);
        }
    }

    /**
     * 【16.1.8.305 适配】「我的」页推荐响应汇聚点：
     * MarketManageFragment#Z1(BaseResp,boolean,boolean,boolean)。
     *
     * 汇聚点方法名随版本漂移：V1(16.1.7 及以前) → W1(16.1.8.301) → Z1(16.1.8.305)。
     * 三个版本里它都是【唯一收口】：网络响应（observer$e#invoke → H1 → G1 → X1）与
     * 缓存路径（loadCacheRecommendData$1 → Y1 → X1）都最终进它，内部读
     * BaseResp.getData().getAssemblyList() 并喂 RecommendAdapter#s1 / jb4#w。
     * 16.1.8.305 里 W1 只剩 W1(int)（gw#a 调用的无关方法）、V1 只剩 V1(boolean)，
     * 真身已挪到 Z1——按 4 参 BaseResp 签名锚定即可跨版本稳定命中真 sink。
     *
     * 关键手法——【置空数据而非吞响应】（沿用本项目坑 #9 哲学）：把 args[0](BaseResp)
     * 换成 errorCode=0、data=空 assemblyList 的【成功空响应】，使 sink 走
     * "assemblyList.isEmpty() → 空数据分支"，feed 整段干净收起；若直接置 null 会落进
     * App 的 error 分支，残留"加载失败，点击重试"占位——不可取。
     * before-hook 只替换入参、不改控制流，幂等、无回调、不死循环。
     *
     * 老 W1/V1 钩子原样保留（新版下 4 参签名不匹配 → 自然落空，无害存档；老市场版本兼容）。
     */
    private static void hookMineResp(final ClassLoader cl) {
        try {
            Class<?> clazz = XposedHelpers.findClass(MINE_FRAG, cl);
            // 汇聚点方法名跨版本漂移：Z1(16.1.8.305) 为主，W1/V1 存档兜底老版本。
            // 回调按 4 参 BaseResp 过滤，只有真 sink 命中，同名无关方法（W1(int)/V1(boolean)）落空。
            final XC_MethodHook sinkHook = new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        // Z1/W1/V1(BaseResp, boolean, boolean, boolean) = 4 参
                        if (param.args.length != 4 || param.args[0] == null) {
                            return;
                        }
                        Object empty = emptySuccessResp(cl);
                        if (empty == null) {
                            // 构造失败兜底：置 null（至少不出数据），error 态由空态扫描兜底
                            param.args[0] = null;
                        } else {
                            param.args[0] = empty;
                        }
                        int n = sW1Hit.incrementAndGet();
                        if (n <= 10 || n % 100 == 0) {
                            XposedBridge.log(TAG + "mine-feed resp-sink(Z1) resp->empty #"
                                    + n);
                        }
                    } catch (Throwable t) {
                        XposedBridge.log(TAG + "mine-feed resp-sink err: " + t);
                    }
                }
            };
            XposedBridge.hookAllMethods(clazz, "Z1", sinkHook);   // 16.1.8.305 真 sink
            XposedBridge.hookAllMethods(clazz, "W1", sinkHook);   // 16.1.8.301 存档
            XposedBridge.hookAllMethods(clazz, "V1", sinkHook);   // 16.1.7 及以前存档
            XposedBridge.log(TAG + "RecommendFeedBlocker armed on MarketManageFragment "
                    + "Z1/W1/V1 (resp sink, 4-arg BaseResp)");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "RecommendFeedBlocker mine-resp FAILED: " + t);
        }
    }

    /**
     * 构造空数据的成功响应：BaseResp{errorCode=0, data=MultiAssemblyDataResp{空 assemblyList}}。
     * 与 App 自身"请求成功但无推荐内容"的响应同形态，供 W1 走空数据分支。
     */
    private static Object emptySuccessResp(ClassLoader cl) {
        try {
            Class<?> respCls = XposedHelpers.findClass(
                    "com.hihonor.appmarket.network.base.BaseResp", cl);
            Class<?> dataCls = XposedHelpers.findClass(
                    "com.hihonor.appmarket.network.response.MultiAssemblyDataResp", cl);
            Object data = dataCls.newInstance();
            java.util.List<Object> emptyList = new java.util.ArrayList<Object>();
            XposedHelpers.callMethod(data, "setAssemblyList", emptyList);
            XposedHelpers.callMethod(data, "setAssemblyNewList", emptyList);
            XposedHelpers.callMethod(data, "setAssemblyOffset", Integer.valueOf(0));
            Object resp = respCls.newInstance();
            XposedHelpers.callMethod(resp, "setErrorCode", Integer.valueOf(0));
            XposedHelpers.callMethod(resp, "setData", data);
            return resp;
        } catch (Throwable t) {
            XposedBridge.log(TAG + "mine empty-resp build err: " + t);
            return null;
        }
    }

    /** 「我的」页缓存路径兜底：V1 置空 resp，返回后主线程补一次空数据收尾 M1 */
    private static void hookMine(final ClassLoader cl) {
        try {
            Class<?> clazz = XposedHelpers.findClass(MINE_FRAG, cl);
            XposedBridge.hookAllMethods(clazz, "V1", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        if (param.args.length == 4 && param.args[0] != null) {
                            param.args[0] = null;
                            int n = sV1Hit.incrementAndGet();
                            if (n <= 5 || n % 100 == 0) {
                                XposedBridge.log(TAG + "feed-block mine V1 resp->null #"
                                        + n);
                            }
                        }
                    } catch (Throwable t) {
                        XposedBridge.log(TAG + "feed-block mine err: " + t);
                    }
                }

                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        // 模仿 E1 无数据分支：M1(null,null,true,false,false)
                        final Object fragment = param.thisObject;
                        android.os.Handler h = new android.os.Handler(
                                android.os.Looper.getMainLooper());
                        h.post(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    XposedHelpers.callMethod(fragment, "M1",
                                            new Object[]{null, null,
                                                    Boolean.TRUE, Boolean.FALSE,
                                                    Boolean.FALSE});
                                } catch (Throwable ignored) { }
                            }
                        });
                    } catch (Throwable ignored) { }
                }
            });
            XposedBridge.log(TAG + "RecommendFeedBlocker armed on MarketManageFragment#V1");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "RecommendFeedBlocker mine FAILED: " + t);
        }
    }

    /** 搜索发现页 A1（保留，双保险） */
    private static void hookSearch(final ClassLoader cl) {
        try {
            Class<?> clazz = XposedHelpers.findClass(SEARCH_FRAG, cl);
            XposedBridge.hookAllMethods(clazz, "A1", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        if (param.args.length == 4 && param.args[0] != null) {
                            param.args[0] = null;
                            int n = sA1Hit.incrementAndGet();
                            if (n <= 5 || n % 100 == 0) {
                                XposedBridge.log(TAG + "feed-block search A1 list->null #"
                                        + n);
                            }
                        }
                    } catch (Throwable t) {
                        XposedBridge.log(TAG + "feed-block search err: " + t);
                    }
                }
            });
            XposedBridge.log(TAG + "RecommendFeedBlocker armed on SearchActivationFragment#A1");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "RecommendFeedBlocker search FAILED: " + t);
        }
    }

    /**
     * 【v1.3.0】搜索发现页请求源头：SearchActivationViewModel#o(6参) 是首屏与加载更多
     * 共用的唯一网络出口（内部同步执行 R008 装配列表请求，云控变体 p(...) 也只从它进入）。
     * before-hook 直接返回 errorCode=-1 的空 BaseResp——与 App 自身“execute 失败”分支
     * 构造的对象同形态，下游 u1()/t()/A1 全链路判空安全且自带收尾（空列表 →
     * setActiveData isNullOrEmpty 分支 → 立即展示内容并记录软键盘状态）。
     * 零流量、协程立即完成、loading 占位瞬间消失、输入框即刻可输入。
     */
    private static void hookSearchSource(final ClassLoader cl) {
        try {
            Class<?> vm = XposedHelpers.findClass(SEARCH_VM, cl);
            Class<?> respCls = null;
            try {
                respCls = XposedHelpers.findClass(
                        "com.hihonor.appmarket.network.base.BaseResp", cl);
            } catch (Throwable t2) {
                respCls = null;
            }
            final Class<?> baseRespCls = respCls;
            XposedBridge.hookAllMethods(vm, "o", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        if (param.args.length != 6) {
                            return;
                        }
                        Object resp = null;
                        if (baseRespCls != null) {
                            // 构造与 VM 内部失败分支一致的 BaseResp(errorCode=-1)
                            resp = baseRespCls.newInstance();
                            XposedHelpers.callMethod(resp, "setErrorCode",
                                    Integer.valueOf(-1));
                        }
                        param.setResult(resp);
                        int n = sSearchSrcBlocked.incrementAndGet();
                        if (n <= 10 || n % 50 == 0) {
                            XposedBridge.log(TAG + "search-req killed at source #" + n
                                    + (resp == null ? " (null fallback)" : ""));
                        }
                    } catch (Throwable t2) {
                        // 构造异常兜底：返回 null，u1/t() 对 null 同样全链路判空
                        param.setResult(null);
                        XposedBridge.log(TAG + "search-src fallback(null): " + t2);
                    }
                }
            });
            XposedBridge.log(TAG + "RecommendFeedBlocker armed on "
                    + "SearchActivationViewModel#o (request source)");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "RecommendFeedBlocker search-source FAILED: " + t);
        }
    }

    /**
     * 搜索发现页兜底：拦渲染适配器唯一数据入口。方法名随版本漂移：
     * w1(List,boolean)(16.1.7 及以前) → y1(List,boolean)(16.1.8.305)。
     * y1 被 SearchActivationFragment#A1 / requestLoadMoreData / l0 调用，是卡片列表唯一入口，
     * before-hook 把 args[0] 置 null（空安全，App 自身 isNullOrEmpty 分支收尾）。
     * 老 w1 钩子原样保留（新版 w1 已变成 0 参无关方法 → 2 参过滤自然落空，无害存档）。
     */
    private static void hookSearchAdapter(final ClassLoader cl) {
        try {
            Class<?> adapter = XposedHelpers.findClass(SEARCH_ADAPTER, cl);
            final XC_MethodHook listHook = new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        if (param.args.length == 2 && param.args[0] != null) {
                            param.args[0] = null;
                            int n = sSearchBlocked.incrementAndGet();
                            if (n <= 5 || n % 100 == 0) {
                                XposedBridge.log(TAG + "feed-block search-adapter list->null #"
                                        + n);
                            }
                        }
                    } catch (Throwable t) {
                        XposedBridge.log(TAG + "feed-block search-adapter err: " + t);
                    }
                }
            };
            XposedBridge.hookAllMethods(adapter, "y1", listHook);   // 16.1.8.305 数据入口
            XposedBridge.hookAllMethods(adapter, "w1", listHook);   // 16.1.7 及以前存档
            XposedBridge.log(TAG + "RecommendFeedBlocker armed on AssSearchActivationAdapter "
                    + "y1/w1 (list entry, 2-arg)");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "RecommendFeedBlocker search-adapter FAILED: " + t);
        }
    }

    /**
     * 【v1.3.0】应用详情页推荐请求入口 AppDetailRecommendViewModel#h 直接吞。
     * h 是 MultiAssemblyDataReq（setRecommendCode）→ BaseViewModel.request 的唯一出口。
     * 参数个数随版本漂移：11 参(16.1.7 及以前) → 12 参(16.1.8.305)——Kotlin 新增了一个
     * 默认参。这里对 11/12 参都拦，跨版本稳定命中真请求入口；静态合成桥 i(...)（多一参
     * this）不在此列，由 h 收口即可。
     * 覆盖调用方：主详情页 D0 尾部 / 介绍 Tab W0() / 半屏 AppDetailRecommendFragment
     * （刷新+加载更多）/ 分发详情子类 / 签到详情 SignDetailActivity。
     * 返回 Job 在各调用方均按可空处理或直接丢弃，setResult(null) 安全。
     *
     * 吞请求后向该 VM 的 d() LiveData 补发 Success(BaseResp{code=0,data=null})：
     * d() 继承自 CommonListViewModel（16.1.8.305 里 VM 自身不再显式声明 d，但实例上仍可
     * 反射调到），所有消费方（介绍页 onSuccess / 推荐页签 / 分发页 / 签到页）都有原生
     * data==null 空态收尾分支，不会残留“正在加载”。
     */
    private static void hookDetailRecommend(final ClassLoader cl) {
        try {
            Class<?> vm = XposedHelpers.findClass(DETAIL_REC_VM, cl);
            XposedBridge.hookAllMethods(vm, "h", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        // h 请求入口：16.1.7 及以前 11 参、16.1.8.305 起 12 参，两版都拦
                        if (param.args.length != 11 && param.args.length != 12) {
                            return;
                        }
                        param.setResult(null);
                        postEmptySuccess(cl, param.thisObject);
                        int n = sDetailRecBlocked.incrementAndGet();
                        if (n <= 10 || n % 50 == 0) {
                            XposedBridge.log(TAG + "detail-recommend request killed #"
                                    + n);
                        }
                    } catch (Throwable t2) {
                        XposedBridge.log(TAG + "detail-rec err: " + t2);
                    }
                }
            });
            XposedBridge.log(TAG + "RecommendFeedBlocker armed on "
                    + "AppDetailRecommendViewModel#h");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "RecommendFeedBlocker detail-rec-vm FAILED: " + t);
        }
    }

    /** 构造 Success(BaseResp{errorCode=0, data=null}) 并 postValue 进 VM 的 d() LiveData */
    private static void postEmptySuccess(final ClassLoader cl, final Object vm) {
        try {
            Object liveData = XposedHelpers.callMethod(vm, "d");
            Class<?> respCls = XposedHelpers.findClass(
                    "com.hihonor.appmarket.network.base.BaseResp", cl);
            Object resp = respCls.newInstance();
            XposedHelpers.callMethod(resp, "setErrorCode", Integer.valueOf(0));
            Class<?> successCls = XposedHelpers.findClass(
                    "com.hihonor.appmarket.network.base.BaseResult$Success", cl);
            Object success = successCls.getConstructor(Object.class).newInstance(resp);
            XposedHelpers.callMethod(liveData, "postValue", success);
        } catch (Throwable t) {
            XposedBridge.log(TAG + "detail empty-success post err: " + t);
        }
    }

    /**
     * 【v1.3.0】详情页 inner-detail 预取桥：AppDetailsActivity$requestMiddle$1$1
     * 的 invokeSuspend 整体吞掉，阻止它把预取 resp setValue 进推荐 LiveData。
     * 只影响推荐进料；InnerDetailPreloadManager 本身不动，详情页加载速度不受影响。
     * 协程以 null 结果完成，启动方不消费结果值，安全。
     * 同样补发空成功事件（该 AB 路径下 h() 不会被调用，介绍页/推荐页签都等这个事件收尾）。
     */
    private static void hookDetailMiddleBridge(final ClassLoader cl) {
        try {
            Class<?> bridge = XposedHelpers.findClass(DETAIL_MIDDLE_BRIDGE, cl);
            XposedBridge.hookAllMethods(bridge, "invokeSuspend", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        param.setResult(null);
                        Object activity = XposedHelpers.getObjectField(
                                param.thisObject, "this$0");
                        Object vm = XposedHelpers.callMethod(activity,
                                "getMAppDetailRecommendViewModel");
                        if (vm != null) {
                            postEmptySuccess(cl, vm);
                        }
                        int n = sDetailRecBlocked.incrementAndGet();
                        if (n <= 10 || n % 50 == 0) {
                            XposedBridge.log(TAG + "detail-recommend middle-bridge killed #"
                                    + n);
                        }
                    } catch (Throwable t2) {
                        XposedBridge.log(TAG + "detail-bridge err: " + t2);
                    }
                }
            });
            XposedBridge.log(TAG + "RecommendFeedBlocker armed on "
                    + "AppDetailsActivity$requestMiddle$1$1");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "RecommendFeedBlocker detail-middle FAILED: " + t);
        }
    }

    /** 更新页「发现更多精彩内容」入口卡：独立单条目适配器，条目数直接归零 */
    private static void hookUpdateDiscover(final ClassLoader cl) {
        try {
            Class<?> dm = XposedHelpers.findClass(UPDATE_DISCOVER, cl);
            XposedBridge.hookAllMethods(dm, "getItemCount", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        if (!Integer.valueOf(0).equals(param.getResult())) {
                            param.setResult(Integer.valueOf(0));
                            int n = sDiscoverHit.incrementAndGet();
                            if (n <= 5 || n % 100 == 0) {
                                XposedBridge.log(TAG + "feed-block update discover-more card #"
                                        + n);
                            }
                        }
                    } catch (Throwable ignored) { }
                }
            });
            XposedBridge.log(TAG + "RecommendFeedBlocker armed on DiscoverMoreAdapter");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "RecommendFeedBlocker update-discover FAILED: " + t);
        }
    }

    /** 更新页：桥接四参 args[1]=null / 私有三参 args[0]=null */
    private static void hookUpdate(final ClassLoader cl) {
        try {
            Class<?> clazz = XposedHelpers.findClass(UPDATE_ACT, cl);
            XposedBridge.hookAllMethods(clazz, "access$setRecommendResponseData",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            try {
                                if (param.args.length == 4 && param.args[1] != null) {
                                    param.args[1] = null;
                                    int n = sUpdBridgeHit.incrementAndGet();
                                    if (n <= 5 || n % 100 == 0) {
                                        XposedBridge.log(TAG
                                                + "feed-block update bridge resp->null #"
                                                + n);
                                    }
                                }
                            } catch (Throwable t) {
                                XposedBridge.log(TAG + "feed-block update bridge err: " + t);
                            }
                        }
                    });
            XposedBridge.hookAllMethods(clazz, "setRecommendResponseData",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            try {
                                if (param.args.length == 3 && param.args[0] != null) {
                                    param.args[0] = null;
                                    int n = sUpdRespHit.incrementAndGet();
                                    if (n <= 5 || n % 100 == 0) {
                                        XposedBridge.log(TAG + "feed-block update resp->null #"
                                                + n);
                                    }
                                }
                            } catch (Throwable t) {
                                XposedBridge.log(TAG + "feed-block update err: " + t);
                            }
                        }
                    });
            XposedBridge.log(TAG + "RecommendFeedBlocker armed on UpdateManagerActivity");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "RecommendFeedBlocker update FAILED: " + t);
        }
    }
}
