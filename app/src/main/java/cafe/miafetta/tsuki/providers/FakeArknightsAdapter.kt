package cafe.miafetta.tsuki.providers

import cafe.miafetta.tsuki.core.DataSourceAdapter
import cafe.miafetta.tsuki.core.WidgetCharacter
import cafe.miafetta.tsuki.core.WidgetInstanceConfig
import cafe.miafetta.tsuki.core.WidgetMetric
import cafe.miafetta.tsuki.core.WidgetSnapshot
import cafe.miafetta.tsuki.core.WidgetTraining
import cafe.miafetta.tsuki.core.TrainingState
import java.io.IOException

class FakeArknightsAdapter : DataSourceAdapter {
    override val id = ID
    companion object { const val ID = "fake.skland" }
    override val characters = listOf(
        WidgetCharacter("demo-1", "amiya", "模拟账号一 · 阿米娅"),
        WidgetCharacter("demo-1", "kaltsit", "模拟账号一 · 凯尔希"),
        WidgetCharacter("demo-2", "amiya", "模拟账号二 · 阿米娅"),
    )

    override fun fetch(config: WidgetInstanceConfig, now: Long): WidgetSnapshot {
        val index = characters.indexOfFirst { it.accountId == config.accountId && it.characterId == config.characterId }
        require(index >= 0 && config.providerId == id)
        if (config.simulateFailure) throw IOException("Simulated data source failure")
        // Labels keep the internal " · " delimiter; the displayed title uses a plain space.
        return WidgetSnapshot(characters[index].label.replace(" · ", " "), listOf(
            WidgetMetric("理智", 84 + index * 12, 135),
            WidgetMetric("无人机", 120 + index * 20, 200),
            WidgetMetric("订单进度", 3 + index, 20),
            WidgetMetric("制造进度", 4 + index, 64),
            WidgetMetric("公开招募", 2, 4),
            WidgetMetric("干员疲劳", index),
            WidgetMetric("每日任务", 7 + index, 10),
            WidgetMetric("合成玉", 1200 + index * 300, 1800),
            WidgetMetric("训练室", 0, text = "空闲中"),
            WidgetMetric("休息", 18, 20),
            WidgetMetric("保全酬劳", 24 + index * 12, 84),
        ), now, training = WidgetTraining(TrainingState.IDLE))
    }
}
