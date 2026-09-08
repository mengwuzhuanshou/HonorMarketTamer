# 踩坑记录 / Pitfalls & Lessons

> 本文档记录 HonorMarketTamer 构建过程中真实踩过的坑（AI 生成项目，坑也是 AI 踩的）。
> This document records the pitfalls actually hit while building HonorMarketTamer
> (an AI-generated project — the pitfalls were hit by the AI, too).

## 1. MagicOS 清单解析怪癖：属性顺序 / MagicOS manifest quirk: attribute order

`AndroidManifest.xml` 的 `<manifest>` 属性若按常规顺序（package 在前）书写，
MagicOS 的解析器会把 **versionCode 解析成 0**，LSPosed 会因此跳过模块。
必须让 versionCode/versionName 排在 package 之前（aapt 风格），构建脚本里永远保持 `order_std=True`。

If `<manifest>` attributes are written in the usual order (package first), MagicOS's
parser reads **versionCode as 0** and LSPosed skips the module. Keep
versionCode/versionName before package (aapt style); never disable `order_std`.

## 2. resources.arsc 必须是首条目且 STORED 对齐 / arsc must be the first zip entry

targetSdk≥30 硬性要求：resources.arsc 必须不压缩（STORED）且数据偏移 4 字节对齐。
做法：arsc 作为 zip 第一个条目写入，文件名恰 14 字节 → 数据偏移 = 30+14 = 44，天然对齐。

On targetSdk≥30, resources.arsc must be uncompressed and 4-byte aligned. Write it
as the FIRST zip entry; its name is exactly 14 bytes so the data offset is 44 —
aligned by construction.

## 3. 手写 ResTable_type：size 少算 4 字节 / Hand-built ResTable_type: size off-by-4

chunk 总长公式 = `entriesStart + Σ(entry 字节数)`。entriesStart 已经包含了条目偏移表，
不能把偏移表再加一次——否则声明 size 比物理字节多 4。
**单 type 时溢出落在包尾侥幸能装；一旦有第二个 type，下一个 chunk 解析即错位**，
表现为安装时 `INSTALL_PARSE_FAILED_NOT_APK: Failed to load asset path`。

Chunk total = `entriesStart + Σ(entry bytes)`. entriesStart already covers the
offset table; adding it twice declares a size 4 bytes larger than the physical
bytes. With a single type the overrun lands at end-of-package and installs fine;
with two types the parser walks into garbage → `INSTALL_PARSE_FAILED_NOT_APK:
Failed to load asset path`.

## 4. typeSpec 的 id 字段硬编码 / Hardcoded id in typeSpec

第二个 `_type_spec(2, ...)` 打包时把 id 写死为 1 → 表里出现两个 "typeId=1" 的 spec，
type2 无主，AssetManager 整表拒绝加载。排查时 chunk 链校验器看不出来
（结构自洽），要对照字段值才现形。教训：参数化函数里的每个字段都要真的用参数。

Passing `2` to the helper but still packing a literal `1` produced two specs with
id=1 and an orphaned type2 — AssetManager rejects the whole table even though the
chunk chain is structurally self-consistent. Lesson: every field of a parametrized
struct must actually come from the parameter.

## 5. dx 不支持 lambda / dx rejects lambdas

老版 dalvik-dx 编译 lambda 会产出 VerifyError。全部用匿名内部类；
自定义类一律全限定名引用，避免 defpackage 冲突。

Legacy dalvik-dx produces VerifyError for lambdas — use anonymous classes only,
and reference your own classes by fully-qualified names to avoid defpackage clashes.

## 6. adb/su 引号纪律 / adb & su quoting discipline

`su -c` 里一条命令一个进程，禁止 `&&` 和管道；grep 多词模式会被 su 分词拆散，
只允许单 token 模式。点号正则偶发空结果，优先用通配路径 + 单词模式。

One plain command per `su -c` (no `&&`, no pipes); multi-word grep patterns get
word-split by su quoting — single tokens only.

## 7. 配置三级副本策略 / Three-copy config strategy

LSPosed 把模块数据重定向到 apexdata 目录且默认 660 权限，目标进程读不到。
方案：设置页同时写 ① 自身 files 目录 ② apexdata prefs 同级 ③ root 写
/data/local/tmp；Hook 侧按 ①→②→③ 顺序读，任一可读即生效（XSharedPreferences 终兜底）。
拒绝 root 授权同样能工作（①② 不需要 root）。
**v1.3.4 起把 ③ 降为末位**：③ 只在拿到过 root 时写入且之后不再刷新，排最前会让
「撤 root 后继续拨开关」的设备读到陈旧配置；①② 随开关实时重写，永远最新。

LSPosed redirects module data to an apexdata dir with mode 660, unreadable by the
target process. The settings UI writes three copies (own files dir, apexdata
sibling, /data/local/tmp via root) and the hook side tries them in order
①→②→③ with XSharedPreferences as the final fallback — denying the root prompt
is harmless since the first two copies need no root. **Since v1.3.4 the root
copy ranks LAST**: it is only written while root is granted and never refreshed
afterwards, so ranking it first would freeze switches on devices that lost root.

## 8. “请求源头拦截”模式 / Kill-the-request-at-source pattern

