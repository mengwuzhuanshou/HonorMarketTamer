package android.os;

import java.io.File;
import java.io.FileNotFoundException;

/** 桩：AOSP 原样声明。ConfigProvider.openFile 用它返回 SP 的只读 fd。
 *  MODE_READ_ONLY=0（AOSP 原值）。 */
public final class ParcelFileDescriptor {
    public static final int MODE_READ_ONLY  = 0;
    public static final int MODE_WRITE_ONLY = 1;

    public static ParcelFileDescriptor open(File file, int mode) throws FileNotFoundException {
        return null;
    }

    private ParcelFileDescriptor() {}
}
