package cafe.miafetta.tsuki.core

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Properties

/** One outstanding placement, retained across process death but never across a reboot. */
internal class WidgetPinRequest(private val file: File) {
    val pending get() = file.exists()
    fun prepare(token: String, config: WidgetInstanceConfig, owned: Set<Int>, now: Long, boot: Int) {
        val values = Properties().apply {
            setProperty("token", token)
            setProperty("time", now.toString())
            setProperty("boot", boot.toString())
            setProperty("owned", owned.joinToString(","))
            setProperty("provider", config.providerId)
            setProperty("feature", config.featureId)
            setProperty("account", config.accountId)
            setProperty("character", config.characterId)
            setProperty("theme", config.theme.name)
            setProperty("failure", config.simulateFailure.toString())
        }
        val bytes = ByteArrayOutputStream().apply { values.store(this, null) }.toByteArray()
        writeAtomic(file, bytes)
    }

    // [features] is the widget feature of each owned instance's provider; a new instance of another game never matches.
    fun candidate(owned: Map<Int, WidgetTheme>, configured: Set<Int>, now: Long, boot: Int,
        features: Map<Int, String> = emptyMap()): WidgetInstanceConfig? {
        val values = read() ?: return null
        val age = now - values.getProperty("time").toLong()
        if (age !in 0..120_000L || values.getProperty("boot").toInt() != boot) {
            clear()
            return null
        }
        val previous = values.getProperty("owned").split(',').filter(String::isNotEmpty).map(String::toInt).toSet()
        val theme = WidgetTheme.valueOf(values.getProperty("theme"))
        val feature = values.getProperty("feature")
        val candidates = owned.filter { (id, palette) ->
            id !in previous && id !in configured && palette == theme && (features[id] ?: feature) == feature
        }.keys
        if (candidates.size > 1) {
            clear()
            return null
        }
        if (candidates.isEmpty()) return null
        return WidgetInstanceConfig(candidates.single(), values.getProperty("provider"), feature,
            values.getProperty("account"), values.getProperty("character"), theme, values.getProperty("failure").toBooleanStrict())
    }

    fun clear(token: String? = null, before: Long? = null) {
        val values = read()
        if (token != null && values?.getProperty("token") != token) return
        if (before != null && (values?.getProperty("time")?.toLong() ?: return) >= before) return
        if (file.exists() && !file.delete()) throw java.io.IOException("Cannot clear widget placement")
    }

    private fun read(): Properties? = if (!file.exists()) null else Properties().apply { file.inputStream().use { load(it) } }
}
