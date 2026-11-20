package cafe.miafetta.tsuki.providers

// Data sources whose roles are Arknights roles: the Skland adapter and the debug fixture that shares its widget. Their
// snapshots stored before metric IDs existed resolve by label (core/MetricIds.kt), and their roles keep the "<account>/
// <role>" key that installed versions saved in the app.
internal val ARKNIGHTS_SOURCE_IDS = setOf(SklandArknightsAdapter.ID, FakeArknightsAdapter.ID)

internal fun isArknightsSource(providerId: String) = providerId in ARKNIGHTS_SOURCE_IDS
