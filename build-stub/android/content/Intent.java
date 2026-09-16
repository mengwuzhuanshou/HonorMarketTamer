package android.content;
public class Intent {
    public static final String ACTION_SCREEN_ON  = "android.intent.action.SCREEN_ON";
    public static final String ACTION_SCREEN_OFF = "android.intent.action.SCREEN_OFF";
    public static final String ACTION_MAIN       = "android.intent.action.MAIN";
    public static final String CATEGORY_LAUNCHER = "android.intent.category.LAUNCHER";
    // flag 值为运行时值（系统按位解释），必须用 AOSP 标准值。0x04000000(CLEAR_TOP)/
    // 0x02000000(SINGLE_TOP) 组合经实机 am start -f 0x16000000 warm 投递实测有效。
    // 注意：兄弟项目 BiliTamer 桩把 CLEAR_TOP 写成 0x08000000（那是 AOSP 的
    // CLEAR_WHEN_TASK_RESET），本项目不沿用其错误值。
    public static final int FLAG_ACTIVITY_NEW_TASK   = 0x10000000;
    public static final int FLAG_ACTIVITY_CLEAR_TOP  = 0x04000000;
    public static final int FLAG_ACTIVITY_SINGLE_TOP = 0x02000000;
    // 把已存在任务提到前台而不重建（0x00002000）：拉市场投递 conf 后立刻用它抢回
    // 设置模块前台，市场 Splash 仅在其自身 task 内 onCreate 投递、不占前台。
    public static final int FLAG_ACTIVITY_REORDER_TO_FRONT = 0x00002000;
    // URI 授权标志（AOSP 原值）：Provider 通道 grantUriPermission 用。
    // 注意 PERSISTABLE 是 0x00000004（不是 0x800）——本机 grantUriPermission
    // 只允许 0xc3 内的标志，0x800 会触发 IllegalArgumentException 使授权失败。
    public static final int FLAG_GRANT_READ_URI_PERMISSION        = 0x00000001;
    public static final int FLAG_GRANT_PERSISTABLE_URI_PERMISSION = 0x00000004;
    public Intent() { }
    public Intent(String action) { }
    public String getAction() { return null; }
    public Intent setComponent(ComponentName component) { return this; }
    // 桩签名必须与【目标机真实 framework】描述符严格一致——ART 按 name+descriptor
    // 精确解析，返回类型错一个字母即 NoSuchMethodError。本 Honor 机 framework 对
    // android.content.Intent 打过补丁：链式 mutator 返回 Intent 以便链式调用，
    // 而 getter 与 startActivity 保持 AOSP 原样。描述符来自市场 app 自身 dex 的
    // invoke 指令（dexdump 反汇编实证，非猜测）：
    //   addFlags(I)Landroid/content/Intent;
    //   putExtra(String,String)Landroid/content/Intent;
    //   putExtra(String,long)Landroid/content/Intent;  (本项目 gen 代次即走此 long 重载)
    //   addCategory(String)Landroid/content/Intent;
    //   hasExtra(String)Z / getStringExtra(String)Ljava/lang/String; /
    //   getLongExtra(String,long)J  —— 均为 AOSP 原样
    public Intent addFlags(int flags) { return this; }
    public Intent addCategory(String name) { return this; }
    public Intent putExtra(String name, String value) { return this; }
    public Intent putExtra(String name, long value) { return this; }
    public boolean hasExtra(String name) { return false; }
    public String getStringExtra(String name) { return null; }
    public long getLongExtra(String name, long defaultValue) { return defaultValue; }
}
