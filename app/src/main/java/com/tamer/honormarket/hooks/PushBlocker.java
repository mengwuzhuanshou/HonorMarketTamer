package com.tamer.honormarket.hooks;

import de.robv.android.xposed.XC_MethodHook.MethodHookParam;
import de.robv.android.xposed.XposedBridge;

/**
 * 推送/通知屏蔽（弹性签名版，不再使用 "int"/"boolean" 等字符串参数类型）。
 */
public final class PushBlocker {

    public static void hook(ClassLoader cl, final com.tamer.honormarket.TamerConfig cfg) {
        boolean push = cfg.get(com.tamer.honormarket.TamerConfig.KEY_PUSH,
                com.tamer.honormarket.TamerConfig.defaultValueOf(
                        com.tamer.honormarket.TamerConfig.KEY_PUSH));

        if (push) {
            // P1：推送到达入口 c(单参)—— 类型感知：放行更新族(type 以 1 开头)，拦其它
            HookUtil.tryHookAll(cl,
                    "com.hihonor.appmarket.base.support.push.honor.AppMarketPushMessageService",
                    "c", new de.robv.android.xposed.XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            if (param.args == null || param.args.length != 1) return;
                            String[] info = probePushType(param.args[0]);
                            String type = info[0], action = info[1];
                            if (type != null && type.startsWith("1")) {
                                XposedBridge.log("[HonorMarketTamer] push allowed(update-family)"
                                        + " type=" + type + " action=" + action);
                                return; // 应用更新/静默更新族 → 放行
                            }
                            XposedBridge.log("[HonorMarketTamer] suppressed push"
                                    + " type=" + type + " action=" + action);
                            param.setResult(null);
                        }
                    });

            // N1：推送→系统通知展示总入口（3 参静态方法）——按服务类型串放行更新族
            HookUtil.tryHookFlex(cl,
                    "com.hihonor.appmarket.base.support.push.notification.a",
                    "a", 3, new HookUtil.FlexCallback() {
                        @Override
                        public void fire(MethodHookParam param) {
                            Object p1 = param.args[1];
                            if (p1 == null || !p1.getClass().getName().endsWith(
                                    "ActivityPushNotificationService")) return;
                            String type = null;
                            try {
                                Object d = de.robv.android.xposed.XposedHelpers.callMethod(p1, "D");
                                if (d != null) type = String.valueOf(d);
                            } catch (Throwable ignored) {}
                            if (type != null && type.startsWith("1")) {
                                XposedBridge.log("[HonorMarketTamer] notify allowed(update-family) type=" + type);
                                return;
                            }
                            XposedBridge.log("[HonorMarketTamer] block push notify(N1) type=" + type);
                            param.setResult(null);
                        }
                    });

