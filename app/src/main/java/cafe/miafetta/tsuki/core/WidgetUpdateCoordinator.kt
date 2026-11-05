package cafe.miafetta.tsuki.core

class WidgetUpdateCoordinator(
    private val repository: WidgetInstanceRepository,
    private val definitions: Map<String, WidgetDefinition>,
    private val adapters: Map<String, DataSourceAdapter>,
) {
    private val requests = mutableMapOf<Int, Any>()

    @Synchronized fun configure(config: WidgetInstanceConfig): Boolean {
        val adapter = adapter(config)
        require(adapter.characters.any { it.accountId == config.accountId && it.characterId == config.characterId })
        val previous = repository.read(config.widgetId)
        if (previous != null && previous.config.sameDataBinding(config) &&
            previous.config.simulateFailure == config.simulateFailure) {
            repository.write(previous.copy(config = config))
            return false
        }
        val cache = previous?.takeIf { it.config.sameDataBinding(config) }?.snapshot
        repository.write(WidgetRecord(config, cache, RefreshState.LOADING))
        requests.remove(config.widgetId)
        return true
    }

    fun refresh(id: Int, now: Long, onLoading: (WidgetRecord) -> Unit = {}): WidgetRecord? {
        val request = Any()
        val previous = synchronized(this) {
            val record = repository.read(id) ?: return null
            val loading = record.copy(state = RefreshState.LOADING, failure = null, retryAt = 0)
            repository.write(loading)
            requests[id] = request
            onLoading(loading)
            record
        }
        val updated = try {
            val snapshot = adapter(previous.config).fetch(previous.config, now)
            previous.copy(snapshot = snapshot, state = RefreshState.READY, failure = null, retryAt = 0)
        } catch (error: DataSourceFailure) {
            previous.copy(state = RefreshState.ERROR, failure = error.reason, retryAt = error.retryAt)
        } catch (_: Exception) {
            previous.copy(state = RefreshState.ERROR, failure = RefreshFailure.NETWORK, retryAt = 0)
        }
        return synchronized(this) {
            if (requests[id] !== request) return null
            requests.remove(id)
            // Theme-only saves must survive a fetch that started with the previous theme.
            val current = repository.read(id) ?: return null
            val result = updated.copy(config = current.config)
            repository.write(result)
            result
        }
    }

    @Synchronized fun cancel(id: Int) { requests.remove(id) }

    @Synchronized fun queued(id: Int): WidgetRecord? {
        val record = repository.read(id) ?: return null
        return record.copy(state = RefreshState.LOADING, failure = null, retryAt = 0).also(repository::write)
    }

    @Synchronized fun signedOut(id: Int) {
        requests.remove(id)
        repository.read(id)?.let {
            repository.write(it.copy(snapshot = null, state = RefreshState.ERROR,
                failure = RefreshFailure.AUTH_EXPIRED, retryAt = 0))
        }
    }

    @Synchronized fun delete(id: Int) {
        requests.remove(id)
        repository.delete(id)
    }

    @Synchronized fun remap(oldIds: IntArray, newIds: IntArray) {
        (oldIds + newIds).forEach { requests.remove(it) }
        repository.remap(oldIds, newIds)
    }

    private fun adapter(config: WidgetInstanceConfig): DataSourceAdapter {
        val definition = requireNotNull(definitions[config.featureId]) { "Unknown widget definition" }
        require(definition.providerId == config.providerId || config.providerId in definition.otherProviders)
        return requireNotNull(adapters[config.providerId]) { "Unknown data source" }
    }
}
