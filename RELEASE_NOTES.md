Release v1.3.9（versionCode 30）

⚠ AI-generated module / 本模块由 AI 生成，代码未经人工长期审计，请自行评估风险。

## 更新内容 / What's new

- **「我的」页推荐流适配市场 16.1.8.301**：市场自升级后「我的」页底部
  应用推荐流复活——真实请求核心漂移为新的混淆名（上拉加载直接调用、绕过旧
  钩子），响应汇聚方法也改名。新增请求核心拦截 + 空成功响应收口两个钩子；
  旧锚点全部保留，老版本市场继续可用。
  Mine-page feed re-adapted for market 16.1.8.301: the feed returned after a
  market self-upgrade because the real request core moved to a new obfuscated
  name (load-more calls it directly, bypassing the legacy hook) and the
  response sink was renamed. Two new hooks intercept the request core and
  settle the response with an empty success; all legacy anchors are kept.
- **搜索结果页净化**：屏蔽结果首位的广告展位大卡与底部「装机必备」横滑
  推荐区块（新开关：屏蔽搜索结果广告展位 / 屏蔽搜索页装机必备）。在装配
  适配器的数据入口按条目类型与标题过滤，正常搜索结果与加载更多不受影响。
  Search-result cleanup: the first-position ad booth card and the bottom
  "Must-install" strip are now removed (two new switches). Filtering happens
  at the assembly adapter's data entries by item type and section title;
  organic results and load-more keep working.
- **工具页运营活动入口屏蔽**：应用更新、安装管理、安装记录、卸载管理、
  清理加速、安全检测六个页面标题栏的运营活动按钮（如"耀耀农场"树形图标）
  不再显示（新开关：屏蔽工具页活动入口）。活动 H5 页面本身不受影响——
  入口消失后自然无法进入。
  Tool-page activity entries hidden: the promo buttons in the title bars of
  six tool pages (Updates, Install Manager, Install Record, Uninstall,
  Cleaner, Security Check) — e.g. the tree-shaped "Farm" icon — no longer
  show (one new switch). The H5 pages themselves are untouched; with the
  entry gone they are simply unreachable.
- 与 v1.3.3+ 同签名密钥，可直接覆盖安装，无需卸载。
  Same signing key as v1.3.3+ — install over the old one directly.
