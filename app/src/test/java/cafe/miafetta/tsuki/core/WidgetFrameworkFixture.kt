package cafe.miafetta.tsuki.core

import cafe.miafetta.tsuki.providers.FakeArknightsAdapter
import java.io.File
import java.util.Properties
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

// Shared fixtures and helpers for the classes split out of the former WidgetFrameworkTest.
abstract class WidgetFrameworkFixture {
    @get:Rule val directory = TemporaryFolder()
    protected val adapter = FakeArknightsAdapter()
    protected fun config(id: Int = 10, account: String = "demo-1", character: String = "amiya") =
        WidgetInstanceConfig(id, adapter.id, "skland.arknights.status", account, character)
    protected fun repository() = FileWidgetRepository(directory.root)
    protected fun coordinator(repository: WidgetInstanceRepository) = WidgetUpdateCoordinator(repository,
        mapOf("skland.arknights.status" to WidgetDefinition("skland.arknights.status", adapter.id)),
        mapOf(adapter.id to adapter))

    protected fun inFlight(change: (WidgetUpdateCoordinator, FileWidgetRepository) -> Unit) {
        val started = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        val blocking = object : DataSourceAdapter {
            override val id = adapter.id
            override val characters = adapter.characters
            override fun fetch(config: WidgetInstanceConfig, now: Long): WidgetSnapshot {
                started.countDown()
                check(release.await(5, java.util.concurrent.TimeUnit.SECONDS))
                return adapter.fetch(config, now)
            }
        }
        val repository = repository()
        val coordinator = WidgetUpdateCoordinator(repository,
            mapOf("skland.arknights.status" to WidgetDefinition("skland.arknights.status", blocking.id)), mapOf(blocking.id to blocking))
        coordinator.configure(config())
        val executor = java.util.concurrent.Executors.newFixedThreadPool(2)
        try {
            val refresh = executor.submit<WidgetRecord?> { coordinator.refresh(10, 1000) }
            assertTrue(started.await(2, java.util.concurrent.TimeUnit.SECONDS))
            executor.submit { change(coordinator, repository) }.get(2, java.util.concurrent.TimeUnit.SECONDS)
            val after = repository.read(10)
            release.countDown()
            assertNull(refresh.get(2, java.util.concurrent.TimeUnit.SECONDS))
            assertEquals(after, repository.read(10))
        } finally {
            release.countDown()
            executor.shutdownNow()
        }
    }
}
