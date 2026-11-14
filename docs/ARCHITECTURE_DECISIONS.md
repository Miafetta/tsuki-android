# 架构

Tsuki 只有一个 Gradle 模块 `app`。应用界面使用 Jetpack Compose 与 Miuix 0.9.4，桌面小部件使用原生 RemoteViews。硬性边界见 [开发约束](DEVELOPMENT_CONSTRAINTS.md)，包分层见 [代码与仓库结构](PROJECT_STRUCTURE.md)，界面规格见 [界面设计约定](UI_DESIGN_CONVENTIONS.md)。

## 1. 依赖

- `miuix-ui`、`miuix-preference`、`miuix-icons`、`miuix-nav`、`miuix-squircle`、`miuix-blur`，版本固定 0.9.4。
- `kotlinx-serialization-json` 只用于界面状态：导航路由（`AppRoute`）与随重建保存的配置面板状态（`ConfigSheet`、`UiSession`）。快照、角色缓存、账号文件与跨进程数据的 JSON 用平台自带的 `org.json`。
- Compose `ui-tooling` 与 `ui-tooling-preview` 只用于 Debug 预览（`debugImplementation`，`@Preview` 只放 `src/debug`），Release 不包含。版本要与解析到的 Compose UI 一致（目前 1.12.0），所以带 `GradleDependency` 的 lint 豁免，设备验证后两者一同升级并去掉豁免；`ui-tooling` 排除了可选的 Material3 适配。

主题由 `ThemeController`／`MiuixTheme` 提供，支持跟随系统、浅色和深色；`TsukiTextStyles` 只缩小字号，不改变字重和行高。

## 2. 入口与页面

