# 添加新游戏支持

本文说明如何为 Tsuki 接入一款新游戏：同在森空岛上的游戏（《明日方舟：终末地》就是这样接入的，可作参照），或其他平台上的游戏。开始之前请先阅读[开发约束](DEVELOPMENT_CONSTRAINTS.md)与[架构](ARCHITECTURE_DECISIONS.md)。动手写代码之前，建议先开一个 Issue 说明打算接入的游戏和数据来源。

## 1. 现状：哪些是通用的，哪些由每款游戏各自实现

| 层 | 已经通用 | 按游戏或平台各自实现 |
| --- | --- | --- |
| 数据源 | `DataSourceAdapter` 接口、`WidgetDefinition`、`WidgetUpdateCoordinator` 的配置、失败缓存与恢复；森空岛游戏共用的 `SklandRoleAdapter`（账号查找、20 秒窗口、身份核对与限流退避） | 每款游戏的适配器与解析器：`SklandArknightsAdapter`／`ArknightsParser`、`SklandEndfieldAdapter`／`EndfieldParser` |
| 平台与账号 | 实例级的账号／角色绑定（`WidgetInstanceConfig`）；森空岛绑定列表按游戏解析（`SklandGame`、`SklandRoles`）；角色缓存、界面角色键与选中角色都带数据源 ID | 账号仓库只存森空岛账号；应用界面只列出 `AppState` 中 `SHOWN_PROVIDERS` 的游戏 |
| 数据模型 | `WidgetSnapshot`、`WidgetMetric`（含稳定的 `metricId`） | 只属于《明日方舟》的 `WidgetTraining`，以及 `core/Dashboard.kt` 中它的指标标签与重置规则 |
| 桌面小部件 | 曝光刷新、后台任务、添加流程（`PinWidgetReceiver`）；Provider 按功能与配色选择（`StatusWidgetProvider.providerClass`、`feature`、`theme`）；`StatusWidgetRenderer` 按实例的功能选择卡片 | 每款游戏自己的 Provider 子类、布局与卡片渲染（方舟：浅色与深色两个 Provider、`status_widget_*` 与 `StatusWidgetRenderer`；终末地：一个固定深色的 Provider、`endfield_widget*` 与 `EndfieldCard`） |
| 应用界面 | `GameTabs` 的标签 ID 与选择回调、`Branding`、`WidgetPreviewStage`；部件页按 `widgetCatalog` 列出所有游戏的部件 | 各游戏的概览卡片与指标顺序（方舟在 `OverviewPage.kt`，终末地在 `EndfieldOverview.kt`） |

已规划的功能标识（`featureId`）：

| 功能标识 | 状态 |
| --- | --- |
| `skland.arknights.status` | 已实现 |
| `skland.endfield.status` | 已实现 |
| `miyoushe.genshin.status` | 未实现 |
| `miyoushe.starrail.status` | 未实现 |
| `miyoushe.zzz.status` | 未实现 |

## 2. 不能改动的部分

