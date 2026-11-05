package cafe.miafetta.tsuki.ui.pages

import cafe.miafetta.tsuki.ui.components.MutedText
import cafe.miafetta.tsuki.ui.components.TsukiSmallTitle
import cafe.miafetta.tsuki.ui.theme.Branding

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cafe.miafetta.tsuki.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

// Bundled license texts in assets/third_party, shown under readable names in this order.
private val licenseTitles = mapOf("MIUIX_LICENSE" to "Apache License 2.0")

private const val Apache = "Apache License 2.0"

// Keep the versions in step with app/build.gradle.kts.
private val licenseSections = listOf(
    InfoSection("本应用", listOf(
        "Tsuki ${BuildConfig.VERSION_NAME}" to "以 MIT License 发布。Copyright (c) 2026 Miafetta。")),
    InfoSection("第三方开源软件", listOf(
        "Miuix 0.9.4" to "许可证：$Apache。版权所有：compose-miuix-ui contributors。" +
            "提供界面组件、模糊效果与图标；桌面小部件的刷新图标与头像占位图标由其图标导出。",
        "Jetpack Compose 1.12 与 AndroidX" to "许可证：$Apache。版权所有：The Android Open Source Project。提供界面框架与 Android 基础组件。",
        "Kotlin 2.4 与 kotlinx.serialization 1.12" to "许可证：$Apache。版权所有：JetBrains s.r.o. 及其贡献者。" +
            "提供语言运行库与 JSON 序列化。")),
    InfoSection("商标与素材", listOf(
        "${Branding.PLATFORM}与${Branding.GAME}" to "相关名称、图标、小部件指标图标和角色头像归上海鹰角网络科技有限公司" +
            "（Hypergryph）及其关联公司所有，不适用上述开源许可，仅用于标识所接入的平台和游戏。",
        "非官方声明" to "Tsuki 是非官方第三方应用，与鹰角网络及${Branding.PLATFORM}不存在任何合作或授权关系。")))

// The source files wrap at ~70 columns; join wrapped lines so paragraphs reflow to the screen width.
internal fun reflowLicense(text: String): String = text.replace("\r\n", "\n").trim()
    .split(Regex("\n\\s*\n")).joinToString("\n\n") { paragraph ->
        paragraph.lines().joinToString(" ") { it.trim() }.replace(Regex(" {2,}"), " ")
    }

@Composable
internal fun LicensesPage(blurEnabled: Boolean, onBack: () -> Unit) {
    val context = LocalContext.current
    val licenses by produceState<List<Pair<String, String>>?>(null, context) {
        value = withContext(Dispatchers.IO) {
            try {
                val order = licenseTitles.keys.toList()
                context.assets.list("third_party").orEmpty().filter { it.endsWith(".txt") }
                    .sortedBy { name -> order.indexOf(name.removeSuffix(".txt")).let { if (it < 0) order.size else it } }
                    .map { name ->
                        val key = name.removeSuffix(".txt")
                        (licenseTitles[key] ?: key) to reflowLicense(context.assets.open("third_party/$name").bufferedReader()
                            .use { it.readText() })
                    }
            } catch (_: java.io.IOException) { listOf("读取失败" to "无法读取许可证全文，请返回后重试。") }
        }
    }
    InfoPage("开源许可", onBack, blurEnabled,
        heading = "开源软件声明",
        meta = "适用版本 ${BuildConfig.VERSION_NAME}",
        intro = "Tsuki 使用了下列开源软件，并遵守各自许可证的条款。许可证全文附于本页末尾。",
        sections = licenseSections) {
        TsukiSmallTitle("${licenseSections.size + 1}. 许可证全文")
        if (licenses == null) MutedText("正在读取…", Modifier.padding(horizontal = 16.dp))
        licenses.orEmpty().forEach { (name, text) ->
            Card(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(name, style = MiuixTheme.textStyles.headline2)
                    Text(text, style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                }
            }
        }
    }
}
