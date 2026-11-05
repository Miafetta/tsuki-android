package cafe.miafetta.tsuki.core

import cafe.miafetta.tsuki.providers.FakeArknightsAdapter
import java.io.File
import java.util.Properties
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

// Update coordinator: refresh results, rebinding, adapter contract and changes made while a fetch is running.
class WidgetCoordinatorTest : WidgetFrameworkFixture() {
    @Test fun failedRefreshRetainsLastSuccessfulSnapshotAndItsTime() {
        val repository = repository()
        val coordinator = coordinator(repository)
        coordinator.configure(config())
        val successful = coordinator.refresh(10, 1000)!!
        coordinator.configure(config().copy(simulateFailure = true))
        val loading = mutableListOf<WidgetRecord>()
        val failed = coordinator.refresh(10, 2000) { loading.add(it) }!!
        assertEquals(RefreshState.LOADING, loading.single().state)
        assertEquals(successful.snapshot, loading.single().snapshot)
        assertEquals(RefreshState.ERROR, failed.state)
        assertEquals(successful.snapshot, failed.snapshot)
        assertEquals(failed, repository().read(10))
        coordinator.configure(config())
        assertEquals(RefreshState.READY, coordinator.refresh(10, 3000)!!.state)
        assertEquals(3000L, repository.read(10)!!.snapshot!!.fetchedAt)
    }

    @Test fun failedFirstFetchHasNoInventedSnapshot() {
        val repository = repository()
        val coordinator = coordinator(repository)
        coordinator.configure(config().copy(simulateFailure = true))
        val result = coordinator.refresh(10, 1000)!!
        assertEquals(RefreshState.ERROR, result.state)
        assertNull(result.snapshot)
    }

    @Test fun changingCharacterOrAccountClearsOldDataButChangingThemeKeepsIt() {
        val repository = repository()
        val coordinator = coordinator(repository)
        coordinator.configure(config())
        val original = coordinator.refresh(10, 1000)!!.snapshot
        coordinator.configure(config().copy(theme = WidgetTheme.LIGHT))
        assertEquals(original, repository.read(10)!!.snapshot)
        coordinator.configure(config(character = "kaltsit"))
        assertNull(repository.read(10)!!.snapshot)
        coordinator.refresh(10, 2000)
        coordinator.configure(config(account = "demo-2").copy(simulateFailure = true))
        assertNull(coordinator.refresh(10, 3000)!!.snapshot)
    }

    @Test fun invalidAccountBindingDoesNotOverwriteExistingConfiguration() {
        val repository = repository()
        val coordinator = coordinator(repository)
        coordinator.configure(config())
        val original = repository.read(10)
        assertThrows(IllegalArgumentException::class.java) { coordinator.configure(config(account = "unknown")) }
        assertEquals(original, repository.read(10))
        assertNull(coordinator.refresh(999, 0))
    }

    @Test fun newDefinitionAndAdapterUseTheSameCoordinatorAndSnapshotModel() {
        val repository = repository()
        val other = object : DataSourceAdapter {
            override val id = "fake.other"
            override val characters = listOf(WidgetCharacter("other", "role", "其他模拟角色"))
            override fun fetch(config: WidgetInstanceConfig, now: Long) =
                WidgetSnapshot("其他组件", listOf(WidgetMetric("进度", 1, 5)), now)
        }
        val coordinator = WidgetUpdateCoordinator(repository,
            mapOf("other.status" to WidgetDefinition("other.status", other.id)), mapOf(other.id to other))
        coordinator.configure(WidgetInstanceConfig(10, other.id, "other.status", "other", "role"))
        assertEquals("其他组件", coordinator.refresh(10, 1000)!!.snapshot!!.title)
    }

    @Test fun reconfigurationWhileFetchRunsRejectsItsOldResultWithoutBlocking() {
        inFlight { coordinator, repository ->
            coordinator.configure(config(character = "kaltsit"))
            assertEquals("kaltsit", repository.read(10)!!.config.characterId)
        }
    }

