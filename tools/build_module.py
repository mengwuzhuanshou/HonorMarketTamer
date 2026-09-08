# -*- coding: utf-8 -*-
"""One-shot builder: compiles the LSPosed module WITHOUT Android SDK.

Pipeline:
  1. javac (JDK21, --release 8) compile-time stubs -> build/stub_classes
  2. javac module sources against stubs            -> build/app_classes
  3. dx (dalvik-dx from jadx dist)                 -> build/classes.dex
  4. axml_writer encodes AndroidManifest.xml       -> build/AndroidManifest.xml
  5. zip manifest + dex + assets/xposed_init       -> build/unsigned.apk
  6. apksig sign (v1+v2+v3, keystore from env/signing.local) -> dist/HonorMarketTamer-<ver>.apk
"""
import os
import subprocess
import sys
import zipfile

# 本地工具链路径。默认布局是「仓库同级 tools\」；其他人构建时用环境变量覆盖
# （工具与签名密钥不入库，需自备）：
#   HMT_ROOT      工具根目录（含 jdk21/、jadx147/lib/）
#   HMT_JDK       JDK 根目录（含 bin/javac.exe）
#   HMT_DX_JAR    dalvik-dx jar
#   HMT_APKSIG_JAR  apksig jar
#   HMT_KEYSTORE / HMT_KS_PASS / HMT_KS_ALIAS  签名密钥三件套（或 tools/signing.local）
PROJ = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ROOT = os.environ.get("HMT_ROOT", os.path.dirname(PROJ))
JDK = os.environ.get("HMT_JDK",
                     os.path.join(ROOT, "tools", "jdk21", "jdk-21.0.12+8"))
JAVAC = os.path.join(JDK, "bin", "javac.exe")
JAVA = os.path.join(JDK, "bin", "java.exe")
DX_JAR = os.environ.get("HMT_DX_JAR", os.path.join(
    ROOT, "tools", "jadx147", "lib", "dalvik-dx-11.0.0_r3.jar"))
APKSIG_JAR = os.environ.get("HMT_APKSIG_JAR", os.path.join(
    ROOT, "tools", "jadx147", "lib", "apksig-7.4.1.jar"))
# 签名库：优先环境变量，其次 tools/signing.local（gitignored）。
# 旧 modder.jks 密码已泄露且已废弃，禁止再用；未配置密钥时构建直接失败。
def _signing_local():
    try:
        vals = {}
        with open(os.path.join(PROJ, "tools", "signing.local"), encoding="utf-8") as f:
            for line in f:
                if "=" in line:
                    k, v = line.strip().split("=", 1)
                    vals[k.strip()] = v.strip()
        return vals
    except OSError:
        return {}

_LOCAL = _signing_local()
KS = os.environ.get("HMT_KEYSTORE", _LOCAL.get("KS_PATH", ""))
KS_PASS = os.environ.get("HMT_KS_PASS", _LOCAL.get("KS_PASS", ""))
KS_ALIAS = os.environ.get("HMT_KS_ALIAS", _LOCAL.get("KS_ALIAS", ""))
if not (KS and KS_PASS and KS_ALIAS):
    raise SystemExit(
        "signing key not configured: set HMT_KEYSTORE/HMT_KS_PASS/HMT_KS_ALIAS or create "
        "tools/signing.local (KS_PATH= / KS_PASS= / KS_ALIAS=). "
        "Do NOT reuse the retired modder.jks (its password was leaked).")

BUILD = os.path.join(PROJ, "build")
DIST = os.path.join(PROJ, "dist")
STUB_SRC = os.path.join(PROJ, "build-stub")
APP_SRC = os.path.join(PROJ, "app", "src", "main", "java")
ASSETS = os.path.join(PROJ, "app", "src", "main", "assets")

PKG = "com.tamer.honormarket"
VERSION_NAME = "1.3.9"
VERSION_CODE = 30

# 应用图标：中性购物袋图标（tools/icon/ic_launcher.png，无品牌素材）
ICON_PNG = os.path.join(PROJ, "tools", "icon", "ic_launcher.png")
ICON_RES_PATH = "res/drawable/ic_launcher.png"

# 实验变体开关：--utf16（UTF-16 字符串池）/ --order-std（aapt 式属性顺序）
UTF16_FLAG = "--utf16" in sys.argv
ORDER_STD = "--order-std" in sys.argv

sys.path.insert(0, os.path.join(PROJ, "tools"))
import axml_writer  # noqa: E402
import arsc_builder  # noqa: E402


def run(cmd, desc):
    print("==>", desc)
    p = subprocess.run(cmd, capture_output=True, text=True)
    if p.returncode != 0:
        print(p.stdout[-4000:])
        print(p.stderr[-4000:])
        raise SystemExit("FAILED: " + desc)
    if p.stdout.strip():
        print(p.stdout.strip()[-1500:])
    if p.stderr.strip():
        print(p.stderr.strip()[-1500:])


def java_sources(base):
    out = []
    for dirpath, _dirnames, filenames in os.walk(base):
        for fn in filenames:
            if fn.endswith(".java"):
                out.append(os.path.join(dirpath, fn))
    return out


