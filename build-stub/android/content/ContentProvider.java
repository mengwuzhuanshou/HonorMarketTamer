package android.content;

import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.FileNotFoundException;

/**
 * 桩：签名必须与目标机真实 framework 描述符一致（ART 按 name+descriptor 解析）。
 * ConfigProvider 覆写 openFile/query/getType/insert/delete/update，并调用
 * getContext()。此处按 AOSP 原样声明。
 */
public abstract class ContentProvider {
    public boolean onCreate() { return false; }
    public void attachInfo(Context context, ProviderInfo info) { }
    public Context getContext() { return null; }
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        return null;
    }
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        return null;
    }
    public String getType(Uri uri) { return null; }
    public Uri insert(Uri uri, ContentValues values) { return null; }
    public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
    public int update(Uri uri, ContentValues values, String selection,
                      String[] selectionArgs) { return 0; }
}
