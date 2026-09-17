Release v1.4.7（versionCode 38）

⚠ AI-generated module / 本模块由 AI 生成，代码未经人工长期审计，请自行评估风险。

## 更新内容 / What's new

- 修复「运动健康传感器闸门」在部分设备上反复刷错误日志的问题：亮/灭屏接收器
  注册桩签名写错导致每次注册都抛异常、每 5 秒重试刷屏；现已修正，接收器一次
  注册成功，闸门按预期在熄屏停采、亮屏恢复。
  Fixed the health-sensor gate repeatedly logging errors on some devices: a wrong
  compile-time stub signature made the screen on/off receiver registration throw on
  every attempt and retry-spam the log every 5s. The receiver now registers in one
  shot and the gate stops/resumes sensor sampling as intended.
- 配置读取链路加固：对 LSPosed 即将弃用/移除的 XSharedPreferences 通道做兜底，
  框架升级移除该通道后自动降级到 conf 文件链路、不再影响开关生效。
  Hardened the config read path against the LSPosed XSharedPreferences channel that
  is being deprecated/removed: after a framework upgrade drops it, the module falls
  back to the conf-file channel automatically instead of breaking.
- 与 v1.3.3+ 同签名密钥，可直接覆盖安装，无需卸载。
  Same signing key as v1.3.3+ — install over the old one directly.
