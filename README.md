# Tsuki

<p align="center">
  <img src="docs/assets/Tsuki.png" width="120" alt="Tsuki icon">
</p>

<p align="center">
  <a href="https://github.com/Miafetta/tsuki-android/releases/latest"><img src="https://img.shields.io/github/v/release/Miafetta/tsuki-android?label=Release" alt="Latest release"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-MIT-blue.svg" alt="License: MIT"></a>
  <a href="#系统要求"><img src="https://img.shields.io/badge/Android-13%2B-3DDC84?logo=android&logoColor=white" alt="Android 13+"></a>
  <a href="https://kotlinlang.org/"><img src="https://img.shields.io/badge/Kotlin-2.4-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin 2.4"></a>
</p>

Tsuki 是一款面向小米 HyperOS 的 Android 桌面小部件应用，在桌面上显示《明日方舟》的理智、任务与基建进度，以及《明日方舟：终末地》的理智、活跃度与通行证进度。数据来自森空岛，账号信息只保存在本机。

> Tsuki 是非官方的第三方应用，与鹰角网络（Hypergryph）及森空岛没有任何关联。

## 功能

- **桌面小部件**：“明日方舟数据工具”（浅色、深色）和“明日方舟：终末地数据工具”（与官方一样固定为深色），4×2 尺寸；每个小部件独立绑定角色，可单独刷新。小部件名称沿用官方小组件，桌面上的这些小部件由 Tsuki 提供。
- **概览**：查看《明日方舟》的理智、无人机、训练室、公开招募、每日／每周任务、基建等全部指标，以及恢复和完成时间；《明日方舟：终末地》显示理智、活跃度、每周事务、通行证与蚀像寻遗。
- **多账号与多角色**：添加多个森空岛账号，在概览顶部切换游戏，在概览和小部件中自由切换角色。
- **HyperOS 风格界面**：基于 [Miuix](https://github.com/compose-miuix-ui/miuix) 构建，支持浅色／深色模式；在 HyperOS 4 上使用系统原生模糊材质，其他设备自动回退。
- **注重隐私**：没有自己的服务器，不含广告、统计或第三方 SDK；凭据经 Android Keystore 加密，不参与云备份。

## 系统要求

- Android 13（API 33）及以上
- 已在小米 HyperOS 4 上完整验证；其他机型和系统可以使用，但界面效果与桌面兼容性未经验证。
- 一个可用的森空岛账号

## 安装

从 [Releases](https://github.com/Miafetta/tsuki-android/releases) 下载最新的 `Tsuki-<版本>-release.apk` 安装，发布页附有 APK 与签名证书的 SHA-256。正式版之间直接覆盖安装即可，账号与小部件会保留。也可以从源码构建，步骤见[构建](#构建)；自己构建的版本签名不同，不能覆盖维护者签名的正式版。

## 使用

1. 打开 Tsuki，在“我的”页点击右上角的添加按钮，选择“在森空岛网页登录”并在官方页面完成登录，Cred 会自动填入输入框，确认后点击“验证并添加”；也可以按页面上的说明手动获取并粘贴森空岛 Cred。账号名称是可选的备注，留空时显示为“森空岛账号”。验证通过后会自动导入账号下的角色。
2. 在“概览”页查看角色数据，下拉或点击右上角按钮刷新。
3. 在“部件”页打开“全部小部件”，选择角色和配色后添加到桌面；也可以从桌面的小部件列表直接添加。
4. 点击桌面小部件进入它所绑定角色的概览，尚未配置的小部件点击后直接进入配置；长按小部件并选择编辑，可以更换绑定的角色或配色。

在小米桌面上从应用内添加小部件，需要为 Tsuki 开启“桌面快捷方式”权限，应用会在需要时引导你前往设置。

## 构建

需要 JDK 21 及以上，以及 Android SDK Platform 37 与 Build Tools 36.0.0。项目使用 AGP 9.3.3、Gradle 9.8.2 和 Kotlin 2.4.20。

```powershell
# 首次构建时指定 SDK 路径；local.properties 不纳入版本控制
$sdkPath = (Join-Path $env:LOCALAPPDATA 'Android\Sdk').Replace('\', '/').Replace(':', '\:')
Set-Content -LiteralPath local.properties -Value "sdk.dir=$sdkPath" -Encoding ascii

.\gradlew.bat :app:assembleDebug
```

生成的安装包位于 `app/build/outputs/apk/debug/app-debug.apk`。在 macOS 或 Linux 上请使用 `./gradlew`，并在 `local.properties` 中写入 `sdk.dir=<Android SDK 路径>`。

正式版由维护者使用仓库外的私钥签名（`tools/Sign-Release.ps1`），仓库中不包含任何签名材料。如果你的设备上已经安装了维护者签名的正式版，请先卸载，或改用另一台设备安装自己构建的版本。

## 测试

不同改动范围需要通过的构建与检查命令见[开发约束 §8](docs/DEVELOPMENT_CONSTRAINTS.md#8-测试与验收)。

默认的设备场景使用生产页面和内存中的示例数据，不会保存账号或修改桌面小部件。连接设备后可以用 `tools/Run-DeviceTests.ps1` 一键构建、安装并逐项运行，每个场景单独输出日志并汇总结果：

```powershell
.\tools\Run-DeviceTests.ps1 -WhatIf                      # 只打印将执行的命令
.\tools\Run-DeviceTests.ps1 -Serial <设备> -SkipInstall   # 使用已安装的测试包
```

脚本默认使用维护者的签名链（`tools/Sign-Debug.ps1`）；其他开发者请自行安装 Debug 与测试包后使用 `-SkipInstall`。脚本不会卸载应用、清除数据或修改系统设置。网页登录相关的场景（`web-login` 等）不在默认列表中，需要单独指定：它们会联网打开森空岛官方首页（不登录），并清除 Tsuki 自己的网页数据。其他需要单独指定的场景（会保存或编辑已放置小部件的 `widget-editor-save`、`widget-editor-change`）及其影响见[开发约束 §8](docs/DEVELOPMENT_CONSTRAINTS.md#8-测试与验收)。

## 项目结构

| 路径 | 说明 |
| --- | --- |
| `app/src/main/` | 应用源码与资源，包分层见[代码与仓库结构](docs/PROJECT_STRUCTURE.md#2-包与目录) |
| `app/src/test/` | JVM 单元测试与脱敏的接口样本 |
| `app/src/androidTest/` | 设备上的界面、生命周期与渲染测试 |
| `docs/` | 开发文档，见下一节 |
| `tools/` | 设备测试与签名脚本 |

## 文档

- [开发约束](docs/DEVELOPMENT_CONSTRAINTS.md)：兼容性、凭据安全、小部件添加、模糊材质、签名与测试的硬性要求
- [架构](docs/ARCHITECTURE_DECISIONS.md)：进程划分、数据流与多游戏扩展
- [界面设计约定](docs/UI_DESIGN_CONVENTIONS.md)：布局、配色、动效与自定义组件规格
- [代码与仓库结构](docs/PROJECT_STRUCTURE.md)：分层、代码风格与目录约定
- [添加新游戏支持](docs/ADDING_A_GAME.md)：现有扩展点、接入步骤与检查清单
- [素材来源](docs/UI_ASSET_SOURCES.md)
- [更新日志](CHANGELOG.md)

## 隐私

Tsuki 没有自己的服务器，只连接森空岛接口 `zonai.skland.com` 与鹰角网络素材服务器 `web.hycdn.cn`；选择网页登录时，会在应用内网页中打开森空岛官方登录页，登录完成后只读取 Cred 并清除网页数据。账号与数据只保存在本机。完整的隐私说明见应用内“我的 → 数据与隐私”，内容以 `app/src/main/java/cafe/miafetta/tsuki/ui/pages/PrivacyPage.kt` 为准。

## 参与贡献

欢迎提交 Issue 和 Pull Request。开始之前请阅读 [CONTRIBUTING.md](CONTRIBUTING.md)；安全问题请按 [SECURITY.md](SECURITY.md) 私下报告。

## 许可

本项目代码以 [MIT License](LICENSE) 发布。以下内容不在 MIT 许可范围内，逐项出处见[素材来源](docs/UI_ASSET_SOURCES.md)：

- 鹰角网络（Hypergryph）的商标与素材：森空岛、《明日方舟》与《明日方舟：终末地》的名称和图标，小部件指标图标，以及运行时下载的游戏头像。
- 源自 Miuix 的图标资源，按 Apache License 2.0 分发，全文随应用打包在 `app/src/main/assets/third_party/`。