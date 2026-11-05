package cafe.miafetta.tsuki.core

import cafe.miafetta.tsuki.providers.FakeArknightsAdapter
import java.io.File
import java.util.Properties
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

// Instance storage: persistence, restore after ID remapping, version migration and rejection of invalid data.
class WidgetPersistenceTest : WidgetFrameworkFixture() {
    @Test fun persistedInstancesKeepAccountsThemesAndSnapshotsSeparate() {
        val repository = repository()
        val coordinator = coordinator(repository)
        coordinator.configure(config())
        coordinator.configure(config(20, "demo-2").copy(theme = WidgetTheme.LIGHT))
        coordinator.refresh(10, 1000)
        coordinator.refresh(20, 2000)
        val reopened = repository()
        assertEquals("demo-1", reopened.read(10)!!.config.accountId)
        assertEquals(WidgetTheme.LIGHT, reopened.read(20)!!.config.theme)
        assertEquals(1000L, reopened.read(10)!!.snapshot!!.fetchedAt)
        assertEquals(2000L, reopened.read(20)!!.snapshot!!.fetchedAt)
        assertNotEquals(reopened.read(10)!!.snapshot!!.metrics, reopened.read(20)!!.snapshot!!.metrics)
    }

    @Test fun optionalDashboardMetricsRoundTripAndOldFilesStayReadable() {
        val repository = repository()
        val coordinator = coordinator(repository)
        coordinator.configure(config())
        val record = coordinator.refresh(10, 1000)!!
        val snapshot = record.snapshot!!.copy(dashboardMetrics = listOf(WidgetMetric("每周任务", 2, 5)))
        repository.write(record.copy(snapshot = snapshot))
        assertEquals(snapshot, repository().read(10)!!.snapshot)
        val file = File(directory.root, "10.properties")
        val values = Properties().apply { file.inputStream().use { load(it) } }
        values.remove("snapshot.dashboard")
        file.outputStream().use { values.store(it, null) }
        assertTrue(repository().read(10)!!.snapshot!!.dashboardMetrics.isEmpty())
        assertEquals(record.snapshot.metrics, repository().read(10)!!.snapshot!!.metrics)
    }

    @Test fun idsListStoredInstancesAndSkipUnrelatedFiles() {
        val repository = repository()
        repository.write(WidgetRecord(config()))
        repository.write(WidgetRecord(config(20)))
        File(directory.root, "30.properties.tmp").writeText("")
        File(directory.root, "notes.txt").writeText("")
        assertEquals(listOf(10, 20), repository.ids().sorted())
        repository.delete(10)
        assertEquals(listOf(20), repository().ids())
    }

    @Test fun deleteRemovesOnlyTheRequestedInstance() {
        val repository = repository()
        repository.write(WidgetRecord(config()))
        repository.write(WidgetRecord(config(20)))
        repository.delete(10)
        assertNull(repository().read(10))
        assertNotNull(repository().read(20))
    }

    @Test fun restoreMovesConfigurationCacheAndFailureStateAndCanBeRepeated() {
        val repository = repository()
        val original = WidgetRecord(config().copy(theme = WidgetTheme.LIGHT),
            adapter.fetch(config(), 1000), RefreshState.ERROR)
        repository.write(original)
        repository.remap(intArrayOf(10), intArrayOf(30))
        assertNull(repository.read(10))
        assertEquals(original.copy(config = original.config.copy(widgetId = 30)), repository().read(30))
        repository.remap(intArrayOf(10), intArrayOf(30))
        assertEquals(original.snapshot, repository.read(30)!!.snapshot)
    }

    @Test fun overlappingRestoreIsRejectedBeforeChangingAnyData() {
        val repository = repository()
        repository.write(WidgetRecord(config()))
        repository.write(WidgetRecord(config(20, "demo-2")))
        assertThrows(IllegalArgumentException::class.java) {
            repository.remap(intArrayOf(10, 20), intArrayOf(20, 10))
        }
        assertEquals("demo-1", repository.read(10)!!.config.accountId)
        assertEquals("demo-2", repository.read(20)!!.config.accountId)
    }

