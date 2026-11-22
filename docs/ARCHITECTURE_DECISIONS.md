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
| `MainActivity` → `TsukiApp` | 应用图标、点击已配置桌面小部件的主体或点按提醒通知打开；从小部件或通知进入时，首次加载完成后切到它对应的角色（只生效一次）。三个标签页为概览、部件、我的 |
| `WidgetConfigActivity` | 系统 `APPWIDGET_CONFIGURE`、桌面长按编辑与点击未配置小部件（`tsuki://configure/<id>`）；校验实例归属后直接显示配置面板 |
| `SklandAccountActivity` | 系统添加流程中没有可用角色时进入，添加账号后返回继续配置 |
| `AccountEditor`／`PlatformSelector` | 添加账号、更新 Cred；平台行进入平台选择 |
| `WebLoginPage` | 在应用内网页中打开森空岛官方登录页，取得 Cred 后交回 `AccountEditor` 提交；网页数据由 `WebLoginData` 清除，边界见[开发约束 §3](DEVELOPMENT_CONSTRAINTS.md#3-凭据与账号数据) |
| `PrivacyPage`／`LicensesPage` | 我的 → 关于；许可证全文读取 `assets/third_party/*.txt` |
| `RemindersPage`／`ReminderAccountPage` | 我的 → 通知；`RemindersPage` 是总开关、状态、账号列表与后台运行，点账号进入 `ReminderAccountPage`，设置该账号各角色的提醒项目，见 §4 |
| `ConfigEditor` | 选择角色和配色，添加或编辑小部件；新建模式经 `WidgetPreviewPager` 支持横向切换全部小部件并与配色联动，编辑模式保持单卡 |
| 开发诊断 | 仅 Debug 构建且以 `--ez diagnostics true` 启动时显示 |

- `TsukiApp` 负责主题、导航和页面组装（由 `TsukiNavHost`／`TsukiNavEntry` 托管二级页转场，仅在转场外框停用超椭圆离屏层、避免原生玻璃跨页采样）。
- `AppRoot` 与 `AppController` 持有主页面脚手架控制、所选角色、配置面板、刷新以及添加到桌面的权限流程。
- `AppScaffold` 组合 `TsukiTopBar`、游戏标签、`PullToRefresh`、`TsukiNavDock` 与底部渐变遮罩，每个标签页独立滚动。页面可以通过底栏或横向滑动切换；无论哪种方式，都以 Pager 最终停留的页同步底栏与当前页，顶栏按钮只在两者一致时响应。
- 二级页面共用 `SecondaryScaffold`，顶栏使用与主页面相同的 `TsukiTopBar` 与玻璃路径（经 `TransitionGlass` 订阅转场状态），标题同时作为无障碍窗格标题。数据与隐私、开源许可再共用 `InfoPage` 的文档式版式。
- `components` 中的组件只接收数据、回调和图像，不直接读取存储。
- 一次性提示由 `UiMessages` 在 STARTED 之后消费，`UiNotifier` 以系统 Toast 显示。
- 小部件预览只有一个绘制入口 `WidgetPreviewImage`：概览空状态（`WidgetPreviewStage`，放在舞台上）、部件页与部件面板（`ScaledWidgetPreview`，直接放在背景上）共用同一渲染源、位图缓存与外观。部件面板的预览快照通过 `AppState.previewSnapshot` 只读获取。

## 3. 进程与数据流

主进程负责界面和用户操作。桌面 Provider、私有 `WidgetStorageProvider`、`WidgetRefreshJob` 与 `ReminderJob` 运行在 `:widgetProvider` 进程。`PinWidgetReceiver` 在主进程接收桌面回调，再通过 IPC 保存配置。

- `AppState` 在 IO 线程调用私有 Provider，返回的 JSON 和快照在 Default 线程解析，主线程只负责赋值。Provider 核对调用方 UID，写入配置时另外校验实例归属。
- 启动时，屏幕一拿到 `AppState` 就开始第一次概览读取（`startInitialLoad`），让 IPC 和 `:widgetProvider` 的冷启动与首次组合同时进行；之后第一次 `load()` 直接等待这次读取，不重复调用，`finishedLoads` 仍然是每完成一次读取加 1。第一次读取完成并画上屏幕后，调用一次 `reportFullyDrawn()`。
- 三个 Activity 都用 `setTsukiContent` 设置内容：`ComposeView` 仍是内容区的第一个子 View（和 `setContent` 一致，状态恢复与 `setContent` 复用都依赖这一点），其上并列一个空的 `ScrollToTopHost`。HyperOS 4 点击状态栏时，会在当前窗口里找一个在屏幕上、`canScrollVertically(-1)` 为真且 `computeVerticalScrollOffset() > 0` 的 ViewGroup，再用反射调用它的 `smoothScrollToPosition(int)`（滚动距离很长时调用 `scrollToPosition(int)`）。Compose 列表不是 View，系统找不到，所以由这个容器报告当前页面能否往上滚，并把调用转给页面（`ScrollToTopEffect`）。容器不能套在内容外面：作为父级并报告可滚动时，会干扰界面本身的触摸和无障碍查找，设备测试已经证实了这一点。它没有子 View、不绘制、不处理触摸、对无障碍隐藏。这两个方法加了 `@Keep`，名字不能改。
- 品牌图标（`BrandLogos`）每个进程只解码一次，按显示所需的尺寸解码，并在 `TsukiApp` 开头于后台线程预加载；页面组合时不在主线程解码位图。`app/src/main/baseline-prof.txt` 为 Tsuki 与 Miuix 的代码提供 Baseline Profile（profileinstaller 已随 Compose 打包）。
- `StorageContract` 集中定义操作名与传输键，`SnapshotJson` 定义快照格式。
- 概览刷新和账号验证在 Binder 工作线程同步执行，读取角色缓存与实例快照。
- 桌面更新由 `WidgetRuntime` 渲染缓存，并通过 JobScheduler 调度 `WidgetRefreshJob` 获取数据与头像。广播和主线程不发网络请求。
- `WidgetUpdateCoordinator` 管理配置、失败缓存、取消与恢复；`DataSourceAdapter` 把接口数据转换成领域快照。同一角色 20 秒内的重复请求不发新请求，按上一次请求的结果应答：成功快照可以复用，失败重放同一失败类型，请求仍在进行或被取消时按限流处理；这不是周期轮询。定时的后台请求只用于用户开启的提醒与限流后的一次补刷，边界见[开发约束 §2](DEVELOPMENT_CONSTRAINTS.md#2-进程边界)。
- 应用内预览与桌面共用同一套原生渲染逻辑，预览以 Bitmap 传回界面；组件进程不加载 Compose，所需图标导出为 VectorDrawable（来源见[素材来源](UI_ASSET_SOURCES.md)）。

## 4. 后台提醒

调度边界见[开发约束 §2](DEVELOPMENT_CONSTRAINTS.md#2-进程边界)，文件与协议的兼容要求见[开发约束 §1](DEVELOPMENT_CONSTRAINTS.md#1-已安装实例兼容)。

- **数据位置**：总开关、规则、触发记录与各角色的检查时间都在 `:widgetProvider` 中，由 `ReminderStore` 存成一个文件；每次读写和账号变更一样持有 `WidgetRuntime` 的锁。设置页（`RemindersPage`）只经私有 Provider 的四个提醒操作读写，每次得到总开关和全部规则；触发记录与检查时间不出组件进程。保存时核对规则的指标属于该角色的游戏、角色属于已保存的账号。登出、重新登录或更新 Cred、重置账号时，在账号变更的同一把锁里删除不再存在的角色的规则与记录。
- **判定**：都是纯函数。`core/reminder` 的 `ReminderEvaluator` 按能力登记（`ReminderCapabilities`）判定一条规则，`ReminderObservations` 把快照读成各指标的观测，`ReminderPlanner` 决定判定哪些规则、发出哪些通知、每个角色下次何时检查。规则按角色保存，不按小部件，同一角色只判定一次。规则从未满足变为满足时报告一次，之后只有新数据显示未满足才重新生效。
- **唯一的提醒任务**：只有一个 `ReminderJob`（jobId `-1`，持久任务）。一次运行检查所有到期的角色，结束时按所有角色中最早的下一次检查重排。保存或删除规则、切换总开关、账号变更和每次校准之后都会重排；任务运行期间的重排推迟到运行结束，由结束时的那次按最新状态重排。没有需要检查的角色时（总开关关闭、没有开启的规则、系统通知被关闭）取消任务。
- **检查时间**：每个角色取各规则预计达到条件的时间与上次校准后 3 小时中较早的一个，且不早于上次请求后 30 分钟。请求失败后，下一次等待该角色已经没有新数据的时长（至少 30 分钟，最多 3 小时），从未取得数据的角色等 3 小时；账号被限流时等到限流结束。请求需要等待时，可以按已知时间推算的规则在预计时间只用缓存判定，不发请求。
- **校准与锁顺序**：任何途径取得新快照（小部件刷新、概览刷新、提醒任务的请求）时，适配器的 `onSnapshot` 写入角色缓存后，把快照交给单线程的判定执行器，按新数据判定该角色的规则并记一次校准。`onSnapshot` 在取数路径里持有账号仓库的锁，所以只提交快照、不等待；执行器先拿 runtime 锁，再只拿提醒、账号与角色缓存这些叶子锁。账号变更的顺序是 runtime → 账号仓库，两者不会形成环。提醒任务的请求在所有锁之外逐个角色发出，最后也在这个执行器上收尾。
- **推算**：没有新数据时，只有能力登记允许推算的模式可以按已知的恢复或完成时间判定为达到条件，并且这条规则必须已经判定过一次新数据，以免用开启前的缓存重报已经发生过的事。
- **通知**：每款游戏一个通知渠道（`reminder.<数据源 ID>`），每条规则一条通知，同一规则的新通知替换旧的。点按通知打开 `MainActivity` 并切到该角色的概览；角色放在显式 Intent 的 extras 中，不用 `tsuki://` 深链。系统比较 `PendingIntent` 时不看 extras，所以 Intent 用 `setIdentifier` 按角色区分，否则后一条通知会改变前一条的去向。系统不会显示的通知（通知或该游戏的渠道已关闭）不发送，规则仍照常判定并记录，通知恢复后不补发。

## 5. 模糊材质

- 后端优先级为 HyperOS MiGlass → miuix-blur → 不透明，支持性判断与材质参数集中在 `ui/glass`。
- `GlassSession` 负责原生材质的初始化、增量更新和失败清理；`MiGlassView` 缓存反射方法，并只读监听系统材质开关。
- `TransitionGlass` 统一订阅转场活跃度与玻璃开关，避免转场期间逐帧重组页面；`GlassShadows` 的 `chipRowShadow` 为未选中游戏胶囊在滚动后统一绘制并挖空整行轮廓的对称外投影。
- backdrop 只在 `HorizontalPager` 外侧的单一容器中录制，避免各页采样范围不一致。
- 可以模糊的范围与实现限制见[开发约束 §6](DEVELOPMENT_CONSTRAINTS.md#6-模糊与-hyperos-原生玻璃)，各表面的参数见[界面设计约定](UI_DESIGN_CONVENTIONS.md#玻璃范围与回退)。

## 6. 多游戏扩展

`DataSourceAdapter`、`WidgetDefinition`、`providerId`／`featureId` 与实例级的账号／角色绑定已用于《明日方舟》和《明日方舟：终末地》。两款游戏共用森空岛平台账号，各自解析和展示数据。哪些部分已经通用、接入步骤与检查清单见[添加新游戏支持](ADDING_A_GAME.md)。

## 7. 数据保护与升级兼容

凭据加密、迟到请求、稳定资源 ID 与小部件元数据的约束统一写在[开发约束](DEVELOPMENT_CONSTRAINTS.md) §1 与 §3。架构上对应的位置：

- 凭据：`providers/AccountRepository.kt`（加密存储）与 `AndroidCredentialKey`（Keystore 密钥）。
- 请求代数与身份核对：`WidgetUpdateCoordinator` 与森空岛游戏共用的 `SklandRoleAdapter`。
- 稳定资源 ID：`app/widget-resource-ids.txt`，由 `app/build.gradle.kts` 的 `--stable-ids` 应用，`ProbeManifestTest` 校验。

## 8. 测试分工

- **JVM 单元测试**：业务协议、账号安全、缓存与迁移、XML 契约、稳定资源 ID，以及提醒的判定、调度、文件格式与通知内容。
- **设备测试**：使用生产页面和内存样本，覆盖导航、菜单、账号校验、键盘、大字体、重建恢复、玻璃回退、桌面渲染对照和通知提醒设置页。场景目录见 `tools/Run-DeviceTests.ps1`，共用的辅助方法在 `app/src/androidTest/.../ui/DeviceTestSupport.kt`。
- 每次改动必须运行的命令与“未验证”的记录规则见[开发约束 §8](DEVELOPMENT_CONSTRAINTS.md#8-测试与验收)。
