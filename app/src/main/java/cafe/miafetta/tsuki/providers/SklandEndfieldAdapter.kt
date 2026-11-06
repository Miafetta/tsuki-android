package cafe.miafetta.tsuki.providers

import cafe.miafetta.tsuki.core.WidgetSnapshot

internal class SklandEndfieldAdapter(accounts: AccountRepository, private val client: SklandClient,
    onSnapshot: (String, String, WidgetSnapshot) -> Unit = { _, _, _ -> },
    clock: () -> Long = System::currentTimeMillis) : SklandRoleAdapter(SklandGame.ENDFIELD, accounts, onSnapshot, clock) {
    override fun request(cred: String, characterId: String): WidgetSnapshot {
        val (serverId, roleId) = SklandRole.endfieldParts(characterId)
        return EndfieldParser.snapshot(client.endfieldCard(cred, serverId, roleId), roleId, clock())
    }
}