    @Test fun restoreCanRetryAnAlreadyWrittenDestinationAndKeepUnchangedIds() {
        val repository = repository()
        val original = WidgetRecord(config(), adapter.fetch(config(), 1000), RefreshState.READY)
        repository.write(original)
        repository.write(original.copy(config = original.config.copy(widgetId = 30)))
        repository.remap(intArrayOf(10), intArrayOf(30))
        repository.remap(intArrayOf(30), intArrayOf(30))
        assertNull(repository.read(10))
        assertEquals(original.snapshot, repository.read(30)!!.snapshot)
    }

    @Test fun invalidOrOccupiedRestoreMappingsLeaveDataUntouched() {
        val repository = repository()
        repository.write(WidgetRecord(config()))
        repository.write(WidgetRecord(config(20, "demo-2")))
        assertThrows(IllegalArgumentException::class.java) { repository.remap(intArrayOf(10), intArrayOf(20)) }
        assertThrows(IllegalArgumentException::class.java) { repository.remap(intArrayOf(10, 20), intArrayOf(30, 30)) }
        assertThrows(IllegalArgumentException::class.java) { repository.remap(intArrayOf(10), intArrayOf()) }
        assertEquals("demo-1", repository.read(10)!!.config.accountId)
        assertEquals("demo-2", repository.read(20)!!.config.accountId)
    }

    @Test fun versionZeroConfigGetsDefaultsAndMigratesOnWrite() {
        val properties = Properties().apply {
            setProperty("providerId", adapter.id)
            setProperty("featureId", "skland.arknights.status")
            setProperty("accountId", "demo-1")
            setProperty("characterId", "amiya")
        }
        File(directory.root, "10.properties").outputStream().use { properties.store(it, null) }
        val repository = repository()
        val record = repository.read(10)!!
        assertEquals(WidgetTheme.DARK, record.config.theme)
        assertFalse(record.config.simulateFailure)
        assertEquals(RefreshState.LOADING, record.state)
        repository.write(record)
        val migrated = Properties().apply { File(directory.root, "10.properties").inputStream().use { load(it) } }
        assertEquals("4", migrated.getProperty("version"))
    }

    @Test fun corruptAndFutureVersionDataAreRejectedWithoutRewritingFiles() {
        val file = File(directory.root, "10.properties")
        for (content in listOf("version=1\n", "version=999\n")) {
            file.writeText(content)
            assertThrows(IllegalArgumentException::class.java) { repository().read(10) }
            assertEquals(content, file.readText())
        }
    }

    @Test fun failedStorageWriteDoesNotCreateAFalseSuccess() {
        val blocked = directory.newFile("not-a-directory")
        val repository = FileWidgetRepository(blocked)
        assertThrows(IllegalArgumentException::class.java) { repository.write(WidgetRecord(config())) }
        assertEquals(0L, blocked.length())
    }

    @Test fun invalidMetricAndInstanceIdsCannotEnterStorage() {
        assertThrows(IllegalArgumentException::class.java) { config(0) }
        assertThrows(IllegalArgumentException::class.java) { WidgetMetric("理智", -1, 135) }
        assertEquals(136, WidgetMetric("理智", 136, 135).value)
        assertThrows(IllegalArgumentException::class.java) { WidgetMetric("理智", 1, 0) }
        assertThrows(IllegalArgumentException::class.java) { repository().delete(-1) }
        assertThrows(IllegalArgumentException::class.java) { WidgetSnapshot("模拟", emptyList(), 0) }
    }

