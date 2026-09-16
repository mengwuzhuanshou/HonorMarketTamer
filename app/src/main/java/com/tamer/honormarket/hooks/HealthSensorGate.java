package com.tamer.honormarket.hooks;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/**
 * 健康类应用传感器闸门（默认关，health_sensor_gate）。
 *
 * 背景：荣耀运动健康为桌面步数卡片保持 24h 传感器会话（加速度计 + 计步器），
 * 熄屏期间传感器中枢(scp_mboxdev)持续唤醒 AP——实测占熄屏唤醒第一大户
 * （单周期 4058s / 751 次）。而熄屏时卡片不可见，实时数据无人消费。
 *
 * 策略（亮屏放行 / 熄屏全停——已实证微信直连系统计步传感器、不依赖健康 app，
 *  桌面卡片又只在亮屏可见，熄屏会话无存在价值）：
 *  - 拦 SensorManager.registerListener 全部重载：熄屏期间直接拒绝注册
 *    （result=false，请求入 pending 队列），已注册会话在 SCREEN_OFF 全部
 *    unregister——AP 侧投递归零，步数在中枢硬件继续累计，不丢数；
 *  - 亮屏(SCREEN_ON)：pending 里的拒绝请求 + 被注销的会话按注册时原始参数
 *    重放（反射原 Method，Handler 语义不变）；
 *  - 跟踪 unregisterListener，应用自己注销的会话移出管理表，避免复活。
 *
 * 已知取舍：熄屏运动（不亮屏锻炼）的健康数据停采（亮屏恢复后续传，中枢
 * 步数不丢）；需要熄屏锻炼时关掉 health_sensor_gate 即可。
 * 目标包名：health_packages（默认 com.hihonor.health），在目标进程内生效，零跨应用面。
 *
 * 移植自 QQTamer v1.4.29（2026-09-16 迁入本模块，QQTamer 侧已移除）：
 * 逻辑不变，仅换配置类（QQConfig→TamerConfig）与日志通道（HookUtil→XposedBridge）。
 */
public final class HealthSensorGate {

    private static final String TAG = "[HonorMarketTamer] ";

    /** 熄屏拒绝注册的请求缓存上限（防异常应用风暴刷表） */
    private static final int PENDING_CAP = 16;

    private static final Object LOCK = new Object();
    private static final List<Sess> SESS = new ArrayList<Sess>();
    private static final List<Sess> PENDING = new ArrayList<Sess>();
    private static volatile boolean sScreenOn = true;
    private static volatile boolean sReceiverReady;

    private static final class Sess {
        Object manager;            // SensorManager 实例
        final SensorEventListener listener;
        final Sensor sensor;
        Method method;             // 原始注册重载（亮屏重放用）
        Object[] origArgs;         // 原始参数

        Sess(Object mgr, SensorEventListener l, Sensor s, Method m, Object[] a) {
            manager = mgr; listener = l; sensor = s; method = m; origArgs = a;
        }
    }

