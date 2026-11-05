# Tsuki 界面设计约定

应用内界面以 HyperOS 4 系统应用为风格参考；桌面小部件的布局与资源受已安装实例兼容约束。不可放宽的边界见 [DEVELOPMENT_CONSTRAINTS.md](DEVELOPMENT_CONSTRAINTS.md)。

## 应用内组件

### 设计原则

- 单一 app 模块，固定 Miuix 0.9.4。颜色取自 `MiuixTheme.colorScheme`，字号取自 `textStyles`；应用主题通过 `TsukiTextStyles` 统一缩小，仅改字号，保留默认字重和行高。桌面字号投影仍使用 Miuix 默认值。基础行、卡片、标题和弹层使用库 Defaults。明确规格中的胶囊、指标大数字等尺寸在对应小组件实现，不复制或修改库组件。
- 三标签为概览、部件、我的，图标为 Home / All / ContactsCircle。页面可通过底栏或横向滑动切换，以 Pager 最终停留的页同步底栏与当前页；每页独立保存列表位置并在重建后恢复；`initialPage` 0/1/2 对应三个标签，旧 3 进入我的并定位外观。
- 页面居中且不超过 600dp，消费真实 systemBars（状态栏与标题栏、导航栏）、横向安全区和 IME Insets；顶部 Inset 与 Miuix TopAppBar 一致，统一由 `topBarInset()`／`bottomBarInset()` 取得。添加入口在顶栏；无 FAB。列表底部为底部 Inset + 100dp；底栏距底部安全区 2dp。
- 二级页面（数据与隐私、开源许可、添加账号、选择平台）统一使用 `SecondaryScaffold`：与三标签相同的“内容 → 顶部玻璃 → 透明 TopAppBar”叠放、同一玻璃后端顺序与渐隐参数；标题同时作为无障碍窗格标题。二级页面在同一窗口内滑入，转场效果统一取自 `tsukiNavEffects()`：进入页的前缘两角按屏幕圆角（`rememberNavSystemCornerRadius()`）裁切，与 HyperOS 的 Activity 窗口转场一致，由 `nav-corner` 设备场景按截图测量；转场中屏蔽输入。数据与隐私、开源许可使用 `InfoPage` 的文档式版式：顶部文档卡片（title4 粗体标题、footnote1 的适用版本与更新日期、body2 引言），随后是编号的 TsukiSmallTitle 分节与卡片行，末尾为 footnote1 说明。其他页面不使用整段说明文字。
- 分组标题使用 TsukiSmallTitle（SmallTitle 的水平 16dp、垂直 8dp 内边距，与列表 12dp 合计 28dp），与卡片连续排布；列表行之间不加分隔线。仅菜单分组之间使用 dividerLine。不添加设置项摘要或非必要说明；保留同步时间、失败原因、倒计时、必要的权限与错误文案和一行 Cred 安全提示。
- 概览按 realtimeLabels / buildingLabels 的顺序显示全部指标。缺失数据为“未知”；倒计时使用 Dashboard 中的函数，绝对恢复时间和同步年龄使用可测纯函数。训练头像使用 roundedAvatar(1 / 9.5) + trainingPortrait 的正方形裁切。官方玩家头像自带的暗色相框在圆角裁切前按像素检测去除（`bakedFrameInset`，每边最多 10%，透明边缘与普通图片不裁）。
- 小部件预览统一使用 `WidgetPreviewStage`（概览空状态、部件页、配置面板）：舞台展示真实位图，不放图标占位，上下内边距 14dp；预览周围是与 HyperOS 系统小部件预览一致的柔和投影（`dropShadow`，黑色，半径 20dp、下移 6dp，浅色 10%／深色 30%），不使用随光源位置变化的高程阴影。部件页条目标题与桌面小部件列表同名（“明日方舟 浅色”“明日方舟 深色”），只在刷新失败时显示原因。新建配置且角色匹配时先使用概览已加载快照，随后沿用配置面板刷新。配置面板顶部 Close / Ok，无底部按钮；角色与配色使用文字下拉菜单，保留 hostProvider 材质提示、Debug 模拟失败、内联错误及忙碌保护。
- 我的集中账号、外观、关于；更多菜单提供更新 Cred / 删除账号，更新沿用添加流程并预填名称。已有 AUTH_EXPIRED 缓存可显示“需重新授权”，不为状态标记发送请求。开发诊断必须同时满足 Debug 与 diagnostics=true。
- Cred 只保留在内存，AccountDraft Saver 只保存名称。顶栏 Ok 与“验证并添加”共用 save / ready，保留 IME 返回、转场门控、删除与重置确认、桌面快捷方式权限和两个 Activity 入口返回协议。

### 自定义清单

