package cafe.miafetta.tsuki.ui.sheets

import cafe.miafetta.tsuki.StorageContract as Storage

import android.app.Activity
import android.os.Bundle
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.miafetta.tsuki.ui.state.AppController
import cafe.miafetta.tsuki.ui.state.Confirmation
import cafe.miafetta.tsuki.widget.DesktopShortcutPermission
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
internal fun AppDialogs(controller: AppController, activity: Activity, screenActive: Boolean) = with(controller) {
    SideEffect { if (confirmation != null) lastConfirmation = confirmation }
    (confirmation ?: lastConfirmation)?.let { confirm ->
        ConfirmationDialog(confirm, confirmation != null && screenActive, state.error, state.busy,
            onDismiss = {
                if (!state.busy) {
                    confirmation = null
                    state.error = ""
                }
            },
            onConfirm = { scope.launch { state.action(confirm.method, Bundle(confirm.extras), inlineError = true) } })
    }
    fun cancelPin() {
        permissionDialog = false
        pendingPin = null
    }
    WindowDialog(show = permissionDialog && screenActive, title = "桌面快捷方式权限",
        summary = "将小部件添加到桌面需要“桌面快捷方式”权限。请在系统设置中为 Tsuki 开启后返回。",
        onDismissRequest = ::cancelPin) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val permission = DesktopShortcutPermission.check(activity)
            if (permission == DesktopShortcutPermission.State.UNKNOWN) Button(onClick = { pendingPin?.let(::place) },
                modifier = Modifier.fillMaxWidth()) { Text("已允许，继续添加", style = MiuixTheme.textStyles.button) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = ::cancelPin, modifier = Modifier.weight(1f)) {
                    Text("取消", style = MiuixTheme.textStyles.button)
                }
                Button(onClick = {
                    permissionDialog = false
                    waitingSettings = DesktopShortcutPermission.openSettings(activity)
                    if (!waitingSettings) {
                        state.messages.send("无法打开系统设置，请手动开启权限后重试")
                        pendingPin = null
                    }
                }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColorsPrimary()) {
                    Text("打开系统设置", style = MiuixTheme.textStyles.button)
                }
            }
        }
    }
}

@Composable
internal fun ConfirmationDialog(confirmation: Confirmation, show: Boolean, error: String, busy: Boolean,
    onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val destructive = confirmation.method == Storage.LOGOUT
    WindowDialog(show = show, title = confirmation.title, summary = confirmation.summary, onDismissRequest = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (error.isNotEmpty()) Text(error, style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.error)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onDismiss, enabled = !busy, modifier = Modifier.weight(1f)) {
                    Text("取消", style = MiuixTheme.textStyles.button)
                }
                Button(onClick = onConfirm, enabled = !busy, modifier = Modifier.weight(1f),
                    colors = if (destructive) ButtonDefaults.buttonColors(contentColor = MiuixTheme.colorScheme.error)
                    else ButtonDefaults.buttonColorsPrimary()) {
                    Text(if (destructive) "删除" else "确认", style = MiuixTheme.textStyles.button)
                }
            }
        }
    }
}
