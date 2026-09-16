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
    public void registerReceiver(android.content.BroadcastReceiver receiver, android.content.IntentFilter filter) { }
    public void startActivity(Intent intent) { }
    public PackageManager getPackageManager() { return null; }
    // Provider 通道（v1.4.4）：对宿主显式授权读配置 Provider（OEM 强制 exported=false 时
    // 唯一通路）。AOSP 原样声明。
    public void grantUriPermission(String toPackage, android.net.Uri uri, int modeFlags) { }
    public ContentResolver getContentResolver() { return null; }
    // HostCtx 捕获宿主 applicationContext（Activity.getApplicationContext()）
    public Context getApplicationContext() { return this; }
}