| 入口 | 说明 |
| --- | --- |
| `MainActivity` → `TsukiApp` | 应用图标或点击已配置桌面小部件的主体打开；从小部件进入时，首次加载完成后切到它绑定的角色（只生效一次）。三个标签页为概览、部件、我的 |
| `WidgetConfigActivity` | 系统 `APPWIDGET_CONFIGURE`、桌面长按编辑与点击未配置小部件（`tsuki://configure/<id>`）；校验实例归属后直接显示配置面板 |
| `SklandAccountActivity` | 系统添加流程中没有可用角色时进入，添加账号后返回继续配置 |
| `AccountEditor`／`PlatformSelector` | 添加账号、更新 Cred；平台行进入平台选择 |
| `WebLoginPage` | 在应用内网页中打开森空岛官方登录页，取得 Cred 后交回 `AccountEditor` 提交；网页数据由 `WebLoginData` 清除，边界见[开发约束 §3](DEVELOPMENT_CONSTRAINTS.md#3-凭据与账号数据) |
| `PrivacyPage`／`LicensesPage` | 我的 → 关于；许可证全文读取 `assets/third_party/*.txt` |
| `ConfigEditor` | 选择角色和配色，添加或编辑小部件 |
| 开发诊断 | 仅 Debug 构建且以 `--ez diagnostics true` 启动时显示 |

- `TsukiApp` 负责主题、导航和页面组装。
- `AppController` 持有当前页面、所选角色、配置面板、刷新以及添加到桌面的权限流程。
- `AppScaffold` 组合 `TopAppBar`、游戏标签、`PullToRefresh`、`TsukiNavDock` 与底部渐变遮罩，每个标签页独立滚动。页面可以通过底栏或横向滑动切换；无论哪种方式，都以 Pager 最终停留的页同步底栏与当前页，顶栏按钮只在两者一致时响应。
- 二级页面共用 `SecondaryScaffold`，顶栏使用与主页面相同的玻璃路径，标题同时作为无障碍窗格标题。数据与隐私、开源许可再共用 `InfoPage` 的文档式版式。
- `components` 中的组件只接收数据、回调和图像，不直接读取存储。
- 一次性提示由 `UiMessages` 在 STARTED 之后消费，`UiNotifier` 以系统 Toast 显示。
- 小部件预览只有一个入口 `WidgetPreviewStage`：概览空状态、部件页与配置面板共用同一渲染源、位图缓存与外观。配置面板的预览快照通过 `AppState.previewSnapshot` 只读获取。

## 3. 进程与数据流

主进程负责界面和用户操作。桌面 Provider、私有 `WidgetStorageProvider` 与 `WidgetRefreshJob` 运行在 `:widgetProvider` 进程。`PinWidgetReceiver` 在主进程接收桌面回调，再通过 IPC 保存配置。

- `AppState` 在 IO 线程调用私有 Provider，返回的 JSON 和快照在 Default 线程解析，主线程只负责赋值。Provider 核对调用方 UID，写入配置时另外校验实例归属。
- 启动时，屏幕一拿到 `AppState` 就开始第一次概览读取（`startInitialLoad`），让 IPC 和 `:widgetProvider` 的冷启动与首次组合同时进行；之后第一次 `load()` 直接等待这次读取，不重复调用，`finishedLoads` 仍然是每完成一次读取加 1。第一次读取完成并画上屏幕后，调用一次 `reportFullyDrawn()`。
- 三个 Activity 都用 `setTsukiContent` 设置内容：`ComposeView` 仍是内容区的第一个子 View（和 `setContent` 一致，状态恢复与 `setContent` 复用都依赖这一点），其上并列一个空的 `ScrollToTopHost`。HyperOS 4 点击状态栏时，会在当前窗口里找一个在屏幕上、`canScrollVertically(-1)` 为真且 `computeVerticalScrollOffset() > 0` 的 ViewGroup，再用反射调用它的 `smoothScrollToPosition(int)`（滚动距离很长时调用 `scrollToPosition(int)`）。Compose 列表不是 View，系统找不到，所以由这个容器报告当前页面能否往上滚，并把调用转给页面（`ScrollToTopEffect`）。容器不能套在内容外面：作为父级并报告可滚动时，会干扰界面本身的触摸和无障碍查找，设备测试已经证实了这一点。它没有子 View、不绘制、不处理触摸、对无障碍隐藏。这两个方法加了 `@Keep`，名字不能改。
- 品牌图标（`BrandLogos`）每个进程只解码一次，按显示所需的尺寸解码，并在 `TsukiApp` 开头于后台线程预加载；页面组合时不在主线程解码位图。`app/src/main/baseline-prof.txt` 为 Tsuki 与 Miuix 的代码提供 Baseline Profile（profileinstaller 已随 Compose 打包）。
- `StorageContract` 集中定义操作名与传输键，`SnapshotJson` 定义快照格式。
- 概览刷新和账号验证在 Binder 工作线程同步执行，读取角色缓存与实例快照。
- 桌面更新由 `WidgetRuntime` 渲染缓存，并通过 JobScheduler 调度 `WidgetRefreshJob` 获取数据与头像。广播和主线程不发网络请求。
- `WidgetUpdateCoordinator` 管理配置、失败缓存、取消与恢复；`DataSourceAdapter` 把接口数据转换成领域快照。同一角色 20 秒内的重复请求不发新请求，按上一次请求的结果应答：成功快照可以复用，失败重放同一失败类型，请求仍在进行或被取消时按限流处理；这不是周期轮询。
- 应用内预览与桌面共用同一套原生渲染逻辑，预览以 Bitmap 传回界面；组件进程不加载 Compose，所需图标导出为 VectorDrawable（来源见[素材来源](UI_ASSET_SOURCES.md)）。

## 4. 模糊材质

- 后端优先级为 HyperOS MiGlass → miuix-blur → 不透明，支持性判断与材质参数集中在 `ui/glass`。
- `GlassSession` 负责原生材质的初始化、增量更新和失败清理；`MiGlassView` 缓存反射方法，并只读监听系统材质开关。
- backdrop 只在 `HorizontalPager` 外侧的单一容器中录制，避免各页采样范围不一致。
- 可以模糊的范围与实现限制见[开发约束 §6](DEVELOPMENT_CONSTRAINTS.md#6-模糊与-hyperos-原生玻璃)，各表面的参数见[界面设计约定](UI_DESIGN_CONVENTIONS.md#玻璃范围与回退)。

## 5. 多游戏扩展

`DataSourceAdapter`、`WidgetDefinition`、`providerId`／`featureId` 与实例级的账号／角色绑定已用于《明日方舟》和《明日方舟：终末地》。两款游戏共用森空岛平台账号，各自解析和展示数据。哪些部分已经通用、接入步骤与检查清单见[添加新游戏支持](ADDING_A_GAME.md)。

## 6. 数据保护与升级兼容

凭据加密、迟到请求、稳定资源 ID 与小部件元数据的约束统一写在[开发约束](DEVELOPMENT_CONSTRAINTS.md) §1 与 §3。架构上对应的位置：

- 凭据：`providers/AccountRepository.kt`（加密存储）与 `AndroidCredentialKey`（Keystore 密钥）。
- 请求代数与身份核对：`WidgetUpdateCoordinator` 与森空岛游戏共用的 `SklandRoleAdapter`。
- 稳定资源 ID：`app/widget-resource-ids.txt`，由 `app/build.gradle.kts` 的 `--stable-ids` 应用，`ProbeManifestTest` 校验。

## 7. 测试分工

- **JVM 单元测试**：业务协议、账号安全、缓存与迁移、XML 契约和稳定资源 ID。
- **设备测试**：使用生产页面和内存样本，覆盖导航、菜单、账号校验、键盘、大字体、重建恢复、玻璃回退和桌面渲染对照。场景目录见 `tools/Run-DeviceTests.ps1`，共用的辅助方法在 `app/src/androidTest/.../ui/DeviceTestSupport.kt`。
- 每次改动必须运行的命令与“未验证”的记录规则见[开发约束 §8](DEVELOPMENT_CONSTRAINTS.md#8-测试与验收)。
