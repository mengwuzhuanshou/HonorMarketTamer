Release v1.3.4（versionCode 25）

⚠ AI-generated module / 本模块由 AI 生成，代码未经人工长期审计，请自行评估风险。

## 更新内容 / What's new

- **配置读取优先级修正**：设置页随开关实时重写的本地两份配置
  （模块 files 目录、shared_prefs 同级副本）现在优先于 /data/local/tmp 副本；
  tmp 仅作末位兜底。曾给过 root 的设备在撤销 root 后继续拨开关，
  不再会被那份永不刷新的陈旧 tmp 文件劫持。
  Config read-order fix: app-owned copies written on every toggle now outrank
  the /data/local/tmp fallback, so devices that lost root keep following the
  latest switches instead of a stale never-refreshed copy.
- 与 v1.3.3 同签名密钥，可直接覆盖安装，无需卸载。
  Same signing key as v1.3.3 — install over the old one directly.
- 屏蔽能力与 v1.3.3 一致：开屏广告/运营弹窗/悬浮窗、推送与自启唤醒、
  推荐流与详情页推荐、页面频道开关等

## 环境要求 / Requirements

- 已 root + Zygisk + LSPosed / rooted device with Zygisk + LSPosed
- 荣耀应用市场 16.1.6.302 / Honor App Market 16.1.6.302

安装后请在 LSPosed 勾选作用域「荣耀应用市场」并强制停止市场后重开。
After install, select the market in the module scope list, then force-stop and reopen the market.