    @Test fun oldVersionOneSnapshotsLoadAndUpgradeWithRecoveryAndFailureFields() {
        val repository = repository()
        val snapshot = WidgetSnapshot("测试", listOf(WidgetMetric("理智", 136, 135, 2000)), 1000,
            listOf("订单 未提供"), 900)
        repository.write(WidgetRecord(config(), snapshot, RefreshState.ERROR, RefreshFailure.RATE_LIMIT, 3000))
        assertEquals(snapshot, repository().read(10)!!.snapshot)
        assertEquals(3000L, repository().read(10)!!.retryAt)
        val file = File(directory.root, "10.properties")
        val properties = Properties().apply { file.inputStream().use { load(it) } }
        properties.setProperty("version", "1")
        listOf("metric.0.recoveryAt", "snapshot.dataAt", "snapshot.notes", "note.0", "failure", "retryAt")
            .forEach { properties.remove(it) }
        file.outputStream().use { properties.store(it, null) }
        val legacy = repository().read(10)!!
        assertNull(legacy.snapshot!!.metrics.single().recoveryAt)
        assertEquals(1000L, legacy.snapshot.dataAt)
        assertFalse(legacy.snapshot.dataTimeKnown)
        repository.write(legacy)
        assertFalse(repository().read(10)!!.snapshot!!.dataTimeKnown)
        assertTrue(legacy.snapshot.notes.isEmpty())
        assertNull(legacy.failure)
    }

    @Test fun expandedSnapshotsAndTextMetricsPersistAndLegacyVersionTwoStillLoads() {
        val repository = repository()
        val snapshot = adapter.fetch(config(), 1000).copy(notes = List(10) { "测试提示 $it" })
        assertEquals(11, snapshot.metrics.size)
        repository.write(WidgetRecord(config(), snapshot, RefreshState.READY))
        assertEquals(snapshot, repository().read(10)!!.snapshot)
        assertEquals("训练室 空闲中", repository.read(10)!!.snapshot!!.metrics.single { it.label == "训练室" }.display())
        val file = File(directory.root, "10.properties")
        val properties = Properties().apply { file.inputStream().use { load(it) } }
        properties.setProperty("version", "2")
        properties.setProperty("snapshot.count", "6")
        properties.setProperty("snapshot.notes", "0")
        file.outputStream().use { properties.store(it, null) }
        val legacy = repository.read(10)!!
        assertEquals(snapshot.metrics.take(6), legacy.snapshot!!.metrics)
        repository.write(legacy)
        assertEquals(legacy, repository.read(10))
        assertThrows(IllegalArgumentException::class.java) { WidgetMetric("训练室", 0, text = "bad\ntext") }
    }

    @Test fun avatarTrainingAndEstimatesRoundTripAndOldCachesRemainUnknown() {
        val repository = repository()
        val original = adapter.fetch(config(), 1000).copy(dataAt = 800,
            avatarUrl = "https://web.hycdn.cn/arknights/game/assets/avatar/test.png",
            training = WidgetTraining(TrainingState.TRAINING, "char_test_trainee", "测试干员", 2, 60000,
                "https://web.hycdn.cn/arknights/game/assets/char/avatar/char_test_trainee.png"),
            metrics = listOf(WidgetMetric("理智", 1, 10, estimated = true)))
        repository.write(WidgetRecord(config().copy(theme = WidgetTheme.LIGHT), original, RefreshState.READY))
        assertEquals(original, repository().read(10)!!.snapshot)
        for (version in 1..3) {
            val file = File(directory.root, "10.properties")
            val properties = Properties().apply { file.inputStream().use { load(it) } }
            properties.setProperty("version", version.toString())
            properties.keys.toList().filter {
                it.toString().startsWith("training.") || it == "snapshot.avatarUrl" || it == "metric.0.estimated"
            }
                .forEach { properties.remove(it) }
            file.outputStream().use { properties.store(it, null) }
            val old = repository().read(10)!!
            assertEquals(TrainingState.UNKNOWN, old.snapshot!!.training.state)
            assertNull(old.snapshot.avatarUrl)
            assertEquals(800L, old.snapshot.dataAt)
            assertEquals(WidgetTheme.LIGHT, old.config.theme)
            assertFalse(old.snapshot.metrics.single().estimated)
        }
    }

}
