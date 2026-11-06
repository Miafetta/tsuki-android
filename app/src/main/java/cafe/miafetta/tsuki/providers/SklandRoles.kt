package cafe.miafetta.tsuki.providers

import org.json.JSONArray
import org.json.JSONObject

// Games bound to a Skland account. The binding list names them by appCode; Tsuki names them by data source ID.
internal enum class SklandGame(val appCode: String, val providerId: String) {
    // Installed widgets and role caches store "skland" for Arknights, so its ID can never change.
    ARKNIGHTS("arknights", SklandArknightsAdapter.ID),
    ENDFIELD("endfield", "skland.endfield");

    companion object {
        fun fromAppCode(code: String) = entries.firstOrNull { it.appCode == code }
        fun fromProvider(providerId: String) = entries.firstOrNull { it.providerId == providerId }
        val providerIds get() = entries.map { it.providerId }.toSet()
    }
}

// One game role on a Skland account. Arknights roles keep the legacy label "<name> · <channel> · <uid>"; newer games
// store the nickname as the label and the server separately, and their uid also encodes the server (see endfieldId).
internal data class SklandRole(val uid: String, val label: String, val game: SklandGame = SklandGame.ARKNIGHTS,
    val server: String? = null) {
    init {
        require(label.isNotBlank() && label.length <= 80)
        when (game) {
            SklandGame.ARKNIGHTS -> require(uid.matches(ARKNIGHTS_UID) && server == null)
            SklandGame.ENDFIELD -> require(uid.matches(ENDFIELD_UID) && server != null && server.isNotBlank() &&
                server.length <= 20)
        }
    }

    // Role IDs are unique only within one game.
    val key get() = "${game.providerId}/$uid"

    companion object {
        private val ARKNIGHTS_UID = Regex("[0-9]{1,20}")
        private val SERVER_ID = Regex("[0-9A-Za-z_-]{1,20}")
        private val ROLE_ID = Regex("[0-9A-Za-z_-]{1,40}")
        private val ENDFIELD_UID = Regex("[0-9A-Za-z_-]{1,20}~[0-9A-Za-z_-]{1,40}")

        // Endfield roles are identified by server and role together; "~" is outside both parts' alphabets.
        fun endfieldId(serverId: String, roleId: String): String {
            require(serverId.matches(SERVER_ID) && roleId.matches(ROLE_ID))
            return "$serverId~$roleId"
        }

        // Server and role ID of an Endfield character ID made by endfieldId.
        fun endfieldParts(uid: String): Pair<String, String> {
            require(uid.matches(ENDFIELD_UID))
            return uid.substringBefore('~') to uid.substringAfter('~')
        }
    }
}

internal object SklandRoles {
    private const val PER_LIST = 20
    const val LIMIT = 40

    // An account is valid when it has a role in at least one supported game; unknown games are skipped.
    fun parse(data: JSONObject): List<SklandRole> {
        val apps = data.getJSONArray("list")
        val roles = mutableListOf<SklandRole>()
        for (index in 0 until apps.length()) {
            val app = apps.getJSONObject(index)
            val bindings = app.getJSONArray("bindingList")
            require(bindings.length() <= PER_LIST)
            when (SklandGame.fromAppCode(app.getString("appCode"))) {
                SklandGame.ARKNIGHTS -> roles += arknights(bindings)
                SklandGame.ENDFIELD -> roles += endfield(bindings)
                null -> Unit
            }
        }
        require(roles.isNotEmpty() && roles.size <= LIMIT && roles.distinctBy { it.key }.size == roles.size)
        return roles
    }

    private fun arknights(bindings: JSONArray) = (0 until bindings.length()).map { index ->
        val role = bindings.getJSONObject(index)
        val uid = role.getString("uid")
        val name = role.getString("nickName").take(40)
        val channel = role.optString("channelName", "").take(20)
        SklandRole(uid, "$name · $channel · $uid".take(80))
    }

    // Endfield lists its roles per binding; a deleted binding is no longer playable. The role's serverName is a region
    // in English ("China"), so the binding's channel ("官服") names the server, as it does for Arknights.
    private fun endfield(bindings: JSONArray) = (0 until bindings.length()).map(bindings::getJSONObject)
        .filterNot { it.optBoolean("isDelete") }
        .flatMap { binding ->
            val roles = binding.optJSONArray("roles") ?: JSONArray()
            require(roles.length() <= PER_LIST)
            val channel = binding.optString("channelName")
            (0 until roles.length()).map { index ->
                val role = roles.getJSONObject(index)
                val serverId = role.getString("serverId")
                val roleId = role.getString("roleId")
                val server = channel.ifBlank { role.optString("serverName") }.ifBlank { serverId }
                SklandRole(SklandRole.endfieldId(serverId, roleId), role.optString("nickname").ifBlank { roleId }.take(40),
                    SklandGame.ENDFIELD, server.take(20))
            }
        }
}
