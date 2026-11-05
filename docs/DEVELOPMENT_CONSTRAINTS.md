# 开发约束

适用于之后的所有改动，汇总已安装实例兼容、数据安全、界面实现和验收方面不能放宽的边界。界面规格见 [UI_DESIGN_CONVENTIONS.md](UI_DESIGN_CONVENTIONS.md)，进程与数据流见 [ARCHITECTURE_DECISIONS.md](ARCHITECTURE_DECISIONS.md)，代码放置与格式见 [PROJECT_STRUCTURE.md](PROJECT_STRUCTURE.md)。

## 1. 已安装实例兼容

桌面宿主（尤其是小米桌面）会跨版本缓存布局、资源 ID 和 Provider 组件名。以下内容改动后无法靠应用升级修正，只有在真实桌面上完成升级、刷新和恢复验证后才能调整：

- `AndroidManifest.xml` 中的 Activity、Provider、Receiver、Service 以及被 `PendingIntent`／`ComponentName`／`tsuki://` 深链引用的类：不移动、不改名。需要调整职责时，原类保留为薄壳。
- `app/widget-resource-ids.txt` 与 `--stable-ids`：资源名和编号保持含义，已删除的编号不复用。只有钉入的条目编号固定；未钉资源的编号会随新增资源移动，所以 RemoteViews 布局与动作引用的资源（含被布局引用的 `strings.xml` 条目）都必须钉入。
- Provider 元数据：`miuiWidgetVersion=52`（小部件布局、外观或元数据变化时只升不降）、4×2 尺寸、浅色／深色两套主题布局与曝光元数据；资源收缩保持关闭。
- 桌面部件资源：`res/layout/status_widget*.xml`、`probe_widget.xml`、`res/drawable*/widget_*`、`res/values*/widget_palette.xml`、`res/values*/widget_placeholder.xml`、`colors.xml`、`miuix_widget_tokens.xml`、`res/xml/*`，以及 `strings.xml` 中被小部件布局引用的 `status_*` 条目。
- 跨进程协议与存储：`StorageContract`、`StorageCalls`、`SnapshotJson`，以及 `core/` 的实例存储、角色缓存 schema、协调器。

## 2. 进程边界

- 桌面 Provider、私有 `WidgetStorageProvider` 与 `WidgetRefreshJob` 运行在 `:widgetProvider`；`widget` 包不得引用 Compose 或 `ui` 包。
- 广播和主线程不发网络请求；真实请求只由后台任务执行。不添加定时轮询或自动签到；20 秒曝光间隔与成功快照复用不是轮询。
- 私有 Provider 核对调用方 UID，配置写入另外校验实例确实归属本应用。

## 3. 凭据与账号数据

- Cred 使用 Android Keystore（AES-256-GCM）加密，保存在不参与备份的目录，原子写入。
- Cred 不进入 saved instance state、日志、IPC 读返回或崩溃信息；`AccountDraft` 的 Saver 只保存账号名称。
- 迟到的请求不能覆盖新的绑定，也不能恢复已删除的角色（核对身份归属与请求代数）。
- 删除账号、重置存储和添加到桌面都保留明确的确认流程与忙碌保护。
- 不在应用内加入获取 Cred 的步骤、Cookie 键名或网址；表单只接受 Cred 值。

## 4. 部件放置

- 标准路径使用显式、非导出、可变、一次性的 `PendingIntent` 接收宿主回填的真实实例 ID；迟到回调不覆盖已配置的实例。
- HyperOS 添加流程可能不交付成功回调。兜底只用一次性私有暂存：记录本次选择、请求 token、原有 ID 集合、开机计数和开始时间，仅匹配**唯一**新增、未配置、且 Provider 配色一致的实例；出现多个候选时放弃，不猜测账号。暂存最长两分钟，跨重启失效。
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
- 模糊只用于顶栏背景、顶栏按钮、游戏胶囊和底栏胶囊；卡片、菜单、弹层不模糊，底栏下方遮罩只是颜色渐变，文字不模糊。
- 不自写 RuntimeShader／RenderEffect 模糊管线。**唯一例外**：菜单内容层在展开／收起转场中的短暂前景模糊（最大 6dp，只在打开 110ms 显现期和关闭 65ms 隐去期，稳定状态必须为 null），不得扩展到其他位置。
- 滚动时只改变透明度、色调和阴影，不改变模糊半径、不重建后端；不对承载背景采样的容器套整体 alpha，用 DstIn 遮罩控制已采样结果。
- backdrop 只在 HorizontalPager 外的单一容器录制，相邻页不分别注册；只有存在 miuix-blur 消费者时才录制。
- MiGlass 目前只在 HyperOS 4 实机上验证过，其他 ROM 视为未验证，必须能安全回退。

## 7. 签名

- 正式包由维护者用仓库外的私钥与签名链签名（`tools/Sign-Release.ps1`），不要重新生成或覆盖签名文件，也不要把任何密钥、密码文件或 `local.properties` 加入仓库。
- 已安装正式版的设备不能用普通 Debug 证书覆盖安装；在这类设备上测试需要沿用同一签名链（`tools/Sign-Debug.ps1`）。其他开发者应在未安装正式版的设备上使用自己的 Debug 签名。

## 8. 测试与验收

- 每次改动运行：

  ```powershell
  .\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest
  ```

  发布前加上 `:app:assembleRelease :app:lintRelease`。`warningsAsErrors` 保持开启，不添加 lint baseline 或全局关闭项。
- 设备测试只使用生产页面与内存样本，不保存账号或桌面实例；不卸载应用、不清除数据、不修改系统设置（测试临时固定的屏幕方向在结束后恢复原状）。统一入口为 `tools/Run-DeviceTests.ps1`。
- 没有实际执行的设备测试、视觉检查或性能测量一律记为“未验证”。编译与 JVM 测试不能代替真实桌面宿主、模糊观感、GPU 开销和手势手感的检查。
