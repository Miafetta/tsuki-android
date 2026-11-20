package cafe.miafetta.tsuki.ui.components

import cafe.miafetta.tsuki.ui.state.UiAccount
import cafe.miafetta.tsuki.ui.theme.AppLogo
import cafe.miafetta.tsuki.ui.theme.Branding

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.preference.ArrowPreference

// The platform logo at the start of an account's row, in dp.
private const val LogoSize = 40

/**
 * An account's row: the platform logo, the account's name ([accountTitle]) and a short [summary]. "我的" heads each
 * account card with it, with the account menu in [endActions]; "通知提醒" lists the accounts with it and opens an
 * account's page through [onClick], which adds the arrow of ArrowPreference.
 */
@Composable
internal fun AccountRow(account: UiAccount, summary: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null,
    endActions: @Composable (RowScope.() -> Unit)? = null) {
    val logo: @Composable () -> Unit = { AppLogo(platform = true, size = LogoSize) }
    if (onClick == null) {
        BasicComponent(modifier, title = accountTitle(account), summary = summary, startAction = logo, endActions = endActions)
    } else {
        ArrowPreference(accountTitle(account), modifier, summary = summary, startAction = logo, endActions = endActions ?: {},
            onClick = onClick)
    }
}

// The name an account goes by wherever it is shown. The name is the user's optional alias; an unnamed account is
// labelled by its platform, never by one of its roles.
internal fun accountTitle(account: UiAccount) = account.name.ifBlank { "${Branding.PLATFORM}账号" }
