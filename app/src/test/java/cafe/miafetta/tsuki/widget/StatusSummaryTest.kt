package cafe.miafetta.tsuki.widget

import cafe.miafetta.tsuki.core.*
import cafe.miafetta.tsuki.providers.FakeArknightsAdapter
import org.junit.Assert.*
import org.junit.Test

class StatusSummaryTest : WidgetResourceFixture() {
    private val now = 1700000000000L
    private val config = WidgetInstanceConfig(1, "fake.skland", "skland.arknights.status", "demo-1", "amiya")
    private fun snapshot() = FakeArknightsAdapter().fetch(config, now)

    // The renderer resolves label resources with Android; JVM tests read the same strings.xml entries through R.
    private fun shown(label: Int): String = xml("res/values/strings.xml").children("string").single {
        cafe.miafetta.tsuki.R.string::class.java.getField(it.getAttribute("name")).getInt(null) == label
    }.textContent

    @Test fun eightProgressMetricsHaveTwoRowsInDemoOrderWithSyncedRestExplicitlyNamed() {
        val text = cardSecondary(snapshot()).chunked(4).joinToString("\n") { row ->
            row.joinToString(" · ") { (label, value) -> "${shown(label)} $value" }
        }
        assertEquals(2, text.lines().size)
        assertEquals(listOf("公开招募", "干员疲劳", "订单进度", "合成玉", "每日任务", "宿舍休息", "制造进度", "保全酬劳"), summaryLabels)
        assertEquals(listOf("公开招募", "干员疲劳", "订单进度", "合成玉", "每日任务", "休息进度", "制造进度", "保全酬劳"),
            summaryLabelTexts.map { shown(it) })
        for (label in summaryLabels) assertTrue(text.contains(cardAmount(cardMetric(snapshot(), label))))
        assertTrue(text.contains("休息进度"))
        assertTrue(text.contains("合成玉"))
        assertFalse(text.contains("剿灭奖励"))
    }

    @Test fun missingAndLegacyMetricsRemainUnknownAndOldLabelsAreCanonicalized() {
        val legacy = snapshot().copy(metrics = listOf(WidgetMetric("订单", 2, 20)))
        val text = cardSecondary(legacy).joinToString("\n") { (label, value) -> "${shown(label)} $value" }
        assertTrue(text.contains("订单进度 2/20"))
        for (label in listOf("每日任务", "合成玉", "保全酬劳")) assertTrue(text.contains("$label 未知"))
        assertEquals(TrainingState.UNKNOWN, legacy.copy(training = WidgetTraining()).training.state)
    }

    @Test fun trainingKeepsPortraitUntilTheTraineeLeavesEvenAfterCompletionOrRefreshFailure() {
        val training = WidgetTraining(TrainingState.TRAINING, "char_test_trainee", "测试干员", 2, now + 60000, "official-image")
        val record = WidgetRecord(config, snapshot().copy(training = training), RefreshState.READY)
        assertEquals("训练中", training.status(now))
        assertEquals("0时1分", training.time(now))
        assertEquals("official-image", trainingAvatar(record))
        assertEquals("预计完成", training.status(now + 60000))
        assertEquals("预计完成", training.status(now + 60000))
        assertEquals("official-image", trainingAvatar(record.copy(state = RefreshState.ERROR)))
        assertNull(trainingAvatar(record.copy(snapshot = null)))
        assertNull(trainingAvatar(null))
        for (state in listOf(TrainingState.IDLE, TrainingState.UNKNOWN, TrainingState.COMPLETE)) {
            val snapshot = record.snapshot!!.copy(training = training.copy(state = state))
            assertEquals("official-image", trainingAvatar(record.copy(snapshot = snapshot)))
        }
        assertNull(trainingAvatar(record.copy(snapshot = record.snapshot!!.copy(training = WidgetTraining(TrainingState.IDLE)))))
    }

    @Test fun cacheAndRefreshStatesAreExplicitAndIndependentOfTheme() {
        for (theme in WidgetTheme.entries) {
            val record = WidgetRecord(config.copy(theme = theme), snapshot(), RefreshState.LOADING)
            assertEquals("刷新中，显示旧缓存", cardStatus(record))
            assertEquals("刷新失败，显示旧缓存", cardStatus(record.copy(state = RefreshState.ERROR)))
            assertEquals("刷新成功", cardStatus(record.copy(state = RefreshState.READY)))
            assertEquals("刷新失败", cardStatus(record.copy(state = RefreshState.ERROR, snapshot = null)))
            assertEquals("存储失败", cardStatus(record, true))
        }
        assertEquals("点击配置", cardStatus(null))
    }
    @Test fun bothThemeDirectionsUseDifferentRootLayoutsIncludingDefaultUnconfiguredTheme() {
        assertEquals(cafe.miafetta.tsuki.R.layout.status_widget_scaled_dark, themeLayout(WidgetTheme.DARK))
        assertEquals(cafe.miafetta.tsuki.R.layout.status_widget_scaled_light, themeLayout(WidgetTheme.LIGHT))
        for (old in listOf(cafe.miafetta.tsuki.R.layout.status_widget_dark, cafe.miafetta.tsuki.R.layout.status_widget_light)) {
            assertNotEquals(old, themeLayout(WidgetTheme.DARK))
            assertNotEquals(old, themeLayout(WidgetTheme.LIGHT))
        }
        assertEquals(cafe.miafetta.tsuki.R.layout.status_widget_dark, themeLayout(WidgetTheme.DARK, false))
        assertEquals(cafe.miafetta.tsuki.R.layout.status_widget_light, themeLayout(WidgetTheme.LIGHT, false))
        assertNotEquals(themeLayout(WidgetTheme.DARK), themeLayout(WidgetTheme.LIGHT))
        assertNotEquals(cafe.miafetta.tsuki.R.layout.status_widget, themeLayout(WidgetTheme.DARK))
    }

