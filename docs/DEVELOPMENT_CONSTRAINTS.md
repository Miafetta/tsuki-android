# 开发约束

适用于之后的所有改动，汇总已安装实例兼容、数据安全、界面实现和验收方面不能放宽的边界。界面规格见 [UI_DESIGN_CONVENTIONS.md](UI_DESIGN_CONVENTIONS.md)，进程与数据流见 [ARCHITECTURE_DECISIONS.md](ARCHITECTURE_DECISIONS.md)，代码放置与格式见 [PROJECT_STRUCTURE.md](PROJECT_STRUCTURE.md)。

## 1. 已安装实例兼容

桌面宿主（尤其是小米桌面）会跨版本缓存布局、资源 ID 和 Provider 组件名。以下内容改动后无法靠应用升级修正，只有在真实桌面上完成升级、刷新和恢复验证后才能调整：

- `AndroidManifest.xml` 中的 Activity、Provider、Receiver、Service 以及被 `PendingIntent`／`ComponentName`／`tsuki://` 深链引用的类：不移动、不改名。需要调整职责时，原类保留为薄壳。提醒任务 `ReminderJob` 是持久任务，系统在重启和应用更新后按组件名恢复它，所以同样适用。
- 已发出的通知里的 `PendingIntent` 会跨应用更新保留：`ReminderLaunch` 的 extras 键与 `MainActivity` 对它们的解析保持兼容。提醒的通知渠道 ID（`ReminderNotifications.channelId`）发布后不改，用户对声音与振动的设置按渠道 ID 保存。
- `app/widget-resource-ids.txt` 与 `--stable-ids`：资源名和编号保持含义，已删除的编号不复用。只有钉入的条目编号固定；未钉资源的编号会随新增资源移动，所以 RemoteViews 布局与动作引用的资源（含被布局引用的 `strings.xml` 条目）都必须钉入。HyperOS 桌面会沿用已放置部件中同一编号的位图（PNG），应用更新后也不重新读取，所以重绘位图时要换新资源名，旧名在 `ProbeManifestTest` 的退役清单中保留编号；XML 形状与颜色不受影响。
- Provider 元数据：`miuiWidgetVersion=57`（应用级、所有部件共用；小部件布局、外观或元数据变化时只升不降）、4×2 尺寸、《明日方舟》浅色／深色两套主题布局、终末地固定深色一套，以及曝光元数据；资源收缩保持关闭。
- 桌面部件资源：`res/layout/status_widget*.xml`、`endfield_widget*.xml`、`probe_widget.xml`、`res/drawable*/widget_*` 与 `avatar_placeholder_*`、`res/values*/widget_palette.xml`、`res/values*/widget_placeholder.xml`、`colors.xml`、`miuix_widget_tokens.xml`、`widget_endfield_dimens.xml`、`widget_retired_ids.xml`（已退役的 View ID，只为保留编号）、`styles.xml` 中的 `Widget*` 样式、`res/xml/*`，以及 `strings.xml` 中被小部件布局引用的 `status_*` 与 `endfield_*` 条目。
- 跨进程协议与存储：`StorageContract`（包括提醒操作 `REMINDERS`、`SAVE_REMINDER`、`DELETE_REMINDER`、`SET_REMINDERS_ENABLED`）、`StorageCalls`、`SnapshotJson`，以及 `core/` 的实例存储、角色缓存 schema、协调器和提醒文件（`ReminderStore` 写在 `noBackupFilesDir` 的 `reminders/v1.json`，格式由 `ReminderJson` 定义）。

## 2. 进程边界

