# 代码与仓库结构规则

适用于所有改动。与 `UI_DESIGN_CONVENTIONS.md`、`ARCHITECTURE_DECISIONS.md` 冲突时，界面规格以前者为准、进程与数据边界以后者为准；本文件只管"代码和文件放在哪、写成什么样"。

## 1. 不可移动、不可改名的类

被系统、桌面宿主或已安装实例引用的类与资源不得移动或改名，清单见[开发约束 §1](DEVELOPMENT_CONSTRAINTS.md#1-已安装实例兼容)。需要调整这些类的职责时，把逻辑抽到别的类，原类保留为薄壳。

## 2. 包与目录

- **包名必须与目录一致**：`app/src/main/java/cafe/miafetta/tsuki/ui/components/X.kt` 的包名是 `cafe.miafetta.tsuki.ui.components`。测试目录镜像主目录。
- 顶层分工：

| 包 | 职责 | 可依赖 |
| --- | --- | --- |
| `tsuki`（根） | Activity 入口与跨进程调用约定（`StorageContract`、`StorageCalls`） | 全部 |
| `tsuki.core` | 与 Android UI 无关的模型、存储、协调、纯计算 | 标准库、Android 基础 API |
| `tsuki.providers` | 数据源适配、网络、凭据、头像缓存 | `core` |
| `tsuki.widget` | RemoteViews 渲染、Provider、后台刷新（`:widgetProvider` 进程，**不得引用 Compose 或 `ui`**） | `core`、`providers` |
| `tsuki.ui` | 应用入口组合：`TsukiApp`、`AppScaffold` | `ui.*`、`core` |
| `tsuki.ui.state` | `AppState`（ViewModel）、`AppController`、消息、配置面板状态、路由、页面编号与偏好键 | `core`；**不得引用 Compose UI 组件** |
| `tsuki.ui.theme` | `TsukiAppTheme`、`TsukiTextStyles`、`Branding`（游戏／平台／配色名）与品牌图像 | Miuix |
| `tsuki.ui.glass` | 玻璃宿主、后端（MiGlass / miuix-blur / 不透明）、表面参数 | Miuix、`ui.theme` |
| `tsuki.ui.components` | 无状态、可预览的小组件；只接收数据与回调。共用的手势与动效辅助也放这里，可以带少量状态：`SlotMotion` 由底栏与部件页切换按钮共用；`PagerSwipeGuard` 要同时被切换按钮和 `AppScaffold` 引用，而组件不能引用上层的 `tsuki.ui`，所以放在这一层 | `ui.theme`、`ui.glass`、`core` 的模型、`state` 的数据类型 |
| `tsuki.ui.pages` | 三个标签页与二级页面（隐私、许可、添加账号、平台选择、网页登录） | `components`、`state` 的数据类型 |
| `tsuki.ui.sheets` | 配置面板、确认 / 权限弹窗等浮层 | 同上 |

- 依赖只能沿表格向上或同层，`components` 不得引用 `pages`、`sheets`、`state.AppState`。
- 主进程中的 `ui.*` 引用 `widget`／`providers` 只限：数据源与功能标识常量（适配器的 `ID`、`SklandGame`、`WidgetRuntime.FEATURE`、`ENDFIELD_FEATURE` 与 `featureFor`）、Provider 类及其按实例判断配色与功能的伴生函数（`StatusWidgetProvider`、`OpaqueWidgetProvider`、`EndfieldWidgetProvider`、`XiaomiProbeProvider`）、添加流程入口（`PinWidgetReceiver.request`、`DesktopShortcutPermission`、`WidgetStorageProvider.URI`），以及与桌面共用的无状态头像函数（`OfficialAvatars`、`WidgetBitmaps.kt`，只在 `ui.state` 中调用），和网页登录的常量与纯函数（`SklandWebLogin`，只在 `WebLoginPage` 中调用，不含网络客户端）。`WidgetRuntime` 实例、仓库与网络客户端属于 `:widgetProvider` 进程，只经由 `StorageContract` 调用。
- Debug 专用代码（预览、诊断开关、测试夹具）放 `src/debug` 或 `src/androidTest`，不得进入 `src/main` 的 Release 路径；确需在 main 中判断时只用 `BuildConfig.DEBUG`。

## 3. 文件与函数

- 一个文件只放一个主要声明（类或一个主 Composable），文件名与之相同；紧密相关的小型私有辅助可放同一文件。不出现 `ConfigSheet.kt` 里同时放 `AppPage`、`UiPreferences` 这类无关对象。
- 单文件不超过 300 行，单个函数（含 Composable）不超过 80 行；超过时按职责拆分，而不是压缩成长行。`WidgetRenderer.kt` 这类历史大文件在改动时顺带拆分，不单独大改。
- **行宽不超过 140 列**。不要把多个语句用 `;` 挤在一行，不要把整段 Modifier 链写在一行。
- 魔法数字：尺寸、时长、透明度等视觉常量集中在所属组件或 `ui.glass` 的常量对象中，并以名称表达用途；页面里不出现裸数字的模糊半径、alpha 和动画时长。
- 颜色只取 `MiuixTheme.colorScheme`；桌面部件颜色只取资源。
- 注释使用英文，解释"为什么"，不复述代码；引用文档时写文档路径。
- 用户可见文案目前仍写在 Kotlin 中（应用内）或 `strings.xml`（桌面小部件）。不在没有专门任务的情况下批量迁移，避免影响稳定资源 ID。
- **同一内容只写一处**：游戏名、平台名与配色名取自 `ui/theme/GameBranding.kt`；小部件名称与摘要格标签取自 `strings.xml`（桌面列表与静态预览布局也读取它们）；数据源 ID 取自各适配器的 `ID` 常量；应用内的小部件预览只用 `WidgetPreviewStage`；设备测试的辅助方法放在 `DeviceTestSupport.kt`。新增内容前先查找已有的定义。文档同理：约束写在 DEVELOPMENT_CONSTRAINTS，其他文档只链接，不复述。
- 不新增格式化 / 静态检查依赖（ktlint、detekt 等）；需要统一格式时用仓库根目录的 `.editorconfig`。

## 4. 状态与副作用

- 屏幕状态集中在 `ui.state`；Composable 不直接调用 `storageCall`、`SharedPreferences` 或 `ContentResolver`，经由 `AppState` / `AppController`。
- 一次性事件（提示、导航结果）用 `UiMessages` 等事件通道，不用"显示后再清空"的普通状态。
- 跨重建要保留的状态用 `rememberSaveable` 或 ViewModel；凭据永不进入 saved state 或日志，任何 IPC 读操作都不返回凭据，见[开发约束 §3](DEVELOPMENT_CONSTRAINTS.md#3-凭据与账号数据)。

## 5. 仓库目录

| 路径 | 内容 |
| --- | --- |
| `app/` | 应用源码、资源、单元测试与设备测试 |
| `gradle/`、`gradlew*`、根目录构建文件 | Gradle Wrapper 与构建配置 |
| `tools/` | 可复用脚本（设备测试、签名）；不放构建产物和一次性脚本 |
| `docs/` | 当前有效的约束与说明，过时内容直接修改或删除，不保留历史记录 |
| 根目录 `README.md`、`CHANGELOG.md`、`CONTRIBUTING.md`、`SECURITY.md`、`LICENSE` | 项目说明、更新日志、贡献指南、安全策略与许可证 |

以下内容不入库，由 `.gitignore` 排除：构建输出（`build/`）、APK／AAB、签名材料（密钥库、签名链、`.idsig`）、`local.properties`、IDE 配置、截图与录屏，以及维护者的本地记录。

公开文档只写结论，不写本机路径、设备地址、账号或实例 ID，也不链接被排除的目录。

## 6. 文档维护

- 改动行为、约束或界面规格时，同步更新对应文档；文档描述当前状态，不记录修改过程。
- 面向用户的变更写入 `CHANGELOG.md`。
