package cafe.miafetta.tsuki.providers

import cafe.miafetta.tsuki.core.WidgetSnapshot

internal class SklandArknightsAdapter(accounts: AccountRepository, private val client: SklandClient,
    onSnapshot: (String, String, WidgetSnapshot) -> Unit = { _, _, _ -> },
    clock: () -> Long = System::currentTimeMillis) : SklandRoleAdapter(SklandGame.ARKNIGHTS, accounts, onSnapshot, clock) {
    override fun request(cred: String, characterId: String) =
        ArknightsParser.snapshot(client.player(cred, characterId), characterId, clock())

    companion object { const val ID = "skland" }
}