    public static void install(final ClassLoader cl,
            final com.tamer.honormarket.TamerConfig cfg) {
        XposedBridge.log(TAG + "HealthSensorGate init");
        // 注册钩子先于接收器：Application 未就绪时传感器注册已经开始的场子也能拦到
        try {
            XposedBridge.hookAllMethods(SensorManager.class, "registerListener", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    try {
                        Sensor s = findSensor(param.args);
                        if (s == null || !gatedType(s.getType())) return;
                        if (!sScreenOn) {
                            Object l = param.args.length > 0 ? param.args[0] : null;
                            if (l instanceof SensorEventListener) {
                                addPending((SensorEventListener) l, s, param.thisObject,
                                        (param.method instanceof Method) ? (Method) param.method : null,
                                        param.args.clone());
                            }
                            param.setResult(Boolean.FALSE);
                            XposedBridge.log(TAG + "gate: screen-off, DENIED " + typeName(s.getType()));
                        }
                    } catch (Throwable t) {
                        XposedBridge.log(TAG + "gate reg-hook ex: " + t);
                    }
                }

                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    try {
                        Object res = param.getResult();
                        if (!(res instanceof Boolean) || !((Boolean) res)) return;
                        Sensor s = findSensor(param.args);
                        if (s == null || !gatedType(s.getType())) return;
                        if (!(param.thisObject instanceof SensorManager)) return;
                        Object l = param.args.length > 0 ? param.args[0] : null;
                        if (!(l instanceof SensorEventListener)) return;
                        synchronized (LOCK) {
                            Sess dup = find((SensorManager) param.thisObject,
                                    (SensorEventListener) l, s);
                            if (dup != null) {
                                dup.method = (param.method instanceof Method)
                                        ? (Method) param.method : dup.method;
                                dup.origArgs = param.args.clone();
                            } else {
                                SESS.add(new Sess(param.thisObject,
                                        (SensorEventListener) l, s,
                                        (param.method instanceof Method) ? (Method) param.method : null,
                                        param.args.clone()));
                            }
                        }
                    } catch (Throwable t) {
                        XposedBridge.log(TAG + "gate track ex: " + t);
                    }
                }
            });

            XposedBridge.hookAllMethods(SensorManager.class, "unregisterListener", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    try {
                        if (param.args.length >= 2
                                && param.args[0] instanceof SensorEventListener
                                && param.args[1] instanceof Sensor) {
                            forget((SensorEventListener) param.args[0], (Sensor) param.args[1]);
                        }
                    } catch (Throwable ignored) {}
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(TAG + "HealthSensorGate hook install failed: " + t);
            return;
        }
        ensureReceiver();
    }

    // ---- 亮/灭屏切换：熄屏降频已注册会话，亮屏按原参数重放 ----

    private static final BroadcastReceiver SCREEN_RX = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            try {
                String a = intent.getAction();
                boolean on = Intent.ACTION_SCREEN_ON.equals(a);
                if (Intent.ACTION_SCREEN_OFF.equals(a)) on = false;
                else if (!on) return;
                boolean prev = sScreenOn;
                sScreenOn = on;
                if (prev == on) return;
                apply(on);
            } catch (Throwable t) {
                XposedBridge.log(TAG + "gate screen-rx ex: " + t);
            }
        }
    };

    private static void apply(final boolean screenOn) {
        int touched = 0;
        if (!screenOn) {
            List<Sess> snapshot;
            synchronized (LOCK) { snapshot = new ArrayList<Sess>(SESS); }
            for (Sess p : snapshot) {
                try {
                    ((SensorManager) p.manager).unregisterListener(p.listener, p.sensor);
                    addPending(p.listener, p.sensor, p.manager, p.method, p.origArgs);
                    synchronized (LOCK) { SESS.remove(p); }
                    touched++;
                } catch (Throwable t) {
                    XposedBridge.log(TAG + "gate off ex: " + t);
                }
            }
        } else {
            List<Sess> snapshot;
            synchronized (LOCK) { snapshot = new ArrayList<Sess>(PENDING); PENDING.clear(); }
            for (final Sess p : snapshot) {
                try {
                    if (p.method != null) {
                        // 原参数重放：透传本钩子（此刻 screenOn=true 放行）
                        p.method.invoke(p.manager, p.origArgs);
                    } else if (p.manager instanceof SensorManager) {
                        ((SensorManager) p.manager).registerListener(
                                p.listener, p.sensor, SensorManager.SENSOR_DELAY_NORMAL);
                    }
                    touched++;
                } catch (Throwable t) {
                    XposedBridge.log(TAG + "gate on ex: " + t);
                }
            }
        }
        XposedBridge.log(TAG + "gate: screen " + (screenOn ? "ON, re-registered" : "OFF, denied/stopped")
                + " sessions=" + touched);
    }

    // ---- Application 就绪前轮询注册亮/灭屏接收器 ----

    private static void ensureReceiver() {
        if (sReceiverReady) return;
        final android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
        h.postDelayed(new Runnable() {
            @Override public void run() {
                if (sReceiverReady) return;
                try {
                    // 不走 AndroidAppHelper：LSPosed v2 对 Xposed API 类改名注入，
                    // 在部分宿主（实测运动健康）解析到不存在的混淆名 → CNFE。
                    // 反射 ActivityThread.currentApplication 拿当前 Application。
                    android.content.Context app;
                    try {
                        Class<?> at = Class.forName("android.app.ActivityThread");
                        app = (android.content.Context) at
                                .getMethod("currentApplication").invoke(null);
                    } catch (Throwable t) {
                        app = null;
                    }
                    if (app == null) { h.postDelayed(this, 2000); return; }
                    IntentFilter f = new IntentFilter();
                    f.addAction(Intent.ACTION_SCREEN_OFF);
                    f.addAction(Intent.ACTION_SCREEN_ON);
                    app.registerReceiver(SCREEN_RX, f);
                    sReceiverReady = true;
                    // 接收器就绪时若已处于熄屏，补一次降频（开机即熄屏的场景）
                    if (!interactive(app)) {
                        sScreenOn = false;
                        apply(false);
                    }
                    XposedBridge.log(TAG + "gate: receiver ready");
                } catch (Throwable t) {
                    XposedBridge.log(TAG + "gate receiver ex: " + t);
                    h.postDelayed(this, 5000);
                }
            }
        }, 1500);
    }

    // ---- 工具 ----

    private static Sensor findSensor(Object[] args) {
        if (args == null) return null;
        for (int i = 0; i < args.length; i++) {
            if (args[i] instanceof Sensor) return (Sensor) args[i];
        }
        return null;
    }

    /** 熄屏拒绝的注册请求入 pending（按 listener+sensor 去重，封顶防刷） */
    private static void addPending(Object listener, Sensor sensor,
                                   Object manager, Method method, Object[] args) {
        synchronized (LOCK) {
            for (Sess p : PENDING) {
                if (p.listener == listener && p.sensor == sensor) {
                    p.manager = manager;
                    p.method = method;
                    p.origArgs = args;
                    return;
                }
            }
            if (PENDING.size() >= PENDING_CAP) PENDING.remove(0);
            PENDING.add(new Sess(manager, (SensorEventListener) listener, sensor, method, args));
        }
    }

    private static boolean gatedType(int type) {
        return type == Sensor.TYPE_ACCELEROMETER
                || type == Sensor.TYPE_STEP_COUNTER
                || type == Sensor.TYPE_STEP_DETECTOR;
    }

    private static String typeName(int t) {
        if (t == Sensor.TYPE_ACCELEROMETER) return "accel";
        if (t == Sensor.TYPE_STEP_COUNTER) return "step_counter";
        if (t == Sensor.TYPE_STEP_DETECTOR) return "step_detector";
        return "type" + t;
    }

    private static Sess find(SensorManager mgr, SensorEventListener l, Sensor s) {
        for (Sess p : SESS) {
            if (p.manager == mgr && p.listener == l && p.sensor == s) return p;
        }
        return null;
    }

    private static void forget(SensorEventListener l, Sensor s) {
        synchronized (LOCK) {
            for (int i = SESS.size() - 1; i >= 0; i--) {
                Sess p = SESS.get(i);
                if (p.listener == l && p.sensor == s) SESS.remove(i);
            }
        }
    }

    private static boolean interactive(android.content.Context ctx) {
        try {
            Object pm = ctx.getSystemService("power");
            return (Boolean) pm.getClass().getMethod("isInteractive").invoke(pm);
        } catch (Throwable t) {
            return true;
        }
    }
}
