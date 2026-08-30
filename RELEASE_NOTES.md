Release v1.3.6（versionCode 27）

⚠ AI-generated module / 本模块由 AI 生成，代码未经人工长期审计，请自行评估风险。

## 更新内容 / What's new

- **开屏广告适配市场 16.1.7.303**：市场自升级后开屏广告屏蔽失效，
  根因是 V3 实时链路（AB request_splash=1）绕过旧缓存锚点、且旧钩子的
  continuation 判定恒不匹配、广告视图工厂混淆名漂移。现补三点拦截：
  V3 数据入口、缓存数据入口（含预取分支）、新视图工厂；同时原四点
  锚点全部保留，老版本市场继续可用。
  Splash-ad blocking adapted to market 16.1.7.303: the V3 realtime path
  (AB request_splash=1) bypassed the old cached anchor, the legacy hook's
  continuation check never matched, and the ad view factory moved to a new
  obfuscated name. Three new anchors cover the V3 data entry, the cached
  data entry (incl. the prefetch branch) and the new view factory, while
  all four legacy anchors are kept for older market versions.
- **配置解析严格布尔**：配置副本中出现非 true/false 的损坏值时整键跳过，
  不再误判为 false——此前一次传输损坏可能把总开关解析成 false 导致
  模块整体静默失效。
  Strict boolean parsing: corrupted non-boolean values in a config copy are
  skipped per key instead of being read as false — a damaged copy could
  previously turn the master switch off and silently disable the module.
- **签名文件名跟随密钥别名**：v1 JAR 签名的 .SF/.RSA 基名不再固定，
  改由 keystore alias 生成（当前密钥下为 HONORMAR）。
  The v1 JAR signature base name (.SF/.RSA) now derives from the keystore
  alias instead of a fixed string (HONORMAR with the current key).
- 与 v1.3.3+ 同签名密钥，可直接覆盖安装，无需卸载。
  Same signing key as v1.3.3+ — install over the old one directly.

## 环境要求 / Requirements

- 已 root + Zygisk + LSPosed / rooted device with Zygisk + LSPosed
- 荣耀应用市场 16.1.6.302 / 16.1.7.303 / Honor App Market 16.1.6.302 / 16.1.7.303

安装后请在 LSPosed 勾选作用域「荣耀应用市场」并强制停止市场后重开。
After install, select the market in the module scope list, then force-stop and reopen the market.
