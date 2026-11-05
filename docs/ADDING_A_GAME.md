# 添加新游戏支持

本文说明如何为 Tsuki 接入一款新游戏，例如同在森空岛上的《明日方舟：终末地》，或其他平台上的游戏。开始之前请先阅读[开发约束](DEVELOPMENT_CONSTRAINTS.md)与[架构](ARCHITECTURE_DECISIONS.md)。动手写代码之前，建议先开一个 Issue 说明打算接入的游戏和数据来源。

## 1. 现状：哪些是通用的，哪些只属于《明日方舟》

| 层 | 已经通用 | 目前只属于《明日方舟》 |
| --- | --- | --- |
| 数据源 | `DataSourceAdapter` 接口、`WidgetDefinition`、`WidgetUpdateCoordinator` 的配置、失败缓存与恢复 | `SklandArknightsAdapter`、`ArknightsParser` |
| 平台与账号 | 实例级的账号／角色绑定（`WidgetInstanceConfig`） | `SklandClient.parseRoles` 只保留 `appCode == "arknights"` 的角色；账号仓库只存森空岛账号 |
| 数据模型 | `WidgetSnapshot`、`WidgetMetric` | `WidgetTraining`、`core/Dashboard.kt` 中的指标标签与重置规则 |
| 桌面小部件 | 曝光刷新、后台任务、添加流程（`PinWidgetReceiver`） | `StatusWidgetProvider`／`OpaqueWidgetProvider`、`status_widget_*` 布局与 `StatusWidgetRenderer` |
| 应用界面 | `GameTabs` 的标签 ID 与选择回调、`Branding`、`WidgetPreviewStage` | 概览页的卡片与指标顺序、部件页只列出一款游戏 |

已规划的功能标识（`featureId`）：

| 功能标识 | 状态 |
| --- | --- |
| `skland.arknights.status` | 已实现 |
| `skland.endfield.status` | 未实现 |
| `miyoushe.genshin.status` | 未实现 |
| `miyoushe.starrail.status` | 未实现 |

## 2. 不能改动的部分

