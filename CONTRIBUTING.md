# 参与贡献

感谢你愿意改进 Tsuki。提交之前请花几分钟阅读以下说明。

## 报告问题

- 提交 Issue 时，请写明设备型号、系统版本（例如 HyperOS 4.0）、Tsuki 版本号（“我的 → 关于 → 版本”）、复现步骤，以及预期与实际结果。
- **不要**在 Issue、截图或日志中包含 Cred、Cookie、森空岛账号、游戏 UID 或角色名。截图前请先遮挡这些信息。
- 安全问题请不要公开提交，按 [SECURITY.md](SECURITY.md) 私下报告。

## 开发环境

构建要求与步骤见 [README](README.md#构建)。开始改代码之前，请先阅读：

1. [开发约束](docs/DEVELOPMENT_CONSTRAINTS.md)：兼容性、凭据、小部件添加、模糊材质与签名的硬性边界。
2. [代码与仓库结构](docs/PROJECT_STRUCTURE.md)：包分层、文件与函数长度、行宽等代码风格要求。
3. [界面设计约定](docs/UI_DESIGN_CONVENTIONS.md)：修改界面时需要遵守的规格。
4. 接入新游戏时，按[添加新游戏支持](docs/ADDING_A_GAME.md)的步骤与检查清单进行。

## 提交 Pull Request

- 每个 PR 只做一件事，并在描述中说明改了什么、为什么改，以及如何验证。
- 提交前确保[开发约束 §8](docs/DEVELOPMENT_CONSTRAINTS.md#8-测试与验收)中的构建与检查命令全部通过。
- 改动界面或桌面小部件时，请附上浅色和深色模式的截图；能在设备上运行 `tools/Run-DeviceTests.ps1` 中相关场景的，请写明结果。没有实际运行的检查，请明确写“未验证”。
- 会影响已安装小部件的改动（[开发约束 §1](docs/DEVELOPMENT_CONSTRAINTS.md#1-已安装实例兼容)所列内容）需要先在 Issue 中讨论。
- 新增依赖需要先讨论；项目不引入 Material3 或其他 UI 库。
- 不要提交签名材料、`local.properties`、APK、真实账号数据或含个人信息的截图。
- 面向用户的变更请同时更新 [CHANGELOG.md](CHANGELOG.md)。

## 许可

提交贡献即表示你同意以 [MIT License](LICENSE) 授权你的代码。
