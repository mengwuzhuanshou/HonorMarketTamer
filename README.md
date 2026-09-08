# HonorMarketTamer（荣耀市场净化）

> ## ⚠️ 本模块由 AI 生成 / This module is AI-generated
> 本项目由大语言模型（AI）在人类指导下生成并迭代维护，包括全部 Hook 代码、设置界面、
> 构建流水线与文档。代码未经人工长期审计，请自行评估风险后使用；欢迎人工审查与 PR。
>
> This project was generated and iterated by a large language model (AI) under human
> direction, including all hook code, the settings UI, the build pipeline and these docs.
> The code has not been long-term audited by humans — evaluate the risk yourself;
> human review and PRs are welcome.

面向**荣耀应用市场** `com.hihonor.appmarket` 的功能精简 LSPosed 模块。
设计原则：“搜索应用”与“应用更新”永远可用；其余功能全部提供独立开关，可随时屏蔽/恢复。

An LSPosed module that trims the **Honor App Market** (`com.hihonor.appmarket`).
Design principle: "search" and "app updates" always keep working; every other
feature is an independent switch you can toggle on or off at any time.

## 功能一览 / Features

| 分类 Category | 功能 Feature |
| --- | --- |
| 推荐流 Feed | 搜索页推荐流在请求源头拦截（输入框即时可用）/ Search-page feed blocked at the request source (input box usable instantly) |
| 推荐流 Feed | 详情页推荐区块、更新页“新应用”推荐 / Detail-page recommendations & "new apps" on Updates page |
| 广告 Ads | 开屏广告、运营活动弹窗、悬浮窗、桌面卡片推广、个性化广告弹窗 / Splash ads, promo dialogs, floating windows, widget tips, personalized ads |
| 骚扰 Nuisance | 营销推送通知、自启与广播唤醒 / Marketing push notifications, auto-start & broadcast wake-ups |
| 页面 Pages | 底部「游戏/应用/抢鲜」页签 / Bottom tabs: Games / Apps / Early-Access |
| 「我的」页 Mine page | 签到、安全检测、清理加速、常用服务、滑动横幅卡片 / Check-in, security scan, cleaner, services, swipe banner cards |
| 搜索 Search | 搜索框滚动热词横幅 / Rolling hot-word banner in the search box |
| 搜索 Search | 搜索结果首位广告展位大卡、底部「装机必备」横滑 / First-position ad booth card & "Must-install" strip in search results |
| 广告 Ads | 工具页标题栏运营活动入口（应用更新/安装管理等，如"耀耀农场"树形图标）/ Tool-page header activity entries (Updates, Install Manager, …) |

共 26 个开关，默认配置即推荐配置。设置页每个选项均附英文说明。
26 switches in total; defaults are the recommended set. Every option in the
settings UI carries an English subtitle.

## 环境要求 / Requirements

* 已 root 的荣耀/MagicOS 设备：Magisk 或 KernelSU + Zygisk + LSPosed。
  A rooted Honor/MagicOS device: Magisk or KernelSU + Zygisk + LSPosed.
* 荣耀应用市场 16.1.6.302 / 16.1.7.303 / 16.1.8.301（实测适配版本；其它版本类名可能漂移）。
  Honor App Market 16.1.6.302 / 16.1.7.303 / 16.1.8.301 (the versions this module was tested
  against; other versions may drift).

> 该市场的包经过资源与代码混淆，其他版本的类名可能变化导致功能静默失效；
> 可在 LSPosed 日志过滤 `HonorMarketTamer` 查看 `armed` 行确认 Hook 是否命中。
> The market APK is obfuscated; class names may change across versions and hooks
> can silently miss. Filter LSPosed logs for `HonorMarketTamer` and check the
> `armed` lines to confirm hooks are in place.

## 使用方法 / Usage

1. 在 LSPosed 中启用本模块并勾选作用域「荣耀应用市场」。
   Enable the module in LSPosed and select the "Honor App Market" scope.
2. 强制停止「应用市场」后重新打开。
   Force-stop the market app and reopen it.
3. 设置入口：LSPosed 模块详情页，或桌面「荣耀市场净化」图标。
   Open settings from the LSPosed module page, or the launcher icon.

### 权限说明 / Permissions note

模块日常运行（Hook 过程）不执行任何 root 操作。仅在设置页保存开关时会尝试请求 root
同步一份全局配置副本（绕过 LSPosed 数据目录重定向的读取限制）；拒绝该请求不影响功能，
配置会自动落到其余副本路径。

The module performs no root operations while hooking. Root is only requested when
you save switches in the settings UI, to sync a global copy of the config file
(working around LSPosed's data-directory redirection). Denying that request does
not break anything — the config falls back to other copies automatically.

## 从源码构建 / Build from source

本项目刻意不依赖 Gradle / Android SDK：

```
python tools/build_module.py
```

流程：`javac --release 8`（手写 stub）→ dalvik `dx` → 手写二进制 AndroidManifest
（`axml_writer.py`）+ 最小 `resources.arsc`（`arsc_builder.py`）→ zip 打包 → apksig 签名。
产物输出至 `dist/`。构建深坑记录见 [PITFALLS.md](PITFALLS.md)。

This project deliberately avoids Gradle and the Android SDK: compile with plain
`javac --release 8` against hand-written stubs, dex it, emit a binary manifest
(`axml_writer.py`) and a minimal `resources.arsc` (`arsc_builder.py`) by hand,
zip, then sign with apksig. Output lands in `dist/`. See
[PITFALLS.md](PITFALLS.md) for the hard-won lessons.

## 项目结构 / Project layout

```
app/src/main/java/com/tamer/honormarket/
├── MainHook.java              # Xposed 入口 / entrypoint
├── TamerConfig.java           # 开关键与三级配置加载 / keys + config loading
├── hooks/                     # 各功能 Blocker / feature blockers
│   ├── RecommendFeedBlocker   # 搜索&详情推荐流 / search & detail feeds
│   ├── MineSectionBlocker     # 「我的」页卡片 / mine-page cards
│   ├── RollWordBlocker        # 滚动热词 / rolling banner
│   ├── TabStripper            # 底部页签 / bottom tabs
│   ├── PushWakeBlocker        # 推送与自启 / push & wake-up
│   └── SplashAdBlocker        # 开屏广告 / splash ads
└── ui/SettingsActivity.java   # 纯代码设置页 / code-only settings UI
tools/
├── build_module.py            # 一键构建流水线 / one-shot build pipeline
├── axml_writer.py             # 二进制 AXML 编码器 / binary AXML encoder
├── arsc_builder.py            # 最小 resources.arsc 构建器 / minimal arsc builder
├── _validate_arsc.py          # arsc chunk 链校验器 / arsc chain validator
└── icon/ic_launcher.png       # 中性图标 / neutral icon
```

## 免责声明 / Disclaimer

* 本项目仅供学习与研究 Android Hook 技术使用，请勿用于商业用途。
  For learning and research on Android hook techniques only; do not use commercially.
* 与荣耀公司/HONOR 无任何关联；“应用市场”相关商标与原应用版权归原厂所有；
  模块图标为中性图形，不含任何品牌素材。
  Not affiliated with Honor Device Co., Ltd. All trademarks and rights to the
  original app belong to their owner; the module icon is a neutral graphic.
* 使用本模块产生的任何后果由使用者自行承担。
  Use at your own risk.