| 编号 | 实现与边界 |
| --- | --- |
| C1 | TopAppBar 透明背景，下方独立 TsukiGlass；大标题 32sp、折叠标题 18sp，由 TsukiTextStyles 提供，保留库布局；保留库标题底部留白，胶囊行额外上 4dp / 下 10dp，列表项间距 10dp。 |
| C2 | 40dp 顶栏按钮随滚动出现圆形玻璃背景。 |
| C3 | TsukiNavDock：居中 235×52dp、26dp 外圆角；三个 73×52dp 固定触控槽位、两侧 8dp；单个 83×46dp / 23dp 圆角选中层从 3dp 起点连续移动。24dp 原有图标、11sp / 14sp 行高文字，Role.Tab / selectableGroup；选中文字加粗。按住预览、横拖连续跟手并拉伸；横拖启动后，手指移出底栏仍保持控制，胶囊位置及拉伸限制在底栏内；横拖后在底栏内外释放均提交当前跟踪项，系统取消回到实际目标；仅缩放视觉子层，不缩小命中区、不叠加默认 indication。参数按系统应用逐帧标定，以本条和代码常量为准。 |
| C4 | 底部约 92dp surface 渐变遮罩，顶部透明、底部 70%，不模糊。 |
| C5 | GameChips：38dp 高、至少 86dp 宽、水平 22dp、间距 12dp、16sp 字号；行槽位 52dp（上 4 + 38 + 下 10）；来自支持游戏列表，保留 ID 与选择回调。第一列表项预留位置，随大标题上移后停在状态栏 + 52dp 下方，不使用 bottomContent。 |
| C6 | TsukiMenuHost：Compose focusable Popup + ListPopupColumn + DropdownImpl，独立窗口显示于配置面板上方。按 WindowInsets.safeDrawing 限制四边，底部不足向上覆盖触发点下沿；定位扣除 24dp 阴影留白。32dp 阴影半径、向下 8dp、黑色浅色 12% / 深色 50%，不压暗背景。展开从触发角按系统应用拟合的关键帧缩放（打开 330ms、关闭 170ms，圆角由 24dp 过渡到 20dp），内容在打开延迟 40ms 后 110ms 内显现、关闭时 65ms 内隐去，并带短暂前景模糊（见下方"菜单转场例外"）；以代码常量为准。保留库默认 200–288dp 宽度、条目和选中语义，不使用 BasicComponent / IntrinsicSize 测量弹出内容。返回键、外部点击关闭；角色触发文字在打开时隐藏，避免圆角露出笔画；下拉偏好展开时将 LocalIndication 换为透明 MiuixIndication，立即清除触发行按下态，保留点击、Role.DropdownList 与选中项语义。 |
| C7 | TabRowWithContour：surfaceContainer 背景、secondaryContainer 选中背景、17.5dp 圆角。 |
| C8 | OverlayBottomSheet 的 backgroundColor 为 surface，其余弹层使用默认不透明样式。 |
| C9 | TextField 使用 textFieldColors(backgroundColor = surfaceContainer)。 |
| C10 | 删除按钮使用 buttonColors(contentColor = error)，保留默认确认弹窗文案。 |
| C11 | PullToRefresh 的四段文案为“下拉刷新 / 松开刷新 / 正在刷新 / 刷新成功”，刷新失败保留缓存并使用系统 Toast 提示，不提供重试按钮；通过下拉刷新或顶栏刷新重试；顶栏按钮先回到列表顶部并展开标题，再触发原有下拉刷新状态，不使用错误 Card。 |
| C12 | ProgressRing、SyncTag、WidgetStage 为小组件，所有可见颜色由 colorScheme 派生。环为 24dp / 3dp；无上限的疲劳项保留空位；舞台渐变为 primary 28% → 7% 混入 surface。 |

### 玻璃范围与回退