- 桌面 Provider、私有 `WidgetStorageProvider` 与 `WidgetRefreshJob` 运行在 `:widgetProvider`；`widget` 包不得引用 Compose 或 `ui` 包。
- 广播和主线程不发网络请求；真实请求只由后台任务执行。不自动签到。20 秒曝光间隔，以及同一角色 20 秒内按上一次请求的结果应答（复用成功快照、重放失败）不是轮询。刷新结果为限流并带有重试时间时，可以在该时间到点后为这个部件补刷一次；只补一次、不循环，也不是轮询。
- 定时的后台请求只有两种：用户开启的提醒，以及上一条所说的限流后补刷一次；除此之外不添加定时轮询。提醒调度的边界：
  - 提醒默认全部关闭；只为用户开启了提醒的角色调度。
  - 只用 JobScheduler，提醒的 `JobService` 运行在 `:widgetProvider`；jobId 使用负数区间，与 `WidgetRefreshJob` 以 appWidgetId 作 jobId 的正数空间分开。不用前台服务，不申请精确闹钟，不申请忽略电池优化。
  - 提醒任务为同一角色发起的两次请求至少间隔 30 分钟。
  - 为提醒新增的权限只有 `POST_NOTIFICATIONS`（用户开启提醒时请求）与 `RECEIVE_BOOT_COMPLETED`（重启后恢复已安排的任务）。
  - 系统的自启动与省电策略由用户自行设置；Tsuki 只说明并提供系统设置入口，不修改系统设置。
- 私有 Provider 核对调用方 UID，配置写入另外校验实例确实归属本应用。

## 3. 凭据与账号数据

- Cred 使用 Android Keystore（AES-256-GCM）加密，保存在不参与备份的目录，原子写入。
- Cred 不进入 saved instance state、日志或崩溃信息；`AccountDraft` 的 Saver 只保存账号名称和正在更新的账号 ID。任何 IPC 操作都不返回 Cred，已保存的 Cred 不读回界面进程。从“更新 Cred”打开编辑页时，Cred 输入框为空，占位文字提示“留空则不修改当前 Cred”；留空提交时，`StorageContract.REVERIFY` 在 `:widgetProvider` 中用已保存的 Cred 重新验证（例如只修改账号名称）。
- 迟到的请求不能覆盖新的绑定，也不能恢复已删除的角色（核对身份归属与请求代数）。
- 删除账号、重置存储和添加到桌面都保留明确的确认流程与忙碌保护。
- 应用内自动取得 Cred 只有一种方式：网页登录页（`WebLoginPage`）在应用内网页中打开森空岛官方页面，由用户在官方页面完成登录和人机验证；Tsuki 只从当前官方页面读取一次 Cred（起始页、域名与存储键集中在 `SklandWebLogin`），回填到添加页的输入框，由用户确认后再提交验证；读取后随即清除网页的 Cookie、存储、缓存与历史。添加页可以用一段说明文字写明手动获取 Cred 的方法（在电脑浏览器的开发者工具中，复制官方网页本地存储里的同一个键），但不在应用内实现其他获取途径。离开页面时再清除一次；旋转等配置变化除外，登录中途进程被杀时，下次启动按标记文件清除。
- Tsuki 不读取、不保存账号密码、验证码和通行证 token；原生界面不提供账号、密码或验证码输入；不绕过人机验证。官方网站对手机只提供下载页，所以网页登录请求电脑版页面：User-Agent 改为同一 Chrome 主版本的桌面写法，等同浏览器的“桌面版网站”开关；除此之外不改动、不伪造设备指纹或其他设备信息。电脑版页面把 viewport 锁成手机宽度、禁止缩放，固定顶栏里的登录按钮在手机上看不到，所以只改写官方页面的 viewport 标签（宽度 1200，允许缩放），每次加载缩小一次以适应屏幕宽度；页面其他内容一律不改。
- 电脑版在弹窗中登录，页面不一定跳转，因此登录页打开期间每秒在本机读取一次当前官方页面存储中的 Cred 键，取得后停止。这是本地读取，不发请求，不属于 §2 所说的轮询。
- 网页登录的 WebView 只在主进程使用：不添加 JavaScript 接口；主框架导航只放行 `SklandWebLogin` 白名单中的 https 域名，其他一律拦截，不转交外部应用；禁用文件与内容访问、多窗口、地理位置和混合内容，不处理下载，`isSaveEnabled = false`。
- 手动填写 Cred 的表单永久保留，作为兜底；它只接受 Cred 值，不解析 Cookie 或其他格式。输入框带“粘贴”和“显示／隐藏”按钮：只在用户点击粘贴时读取一次剪贴板，默认隐藏内容。账号名称是可选的别名，按填写的原样保存，可以为空；为空时界面以平台名称称呼该账号（例如“森空岛账号”），不从某个游戏的角色推断账号名称。编辑页取得 Cred（网页登录、粘贴或输入）后，如果名称框为空，`StorageContract.PLATFORM_NAME` 在 `:widgetProvider` 中读取一次森空岛账号资料（`/api/v1/user`），只取 `data.user.nickname` 填入名称框，由用户修改或清空；用户已填的名称不覆盖，读取失败时名称留空，不阻断添加。资料的其他字段不保存、不传回界面进程，昵称只在用户保存账号后作为名称存入本机。
- `SklandClient` 只请求 `SklandClient.PATHS` 中的路径：`/api/v1/auth/refresh`、`/api/v1/game/cards`（验证含终末地角色的账号时，只取终末地角色带 `#编号` 的显示名称）、`/api/v1/game/player/binding`、`/api/v1/game/player/info`、`/api/v1/user`（只取昵称）、`/api/v1/user/teenager`（只取用户 ID，保存在内存中）与 `/web/v1/game/endfield/card/detail`；新增路径要同步隐私说明。

