package cafe.miafetta.tsuki.widget

import cafe.miafetta.tsuki.providers.AndroidCredentialKey
import cafe.miafetta.tsuki.providers.SklandAccount
import cafe.miafetta.tsuki.providers.SklandClient
import cafe.miafetta.tsuki.providers.matchingAccountForLogin
import java.util.UUID

// Account changes and their effect on bound widgets. Every change bumps accountGeneration under the runtime lock.

internal fun WidgetRuntime.login(name: String, cred: String) {
    require(name.length <= 40)
    SklandClient.validateCred(cred)
    val generation = synchronized(this) { accountGeneration }
    val roles = client.roles(cred)
    var previousCred: String? = null
    synchronized(this) {
        require(generation == accountGeneration) { "Account operation was cancelled" }
        val existing = matchingAccountForLogin(accounts.list(), cred, roles)
        // The name is an optional alias: blank stays blank (updates prefill it, so blank means it was cleared).
        accounts.save(SklandAccount(existing?.id ?: UUID.randomUUID().toString(), name.trim(), cred, roles))
        accountGeneration++
        if (existing != null && (existing.cred != cred || existing.roles != roles)) {
            previousCred = existing.cred.takeIf { it != cred }
            skland.forget(existing.id)
            roleData.clear(existing.id)
            // Widgets keep their binding when the role is still on the account; otherwise they become unconfigured.
            repository.ids().forEach { id ->
                val config = read(id)?.config
                if (config?.accountId != existing.id) return@forEach
                if (roles.none { it.uid == config.characterId }) {
                    delete(id)
                    redraw(id)
                } else {
                    coordinator.signedOut(id)
                    update(id, true)
                }
            }
        }
    }
    previousCred?.let(client::forget)
}

// "更新 Cred" with the Cred left blank: verify again with the stored Cred, e.g. to change only the name. The Cred stays
// in this process; an account removed in the meantime is reported as an invalid request.
internal fun WidgetRuntime.reverify(accountId: String, name: String) {
    val cred = synchronized(this) { accounts.list().singleOrNull { it.id == accountId }?.cred }
    login(name, requireNotNull(cred) { "Account no longer exists" })
}

internal fun WidgetRuntime.logout(accountId: String) {
    val cred: String?
    synchronized(this) {
        accountGeneration++
        cred = accounts.list().singleOrNull { it.id == accountId }?.cred
        accounts.delete(accountId)
        skland.forget(accountId)
        roleData.clear(accountId)
        clearAccountWidgets(accountId)
    }
    if (cred != null) client.forget(cred)
}

internal fun WidgetRuntime.resetAccounts() {
    synchronized(this) {
        accountGeneration++
        clearAccountWidgets(null)
        accounts.clear()
        skland.forget()
        roleData.clear()
        AndroidCredentialKey.delete()
    }
    client.clear()
}

private fun WidgetRuntime.clearAccountWidgets(accountId: String?) {
    repository.ids().forEach { id ->
        val config = repository.read(id)?.config
        if (config?.providerId == skland.id && (accountId == null || config.accountId == accountId)) {
            coordinator.delete(id)
            scheduler.cancel(id)
            if (id in StatusWidgetProvider.owned(application)) render(id, null)
        }
    }
}