    @Test fun referenceCanvasFitsBothDimensionsWithOneScale() {
        for ((width, height) in listOf(360f to 150f, 360f to 162f, 360f to 180f, 300f to 110f, 410f to 160f)) {
            val scale = referenceScale(width, height)
            assertTrue(REFERENCE_WIDTH * scale <= width + .001f)
            assertTrue(REFERENCE_HEIGHT * scale <= height + .001f)
            assertEquals(1028f / 462f, (REFERENCE_WIDTH * scale) / (REFERENCE_HEIGHT * scale), .0001f)
            assertEquals(minOf(width / REFERENCE_WIDTH, height / REFERENCE_HEIGHT), scale, .0001f)
        }
        assertEquals(1f, referenceScale(Float.NaN, Float.POSITIVE_INFINITY), .0001f)
        assertEquals(1f, referenceScale(0f, -1f), .0001f)
    }

    @Test fun nativeBackgroundRootsChangeInBothThemeDirections() {
        assertEquals(cafe.miafetta.tsuki.R.layout.status_widget_bitmap_dark, bitmapThemeLayout(WidgetTheme.DARK))
        assertEquals(cafe.miafetta.tsuki.R.layout.status_widget_bitmap_light, bitmapThemeLayout(WidgetTheme.LIGHT))
        assertNotEquals(bitmapThemeLayout(WidgetTheme.DARK), bitmapThemeLayout(WidgetTheme.LIGHT))
    }

    @Test fun bothThemesKeepAReadableTintOverHostGlass() {
        assertEquals(230, materialTintAlpha(WidgetTheme.LIGHT))
        assertEquals(224, materialTintAlpha(WidgetTheme.DARK))
    }

    @Test fun compactTimersPreserveUnknownPausedAndCompletedMeanings() {
        assertEquals("18h41m", recoveryTimer(WidgetMetric("理智", 23, 210, now + 1121 * 60000), now))
        assertEquals("0h0m", recoveryTimer(WidgetMetric("无人机", 250, 250, now - 1), now))
        assertEquals("时间未知", recoveryTimer(null, now))
        assertEquals("时间未知", recoveryTimer(WidgetMetric("理智", 23, 210), now))
        assertEquals("2h1m（同步）", compactTimer("2时1分（同步）"))
        assertEquals("时间未知", compactTimer("时间未知"))
    }

    @Test fun progressLinesUseSnapshotRatiosWithoutOverflowOrTreatingUnknownAsZero() {
        assertNull(progressLevel(null))
        assertNull(progressLevel(WidgetMetric("合成玉", 12)))
        assertEquals(0, progressLevel(WidgetMetric("保全酬劳", 0, 84)))
        assertEquals(5000, progressLevel(WidgetMetric("合成玉", 900, 1800)))
        assertEquals(2055, progressLevel(WidgetMetric("合成玉", 370, 1800)))
        assertEquals(10000, progressLevel(WidgetMetric("合成玉", 2000, 1800)))
        assertEquals(10000, progressLevel(WidgetMetric("合成玉", Int.MAX_VALUE, Int.MAX_VALUE)))
    }
    @Test fun darkPinsUseTheOpaqueProviderWhileLightPinsKeepHostBlur() {
        assertEquals(OpaqueWidgetProvider::class.java, StatusWidgetProvider.providerClass(WidgetRuntime.FEATURE, WidgetTheme.DARK))
        assertEquals(StatusWidgetProvider::class.java, StatusWidgetProvider.providerClass(WidgetRuntime.FEATURE, WidgetTheme.LIGHT))
        // The Endfield card has one provider in any palette, so an Endfield pin never lands on an Arknights card.
        for (theme in WidgetTheme.entries) {
            assertEquals(EndfieldWidgetProvider::class.java, StatusWidgetProvider.providerClass(WidgetRuntime.ENDFIELD_FEATURE, theme))
        }
        assertEquals(listOf(WidgetTheme.DARK), StatusWidgetProvider.themes(WidgetRuntime.ENDFIELD_FEATURE))
        assertEquals(listOf(WidgetTheme.LIGHT, WidgetTheme.DARK), StatusWidgetProvider.themes(WidgetRuntime.FEATURE))
    }
    @Test fun widgetAmountsOmitApproximationWithoutChangingSnapshotOrProgress() {
        val metrics = listOf(WidgetMetric("理智", 42, 210, estimated = true),
            WidgetMetric("无人机", 116, 250, estimated = true), WidgetMetric("订单进度", 2, 20, estimated = true),
            WidgetMetric("制造进度", 15, 64, estimated = true))
        assertEquals(listOf("42/210", "116/250", "2/20", "15/64"), metrics.map(::cardAmount))
        for (metric in metrics) {
            assertTrue(metric.estimated)
            assertTrue(metric.amount().startsWith("≈"))
            assertEquals(progressLevel(metric.copy(estimated = false)), progressLevel(metric))
        }
        assertEquals("未知", cardAmount(null))
        assertEquals("0/0", cardAmount(WidgetMetric("宿舍休息", 0, text = "0/0")))
        assertEquals("0", cardAmount(WidgetMetric("干员疲劳", 0)))
        val secondary = cardSecondary(snapshot().copy(metrics = metrics))
        assertTrue(secondary.all { !it.second.contains("≈") })
    }

}
