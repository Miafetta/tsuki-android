# 素材来源

## 第三方商标

| 文件 | 内容 | 权利人 | 来源 |
| --- | --- | --- | --- |
| `res/drawable-nodpi/logo_skland.jpg` | 森空岛图标 | Shanghai Hypergryph Explorer Network Technology Co., Ltd. | [App Store](https://apps.apple.com/cn/app/id6447509138) |
| `res/drawable-nodpi/logo_arknights.jpg` | 明日方舟图标 | Shanghai Hypergryph Network Technology Co., Ltd. | [App Store](https://apps.apple.com/cn/app/id1454663939) |

- 两个图标原样使用，没有重新绘制或转换，只用于标识所接入的平台和游戏，不表示官方合作。
- 它们归各自权利人所有，不在本项目的 MIT 许可范围内。
- 概览与桌面小部件中的角色头像在运行时从鹰角网络的素材服务器下载，同样归权利人所有，不随仓库分发。

## 小部件指标图标

| 文件 | 内容 |
| --- | --- |
| `res/drawable-nodpi/widget_sanity_light.png`、`widget_sanity_dark.png` | 理智 |
| `res/drawable-nodpi/widget_drone_light.png`、`widget_drone_dark.png` | 无人机 |
| `res/drawable-nodpi/widget_training_light.png`、`widget_training_dark.png` | 训练室（无头像时的占位图） |

- 原始素材来自森空岛的《明日方舟》官方小组件，由 ChatGPT 复刻重绘，分浅色与深色两版。
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
