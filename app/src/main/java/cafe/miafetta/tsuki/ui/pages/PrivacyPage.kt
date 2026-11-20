package cafe.miafetta.tsuki.ui.pages

import cafe.miafetta.tsuki.ui.theme.Branding

import androidx.compose.runtime.Composable
import cafe.miafetta.tsuki.BuildConfig

// Update the date whenever the content below changes; every statement is checked against the code.
private const val PrivacyUpdated = "2026 年 11 月 20 日"

private val privacySections = listOf(
    InfoSection("我们保存的信息", listOf(
        "${Branding.PLATFORM}凭据" to "你填写或通过网页登录取得的 Cred，以及账号名称（可以不填）。" +
            "添加账号或更新 Cred 时名称留空的，会自动填入你的${Branding.PLATFORM}昵称，可以修改或清空；名称只保存在本机。" +
            "Cred 加密后保存，不会写入日志或崩溃信息。",
        "角色信息" to "通过${Branding.PLATFORM}获取的明日方舟与明日方舟：终末地角色名称、服务器与 UID。",
        "游戏数据" to "最近一次同步的理智、任务、基建、活跃度、通行证等数据以及角色头像，用于离线查看、显示小部件和判断提醒。",
        "小部件配置" to "每个小部件绑定的角色与配色。",
        "提醒设置" to "通知提醒的开关、为各角色设置的提醒项目与阈值，以及已提醒的记录和后台检查的时间。" +
            "只保存在本机，不参与备份。",
        "偏好设置" to "深色模式、界面模糊效果与上次查看的游戏和角色。")),
    InfoSection("信息的使用", listOf(
        "用途" to "上述信息仅用于在应用内、桌面小部件和提醒通知中展示你的游戏数据。",
        "剪贴板" to "只在你点击 Cred 输入框的粘贴按钮时读取一次剪贴板，用来填入 Cred；不在后台读取，也不保存剪贴板内容。",
        "不进行的处理" to "Tsuki 不包含广告、统计分析或任何第三方 SDK，不收集设备标识符，" +
            "除下文所列的网络请求外，不会向任何第三方提供你的信息。")),
    InfoSection("网络通信", listOf(
        "${Branding.PLATFORM}（zonai.skland.com）" to "使用 Cred 验证账号，并获取明日方舟与终末地的角色数据。" +
            "添加账号或更新 Cred 时，如果账号名称为空，会读取一次${Branding.PLATFORM}账号资料（/api/v1/user），只取其中的昵称" +
            "填入名称框，资料的其他内容不保存。获取终末地数据前，" +
            "会先读取一次${Branding.PLATFORM}账号的用户 ID，它只保存在内存中。Cred 只会发送到该地址。",
        "鹰角网络素材服务器（web.hycdn.cn）" to "下载公开的游戏头像，请求中不包含任何账号信息。",
        "${Branding.PLATFORM}官方登录页（仅在你选择网页登录时）" to "在应用内网页中打开官方页面。账号、密码与验证码由你直接" +
            "提交给官方页面及其加载的服务（包括人机验证），Tsuki 不读取这些内容；登录完成后，Tsuki 只从页面读取 Cred，" +
            "随即清除网页的 Cookie 与存储。",
        "联网时机" to "在你添加账号（填入 Cred 后读取昵称，以及验证账号）、手动刷新、保存小部件、使用网页登录，" +
            "或桌面显示、刷新小部件时联网；打开概览时，" +
            "数据超过 5 分钟也会更新。小部件刷新被限流时，会在限流结束后自动补刷一次。" +
            "开启通知提醒后，Tsuki 会在后台为设置了提醒的角色定时检查：" +
            "按各提醒项目的预计时间，并约每 3 小时校准一次，同一角色两次后台检查至少间隔 30 分钟，系统可能推迟执行；" +
            "关闭通知提醒或系统通知时不做这些检查。同一角色 20 秒内不重复请求，不会自动签到。",
        "网络异常" to "断网、授权失效或服务器异常时，继续显示上次同步的数据。")),
    InfoSection("存储与安全", listOf(
        "存储位置" to "所有数据保存在应用的私有目录中，其他应用无法读取。",
        "加密" to "Cred 使用 AES-256-GCM 加密，密钥由 Android Keystore 保管，无法从设备中导出。",
        "备份" to "数据不参与系统云备份，也不会随换机迁移。",
        "网页登录数据" to "网页登录期间官方页面产生的 Cookie 与网页存储保存在应用的私有目录中，不参与备份；" +
            "取得 Cred 或离开网页登录后即清除，登录中途应用被关闭时，下次打开时清除。")),
    InfoSection("权限说明", listOf(
        "网络访问" to "连接上述地址，并在后台刷新前确认设备已联网。",
        "桌面快捷方式" to "在应用内将小部件添加到小米桌面时需要。未授予时，仍可从桌面的小部件列表添加。",
        "通知" to "开启通知提醒时请求，用于发送提醒。拒绝后仍可使用其他功能。",
        "开机后恢复" to "设备重启后恢复已安排的提醒检查。")),
    InfoSection("数据的保留与删除", listOf(
        "保留期限" to "数据保存在本机，直到你删除账号或卸载应用。",
        "删除账号" to "删除该账号的 Cred 与角色缓存，以及该账号各角色的提醒设置与记录，并解除相关小部件的绑定，" +
            "其他账号不受影响。",
        "卸载应用" to "所有本地数据随应用一并删除。")),
    InfoSection("说明的更新", listOf(
        "变更方式" to "本说明随应用版本更新，内容变化时会修改上方的更新日期。",
        "联系我们" to "如有疑问，可在 Tsuki 的开源仓库提交 Issue；安全问题请通过仓库的私密漏洞报告提交。")))

@Composable
internal fun PrivacyPage(blurEnabled: Boolean, onBack: () -> Unit) = InfoPage("数据与隐私", onBack, blurEnabled,
    heading = "Tsuki 隐私说明",
    meta = "适用版本 ${BuildConfig.VERSION_NAME}，更新于 $PrivacyUpdated",
    intro = "Tsuki 是非官方的开源第三方应用，没有自己的服务器。本说明介绍 Tsuki 保存哪些信息、如何使用，以及你如何删除这些信息。",
    sections = privacySections,
    notes = listOf("应用内显示的剩余时间根据同步时的数据在本机推算，可能与游戏内略有差异。"))
