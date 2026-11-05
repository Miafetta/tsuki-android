package cafe.miafetta.tsuki.ui.pages

import cafe.miafetta.tsuki.ui.components.MutedText
import cafe.miafetta.tsuki.ui.state.AccountDraft
import cafe.miafetta.tsuki.ui.state.rememberAccountDraft
import cafe.miafetta.tsuki.ui.theme.AppLogo
import cafe.miafetta.tsuki.ui.theme.Branding

import cafe.miafetta.tsuki.ui.components.TsukiSmallTitle
import cafe.miafetta.tsuki.ui.glass.GlassIconButton

import android.app.Activity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*
import top.yukonga.miuix.kmp.preference.*
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.nav.gesture.PredictiveBackHandler

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun AccountEditor(activity: Activity, busy: Boolean, error: String,
    onDismiss: () -> Unit, onSave: (String, String) -> Unit, show: Boolean = true, pageProgress: Float = 1f,
    draft: AccountDraft = rememberAccountDraft(), blurEnabled: Boolean = true, onPlatform: () -> Unit = {}) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    var invalid by remember { mutableStateOf("") }
    val imeVisible = WindowInsets.isImeVisible
    // Callbacks are created before the navigation entry becomes interactive.
    val ready by rememberUpdatedState(show && !busy && pageProgress >= .999f)
    val currentBusy by rememberUpdatedState(busy)
    fun dismiss() {
        if (currentBusy) return
        keyboard?.hide()
        draft.clear()
        onDismiss()
    }
    fun save() {
        if (!ready) return
        val name = draft.name.trim()
        val cred = draft.cred.trim()
        if (name.isEmpty() || name.length > 40 || !cred.matches(Regex("[!-~]{10,4096}"))) {
            invalid = "请填写 1～40 个字符的账号名称和有效的 Cred。"
        } else {
            keyboard?.hide()
            draft.cred = ""
            invalid = ""
            onSave(name, cred)
        }
    }
    PredictiveBackHandler(enabled = show && (busy || imeVisible), onProgress = { events -> events.collect {} },
        onCommit = { if (!busy) keyboard?.hide() }, onCancel = {})
    SecondaryScaffold("添加账号", blurEnabled, show && pageProgress >= .999f, ::dismiss, backEnabled = ready,
        modifier = Modifier.imePadding().testTag("account-editor-page"), spacing = 8.dp, actions = { progress ->
            GlassIconButton(onClick = ::save, enabled = ready, progress = progress) { Icon(MiuixIcons.Ok, "保存") }
        }) {
        AccountForm(draft, ready, invalid.ifEmpty { error }, ::save) {
            focus.clearFocus()
            keyboard?.hide()
            onPlatform()
        }
    }
}

@Composable
private fun AccountForm(draft: AccountDraft, ready: Boolean, error: String,
    onSave: () -> Unit, onPlatform: () -> Unit) {
    Column(Modifier.padding(horizontal = 12.dp)) {
    Card {
        ArrowPreference(title = "平台", startAction = { AppLogo(platform = true, size = 40) },
            endActions = { MutedText(Branding.PLATFORM) }, enabled = ready,
            onClick = onPlatform)
    }
    }
    AccountField("账号信息", "账号名称", draft.name, { draft.name = it }, ready)
    AccountField("授权信息", "Cred", draft.cred, { draft.cred = it }, ready, secret = true)
    MutedText("只需填写 Cred 本身，不要填写密码、Token 或完整的 Cookie。", Modifier.padding(horizontal = 28.dp))
    Button(onClick = onSave, enabled = ready, colors = ButtonDefaults.buttonColorsPrimary(),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 16.dp)) { Text("验证并添加") }
    if (error.isNotEmpty()) Text(error, Modifier.padding(horizontal = 28.dp),
        style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.error)
}

@Composable
private fun AccountField(section: String, label: String, value: String, onChange: (String) -> Unit, enabled: Boolean,
    secret: Boolean = false) {
    Column(Modifier.padding(horizontal = 12.dp)) {
        TsukiSmallTitle(section)
        TextField(value, onChange,
            colors = TextFieldDefaults.textFieldColors(backgroundColor = MiuixTheme.colorScheme.surfaceContainer),
            label = label, singleLine = true, enabled = enabled,
            visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = if (secret) KeyboardOptions(keyboardType = KeyboardType.Password) else KeyboardOptions.Default)
    }
}