            // N5：顶部横幅
            HookUtil.tryHookFlex(cl,
                    "com.hihonor.appmarket.business.notification.topnotify.TopNotifyManager",
                    "trigger", 1, new HookUtil.FlexCallback() {
                        @Override
                        public void fire(MethodHookParam param) {
                            Object a0 = param.args[0];
                            if (a0 != null && a0.getClass().getName().endsWith(".EVENT")) {
                                XposedBridge.log("[HonorMarketTamer] block top-notify(trigger)");
                                param.setResult(null);
                            }
                        }
                    });
            HookUtil.tryHookFlex(cl,
                    "com.hihonor.appmarket.business.notification.topnotify.TopNotifyManager",
                    "z", 1, new HookUtil.FlexCallback() {
                        @Override
                        public void fire(MethodHookParam param) {
                            Object a0 = param.args[0];
                            if (a0 != null && a0.getClass().getName().endsWith("TopNotifyPushData")) {
                                XposedBridge.log("[HonorMarketTamer] block top-notify(z)");
                                param.setResult(null);
                            }
                        }
                    });
        }

        // 消息中心红点/未读（随 KEY_MSG_CENTER）
        if (cfg.get(com.tamer.honormarket.TamerConfig.KEY_MSG_CENTER,
                    com.tamer.honormarket.TamerConfig.defaultValueOf(
                            com.tamer.honormarket.TamerConfig.KEY_MSG_CENTER))) {
            HookUtil.tryHookFlex(cl, "tp5", "e", 1, HookUtilNull.get());
            HookUtil.tryHookFlex(cl, "tp5", "g", 1, HookUtilNull.get());
            HookUtil.tryHookFlex(cl, "rk4", "l", 1, HookUtilNull.get());
            HookUtil.tryHookFlex(cl, "com.hihonor.appmarket.msgcenterview.MsgGroupViewModel",
                    "l", 1, HookUtilNull.get());
        }

        // 更新提醒通知（默认保留）
        if (cfg.get(com.tamer.honormarket.TamerConfig.KEY_UPDATE_NOTIFY,
                    com.tamer.honormarket.TamerConfig.defaultValueOf(
                            com.tamer.honormarket.TamerConfig.KEY_UPDATE_NOTIFY))) {
            HookUtil.tryHookFlex(cl,
                    "com.hihonor.appmarket.slientcheck.checkupdate.au.UpdateNotifyHandler",
                    "g", 2, HookUtilNull.get());
        }

        // 全部系统通知总闸（默认保留；4 参 v 方法）
        if (cfg.get(com.tamer.honormarket.TamerConfig.KEY_ALL_NOTIFY,
                    com.tamer.honormarket.TamerConfig.defaultValueOf(
                            com.tamer.honormarket.TamerConfig.KEY_ALL_NOTIFY))) {
            HookUtil.tryHookFlex(cl,
                    "com.hihonor.appmarket.business.notification.controller.NotificationControllerImpl",
                    "v", 4, HookUtilNull.get());
        }
    }

    /** 反射探测消息对象的 pushNotifyType / action；返回 [type, action]，缺失为 null */
    private static String[] probePushType(Object msg) {
        String type = null, action = null;
        if (msg == null) return new String[]{null, null};
        String[] typeM = {"getPushNotifyType", "pushNotifyType"};
        for (String m : typeM) {
            try {
                Object r = de.robv.android.xposed.XposedHelpers.callMethod(msg, m);
                if (r != null) { type = String.valueOf(r); break; }
            } catch (Throwable ignored) {}
        }
        try {
            Object r = de.robv.android.xposed.XposedHelpers.callMethod(msg, "getAction");
            if (r != null) action = String.valueOf(r);
        } catch (Throwable ignored) {}
        if (type == null) {
            // 参数可能是原始 JSON 字符串：用纯字符串查找提取字段
            String s = String.valueOf(msg);
            type = extractJsonString(s, "pushNotifyType");
            if (type == null) type = extractJsonString(s, "PushNotifyType");
            if (action == null) action = extractJsonString(s, "action");
        }
        return new String[]{type, action};
    }

    /** 从 JSON 文本里取字符串字段值（无转义处理，够用于日志与分类） */
    private static String extractJsonString(String json, String key) {
        if (json == null) return null;
        String needle = "\"" + key + "\":";
        int i = json.indexOf(needle);
        if (i < 0) return null;
        int start = json.indexOf('"', i + needle.length());
        if (start < 0 || start > i + needle.length() + 2) {
            start = json.indexOf('"', i + needle.length());
            if (start < 0) return null;
        }
        int end = json.indexOf('"', start + 1);
        if (end < 0) return null;
        return json.substring(start + 1, end);
    }

    /** 共用：单参方法直接返回 null 的小回调集合 */
    static final class HookUtilNull {
        private static volatile HookUtil.FlexCallback instance;
        static HookUtil.FlexCallback get() {
            if (instance == null) {
                synchronized (HookUtilNull.class) {
                    if (instance == null) {
                        instance = new HookUtil.FlexCallback() {
                            @Override
                            public void fire(MethodHookParam param) {
                                param.setResult(null);
                            }
                        };
                    }
                }
            }
            return instance;
        }
    }

    private PushBlocker() {}
}
