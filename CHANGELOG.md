# 更新日志 / Changelog

## 1.3.0

### 新增 / Features
- 液态玻璃底部导航：基于 Kyant0 Backdrop，实时采样 OS3 动态背景与页面内容，叠加 vibrancy + blur + lens 折射；选中态与高亮沿用 MIUIX FloatingNavigationBar。
- 底栏交互：按住可向四周拖拽（带限位的橡皮筋阻尼，越拉越涩）；按压处产生局部"凹陷"形变（形变枢轴跟随按压点，抓哪儿瘪哪儿）；松手时一边弹回原位、一边同步恢复原型。
- 首页选中即全局锁定：在首页选择某档位即作为全局刷新率，由前台服务持续保持（含桌面），抵御系统/桌面把刷新率瞬时不正常提频；分应用配置生效时自动让位，退出后恢复。支持开机自启。

### 修复 / Fixes
- 修复部分设备（`DisplayModeRecord` 为 `mMode={...}` 长格式）无法扫描显示模式、active 模式解析不到、SurfaceFlinger `1035` 回退（sfModeId）不触发的问题。
- 修复刷新率校验误判：面板实际未切换但 settings 已写入时，不再误报成功（真实面板证据优先，否决 settings 翻案）。
- 修复退出已配置应用后刷新率不恢复 / 被覆盖：POST_RESTORE watchdog 顶号、手动选择抑制恢复、takeover 读取不到当前模式等多条路径。
- 修复"恢复自适应"时 min/peak 钉档未真正解除（`min==peak` 仍锁死）的问题。
- 修复 root 命令在输出量较大时可能死锁的问题（并发抽干 stdout/stderr）。

### 其他 / Misc
- 新增依赖：`io.github.kyant0:backdrop:2.0.1`、`io.github.kyant0:shapes:1.2.1`。
- 新增 `GlobalOverclockService`、`BootCompletedReceiver`（开机恢复全局锁定）。
- 分应用刷新率、所有权/恢复状态机、证据优先级校验逻辑保持不变。

---

## 1.2.0
- 手动接管刷新率所有权，防止被旧 auto-restore 覆盖。
- 使用官方 FloatingNavigationBar，选中项主色高亮。
