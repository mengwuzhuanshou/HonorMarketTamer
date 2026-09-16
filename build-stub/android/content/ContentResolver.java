package android.content;

import android.net.Uri;

import java.io.FileNotFoundException;
import java.io.InputStream;

/** 桩：AOSP 原样声明。TamerConfig.refreshFromProvider 经
 *  openInputStream(content://...) 跨应用 binder IPC 拉模块权威 SP。
 *  对只实现 openFile（返回 PFD）的 Provider，框架把 PFD 包成 InputStream。 */
public class ContentResolver {
    public InputStream openInputStream(Uri uri) throws FileNotFoundException {
        return null;
    }
}