def main():
    os.makedirs(os.path.join(BUILD, "stub_classes"), exist_ok=True)
    os.makedirs(os.path.join(BUILD, "app_classes"), exist_ok=True)
    os.makedirs(DIST, exist_ok=True)

    run([JAVAC, "--release", "8", "-nowarn", "-d",
         os.path.join(BUILD, "stub_classes")]
        + java_sources(STUB_SRC), "compile stubs")

    run([JAVAC, "--release", "8", "-nowarn",
         "-cp", os.path.join(BUILD, "stub_classes"), "-d",
         os.path.join(BUILD, "app_classes")]
        + java_sources(APP_SRC), "compile module")

    run([JAVA, "-cp", DX_JAR, "com.android.dx.command.Main",
         "--dex", "--min-sdk-version=26",
         "--output", os.path.join(BUILD, "classes.dex"),
         os.path.join(BUILD, "app_classes")], "dex")

    manifest = axml_writer.build_module_manifest(
        package=PKG,
        version_code=VERSION_CODE,
        version_name=VERSION_NAME,
        min_sdk=26,
        target_sdk=34,
        app_label=u"\u8363\u8000\u5e02\u573a\u51c0\u5316",
        activities=[{
            "name": PKG + ".ui.SettingsActivity",
            "label": u"\u8363\u8000\u5e02\u573a\u51c0\u5316",
            "exported": True,
            "launcher": True,
            "module_settings": True,
        }],
        meta_datas=[
            # 类型必须与标准模块一致：LSPosed 用 getBoolean/getInt 读取
            ("xposedmodule", ("bool", True)),
            ("xposeddescription",
             u"\u8363\u8000\u5e94\u7528\u5e02\u573a\u529f\u80fd\u5f00\u5173\uff1a"
             u"\u4fdd\u7559\u641c\u7d22\u4e0e\u66f4\u65b0\uff0c\u5176\u4f59"
             u"\u529f\u80fd\u53ef\u5c4f\u853d"),
            ("xposedminversion", ("int", 93)),
            ("xposedscope", "com.hihonor.appmarket"),
        ],
        allow_backup=False,
        # 图标引用 @drawable/ic_launcher = 0x7f020000（见 arsc_builder）
        icon_ref=("0x7f020000" if os.path.exists(ICON_PNG) else None),
        # MagicOS 解析器怪癖：package 必须排在属性末位，否则 versionCode 解析为 0
        utf16=UTF16_FLAG,
        order_std=True,
    )
    man_path = os.path.join(BUILD, "AndroidManifest.xml")
    with open(man_path, "wb") as f:
        f.write(manifest)
    print("manifest bytes:", len(manifest))

    # minimal standard resources.arsc（消除无资源表的非常规结构）
    has_icon = os.path.exists(ICON_PNG)
    arsc = arsc_builder.build_minimal_arsc(
        PKG, u"\u8363\u8000\u5e02\u573a\u51c0\u5316",
        icon_path=ICON_RES_PATH)
    arsc_path = os.path.join(BUILD, "resources.arsc")
    with open(arsc_path, "wb") as f:
        f.write(arsc)
    print("resources.arsc bytes:", len(arsc), "icon:", has_icon)

    unsigned = os.path.join(BUILD, "unsigned.apk")
    xinit = os.path.join(ASSETS, "xposed_init")
    with zipfile.ZipFile(unsigned, "w", zipfile.ZIP_DEFLATED) as z:
        # targetSdk>=30 硬性要求：resources.arsc 必须 STORED 且 4 字节对齐。
        # 作为首个条目写入：数据偏移 = 30(local头) + 14("resources.arsc") = 44，天然对齐。
        z.write(arsc_path, "resources.arsc", compress_type=zipfile.ZIP_STORED)
        z.write(man_path, "AndroidManifest.xml")
        z.write(os.path.join(BUILD, "classes.dex"), "classes.dex")
        # xposed_init 用不压缩存储，最大化兼容各种读取器
        z.write(xinit, "assets/xposed_init", compress_type=zipfile.ZIP_STORED)
        if has_icon:
            z.write(ICON_PNG, ICON_RES_PATH)
    print("unsigned apk:", os.path.getsize(unsigned), "bytes")

    # 自检：验证 arsc 对齐与存储方式
    with zipfile.ZipFile(unsigned) as z:
        info = z.getinfo("resources.arsc")
        # 计算数据起始偏移
        header_off = info.header_offset
        name_len = len("resources.arsc".encode())
        extra_len = len(info.extra)
        data_off = header_off + 30 + name_len + extra_len
        print("arsc compress_type=%d data_offset=%d aligned=%s"
              % (info.compress_type, data_off, data_off % 4 == 0))
        assert info.compress_type == 0 and data_off % 4 == 0, "arsc alignment check failed"

    sign_classes = os.path.join(BUILD, "sign_classes")
    os.makedirs(sign_classes, exist_ok=True)
    run([JAVAC, "--release", "8", "-nowarn", "-cp", APKSIG_JAR, "-d",
         sign_classes, os.path.join(PROJ, "scripts", "SignApk.java")],
        "compile SignApk")

    _suffix = "_utf16" if UTF16_FLAG else ""
    signed = os.path.join(DIST, "HonorMarketTamer-v%s%s.apk" % (VERSION_NAME, _suffix))
    cp = APKSIG_JAR + (";" if os.name == "nt" else ":") + sign_classes
    run([JAVA, "-cp", cp, "SignApk", unsigned, signed, KS, KS_PASS,
         KS_ALIAS, KS_PASS, "true"], "sign apk")

    print()
    print("SIGNED MODULE APK:", signed)
    print("size:", os.path.getsize(signed), "bytes")


if __name__ == "__main__":
    main()