新游戏只能**新增**代码和资源，不能改变已发布内容的含义。下面几条约束摘自[开发约束 §1](DEVELOPMENT_CONSTRAINTS.md#1-已安装实例兼容)：

- 已有的 Provider、Activity、Receiver 类名，以及 `widget-resource-ids.txt` 中的资源 ID，保持不变。
- 不修改《明日方舟》的布局、`miuiWidgetVersion` 或小部件元数据。
- 不改变现有快照与缓存格式的含义。新字段必须可选，旧数据要能照常读取。
- 不在应用内提供获取凭据的教程、Cookie 键名或网址。

## 3. 接入步骤

### 3.1 定义标识

- **数据源 ID**（`providerId`）：例如 `skland.endfield`。在适配器的伴生对象里定义为 `const val ID`，参考 `SklandArknightsAdapter.ID`。其他地方只引用这个常量，不重复书写字符串。
- **功能标识**（`featureId`）：取上表中的规划值，与 `WidgetRuntime.FEATURE` 并列定义。
- **游戏标签 ID 与显示名**：写入 `ui/theme/GameBranding.kt` 的 `Branding`，界面上的游戏名只从这里取。

### 3.2 实现数据源

在 `providers/` 中实现 `DataSourceAdapter`：

- `id`：上一步定义的数据源 ID。
- `characters`：当前已保存账号下、属于这款游戏的全部角色。
- `fetch(config, now)`：返回 `WidgetSnapshot`。失败时抛出 `DataSourceFailure`，用 `RefreshFailure` 区分网络、授权失效、限流、数据异常等原因。同一角色在短时间内的重复请求要复用上次的成功结果，参考 `SklandArknightsAdapter` 的 20 秒窗口。

解析逻辑放在独立的 `*Parser` 中，不依赖 Android API，以便 JVM 测试。脱敏后的接口样本放在 `app/src/test/resources/<平台>/`，样本里不能包含真实的 UID、昵称或凭据。

### 3.3 账号与平台

- **同属森空岛的游戏**：复用 `SklandClient` 与现有账号，不需要用户再次登录。要做以下几件事：
  - 让 `parseRoles` 按 `appCode` 区分游戏，不再只保留《明日方舟》。`SklandRole` 与加密账号文件目前没有游戏字段，新增字段必须可选，旧记录按《明日方舟》读取。
  - `WidgetStorageProvider` 的 `OVERVIEW` 返回的角色要带上所属游戏的数据源 ID。`CHARACTERS` 已经按适配器给出数据源 ID，不需要再改。
  - 角色 UID 只在同一款游戏内唯一。`RoleDataStore` 的文件名、`UiRole.key` 与保存的选中角色目前都只由账号和角色组成，要加上数据源 ID，并保证现有缓存仍能读取（见[开发约束 §1](DEVELOPMENT_CONSTRAINTS.md#1-已安装实例兼容)）。
- **其他平台**：需要以下几部分：
  - 新的网络客户端与账号仓库。凭据继续用 `CredentialCipher` 加密，保存在 `noBackupFilesDir` 中。
  - 新的登录方法，在 `StorageContract` 中定义。
  - 平台选择页（`PlatformSelector`）中的新选项。
  - 新的网络地址必须写进隐私说明（见 3.7）。

### 3.4 注册到运行时

在 `widget/WidgetRuntime.kt` 中：

- 在 `WidgetUpdateCoordinator` 的定义表里加入 `featureId → WidgetDefinition(featureId, providerId)`。
- 把新适配器加入 `adapters` 列表。协调器的适配器表与 `CHARACTERS` 的数据源 ID 都从这里取。
- 读取与刷新路径中按 `providerId` 判断账号是否存在的分支，要覆盖新的数据源。

协调器的适配器契约测试见 `WidgetCoordinatorTest`，配置与快照的持久化用例见 `WidgetPersistenceTest`。新适配器应当通过同样的用例。

### 3.5 桌面小部件

- 新增浅色与深色两个 `AppWidgetProvider` 子类，在 `AndroidManifest.xml` 中声明，运行在 `:widgetProvider` 进程。小米桌面所需的 `meta-data` 照抄现有 Receiver。
- 把新 Provider 加入 `StatusWidgetProvider` 伴生对象中的 `providers` 列表。`WidgetRuntime`、`WidgetStorageProvider` 与 `PinWidgetReceiver` 都通过 `owned()` 判断实例是否属于本应用，不在列表中的实例，刷新与配置都会被忽略。`providerClass`／`theme` 目前只在两个《明日方舟》类之间按配色选择，要改为先按游戏选择。
- 新增 `res/xml/<游戏>_widget_info.xml`、布局与资源，命名不能与 `status_widget_*` 冲突。`previewLayout` 用带中性示例数据的静态预览布局，`initialLayout` 用加载占位布局，参照 `status_widget_preview_*` 与 `status_widget_loading_*`。
- 在 `strings.xml` 中新增小部件名称，格式为“游戏名 浅色”“游戏名 深色”。部件页通过 `widgetName` 显示同一个名称，不要另写一份。
- 渲染逻辑放在新的 Renderer 中。`widget` 包不能引用 Compose 或 `ui` 包。
- 首次发布后，把新资源的 ID 追加到 `app/widget-resource-ids.txt`，此后不再改变。

### 3.6 应用界面

- **游戏标签**：把新游戏加入 `AppScaffold` 的 `games` 列表。只显示已经实现的游戏。
- **概览**：按游戏拆分 `core/Dashboard.kt` 中的指标标签与说明函数。新游戏使用自己的卡片组合，界面规格遵守[界面设计约定](UI_DESIGN_CONVENTIONS.md)。
- **部件页与配置面板**：全部小部件按游戏列出；配置面板的角色列表只显示所选游戏的角色。预览统一使用 `WidgetPreviewStage`。`AppConfigSheet` 保存时固定写入 `WidgetRuntime.FEATURE`，`UiRole` 的数据源默认是森空岛《明日方舟》，两处都要改为取自所选角色。
- **品牌素材**：游戏与平台图标放在 `res/drawable-nodpi/`，在 `AppLogo` 中引用。

### 3.7 隐私、许可与文档

以下页面和文档需要同步更新：

- **数据与隐私**（`ui/pages/PrivacyPage.kt`）：新增的网络地址、保存的数据，并修改页内的更新日期。
- **开源许可**（`ui/pages/LicensesPage.kt`）：新的第三方组件，以及商标声明。
- **[素材来源](UI_ASSET_SOURCES.md)**：新图标的出处与权利人。
- **README**：功能、隐私与许可几节，以及 `CHANGELOG.md`。

## 4. 测试

- **JVM**：解析器、限流与失败映射、新快照字段的读写兼容；`ProbeManifestTest` 中的稳定资源 ID 与小部件元数据检查。
- **设备**（`tools/Run-DeviceTests.ps1`）：
  - `render` 与 `comparison`：新小部件的原生渲染与位图对照。
  - `appUi`：概览、部件页、配置面板。
  - 涉及导航或生命周期时，加上 `session-restore`、`page-swipe` 与 `nav-corner`。
- **真实桌面**：添加、刷新、编辑、重启后恢复。另外要确认已有的《明日方舟》小部件在升级后不受影响。

没有实际运行的检查一律写“未验证”。

## 5. 提交前检查清单

- [ ] 数据源 ID、功能标识与显示名各只定义一次，其余地方只引用常量
- [ ] 没有改动已有类名、资源 ID、布局或存储格式的含义
- [ ] 凭据只经由加密存储，不出现在日志、IPC 读返回或保存的状态中
- [ ] 隐私说明、开源许可、素材来源与 README 已更新
- [ ] JVM 测试与受影响的设备场景已通过，未运行的检查已写明“未验证”
