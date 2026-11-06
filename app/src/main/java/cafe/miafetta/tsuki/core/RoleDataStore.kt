package cafe.miafetta.tsuki.core

import java.io.File
import java.nio.file.Files
import org.json.JSONObject

internal data class RoleData(val snapshot: WidgetSnapshot? = null, val failure: RefreshFailure? = null,
    val retryAt: Long = 0)

// Role caches by data source. [legacyProvider] (Arknights) keeps the "<account>.<role>.json" files at the top level that
// installed versions wrote; every other data source gets its own folder, so equal role IDs of two games never collide.
internal class RoleDataStore(private val directory: File, private val legacyProvider: String) {
    private fun file(providerId: String, accountId: String, roleId: String): File {
        require(accountId.matches(ACCOUNT))
        if (providerId == legacyProvider) {
            require(roleId.matches(LEGACY_ROLE))
            return File(directory, "$accountId.$roleId.json")
        }
        require(providerId.matches(PROVIDER) && roleId.matches(ROLE))
        return File(File(directory, providerId), "$accountId.$roleId.json")
    }

    @Synchronized fun read(providerId: String, accountId: String, roleId: String): RoleData {
        val file = file(providerId, accountId, roleId)
        if (!file.exists()) return RoleData()
        require(file.length() <= 128 * 1024)
        val value = JSONObject(file.readText())
        require(value.getInt("version") == 1)
        return RoleData(value.optJSONObject("snapshot")?.let(SnapshotJson::read),
            value.optString("failure").takeIf { it.isNotEmpty() }?.let(RefreshFailure::valueOf),
            value.optLong("retryAt", 0).also { require(it >= 0) })
    }

    @Synchronized fun write(providerId: String, accountId: String, roleId: String, data: RoleData) {
        require(data.retryAt >= 0)
        val value = JSONObject().put("version", 1).put("snapshot", data.snapshot?.let(SnapshotJson::write))
            .put("failure", data.failure?.name).put("retryAt", data.retryAt)
        writeAtomic(file(providerId, accountId, roleId), value.toString().toByteArray())
    }

    @Synchronized fun clear(accountId: String? = null) {
        require(accountId == null || accountId.matches(ACCOUNT))
        val folders = listOf(directory) + (directory.listFiles()?.filter { it.isDirectory } ?: emptyList())
        folders.flatMap { it.listFiles()?.asList() ?: emptyList() }
            .filter { it.isFile && it.extension == "json" && (accountId == null || it.name.startsWith("$accountId.")) }
            .forEach { Files.deleteIfExists(it.toPath()) }
    }

    private companion object {
        val ACCOUNT = Regex("[a-zA-Z0-9-]{1,80}")
        val LEGACY_ROLE = Regex("[0-9]{1,20}")
        val PROVIDER = Regex("[a-z]{1,20}(\\.[a-z]{1,20}){1,3}")
        val ROLE = Regex("[0-9A-Za-z_~-]{1,80}")
    }
}
