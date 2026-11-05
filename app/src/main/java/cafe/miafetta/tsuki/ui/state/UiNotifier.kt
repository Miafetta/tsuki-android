package cafe.miafetta.tsuki.ui.state

import android.content.Context
import android.widget.Toast

internal fun interface UiNotifier {
    fun show(text: String)
}

internal class ToastUiNotifier(context: Context) : UiNotifier {
    private val application = context.applicationContext
    private var current: Toast? = null

    override fun show(text: String) {
        current?.cancel()
        current = Toast.makeText(application, text, Toast.LENGTH_SHORT).also { it.show() }
    }
}
