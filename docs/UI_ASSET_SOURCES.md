# 素材来源

## 第三方商标

| 文件 | 内容 | 权利人 | 来源 |
| --- | --- | --- | --- |
| `res/drawable-nodpi/logo_skland.jpg` | 森空岛图标 | Shanghai Hypergryph Explorer Network Technology Co., Ltd. | [App Store](https://apps.apple.com/cn/app/id6447509138) |
| `res/drawable-nodpi/logo_arknights.jpg` | 明日方舟图标 | Shanghai Hypergryph Network Technology Co., Ltd. | [App Store](https://apps.apple.com/cn/app/id1454663939) |
| `res/drawable-nodpi/logo_endfield.jpg` | 明日方舟：终末地图标 | Shanghai Hypergryph Talos Network Technology Co., Ltd. | [App Store](https://apps.apple.com/cn/app/id6753859465) |

- 这些图标原样使用，没有重新绘制或转换，只用于标识所接入的平台和游戏，不表示官方合作。终末地图标自带圆角和白色角落，应用内显示时由圆角裁剪遮住。
- 它们归各自权利人所有，不在本项目的 MIT 许可范围内。
- 概览与桌面小部件中的角色头像在运行时从鹰角网络的素材服务器下载，同样归权利人所有，不随仓库分发。

## 小部件指标图标

| 文件 | 内容 |
| --- | --- |
| `res/drawable-nodpi/widget_sanity_light.png`、`widget_sanity_dark.png` | 理智 |
| `res/drawable-nodpi/widget_drone_light.png`、`widget_drone_dark.png` | 无人机 |
| `res/drawable-nodpi/widget_training_light.png`、`widget_training_dark.png` | 训练室（无头像时的占位图） |
| `res/drawable-nodpi/widget_endfield_sanity.png` | 终末地理智（小部件底纹） |
| `res/drawable-nodpi/widget_endfield_daily.png` | 终末地日常活跃度 |
| `res/drawable-nodpi/widget_endfield_weekly.png` | 终末地每周事务 |
| `res/drawable-nodpi/widget_endfield_pass.png` | 终末地通行证等级 |

- 方舟的三组 PNG 原始素材来自森空岛的《明日方舟》官方小组件，由 ChatGPT 复刻重绘，分浅色与深色两版。终末地的理智沿用同一个理智图标，森空岛对终末地理智用的是同一图形。
- 终末地小部件左侧的黄色竖条 `res/drawable-nodpi/widget_endfield_band.png` 参照官方小组件绘制，文字用 Windows 自带的 Bahnschrift（Bold Condensed）排版后栅格化，不包含字体文件；面板、胶囊、圆点、点状装饰与底部三色线是本项目绘制的形状，颜色取自官方小组件截图，官方背景纹理没有复制。
- 终末地的四个图标是官方图形：取自森空岛 App《明日方舟：终末地》实时数据页的截图，按灰度换算成白色图形加透明度，没有重新绘制；终末地小部件固定深色，只有白色一版。
- 图形设计归鹰角网络（Hypergryph）所有，不在本项目的 MIT 许可范围内，仅用于在小部件和概览中标识对应的游戏数据。

## Miuix 图标（Apache License 2.0）

- 应用内的操作图标全部来自 miuix-icons 0.9.4：Home、All、ContactsCircle、ChevronBackward、Refresh、Ok、Add、Close、More、ArrowRight 与 ArrowUpDown。
- 桌面小部件的两个资源由同版本 Miuix 图标的原始路径导出为 VectorDrawable，资源名称和 ID 保持不变：
  - 刷新图标 `widget_refresh.xml`，来自 Refresh。
  - 浅色／深色头像占位图标，来自 ContactsCircle.Regular。
- JVM 测试逐节点核对导出的路径、viewport 与变换。
- 许可证全文打包在 `app/src/main/assets/third_party/MIUIX_LICENSE.txt`。

## Tsuki 自有素材

- 应用图标（夜空新月：`mipmap-anydpi/ic_launcher*.xml` 自适应图标，背景、前景与主题图标单色层为 `drawable/ic_launcher_*.xml`）与桌面小部件计时行的时钟图标（`widget_clock_*.xml`）为本项目自绘的 VectorDrawable，随代码以 MIT 许可发布。README 中的 `docs/assets/Tsuki.png` 由同一图标渲染而成。
- 桌面深色配色参考了森空岛官方小组件的整体色调，用纯色做平面近似，没有复制官方纹理或实现。