## 4. 部件放置

- 标准路径使用显式、非导出、可变、一次性的 `PendingIntent` 接收宿主回填的真实实例 ID；迟到回调不覆盖已配置的实例。
- HyperOS 添加流程可能不交付成功回调。兜底只用一次性私有暂存：记录本次选择、请求 token、原有 ID 集合、开机计数和开始时间，仅匹配**唯一**新增、未配置、且 Provider 的配色与功能（游戏）都一致的实例；出现多个候选时放弃，不猜测账号。暂存最长两分钟，跨重启失效。
- 添加前检查小米“桌面快捷方式”权限；状态不可读时要求用户明确确认，取消不创建实例。

## 5. 界面依赖与实现

- Miuix 固定 0.9.4（ui／preference／nav／squircle／icons／blur）。不引入 Material3 或其他 UI 库；新增依赖需维护者同意。
- 颜色只取 `MiuixTheme.colorScheme`，字号只取 `textStyles`（由 `TsukiTextStyles` 统一缩放）；偏离库默认只允许出现在 UI_DESIGN_CONVENTIONS 的自定义清单中，用组件参数或新的小组件实现，不复制、不改写 Miuix 组件。
- Compose ui-tooling 只用于 Debug（`debugImplementation`，`@Preview` 只放 `src/debug`），Release 不包含。
- 开发诊断必须同时满足 Debug 构建和 `diagnostics=true` 启动参数；Release 不显示诊断入口，测试 Provider 保持禁用。

## 6. 模糊与 HyperOS 原生玻璃

