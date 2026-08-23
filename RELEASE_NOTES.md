Release v1.3.3（versionCode 24）

⚠ AI-generated module / 本模块由 AI 生成，代码未经人工长期审计，请自行评估风险。

## 更新内容 / What's new

- **更换签名密钥**：旧密钥密码曾泄露，已废弃并换新钥重新签名；与 v1.3.2 及更早版本
  签名不兼容，安装前请先卸载旧版本。
  **Re-signed with a fresh key** (the previous key's password was leaked and is now
  retired). The new signature is incompatible with older builds — uninstall the old
  module before installing this one.
- 仓库卫生：移除未使用的 Gradle 残留、清理构建脚本中的本地路径与默认密码
  Housekeeping: dropped unused Gradle leftovers; removed local paths & default passwords.
- 许可证：MIT 版权持有人改为 mengwuzhuanshou
  License: MIT copyright holder set to mengwuzhuanshou.

## 环境要求 / Requirements

- 已 root + Zygisk + LSPosed / rooted device with Zygisk + LSPosed
- 荣耀应用市场 16.1.6.302 / Honor App Market 16.1.6.302

安装后请在 LSPosed 勾选作用域「荣耀应用市场」并强制停止市场后重开。
After install, select the market in the module scope list, then force-stop and reopen the market.
