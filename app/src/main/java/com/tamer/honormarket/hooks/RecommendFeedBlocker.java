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
 *  - 请求源头 MarketManageViewModel#o(...)（realRequestRecommend R232 唯一出口）直接吞掉
 *    → 零流量、无响应循环、无加载闪烁；
 *  - 缓存路径走 V1：置空 resp 后补调 M1(null,null,true,false,false) 收尾加载占位；
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
            hookMineReq(cl);
            hookMine(cl);
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

    /** 搜索发现页兜底：拦渲染适配器唯一数据入口 w1(List,boolean)，null 安全 */
    private static void hookSearchAdapter(final ClassLoader cl) {
        try {
            Class<?> adapter = XposedHelpers.findClass(SEARCH_ADAPTER, cl);
            XposedBridge.hookAllMethods(adapter, "w1", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        if (param.args.length == 2 && param.args[0] != null) {
                            param.args[0] = null;
                            int n = sSearchBlocked.incrementAndGet();
                            if (n <= 5 || n % 100 == 0) {
                                XposedBridge.log(TAG + "feed-block search w1 #" + n);
                            }
                        }
                    } catch (Throwable t) {
                        XposedBridge.log(TAG + "feed-block search-w1 err: " + t);
                    }
                }
            });
            XposedBridge.log(TAG + "RecommendFeedBlocker armed on AssSearchActivationAdapter#w1");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "RecommendFeedBlocker search-adapter FAILED: " + t);
        }
    }

    /**
     * 【v1.3.0】应用详情页推荐请求入口 AppDetailRecommendViewModel#h(11参) 直接吞。
     * 覆盖调用方：主详情页 D0 尾部 / 介绍 Tab W0() / 半屏 AppDetailRecommendFragment
     * （刷新+加载更多）/ 分发详情子类 / 签到详情 SignDetailActivity。
     * 返回 Job 在各调用方均按可空处理或直接丢弃，setResult(null) 安全。
     *
     * 吞请求后向该 VM 的 d() LiveData 补发 Success(BaseResp{code=0,data=null})：
     * 所有消费方（介绍页 onSuccess / 推荐页签 U0 / 分发页 / 签到页）都有原生的
     * data==null 空态收尾分支（finishRefresh/空视图/报告），不会残留“正在加载”。
     */
    private static void hookDetailRecommend(final ClassLoader cl) {
        try {
            Class<?> vm = XposedHelpers.findClass(DETAIL_REC_VM, cl);
            XposedBridge.hookAllMethods(vm, "h", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        if (param.args.length != 11) {
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