- 策略：所有页面（含首页）的顶部背景均优先 HyperOS MiGlass，缺少能力或初始化／更新失败时回退 miuix-blur，再回退现有不透明外观。顶栏复用胶囊配方，采用矩形轮廓、shape.edge=1、关闭胶囊增强，背景材质结果单独作渐隐。边界见 [开发约束 §6](DEVELOPMENT_CONSTRAINTS.md#6-模糊与-hyperos-原生玻璃)。
- miuix-blur 回退依赖 `top.yukonga.miuix.kmp:miuix-blur:0.9.4`，使用公开 `rememberLayerBackdrop`、`layerBackdrop`、`drawBackdrop`、`progressiveBlur`、`blur`、`colorControls` 和支持性判断。一个主框架在 HorizontalPager 外最多记录单一容器，不在相邻页分别注册 backdrop；各表面报告实际后端，仅有 miuix-blur 消费者时录制，回退恢复录制；离开当前导航页后停止采样。
- 可以模糊的范围、反射调用与自写模糊管线的禁令见[开发约束 §6](DEVELOPMENT_CONSTRAINTS.md#6-模糊与-hyperos-原生玻璃)，本节只记录各表面的参数。
- 背景组合集中在 `ui/glass/TsukiGlass.kt`，原生顶栏承载层为 `NativeTopGlass.kt`，按钮／胶囊／底栏参数在 `GlassSurfaces.kt`，宿主与后端选择在 `GlassHost.kt`。其他页为 30dp / 不增强饱和度 / surface 最高 34%，从操作行底边上方 32dp 开始渐隐，到底边下方 16dp 结束；前景标题保持清晰。公开 ProgressiveBlur 的 curve 为 0.65，原生路径继续 DstIn。原生材质上边缘向窗口外延伸 2 个物理像素，遮罩坐标不变，避免第 0 行露出未经模糊的内容；不扩展页面布局或触控区域。
- **首页游戏行背景**：MiGlass 优先，原生固定半径 30 加透明度渐隐。miuix-blur 作为回退，使用按系统主题商店校准的 progressiveBlur：固定基准 10dp、startFraction=0.4、curve=1.4。两条路径底端均为操作行底边 +60dp，DstIn 从 +20dp 渐隐到 +60dp；surface 34% 蒙色从操作行底边上方 52dp 开始淡出，在上方 8dp 归零，不覆盖游戏行。滚动只改变显隐进度。原生固定半径渐隐不等同于公开渐进半径模糊。
- 顶栏原生实例在首页与其他主页面复用，零进度保持 INVISIBLE；按钮、胶囊本体和底栏同样 MiGlass 优先。仅存在实际 miuix 消费者时启用 backdrop 录制。drawBackdrop 不传 progressiveGradient，不使用多档合成，不自写着色器。模糊关闭、回退和文字清晰边界不变。空间遮罩与色调使用缓存的 33 个 smoothstep 色标。
- 顶部背景透明度按 smoothstep(clamp(collapsedFraction × 3)) 连续跟随滚动，主页面首个可见项 > 0 时为全强度；在绘制阶段读取进度，不因此逐帧重组页面。游戏胶囊与顶部背景共用连续滚动进度，在胶囊组件内读取，避免重组整页；按钮仍沿用标题阈值与 folmeSpring(damping=1, response=0.3/0.15)。
- 未选中胶囊静止为不透明 surfaceContainerHigh；玻璃态固定 24dp / saturation 1.8、surfaceContainer 55%（深色向 onSurface 混入 4%，约 #2C2C2C）、1dp 高光（浅 90% / 深 12%）与 0/4/16dp 黑 6% 阴影。选中底色先将 onSurface 80% 合成到 surface 成为不透明色，玻璃态降为 90% 不透明度并使用固定 20dp 模糊，无描边或阴影。文字始终清晰。
- 按钮固定 20dp / saturation 1.6；底栏固定 28dp / saturation 1.8，外部投影位于材质层之外（26dp 圆角、18dp 阴影半径、下移 4dp、主题暗色 8%，三种后端共用、不增加测量或触控范围），投影只绘制在胶囊轮廓之外，内部不得透过半透明材质使胶囊发灰。浅色底栏本体与系统一致：不暗于页面（白底约 +0、#F2F2F2 底约 +8 亮度级），原生路径白色叠加 80%、miuix-blur 50%，由 `dock-tone` 设备场景按三种后端测量。玻璃选中叠色为 onSurface 浅色6%／深色12%。进度只改变采样后透明度、色调、描边、阴影，不改变模糊半径。
- 对已经采样的背景结果使用 DstIn 遮罩控制透明度，色调层单独淡入，再绘制前景。禁止给承载采样的容器套整体 alpha。模糊开关、硬件加速及公开支持性判断只在进入宿主或开关变化时计算。
- “界面模糊效果”默认开启，保存于现有 ui SharedPreferences 的 blur 键。关闭，或两种后端均不可用时：顶栏改为 surface（随滚动显隐），按钮和底栏为 surfaceContainer 加轻阴影；游戏胶囊始终采用静止样式（未选中 surfaceContainerHigh、选中合成后的 onSurface 80%），底栏选中项为 secondaryContainer。回退不改变测量和布局。
- MiGlassCapability 在进程级 lazy 缓存签名／ROM 能力，GlassSession 管理完整初始化、更新与逐项清理；失败实例不重试，View／配置重建才重探测。关闭模糊或离开导航页时移除原生 View；主标签切换复用同一个顶栏操作背景；游戏胶囊在非概览页保持组合以复用原生材质，但移出窗口并从无障碍树隐藏，避免透明的覆盖层拦截下方内容的点击。能力不支持时不创建 AndroidView。许可仍由实例监听，并在 detach 时注销；原生 View 进度为 0 时 alpha=0 且 INVISIBLE，恢复时只改 alpha／可见性，不重新初始化或改变半径。
- **菜单转场例外**：TsukiMenu 内容层的短暂前景模糊是唯一的 RenderEffect 用法，范围、半径与时长见[开发约束 §6](DEVELOPMENT_CONSTRAINTS.md#6-模糊与-hyperos-原生玻璃)。
- 圆角材质只放在背景 AndroidView，Compose 前景保持独立；尺寸来自实际布局，位置由 AndroidView 的窗口变换承接，不引入固定采样大画布。MiGlass 自带轮廓时不叠加 Compose 描边。矩形顶栏参数由胶囊配方派生并弱化边缘项，不是厂商提供的矩形标准配方，兼容性以实机验证为界。

### 预览与依赖检查

所有 @Preview 位于 `app/src/debug/java/cafe/miafetta/tsuki/ui/preview/ComponentPreviews.kt`。每个小组件一个预览函数，经 `PreviewParameter` 分别以浅色与深色渲染固定假数据；菜单仅预览 ListPopupColumn / DropdownImpl 内容，不创建弹出窗口并套 TsukiAppTheme，不读取 AppState、凭据、存储或网络；舞台预览使用固定内容槽，生产页面统一使用 `WidgetPreviewStage` 展示真实位图。

Compose tooling 的版本、Material3 排除与 lint 豁免见[架构 §1](ARCHITECTURE_DECISIONS.md#1-依赖)。

## 桌面小部件

RemoteViews 是平台边界，组件进程不加载 Compose。点击已配置小部件的主体进入概览，并切到该小部件绑定的角色（角色已不存在时保持原选择）；点击未配置的小部件进入配置；桌面长按后的编辑进入配置；独立刷新热区只刷新当前实例。Provider、4×2 尺寸、账号／角色绑定、曝光刷新、原生渲染与位图对照、稳定资源 ID 与进度几何受[开发约束 §1](DEVELOPMENT_CONSTRAINTS.md#1-已安装实例兼容)保护。

- 参考尺寸为 360×161.78988dp，整体等比例缩放。刷新文字、顶部同步时间和刷新图标在基准下为 12dp。
- 顶栏文字采用独立 widget_header_text 资源，不改变 miuix_widget_body1。XML、运行时尺寸动作、预览和位图对照保持同步。
- 系统小部件列表的预览（`previewLayout`）是静态示例布局 `status_widget_preview_*`，示例数据为中性占位，不含真实昵称或 UID。`initialLayout` 与首次拉取期间（没有快照的刷新中）显示加载布局 `status_widget_loading_*`：保留卡片背景、摘要格与图标，文字换成半透明占位条，不显示“未知”。加载布局与正式卡共用框架 ID 和尺寸常量：首次渲染前靠自身权重与最小尺寸适应格子，运行时按与正式卡相同的比例逐尺寸缩放，数据到达时不跳变。按小米小部件规范，预览与实际外观一致、加载时显示占位。
- 深色配色独立于 Miuix 默认主题：背景 #353535，摘要格为 19% 黑（#30000000，叠在背景上约 #2B2B2B，浅色实例夜间可透出背景模糊），主文字 #FFFFFF，辅助时间 #C2C2C2，标签 #D6D6D6，进度填充 #D9D9D9，轨道 #878787。刷新文字与图标用森空岛官方小部件“签到”的颜色 #3BB8FF（由真机截图校正估算）。
- 浅色配色：摘要格为 60% 白（#99FFFFFF），让宿主的背景模糊透出；日间刷新仍为 Miuix primary #3482FF。
- 色值是森空岛官方小组件屏幕像素的近似，不复刻纹理，不推断源 alpha 或真实模糊。浅色实例在系统夜间模式下采用整套深色 palette。
- 色彩通过应用时解析的资源动作绑定，避免宿主复用缓存时保留上一模式。预览缓存区分系统模式；预览不能证明宿主背景模糊。

## 验证

必须运行的构建命令与“未验证”的记录规则见[开发约束 §8](DEVELOPMENT_CONSTRAINTS.md#8-测试与验收)。与界面相关的覆盖范围：JVM 覆盖时间边界、未知值、模糊可用性、标题阈值、渐隐分界和菜单定位；AndroidTest 使用生产页面的内存样本覆盖导航、角色与配色菜单、表单、键盘、360dp / 1.3 倍字体、布局回退、卡片顺序和重建恢复。

编译与纯逻辑测试不能证明顶部滚动 / 切页模糊连续性、GPU 开销、菜单阴影、下拉手感、深色与大字体实际观感。桌面资源改动需要在真实桌面宿主上验收，不以应用内预览代替。