    @Test fun deletionWhileFetchRunsCannotRecreateAnInstance() {
        inFlight { coordinator, repository ->
            coordinator.delete(10)
            assertNull(repository.read(10))
        }
    }

    @Test fun logoutWhileFetchRunsErasesSnapshotAndRejectsTheOldResult() {
        inFlight { coordinator, repository ->
            coordinator.signedOut(10)
            assertNull(repository.read(10)!!.snapshot)
            assertEquals(RefreshFailure.AUTH_EXPIRED, repository.read(10)!!.failure)
        }
    }

    @Test fun themeOnlySavePreservesEveryRefreshStateAndOtherInstancesAcrossReopen() {
        val repository = repository()
        val coordinator = coordinator(repository)
        val snapshot = adapter.fetch(config(), 1000).copy(dataAt = 900)
        val other = WidgetRecord(config(20), snapshot, RefreshState.READY)
        repository.write(other)
        for (state in RefreshState.entries) {
            val original = WidgetRecord(config(), snapshot, state,
                if (state == RefreshState.ERROR) RefreshFailure.NETWORK else null, 4000)
            repository.write(original)
            for (theme in listOf(WidgetTheme.LIGHT, WidgetTheme.DARK)) {
                assertFalse(coordinator.configure(config().copy(theme = theme)))
                assertEquals(original.copy(config = config().copy(theme = theme)), repository().read(10))
                assertEquals(other, repository().read(20))
                assertEquals(900L, repository().read(10)!!.snapshot!!.dataAt)
            }
        }
    }

    @Test fun themeChangeDoesNotCancelFetchAndItsNewThemeSurvivesTheResult() {
        val repository = repository()
        val started = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        var calls = 0
        val blocking = object : DataSourceAdapter {
            override val id = adapter.id
            override val characters = adapter.characters
            override fun fetch(config: WidgetInstanceConfig, now: Long): WidgetSnapshot {
                calls++
                started.countDown()
                check(release.await(5, java.util.concurrent.TimeUnit.SECONDS))
                return adapter.fetch(config, now)
            }
        }
        val coordinator = WidgetUpdateCoordinator(repository,
            mapOf("skland.arknights.status" to WidgetDefinition("skland.arknights.status", blocking.id)), mapOf(blocking.id to blocking))
        coordinator.configure(config())
        val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
        try {
            val request = executor.submit<WidgetRecord?> { coordinator.refresh(10, 1000) }
            assertTrue(started.await(2, java.util.concurrent.TimeUnit.SECONDS))
            assertFalse(coordinator.configure(config().copy(theme = WidgetTheme.LIGHT)))
            release.countDown()
            assertEquals(WidgetTheme.LIGHT, request.get(2, java.util.concurrent.TimeUnit.SECONDS)!!.config.theme)
            assertEquals(WidgetTheme.LIGHT, repository().read(10)!!.config.theme)
            assertEquals(1, calls)
        } finally {
            release.countDown()
            executor.shutdownNow()
        }
    }

    @Test fun characterChangeAndLogoutClearBothAvatarIdentitiesIncludingFailedFirstRefresh() {
        val repository = repository()
        val coordinator = coordinator(repository)
        val original = adapter.fetch(config(), 1000).copy(avatarUrl = "official-account-avatar",
            training = WidgetTraining(TrainingState.PAUSED, "char_test_trainee", "测试干员", 1,
                avatarUrl = "official-trainee-avatar", remainingSecs = 900))
        repository.write(WidgetRecord(config(), original, RefreshState.READY))
        assertEquals(original, repository().read(10)!!.snapshot)
        assertTrue(coordinator.configure(config(character = "kaltsit").copy(simulateFailure = true)))
        assertNull(repository.read(10)!!.snapshot)
        assertNull(coordinator.refresh(10, 2000)!!.snapshot)
        repository.write(WidgetRecord(config(), original, RefreshState.READY))
        coordinator.signedOut(10)
        assertNull(repository().read(10)!!.snapshot)
    }

}
