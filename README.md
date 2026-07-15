# 屏幕刷新率

一款面向已 Root Android 设备的显示模式控制工具。应用使用 Compose Multiplatform 和 MIUIX 构建，支持手动切换刷新率、按应用自动应用分辨率与刷新率，以及在退出目标应用后恢复原始显示状态。

> 项目仍处于开发和设备适配阶段。显示模式切换依赖 Android、SurfaceFlinger、厂商服务和设备驱动的具体实现，不保证适用于所有设备。

## 界面

- MIUIX 风格的卡片、设置项和页面转场
- OS3 风格全屏动态背景，自动适配明暗主题
- 采样动态背景的透明液态玻璃底部导航
- 首页实时展示当前分辨率、刷新率和授权状态
- 切换刷新率时显示目标档位与执行进度

## 功能

- 扫描并展示设备支持的分辨率和刷新率模式
- 在首页手动切换指定显示模式
- 自动最高：选择当前分辨率下设备支持的最高刷新率
- 为不同应用分别配置目标分辨率和刷新率
- 进入已配置应用时自动升档或降档
- 离开已配置应用后恢复进入前的显示模式
- 升档时按设备支持的模式逐级切换
- 降档时严格按刷新率逆序逐级切换
- 使用 Android API、系统设置、SurfaceFlinger 和面板状态校验切换结果
- 恢复后执行延迟复检，检测刷新率反弹并重新应用目标模式
- 前台常驻通知展示当前应用和刷新率状态
- 实时检测应用实际获得的帧率、帧时间、抖动和掉帧
- 查看、清空和导出详细运行日志
- 识别部分系统分身应用配置

## 使用要求

- Android 13 或更高版本（`minSdk 33`）
- 设备已经 Root
- Root 管理器允许本应用执行超级用户命令
- 开启本应用的无障碍服务
- 建议关闭系统对本应用的省电限制，并允许后台运行

本项目主要针对 MIUI/HyperOS 设备开发。其他 Android 系统能否正常使用，取决于设备是否支持项目使用的显示控制命令和状态节点。

## 使用方法

1. 安装 APK，并在 Root 管理器中授予超级用户权限。
2. 打开“设置”，点击“开启无障碍服务”。
3. 在系统设置中开启“屏幕刷新率”的无障碍服务。
4. 打开“应用”，选择需要单独控制显示模式的应用。
5. 开启自定义刷新率，选择目标分辨率和刷新率。
6. 切换到目标应用，等待程序应用配置。
7. 离开目标应用，程序会尝试恢复进入应用前的显示模式。

首页支持直接点击刷新率档位进行手动切换。配置中的“自动最高”只会选择当前分辨率下支持的最高刷新率，不会擅自改变分辨率。

## 工作原理

应用通过无障碍服务和前台窗口状态识别当前应用，并使用 Root 权限组合执行以下操作：

- 设置系统最高、最低和用户刷新率
- 设置 MIUI/HyperOS 刷新率配置
- 设置用户首选显示模式
- 调用 SurfaceFlinger 切换活动模式
- 读取 `dumpsys display`、SurfaceFlinger 和可用的面板 sysfs 状态

高刷新率切换可能按照设备支持的模式逐档执行。例如从 120Hz 切换到 165Hz 时，可能依次经过 132Hz、144Hz、156Hz 和 165Hz。降档时采用相反顺序，避免直接跨越多个档位。

程序会在进入目标应用前记录恢复基线。退出应用后先恢复该基线，再进行多次延迟复检；如果系统、温控服务或厂商刷新率服务覆盖了结果，程序会尝试重新应用恢复目标。

## 运行日志

在“设置 -> 运行日志”中可以查看、清空或导出日志。日志主要包含：

- 无障碍服务连接、中断和销毁状态
- 当前识别到的前台应用
- 应用配置匹配结果
- 升档、降档和逐档切换过程
- Root 命令退出状态和错误信息
- Android API 报告的刷新率
- `dumpsys display` 报告的活动模式
- 面板节点和 SurfaceFlinger 帧周期推算的刷新率
- 恢复后的 `POST_RESTORE` 延迟复检结果

反馈自动切换问题前，建议先清空旧日志，然后完整执行一次以下流程：

1. 停留在桌面或未配置的应用。
2. 进入已配置刷新率的应用。
3. 等待切换完成。
4. 返回桌面或另一个未配置应用。
5. 等待至少 12 秒。
6. 导出并附上完整日志。

常见日志字段：

- `panel`：显示驱动节点报告的面板刷新率
- `physical`：根据 SurfaceFlinger 帧周期推算的刷新率
- `active`：系统显示服务报告的活动模式刷新率
- `preferred`：用户首选显示模式刷新率
- `peak/min/user/miui`：相关系统设置值
- `POST_RESTORE`：退出应用后的延迟复检和重新应用记录

## 构建

项目使用 Kotlin Multiplatform、Compose Multiplatform 和 MIUIX，Android 构建需要：

- JDK 17
- Android SDK
- 指向 Android SDK 的 `local.properties`

构建 Debug APK：

```bash
./gradlew :composeApp:assembleDebug
```

输出位置：

```text
composeApp/build/outputs/apk/debug/composeApp-debug.apk
```

GitHub Actions 会在推送到 `main`、创建 Pull Request 或手动触发工作流时构建 Debug APK，并上传名为 `debug-apk` 的 Artifact。

## 项目结构

```text
composeApp/src/commonMain/       通用 Compose 界面、组件和数据模型
composeApp/src/androidMain/      Android 平台实现、Root 工具和无障碍服务
.github/workflows/build.yml      GitHub Actions 构建配置
```

主要实现：

- `KeepAliveAccessibilityService.kt`：前台应用识别、自动切换和恢复校验
- `RootUtils.kt`：Root 命令、显示模式切换和状态解析
- `AutoOverclockManager.kt`：显示模式扫描、去重和当前模式读取
- `Os3Background.kt`：OS3 风格动态背景和生命周期控制
- `IosGlassNavigationBar.kt`：液态玻璃底部导航
- `RuntimeLog.kt`：运行日志保存与读取

## 源码与反馈

- 源代码：<https://github.com/nagiska/miuix-refreshrate>
- 问题反馈：<https://github.com/nagiska/miuix-refreshrate/issues>

提交问题时，请说明设备型号、系统版本、Root 方案、目标分辨率和刷新率，并尽可能附上完整运行日志。

## 风险提示

- 超出设备官方规格的刷新率可能导致黑屏、闪屏、触控异常、发热或功耗增加。
- 不正确的显示模式可能导致界面暂时不可操作，请确保拥有其他恢复显示设置的方法。
- 系统省电策略、温控策略和厂商刷新率服务可能覆盖本应用的设置。
- 系统状态显示为目标刷新率不代表面板一定按该频率输出，应结合 `physical` 或 `panel` 日志判断。
- 使用本项目产生的设备风险由使用者自行承担。

## 隐私

应用配置和运行日志保存在设备本地。导出日志时可能包含应用包名、显示模式、Root 命令结果和设备显示状态，请在公开分享前检查内容。
