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

## 小部件官方元素与图标

| 文件 | 内容 | 来源 |
| --- | --- | --- |
| `res/drawable-nodpi/widget_arknights_bg_dark.webp`、`widget_sanity_v2_dark.png`、`widget_drone_v2_dark.png`、`widget_training_v2_dark.png`、`widget_training_badge_dark.png` | 明日方舟深色小部件背景纹理、理智、无人机、训练室占位与训练室角标 | 森空岛官方 Android 端小部件资源（背景按卡片圆角与 4×2 比例裁切缩放，图标原样使用） |
| `res/drawable-nodpi/widget_arknights_bg_light.webp`、`widget_arknights_bg_night_dark.webp`、`widget_sanity_v2_light.png`、`widget_drone_v2_light.png`、`widget_training_v2_light.png` | 明日方舟浅色小部件（及浅色夜间模式）半透明背景纹理、深灰单色理智／无人机与训练室占位图标 | 基于上述森空岛官方 Android 端同类元素换算配色生成（保留官方形状与透明通道，适配浅色毛玻璃背景） |
| `res/drawable/widget_clock_dark.xml`、`widget_clock_light.xml` | 明日方舟深色／浅色小部件计时行时钟图标 | 参照森空岛官方 Android 端 `widget_arknights_clock` 矢量路径，分别使用深色／浅色次级文本颜色 |
| `res/drawable-nodpi/widget_endfield_bg_v3.webp`、`widget_endfield_band_hatch.png`、`widget_endfield_band_marks_v2.png`、`widget_endfield_band_word_v2.png`、`widget_endfield_brand_v2.png`、`widget_endfield_refresh_v3.png`、`widget_endfield_sanity_v2.png`、`widget_endfield_panel_mark_v2.png`、`widget_endfield_item_dot.png`、`widget_endfield_daily_v2.png`、`widget_endfield_weekly_v2.png`、`widget_endfield_pass_v2.png` | 终末地小部件等高线点阵底纹、左侧斜纹竖条与标识、顶栏标识与刷新图标、理智底纹、右侧面板徽记水印、行顶双排点阵与三项实时数据图标 | 森空岛官方 Android 端终末地小部件资源（底纹避开左侧黄条圆角区并按右圆角裁切，斜纹层与右下徽记按卡片／面板圆角裁切，刷新图标取自官方原图左侧区域并保留原有半透明灰度） |
| `res/drawable-nodpi/widget_endfield_seek.png` | 终末地“蚀像寻遗”实时数据图标 | 森空岛官方实时数据前端静态资源（按官方白灰透明度换算以与其余三行图标一致） |

- 面板描边渐变、回复时间胶囊、橙／绿状态圆点与底部三色条为 XML 形状与配色，颜色取自森空岛官方 Android 端小部件资源。
- 以上游戏与社区图形设计均归鹰角网络（Hypergryph）所有，不在本项目的 MIT 许可范围内，仅用于在小部件和概览中标识对应的游戏数据。

## Miuix 图标（Apache License 2.0）

- 应用内的操作图标全部来自 miuix-icons 0.9.4：Home、All、ContactsCircle、ChevronBackward、Refresh、Ok、Add、Remove、Close、More、Paste、Show、Hide 与 ArrowUpDown（行末右箭头由库组件自带）。
- 桌面小部件的两个资源由同版本 Miuix 图标的原始路径导出为 VectorDrawable，资源名称和 ID 保持不变：
  - 刷新图标 `widget_refresh.xml`，来自 Refresh。
  - 浅色／深色头像占位图标，来自 ContactsCircle.Regular。
- JVM 测试逐节点核对导出的路径、viewport 与变换。
- 许可证全文打包在 `app/src/main/assets/third_party/MIUIX_LICENSE.txt`。

## Tsuki 自有素材

- 应用图标（夜空新月：`mipmap-anydpi/ic_launcher*.xml` 自适应图标，背景、前景与主题图标单色层为 `drawable/ic_launcher_*.xml`）与通知状态栏单色图标（`drawable/ic_reminder.xml`，由夜空新月单色层缩放派生）为本项目自绘的 VectorDrawable，随代码以 MIT 许可发布。README 中的 `docs/assets/Tsuki.png` 由同一图标渲染而成。