与其 hook UI 隐藏加载态，不如在请求发起处直接 `setResult(合成失败响应)`：
下游 null-safe 分支自己走完收尾逻辑，UI 干净落地，无残留请求、无等待。
合成响应要模仿应用自身的失败形态（如 BaseResp.errorCode=-1）。

Instead of hiding loading spinners in the UI, hook the request initiator and
`setResult()` a synthetic failure shaped like the app's own error object; the
app's own null-safe branches then finish the UI cleanly — no stray requests, no waits.

## 9. 市场包没有位图图标 / The market APK ships no bitmap icons

基础包 res/ 只有 color 与混淆名矢量 XML，assets 全是素材图——启动图标无处可抽。
最终从桌面截屏按 uiautomator bounds 裁剪得到“用户看到的原图标”。
另：给模块加图标需要手搓 drawable 资源条目（见第 3、4 条坑）。

The base APK contains only colors and obfuscated vector XMLs — there is no raster
launcher icon to extract. We screenshotted the launcher and cropped the icon cell
by uiautomator bounds instead. Adding our own icon required hand-building the
drawable resource entry (see pitfalls 3 & 4).

## 10. 日志限频 / Log rate limiting

RecyclerView 高频重绑会让每次 GONE 都打一行日志（实测 645 行/6h）。
统一采样模式：`n <= N || n % M == 0`，计数器用 AtomicInteger。

High-frequency rebinds log per-GONE (645 lines/6h observed). Sample everything:
`n <= N || n % M == 0` on AtomicInteger counters.

## 11. javac 空白 final 二次赋值 / blank-final double assignment

try/catch 两分支都对空白 final 赋值会报“变量已分配”。改非 final 局部变量再拷贝到 final。

Assigning a blank final in both try and catch fails to compile ("variable might
already have been assigned") — assign a non-final local, copy into the final after.

## 12. 排查工具链 / Debugging toolkit

手搓二进制资源出错时，别盲试：写个 chunk 链校验器（`tools/_validate_arsc.py`）
逐 chunk 对账 size/offset/end，再拿真机大厂 APK 的 arsc 当标准答案对比关键字段
（`tools/_cmp_arsc.py`）。二分变体 + 本地校验比反复 install 快得多。

When hand-built binary resources misbehave, don't guess: walk the chunk chain
with `tools/_validate_arsc.py` and diff key fields against a real vendor arsc via
`tools/_cmp_arsc.py`. Local validation plus build variants beats install-and-pray.

## 13. 编译桩混入 UTF-8 BOM / UTF-8 BOM in compile stubs

`javac --release 8` 对源文件头部的 UTF-8 BOM（\ufeff）零容忍，报
"非法字符"且指向整行，第一眼看像语法损坏。共享编译桩曾混入 BOM 导致
构建全线失败。修法：按字节探三个 BOM 头并剥离；同步副本时顺带自检。
另：apksig 的 v1 签名 .SF/.RSA 基名取自 SignerConfig 名称（截断到 8 字符），
跟随 keystore alias 生成即可，别硬编码。

`javac --release 8` rejects a leading UTF-8 BOM in source files with an
"illegal character" error that reads like broken syntax. A BOM once crept into
the shared compile stubs and broke the whole build. Fix: detect and strip the
3-byte BOM bytewise; self-check when syncing copies. Also: apksig derives the
v1 .SF/.RSA base name from the SignerConfig name (truncated to 8 chars) —
generate it from the keystore alias instead of hardcoding.

## 14. 混淆版本漂移：按名钩子静默失效 / Silent hook misses after obfuscated-symbol drift

市场自升级会把混淆符号整体改名（真实请求核心、响应汇聚方法都换过名），按固定名
`hookAllMethods` 注册"成功"但永不触发——不报错、无日志，功能静默复活。铁律：
每个钩子装载后打 `armed` 日志，功能页实测命中计数（`killed #N` / `removed #N`）；
适配新版本先反编译新 APK 复核每个锚点，旧锚点保留作老版本存档。

Market self-upgrades rename obfuscated symbols wholesale (both the real request
core and the response sink moved). `hookAllMethods` on a stale name "succeeds"
but never fires — no error, no log, the feature silently returns. Rules: log an
`armed` line for every hook, verify hit counters on the live page, decompile the
new APK to re-check each anchor when adapting, and keep legacy anchors for
older market versions.

## 15. 构建脚本与 jadx 的两个工具坑 / Two toolchain traps (build script & jadx)

构建脚本用 `subprocess.run(..., text=True)`（按 UTF-8）读 javac 输出，中文 Windows
控制台下 javac 输出 GBK → `UnicodeDecodeError` 且 stderr 变 None，把真实编译错误
盖住。构建入口固定 `PYTHONUTF8=0` 或显式 `encoding=, errors=`。另：jadx fat jar 用
`java -jar` 启动的是 GUI（其 Main-Class），无头环境必须
`java -cp jadx-*.jar jadx.cli.JadxCLI`，定点反编译加 `--single-class`。

A build script reading javac output with `subprocess.run(..., text=True)` (UTF-8)
crashes with `UnicodeDecodeError` on a GBK Chinese-Windows console, and stderr
becomes None, masking the real compiler message. Pin `PYTHONUTF8=0` (or pass
explicit encoding/errors). Also: `java -jar` on the jadx fat jar launches the GUI
(that is its Main-Class); run headless with
`java -cp jadx-*.jar jadx.cli.JadxCLI` and use `--single-class` for surgical
decompiles.
