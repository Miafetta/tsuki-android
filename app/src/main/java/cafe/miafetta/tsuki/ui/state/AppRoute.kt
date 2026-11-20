package cafe.miafetta.tsuki.ui.state

import kotlinx.serialization.Serializable
import top.yukonga.miuix.kmp.nav.core.NavKey

@Serializable
internal sealed interface AppRoute : NavKey {
    @Serializable data object Root : AppRoute
    @Serializable data object Privacy : AppRoute
    @Serializable data object Licenses : AppRoute
    @Serializable data object AddAccount : AppRoute
    @Serializable data object SelectPlatform : AppRoute
    @Serializable data object WebLogin : AppRoute
    @Serializable data object Reminders : AppRoute
    // One account's reminders under "通知提醒". Named by the account ID only, so a rename shows at once and a restored
    // page finds its account again (or leaves when it is gone).
    @Serializable data class ReminderAccount(val accountId: String) : AppRoute
}
