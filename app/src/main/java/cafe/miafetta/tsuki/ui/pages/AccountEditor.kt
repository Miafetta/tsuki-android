package cafe.miafetta.tsuki.ui.pages

import cafe.miafetta.tsuki.ui.components.MutedText
import cafe.miafetta.tsuki.ui.state.AccountDraft
import cafe.miafetta.tsuki.ui.state.rememberAccountDraft
import cafe.miafetta.tsuki.ui.theme.AppLogo
import cafe.miafetta.tsuki.ui.theme.Branding

import cafe.miafetta.tsuki.ui.components.TsukiSmallTitle
import cafe.miafetta.tsuki.ui.glass.GlassIconButton

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
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

private const val NAME_LIMIT = 40
private val CredPattern = Regex("[!-~]{10,4096}")
// How to obtain a Cred by hand; the key and page follow SklandWebLogin, which reads the same entry.
private val CredHint = "推荐使用上方的网页登录。也可以在电脑浏览器登录${Branding.PLATFORM}网页版（www.skland.com），" +
    "按 F12 打开开发者工具，在“应用（Application）→ 本地存储（Local Storage）”中复制 SK_OAUTH_CRED_KEY 的值。" +
    "只填写 Cred 本身，不要填写密码或完整的 Cookie。"

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun AccountEditor(activity: Activity, busy: Boolean, error: String,
    onDismiss: () -> Unit, onSave: (String, String) -> Unit, show: Boolean = true, pageProgress: Float = 1f,
    draft: AccountDraft = rememberAccountDraft(), blurEnabled: Boolean = true, onPlatform: () -> Unit = {},
    onWebLogin: () -> Unit = {}, onMessage: (String) -> Unit = {}) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val imeVisible = WindowInsets.isImeVisible
    // Callbacks are created before the navigation entry becomes interactive, so actions check ready. Controls only look
    // disabled while busy: during the slide-in NavDisplay blocks input, and a gray flash would read as loading.
    val ready by rememberUpdatedState(show && !busy && pageProgress >= .999f)
    val enabled = show && !busy
    val currentBusy by rememberUpdatedState(busy)
    fun dismiss() {
        if (currentBusy) return
        keyboard?.hide()
        draft.clear()
        onDismiss()
    }
    // Local checks are reported as a system toast; a failed platform verification stays inline below the form. The Cred
    // stays in the in-memory draft until the account is added, so a failed attempt can be corrected instead of retyped.
    // When updating an account, a blank Cred keeps the stored one.
    fun save() {
        if (!ready) return
        val name = draft.name.trim()
        val cred = draft.cred.trim()
        val problem = when {
            name.length > NAME_LIMIT -> "账号名称最多 $NAME_LIMIT 个字符。"
            cred.isEmpty() && draft.accountId != null -> null
            !cred.matches(CredPattern) -> "请填写有效的 Cred。"
            else -> null
        }
        if (problem != null) {
            onMessage(problem)
            return
        }
        keyboard?.hide()
        onSave(name, cred)
    }
    fun paste() {
        if (!ready) return
        val text = clipboardText(activity)?.trim()
        if (text.isNullOrEmpty()) onMessage("剪贴板里没有可粘贴的内容。") else draft.cred = text
    }
    fun leave(target: () -> Unit) {
        if (!ready) return
        focus.clearFocus()
        keyboard?.hide()
        target()
    }
    PredictiveBackHandler(enabled = show && (busy || imeVisible), onProgress = { events -> events.collect {} },
        onCommit = { if (!busy) keyboard?.hide() }, onCancel = {})
    SecondaryScaffold("添加账号", blurEnabled, show && pageProgress >= .999f, ::dismiss, backEnabled = enabled,
        modifier = Modifier.imePadding().testTag("account-editor-page"), spacing = 8.dp, actions = { progress ->
            GlassIconButton(onClick = ::save, enabled = enabled, progress = progress) { Icon(MiuixIcons.Ok, "保存") }
        }) {
        AccountForm(draft, enabled, error, ::save, onPlatform = { leave(onPlatform) },
            onWebLogin = { leave(onWebLogin) }, onPaste = ::paste)
    }
}

private fun clipboardText(context: Context): String? {
    val clip = context.getSystemService(ClipboardManager::class.java)?.primaryClip ?: return null
    if (clip.itemCount == 0) return null
    return clip.getItemAt(0).coerceToText(context)?.toString()
}

@Composable
private fun AccountForm(draft: AccountDraft, enabled: Boolean, error: String,
    onSave: () -> Unit, onPlatform: () -> Unit, onWebLogin: () -> Unit, onPaste: () -> Unit) {
    Column(Modifier.padding(horizontal = 12.dp)) {
        Card {
            ArrowPreference(title = "平台", startAction = { AppLogo(platform = true, size = 40) },
                endActions = { MutedText(Branding.PLATFORM) }, enabled = enabled,
                onClick = onPlatform)
        }
    }
    Column(Modifier.padding(horizontal = 12.dp)) {
        TsukiSmallTitle("账号信息")
        TextField(draft.name, { draft.name = it.take(NAME_LIMIT) }, colors = fieldColors(), label = "账号名称（可选）",
            singleLine = true, enabled = enabled)
    }
    CredField(draft, enabled, onWebLogin, onPaste)
    MutedText(CredHint, Modifier.padding(horizontal = 28.dp))
    Button(onClick = onSave, enabled = enabled, colors = ButtonDefaults.buttonColorsPrimary(),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 16.dp)) { Text("验证并添加") }
    if (error.isNotEmpty()) Text(error, Modifier.padding(horizontal = 28.dp),
        style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.error)
}

// The Cred is hidden by default; the trailing buttons paste from the clipboard and toggle visibility (C9). When updating,
// the stored Cred is never shown: the field starts empty and its placeholder says that blank keeps it.
@Composable
private fun CredField(draft: AccountDraft, enabled: Boolean, onWebLogin: () -> Unit, onPaste: () -> Unit) {
    var visible by rememberSaveable { mutableStateOf(false) }
    val updating = draft.accountId != null
    Column(Modifier.padding(horizontal = 12.dp)) {
        TsukiSmallTitle("授权信息")
        Card(Modifier.padding(bottom = 8.dp)) {
            ArrowPreference(title = "在${Branding.PLATFORM}网页登录", enabled = enabled, onClick = onWebLogin,
                modifier = Modifier.testTag("account-web-login"))
        }
        TextField(draft.cred, { draft.cred = it }, Modifier.testTag("account-cred"), colors = fieldColors(),
            label = if (updating) "留空则不修改当前 Cred" else "Cred", useLabelAsPlaceholder = updating,
            singleLine = true, enabled = enabled,
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                Row(Modifier.padding(end = 6.dp)) {
                    IconButton(onPaste, enabled = enabled) { Icon(MiuixIcons.Paste, "粘贴 Cred") }
                    IconButton({ visible = !visible }, enabled = enabled) {
                        Icon(if (visible) MiuixIcons.Hide else MiuixIcons.Show, if (visible) "隐藏 Cred" else "显示 Cred")
                    }
                }
            })
    }
}

@Composable
private fun fieldColors() = TextFieldDefaults.textFieldColors(backgroundColor = MiuixTheme.colorScheme.surfaceContainer)
