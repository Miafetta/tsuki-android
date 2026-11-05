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
}
