package com.tamer.honormarket.hooks;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodHook.MethodHookParam;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/** 容错 Hook 工具：精确签名 + 弹性签名（按参数个数过滤）两种模式 */
public final class HookUtil {

    /** 弹性回调：由调用方在回调内自行做类型判断 */
    public interface FlexCallback {
        void fire(MethodHookParam param) throws Throwable;
    }

    /** 按类名+方法名+精确参数类型 hook（类型名必须是真实存在的类/原始类型描述错误会失败） */
    public static void tryHook(ClassLoader cl, String className, String methodName,
                               Object[] paramTypes, XC_MethodHook callback) {
        try {
            Class<?> clazz = XposedHelpers.findClass(className, cl);
            Object[] args = new Object[paramTypes.length + 1];
            System.arraycopy(paramTypes, 0, args, 0, paramTypes.length);
            args[paramTypes.length] = callback;
            XposedHelpers.findAndHookMethod(clazz, methodName, args);
            XposedBridge.log("[HonorMarketTamer] hooked " + className + "#" + methodName);
        } catch (Throwable t) {
            XposedBridge.log("[HonorMarketTamer] hook FAILED " + className + "#" + methodName + " : " + t);
        }
    }

    /**
     * 弹性 hook：hook 该类同名方法的全部重载，仅在参数个数匹配时执行回调。
     * 不依赖任何参数类型名，规避混淆签名/反编译失败带来的漂移。
     */
    public static void tryHookFlex(ClassLoader cl, String className, String methodName,
                                   final int argc, final FlexCallback cb) {
        try {
            Class<?> clazz = XposedHelpers.findClass(className, cl);
            XC_MethodHook wrapper = new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        if (param.args != null && param.args.length == argc) {
                            cb.fire(param);
                        }
                    } catch (Throwable t) {
                        XposedBridge.log("[HonorMarketTamer] flex callback error "
                                + className + "#" + methodName + ": " + t);
                    }
                }
            };
            XposedBridge.hookAllMethods(clazz, methodName, wrapper);
            XposedBridge.log("[HonorMarketTamer] hookedAll(" + methodName
                    + ", argc=" + argc + ") on " + className);
        } catch (Throwable t) {
            XposedBridge.log("[HonorMarketTamer] hookAll FAILED " + className + "#" + methodName + " : " + t);
        }
    }

    /** hook 一个类的全部同名重载方法 */
    public static void tryHookAll(ClassLoader cl, String className, String methodName,
                                  XC_MethodHook callback) {
        try {
            Class<?> clazz = XposedHelpers.findClass(className, cl);
            XposedBridge.hookAllMethods(clazz, methodName, callback);
            XposedBridge.log("[HonorMarketTamer] hookedAll " + className + "#" + methodName);
        } catch (Throwable t) {
            XposedBridge.log("[HonorMarketTamer] hookAll FAILED " + className + "#" + methodName + " : " + t);
        }
    }

    /** before-hook 中把方法替换为固定返回值 */
    public static XC_MethodHook returnValue(final Object value) {
        return new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                param.setResult(value);
            }
        };
    }

    private HookUtil() {}
}
