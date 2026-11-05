package cafe.miafetta.tsuki.core

import java.io.File
import java.nio.file.Files
import org.json.JSONObject

internal data class RoleData(val snapshot: WidgetSnapshot? = null, val failure: RefreshFailure? = null,
    val retryAt: Long = 0)

internal class RoleDataStore(private val directory: File) {
    private fun file(accountId: String, roleId: String): File {
        require(accountId.matches(Regex("[a-zA-Z0-9-]{1,80}")) && roleId.matches(Regex("[0-9]{1,20}")))
        return File(directory, "$accountId.$roleId.json")
    }

    @Synchronized fun read(accountId: String, roleId: String): RoleData {
        val file = file(accountId, roleId)
        if (!file.exists()) return RoleData()
        require(file.length() <= 128 * 1024)
        val value = JSONObject(file.readText())
        require(value.getInt("version") == 1)
        return RoleData(value.optJSONObject("snapshot")?.let(SnapshotJson::read),
            value.optString("failure").takeIf { it.isNotEmpty() }?.let(RefreshFailure::valueOf),
            value.optLong("retryAt", 0).also { require(it >= 0) })
    }

    @Synchronized fun write(accountId: String, roleId: String, data: RoleData) {
        require(data.retryAt >= 0)
        val value = JSONObject().put("version", 1).put("snapshot", data.snapshot?.let(SnapshotJson::write))
            .put("failure", data.failure?.name).put("retryAt", data.retryAt)
        writeAtomic(file(accountId, roleId), value.toString().toByteArray())
    }

    @Synchronized fun clear(accountId: String? = null) {
        require(accountId == null || accountId.matches(Regex("[a-zA-Z0-9-]{1,80}")))
        directory.listFiles()?.filter { it.extension == "json" &&
            (accountId == null || it.name.startsWith("$accountId.")) }?.forEach { Files.deleteIfExists(it.toPath()) }
    }
}