- 后端顺序：HyperOS MiGlass → miuix-blur 公开 API → 不透明外观。“界面模糊效果”关闭或两种后端都不可用时，布局与测量不变。
- MiGlass 通过反射调用 `View` 上的公开方法：只用 `View.getMethod` 查找，不 `setAccessible`、不绕过隐藏 API 限制、不申请 root、不写系统设置。能力判断只读 `persist.sys.bionic_material_supported` 与 Secure 中的 `background_blur_enable`／`material_style`。
- 方法签名与 42 项材质参数来自已验证的源码和 ROM 字段映射，不凭猜测增改。任一初始化或更新步骤失败时，逐项清理原生状态并回退；失败实例不在滚动或重组中重试，View／配置重建后才重新探测。
- 模糊只用于顶栏背景、顶栏按钮、游戏胶囊和底栏胶囊；卡片、菜单、弹层不模糊，底栏下方遮罩只是颜色渐变。文字不模糊，只有顶栏标题的折叠／展开过渡是下一条所列的例外。
- 不自写 RuntimeShader／RenderEffect 模糊管线。**只有两个例外**：一是菜单内容层在展开／收起转场中的短暂前景模糊（最大 6dp，只在打开显现期（按钮菜单 110ms、下拉字段 50ms）和关闭 65ms 隐去期，稳定状态必须为 null）；二是顶栏标题在大标题折叠／展开过渡中的短暂前景模糊：大标题随折叠距离最大 σ 约 2.3dp，居中标题显现与隐去时最大 σ 约 1.85dp；展开静止与完全折叠静止时必须为 null。两者都不得扩展到其他位置。
- 玻璃表面在滚动时只改变透明度、色调和阴影，不改变模糊半径、不重建后端（顶栏背景的 miuix-blur 回退路径是渐进模糊，半径只随位置渐变，不随滚动变化；顶栏标题过渡的前景模糊按上一条）；不对承载背景采样的容器套整体 alpha，用 DstIn 遮罩控制已采样结果。
- backdrop 只在 HorizontalPager 外的单一容器录制，相邻页不分别注册；只有存在 miuix-blur 消费者时才录制。
- MiGlass 目前只在 HyperOS 4 实机上验证过，其他 ROM 视为未验证，必须能安全回退。

## 7. 签名

- 正式包由维护者用仓库外的私钥与签名链签名（`tools/Sign-Release.ps1`），不要重新生成或覆盖签名文件，也不要把任何密钥、密码文件或 `local.properties` 加入仓库。
- 已安装正式版的设备不能用普通 Debug 证书覆盖安装；在这类设备上测试需要沿用同一签名链（`tools/Sign-Debug.ps1`）。其他开发者应在未安装正式版的设备上使用自己的 Debug 签名。

## 8. 测试与验收

- 构建与检查按改动范围分三档，`warningsAsErrors` 保持开启，不添加 lint baseline 或全局关闭项：

  | 时机 | 命令 |
  | --- | --- |
  | 每次改动 | `.\gradlew.bat :app:testDebugUnitTest :app:lintDebug` |
  | 改动 `src/androidTest`，或准备上设备 | 另加 `:app:assembleDebug :app:assembleDebugAndroidTest` |
  | 发布前 | 另加 `:app:assembleRelease :app:lintRelease` |

- 设备测试只使用生产页面与内存样本，不保存账号或桌面实例；不卸载应用、不清除数据、不修改系统设置（测试临时固定的屏幕方向在结束后恢复原状）。统一入口为 `tools/Run-DeviceTests.ps1`，只跑与改动相关的场景。
  - `web-login`、`web-login-kill`、`web-login-after-kill` 需要单独指定：它们会打开森空岛官方首页（不登录），离开时清除测试设备上 Tsuki 的网页数据。`web-login` 还会临时替换剪贴板，结束后恢复原内容。
  - `reminders` 在默认列表中：它在内存样本上打开“我的”→“通知提醒”，核对账号行与排在最后的“后台运行”，进入账号页核对每个角色的指标行（下拉都显示“关闭”、开关全关、没有滑块），再用内存中一条已开启的规则重新进入，核对它的下拉值与紧接在下面的阈值滑块（下方是当前值与终点，没有“阈值”标题和步进按钮），每次都逐级返回两层；所有提醒写操作都被拒绝，不保存规则、不改总开关、不申请通知权限、不发通知。
  - `widget-editor-save`、`widget-editor-change` 需要单独指定，并且要求桌面上已有一个配置好的 Tsuki 小部件（没有时记为跳过，测试不创建实例）：前者原样保存该实例的配置，后者修改配色与角色并保存，再逐项改回并核对，中途失败时恢复原配置；改角色会重新拉取该角色的数据。
- 没有实际执行的设备测试、视觉检查或性能测量一律记为“未验证”。编译与 JVM 测试不能代替真实桌面宿主、模糊观感、GPU 开销和手势手感的检查。
