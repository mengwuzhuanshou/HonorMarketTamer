package com.tamer.honormarket;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * 无 root 配置通道（最小权限，照抄 LineTamer v1.6.1）：Hook 端（市场进程）
 * 经标准 ContentResolver IPC 读本 Provider，openFile 返回设置页权威 SP
 * （tamer_config.xml）的只读文件描述符。
 *
 * 为什么需要它：LSPosed v2 把模块数据重定向到 apexdata（660、路径重定向
 * 非 bind mount），classic XSharedPreferences 在宿主进程里打不开物理路径；
 * 而跨应用拿 Provider 的 fd 是标准 Android IPC（binder），不依赖任何 LSPosed
 * 特性，不给模块 root 也完整可用。SP 即权威配置（含 conf_gen 代次），Hook 端
 * 解析后代次入协议，root 兜底副本陈旧值自动淘汰。
 *
 * 关键收益：宿主进程自行启动时即可拉到最新配置，设置页拨开关【不必拉起市场】。
 */
public class ConfigProvider extends ContentProvider {

    /** Hook 端统一用这个 URI 拉配置。开放只读；内容仅开关布尔值，无用户数据 */
    public static final String AUTHORITY = TamerConfig.MODULE_PKG + ".config";
    public static final String URI = "content://" + AUTHORITY + "/prefs";

    @Override
    public boolean onCreate() {
        ensureGrant();
        return true;
    }

    /**
     * 部分 OEM（实测 Honor MagicOS/Android 14+）把三方 Provider 的 exported
     * 强制视为 false，跨应用访问一律 Permission Denial。绕开办法：本应用
     * 对目标宿主显式 grantUriPermission（走 AMS 的 authority-grant 分支，
     * 不看 exported）。先试 READ|PERSISTABLE（授权写 urigrants.xml 可跨重启）；
     * 部分 OEM（本机 Honor MagicOS 实测允许集仅 0xc3，不含 PERSISTABLE=0x4）
     * 会抛 IllegalArgumentException，退回仅 READ。非持久无所谓——每次 Provider
     * 起来 + 设置页每次打开都重授一次。
     */
    public static void ensureGrant(android.content.Context ctx) {
        int full = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                | android.content.Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION;
        try {
            ctx.grantUriPermission(TamerConfig.TARGET_PKG, Uri.parse(URI), full);
        } catch (Throwable t) {
            // 允许集不含 PERSISTABLE：退回仅 READ（0x1 在标准/OEM 允许集内）
            try {
                ctx.grantUriPermission(TamerConfig.TARGET_PKG, Uri.parse(URI),
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
                android.util.Log.i("HonorMarketTamer",
                        "provider grant: READ-only (PERSISTABLE rejected: " + t + ")");
            } catch (Throwable t2) {
                android.util.Log.i("HonorMarketTamer", "provider grant failed: " + t2);
            }
        }
    }

    private void ensureGrant() {
        try {
            ensureGrant(getContext());
        } catch (Throwable ignored) {}
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        // 只读 Provider：任何写模式一律拒绝
        if (mode != null && mode.indexOf('w') >= 0) {
            throw new FileNotFoundException("read-only provider");
        }
        // 首选：问框架本 SP 的真实物理路径。LSPosed v2 把模块 SP 重定向到
        // apexdata（getFilesDir() 仍报常规目录）。两条取路：① interface
        // getFilePath()（stock/较新框架有）；② 反射 SharedPreferencesImpl.mFile
        // （OEM 框架该接口无此方法→NoSuchMethodError，改读私有字段）。本 Provider
        // 运行于模块进程，SP 属主即本 uid，可读。
        File primary = resolveSpFile();
        // 兜底候选：apexdata prefs/<pkg>/ 布局 + getFilesDir() 父目录两式
        File filesDir = getContext().getFilesDir();
        File dataRoot = filesDir.getParentFile();
        File apexPrefs = new File("/data/misc/apexdata",
                TamerConfig.MODULE_PKG + "/" + TamerConfig.PREFS_NAME + ".xml");
        File[] candidates = new File[] {
                primary,
                new File(new File(dataRoot, "shared_prefs"),
                        TamerConfig.PREFS_NAME + ".xml"),
                new File(dataRoot, TamerConfig.PREFS_NAME + ".xml"),
                apexPrefs,
        };
        for (int i = 0; i < candidates.length; i++) {
            File c = candidates[i];
            if (c != null && c.isFile()) {
                return ParcelFileDescriptor.open(c, ParcelFileDescriptor.MODE_READ_ONLY);
            }
        }
        throw new FileNotFoundException("prefs xml not found (getFilesDir="
                + (filesDir == null ? "null" : filesDir.getAbsolutePath()) + ")");
    }

    /** 解析本模块 SP 文件真实物理路径：① interface getFilePath()（stock 有）；
     *  ② 反射 SharedPreferencesImpl 私有 mFile 字段（OEM 框架无 interface 方法）。
     *  都失败返回 null（由 openFile 的候选列表兜底）。 */
    private File resolveSpFile() {
        try {
            File f = getContext().getSharedPreferences(
                    TamerConfig.PREFS_NAME, android.content.Context.MODE_PRIVATE)
                    .getFilePath();
            if (f != null && f.isFile()) return f;
        } catch (Throwable ignored) { }
        try {
            android.content.SharedPreferences sp = getContext().getSharedPreferences(
                    TamerConfig.PREFS_NAME, android.content.Context.MODE_PRIVATE);
            java.lang.reflect.Field mf = sp.getClass().getDeclaredField("mFile");
            mf.setAccessible(true);
            Object o = mf.get(sp);
            if (o instanceof File) {
                File f = (File) o;
                if (f.isFile()) return f;
            }
        } catch (Throwable ignored) { }
        return null;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        return null; // 配置只经 openFile 提供
    }

    @Override
    public String getType(Uri uri) {
        return "text/xml";
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection,
                      String[] selectionArgs) {
        return 0;
    }
}
