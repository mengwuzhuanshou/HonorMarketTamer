Release v1.4.6（versionCode 37）

⚠ AI-generated module / 本模块由 AI 生成，代码未经人工长期审计，请自行评估风险。

## 更新内容 / What's new

- 适配市场最新 16.1.8.305：开屏广告、运营弹窗、推荐流、消息中心等多处锚点
  重新对齐，旧版本市场继续可用。
  Adapted to the latest market 16.1.8.305: splash ads, promo dialogs, recommended
  feeds and the message center are re-anchored; older market versions keep working.
- 拨开关即后台同步：市场不再抢占前台，新配置无需拉起或重启市场即可生效。
  Toggling a switch now syncs in the background — the market no longer comes to the
  foreground, and new settings apply without launching or restarting it.
- 新增「运动健康传感器闸门」与「消息中心屏蔽」，均默认关闭。
  New health-sensor gate and message-center blocking, both off by default.
- 与 v1.3.3+ 同签名密钥，可直接覆盖安装，无需卸载。
  Same signing key as v1.3.3+ — install over the old one directly.
