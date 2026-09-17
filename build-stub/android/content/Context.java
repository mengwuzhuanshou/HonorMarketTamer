package android.content;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

public class Context {
    public static final int MODE_PRIVATE = 0;
    public SharedPreferences getSharedPreferences(String name, int mode) { return null; }
    public ApplicationInfo getApplicationInfo() { return null; }
    public String getPackageName() { return null; }
    public java.io.File getFilesDir() { return null; }
    public Object getSystemService(String name) { return null; }
    // 真实框架 Context.registerReceiver(BroadcastReceiver, IntentFilter) 返回 Intent（非 void）——
    // 桩若声明成 void，编译期匹配、运行期找不到 void 版本 → NoSuchMethodError（HealthSensorGate
    // 注册亮/灭屏接收器每 5s 刷屏的根因）。对齐 common 共享桩。
    public Intent registerReceiver(android.content.BroadcastReceiver receiver, android.content.IntentFilter filter) { return null; }
    public void startActivity(Intent intent) { }
    public PackageManager getPackageManager() { return null; }
    // Provider 通道（v1.4.4）：对宿主显式授权读配置 Provider（OEM 强制 exported=false 时
    // 唯一通路）。AOSP 原样声明。
    public void grantUriPermission(String toPackage, android.net.Uri uri, int modeFlags) { }
    public ContentResolver getContentResolver() { return null; }
    // HostCtx 捕获宿主 applicationContext（Activity.getApplicationContext()）
    public Context getApplicationContext() { return this; }
}
