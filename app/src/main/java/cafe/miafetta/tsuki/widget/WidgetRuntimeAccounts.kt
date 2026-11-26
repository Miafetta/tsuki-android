package cafe.miafetta.tsuki.widget

import cafe.miafetta.tsuki.core.AccountProblem
import cafe.miafetta.tsuki.core.AccountProblemException
import cafe.miafetta.tsuki.providers.AndroidCredentialKey
import cafe.miafetta.tsuki.providers.SklandAccount
import cafe.miafetta.tsuki.providers.SklandClient
import cafe.miafetta.tsuki.providers.SklandGame
import cafe.miafetta.tsuki.providers.matchingAccountForLogin
import java.util.UUID

// Account changes and their effect on bound widgets and reminders (WidgetRuntimeReminders.kt). Every change bumps
// accountGeneration under the runtime lock.

// The Skland nickname the editor offers as the name of an account it is adding (StorageContract.PLATFORM_NAME), blank
// when the profile has none. Nothing is saved: the name is stored only if the user then adds the account with it.
internal fun WidgetRuntime.platformName(cred: String): String {
    SklandClient.validateCred(cred)
    return client.nickname(cred).orEmpty()
}

// [updatingId] is set by "更新 Cred": the Cred must then belong to that account (matchingAccountForLogin).
internal fun WidgetRuntime.login(name: String, cred: String, updatingId: String?) =
    login(name, cred, updatingId, synchronized(this) { accountGeneration })

private fun WidgetRuntime.login(name: String, cred: String, updatingId: String?, generation: Long) {
    require(name.length <= SklandClient.NAME_LIMIT)
    SklandClient.validateCred(cred)
    val roles = client.roles(cred)
    var previousCred: String? = null
    synchronized(this) {
        val existing = matchingAccountForLogin(accounts.list(), cred, roles, updatingId)
        require(generation == accountGeneration) { "Account operation was cancelled" }
        // The name is an optional alias: blank stays blank (updates prefill it, so blank means it was cleared).
        accounts.save(SklandAccount(existing?.id ?: UUID.randomUUID().toString(), name.trim(), cred, roles))
        accountGeneration++
        if (existing != null && (existing.cred != cred || existing.roles != roles)) {
            previousCred = existing.cred.takeIf { it != cred }
            sklandGames.forEach { it.forget(existing.id) }
            roleData.clear(existing.id)
            // Widgets keep their binding when the role is still on the account; otherwise they become unconfigured.
            repository.ids().forEach { id ->
                val config = read(id)?.config
                if (config?.accountId != existing.id) return@forEach
                if (roles.none { it.game.providerId == config.providerId && it.uid == config.characterId }) {
                    delete(id)
                    redraw(id)
                } else {
                    coordinator.signedOut(id)
                    update(id, true)
                }
            }
            retainReminders(existing.id, roles)
        }
    }
    previousCred?.let(client::forget)
}

// "更新 Cred" with the Cred left blank: verify again with the stored Cred, e.g. to change only the name. The Cred stays
// in this process. Capture the generation with the Cred so a later update cannot be undone by this verification.
internal fun WidgetRuntime.reverify(accountId: String, name: String) {
    val (cred, generation) = synchronized(this) {
        val account = accounts.list().singleOrNull { it.id == accountId }
            ?: throw AccountProblemException(AccountProblem.ACCOUNT_GONE, "Account no longer exists")
        account.cred to accountGeneration
    }
    login(name, cred, accountId, generation)
}

internal fun WidgetRuntime.logout(accountId: String) {
    val cred: String?
    synchronized(this) {
        accountGeneration++
        cred = accounts.list().singleOrNull { it.id == accountId }?.cred
        accounts.delete(accountId)
        sklandGames.forEach { it.forget(accountId) }
        roleData.clear(accountId)
        clearAccountWidgets(accountId)
        forgetReminders(accountId)
    }
    if (cred != null) client.forget(cred)
}

internal fun WidgetRuntime.resetAccounts() {
    synchronized(this) {
        accountGeneration++
        clearAccountWidgets(null)
        accounts.clear()
        sklandGames.forEach { it.forget() }
        roleData.clear()
        AndroidCredentialKey.delete()
        clearReminders()
    }
    client.clear()
}

private fun WidgetRuntime.clearAccountWidgets(accountId: String?) {
    repository.ids().forEach { id ->
        val config = repository.read(id)?.config
        if (config != null && config.providerId in SklandGame.providerIds &&
            (accountId == null || config.accountId == accountId)) {
            coordinator.delete(id)
            scheduler.cancel(id)
            if (id in StatusWidgetProvider.owned(application)) render(id, null)
        }
    }
}