新游戏只能**新增**代码和资源，不能改变已发布内容的含义。下面几条约束摘自[开发约束 §1](DEVELOPMENT_CONSTRAINTS.md#1-已安装实例兼容)：

- 已有的 Provider、Activity、Receiver 类名，以及 `widget-resource-ids.txt` 中的资源 ID，保持不变。
- 不修改已发布小部件（《明日方舟》与终末地）的布局或元数据。`miuiWidgetVersion` 是应用级、所有部件共用的，新增部件时只升不降。
- 不改变现有快照与缓存格式的含义。新字段必须可选，旧数据要能照常读取。
- 应用内自动获取凭据只能像森空岛一样使用官方网页登录；手动获取的方法只写成添加页上的一段说明文字。边界见[开发约束 §3](DEVELOPMENT_CONSTRAINTS.md#3-凭据与账号数据)。

## 3. 接入步骤

### 3.1 定义标识

- **数据源 ID**（`providerId`）：格式为 `<平台>.<游戏>`，例如 `skland.endfield`。森空岛游戏在 `SklandGame` 中登记（终末地是 `SklandGame.ENDFIELD.providerId`；《明日方舟》沿用已安装版本写下的 `SklandArknightsAdapter.ID`，即 `skland`，不能改）；其他平台在适配器的伴生对象里定义 `const val ID`。其他地方只引用这个常量，不重复书写字符串。
- **功能标识**（`featureId`）：取上表中的规划值，与 `WidgetRuntime.FEATURE` 并列定义。
- **游戏标签 ID 与显示名**：写入 `ui/theme/GameBranding.kt` 的 `Branding`，界面上的游戏名只从这里取。

### 3.2 实现数据源

在 `providers/` 中实现 `DataSourceAdapter`：

- `id`：上一步定义的数据源 ID。
- `characters`：当前已保存账号下、属于这款游戏的全部角色。
- `fetch(config, now)`：返回 `WidgetSnapshot`。失败时抛出 `DataSourceFailure`，用 `RefreshFailure` 区分网络、授权失效、限流、数据异常等原因。同一角色在短时间内的重复请求不再发请求，而是按上一次请求的结果应答：成功时复用这次的结果，失败时重放同一失败类型（不返回更早的成功结果），请求仍在进行或被取消时按限流处理。参考森空岛游戏共用的 `SklandRoleAdapter` 的 20 秒窗口。

每个指标都设置 `metricId`（格式为 `<游戏>.<指标>`，例如 `endfield.sanity`），提醒等功能按它识别指标，不按中文标签识别；2.2.0 之前缓存的《明日方舟》快照没有它，由 `core/MetricIds.kt` 按标签补上（标签兜底只对《明日方舟》数据源生效）；2.2.0、2.2.1 写下的终末地部件记录也没有它，读回后保持为空，直到下次刷新。

解析逻辑放在独立的 `*Parser` 中，不依赖 Android API，以便 JVM 测试。脱敏后的接口样本放在 `app/src/test/resources/<平台>/`，样本里不能包含真实的 UID、昵称或凭据。

### 3.3 账号与平台

- **同属森空岛的游戏**：复用 `SklandClient` 与现有账号，不需要用户再次登录。要做以下几件事：
  - 在 `SklandGame` 中登记游戏的 `appCode` 与数据源 ID，并在 `SklandRoles` 中解析它的绑定结构。角色 UID 只在同一款游戏内唯一；需要服务器才能确定角色时，把服务器编进角色 ID（参考 `SklandRole.endfieldId`）。加密账号文件中没有游戏字段的旧角色按《明日方舟》读取。
  - `WidgetStorageProvider` 的 `OVERVIEW` 返回的角色已带数据源 ID 与服务器，`CHARACTERS` 按适配器给出数据源 ID，都不需要再改。
  - `RoleDataStore` 与 `UiRole.key` 已带数据源 ID：《明日方舟》沿用已安装版本写下的路径与键，其他游戏的角色缓存按数据源分目录（见[开发约束 §1](DEVELOPMENT_CONSTRAINTS.md#1-已安装实例兼容)）。
  - 游戏完成概览与小部件后，把数据源加入 `AppState` 的 `SHOWN_PROVIDERS`，并在 `WidgetRuntime` 中把适配器加入 `sklandGames`（适配器表、概览刷新用的 `roleFetchers` 和账号变更时的重置都从它得到）、在 `featureFor` 中登记功能标识。
- **其他平台**：需要以下几部分：
  - 新的网络客户端与账号仓库。凭据继续用 `CredentialCipher` 加密，保存在 `noBackupFilesDir` 中。
  - 新的登录方法，在 `StorageContract` 中定义。
  - 平台选择页（`PlatformSelector`）中的新选项。
  - 新的网络地址必须写进隐私说明（见 3.7）。

### 3.4 注册到运行时

在 `widget/WidgetRuntime.kt` 中：

- 在 `WidgetUpdateCoordinator` 的定义表里加入 `featureId → WidgetDefinition(featureId, providerId)`。
- 把新适配器加入 `adapters` 列表（森空岛游戏加入 `sklandGames` 即可，见 3.3）。协调器的适配器表与 `CHARACTERS` 的数据源 ID 都从这里取。
- 读取与刷新路径中按 `providerId` 判断账号是否存在的分支，要覆盖新的数据源：森空岛游戏按 `SklandGame.providerIds` 判断，已自动覆盖；其他平台需要补上自己的判断。

协调器的适配器契约测试见 `WidgetCoordinatorTest`，配置与快照的持久化用例见 `WidgetPersistenceTest`。新适配器应当通过同样的用例。

### 3.5 桌面小部件

- 每款新游戏新增一个 `AppWidgetProvider` 子类，在 `AndroidManifest.xml` 中声明，运行在 `:widgetProvider` 进程。外观按下文随官方确定，不要求拆成浅色与深色两个部件；已发布的《明日方舟》Provider 保持兼容。小米桌面所需的 `meta-data` 参考现有 Receiver。
- 把新 Provider 加入 `StatusWidgetProvider` 伴生对象中的 `providers` 列表，并在 `providerClass`、`themes`、`theme` 与 `feature` 中登记。`WidgetRuntime`、`WidgetStorageProvider` 与 `PinWidgetReceiver` 都通过 `owned()` 判断实例是否属于本应用，不在列表中的实例，刷新与配置都会被忽略；`WidgetStorageProvider` 只接受与实例 Provider 同一功能的配置。参考终末地的 `EndfieldWidgetProvider`。
- 新增 `res/xml/<游戏>_widget_info.xml`、布局与资源，命名不能与 `status_widget_*` 冲突。`previewLayout` 用带中性示例数据的静态预览布局，`initialLayout` 用加载占位布局，参照 `status_widget_preview_*` 与 `status_widget_loading_*`。
- 在 `strings.xml` 中新增小部件名称，沿用官方小组件的名称。外观照官方：官方固定一种外观就只做一个 Provider（例如终末地固定深色）；官方随系统外观切换，就做一个 Provider，用 `values-night` 资源跟随系统；不再像《明日方舟》那样拆成浅色、深色两个部件。部件页通过 `widgetName` 显示同一个名称，不要另写一份。
- 渲染逻辑放在新的 Renderer 中。`widget` 包不能引用 Compose 或 `ui` 包。
- 首次发布后，把新资源的 ID 追加到 `app/widget-resource-ids.txt`，此后不再改变。

### 3.6 应用界面

- **游戏标签**：把新游戏加入 `Branding.games`（`AppRoot` 把它传给 `AppScaffold`），并把它的小部件加入同一文件的 `widgetCatalog`。只显示已经实现的游戏。
- **概览**：按游戏拆分 `core/Dashboard.kt` 中的指标标签与说明函数。新游戏使用自己的卡片组合，界面规格遵守[界面设计约定](UI_DESIGN_CONVENTIONS.md)。
- **部件页与配置面板**：全部小部件按游戏列出；配置面板的角色列表只显示所选游戏的角色。预览统一使用 `WidgetPreviewStage`。`AppConfigSheet` 按所选角色的数据源取功能标识（`WidgetRuntime.featureFor`）。
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
  - 涉及账号添加或网页登录时，加上 `web-login`（会打开官方首页，不登录）。
- **真实桌面**：添加、刷新、编辑、重启后恢复。另外要确认已有的《明日方舟》与终末地小部件在升级后不受影响。

没有实际运行的检查一律写“未验证”。

## 5. 提交前检查清单

- [ ] 数据源 ID、功能标识与显示名各只定义一次，其余地方只引用常量
- [ ] 没有改动已有类名、资源 ID、布局或存储格式的含义
- [ ] 凭据只经由加密存储，不出现在日志或保存的状态中；任何 IPC 读操作都不返回凭据，“更新凭据”留空时在组件进程中用已保存的凭据重新验证
- [ ] 隐私说明、开源许可、素材来源与 README 已更新
- [ ] JVM 测试与受影响的设备场景已通过，未运行的检查已写明“未验证”
