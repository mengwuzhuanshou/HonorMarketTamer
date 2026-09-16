package android.content;
// 编译桩：仅用于编译期签名解析，不 dex 进 APK。
// 构造符 (String,String)→void 为 AOSP 标准（setComponent 显式 ComponentName 启动用）。
public class ComponentName {
    public ComponentName(String packageName, String className) {}
}
