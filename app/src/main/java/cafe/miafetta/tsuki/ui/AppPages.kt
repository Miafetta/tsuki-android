package cafe.miafetta.tsuki.ui

import cafe.miafetta.tsuki.StorageContract as Storage

import android.app.Activity
import android.os.Bundle
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.*
import cafe.miafetta.tsuki.*
import cafe.miafetta.tsuki.core.RefreshFailure
import cafe.miafetta.tsuki.providers.FakeArknightsAdapter
import cafe.miafetta.tsuki.ui.components.WidgetPreviews
import cafe.miafetta.tsuki.ui.pages.homePage
import cafe.miafetta.tsuki.ui.pages.mePage
import cafe.miafetta.tsuki.ui.pages.widgetPage
import cafe.miafetta.tsuki.ui.state.AppController
import cafe.miafetta.tsuki.ui.state.AppPage
import cafe.miafetta.tsuki.ui.state.AppRoute
import cafe.miafetta.tsuki.ui.state.ConfigSheet
import cafe.miafetta.tsuki.ui.state.Confirmation
import cafe.miafetta.tsuki.ui.theme.Branding
import top.yukonga.miuix.kmp.nav.core.*

// One preview source for every page and the editor sheet.
internal val AppController.previews get() = WidgetPreviews(previewRender, previewCache, previewVersion)

internal fun LazyListScope.appPages(visiblePage: Int, controller: AppController, activity: Activity, blur: Boolean) {
    when (visiblePage) {
        AppPage.OVERVIEW -> appOverview(controller, activity)
        AppPage.WIDGETS -> appWidgets(controller, activity)
        AppPage.ME -> appMe(controller, activity, blur)
    }
}

private fun LazyListScope.appOverview(controller: AppController, activity: Activity) = with(controller) {
    val failure = state.data.failure?.takeIf { state.dataKey == selected?.key && it != RefreshFailure.RATE_LIMIT }
        ?.let { activity.getString(failureMessage(it.name)) }.orEmpty()
    homePage(state.roles, selected, homeSnapshot, { selectedKey = it.key }, {
        page = AppPage.ME
        addAccount()
    }, failure, previews, portraitLoad)
}

private fun LazyListScope.appWidgets(controller: AppController, activity: Activity) = with(controller) {
    widgetPage(activity, state.widgets, homeSnapshot, catalog, previews, selected?.key.orEmpty(), page == AppPage.WIDGETS,
        { catalog = it }) { widget, chosenTheme ->
        state.error = ""
        configSheet = widget?.let { ConfigSheet.Edit(it.id, chosenTheme, it.provider == FakeArknightsAdapter.ID) }
            ?: ConfigSheet.New(chosenTheme)
    }
}

private fun LazyListScope.appMe(controller: AppController, activity: Activity, blur: Boolean) = with(controller) {
    mePage(state.accounts, state.widgets, state.busy, theme, blur,
        { changeTheme(it) },
        { changeBlur(it) },
        onAdd = { addAccount() },
        onUpdate = { addAccount(updating = it) },
        onDelete = { account ->
            val count = state.widgets.count { it.accountId == account.id }
            val target = if (account.name.isBlank()) "这个${Branding.PLATFORM}账号" else "「${account.name}」"
            confirmation = Confirmation("删除账号",
                "将删除${target}的 Cred 及 ${account.roles.size} 个角色的缓存，并解除 $count 个小部件的绑定。桌面上的这些小部件会显示为未配置，可以手动移除。",
                Storage.LOGOUT, Bundle().apply { putString(Storage.ACCOUNT_ID, account.id) })
        }, onPrivacy = { nav.push(AppRoute.Privacy) }, onLicenses = { nav.push(AppRoute.Licenses) },
        onSimulation = {
            state.error = ""
            configSheet = ConfigSheet.New(diagnostic = true)
        },
        onProbe = { pin(Bundle().apply { putBoolean("probe", true) }) },
        onReset = {
            confirmation = Confirmation("重置森空岛账号存储", "将清除所有账号的 Cred、角色缓存和小部件绑定，之后需要重新添加账号。桌面上的小部件需手动移除。",
                Storage.RESET_ACCOUNTS, Bundle())
        },
        showTools = BuildConfig.DEBUG && activity.intent.getBooleanExtra("diagnostics", false),
        authRoleKeys = if (state.data.failure == RefreshFailure.AUTH_EXPIRED) setOf(state.dataKey) else emptySet())
}
