package cafe.miafetta.tsuki.ui.state

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

internal data class UiMessage(val text: String)

internal class UiMessages {
    private val channel = Channel<UiMessage>(Channel.UNLIMITED)
    val events = channel.receiveAsFlow()
    fun send(text: String) {
        channel.trySend(UiMessage(text))
    }
}

internal class ReadFailureMessages(private val messages: UiMessages) {
    private var lastRoleKey: String? = null
    fun clear() { lastRoleKey = null }
    fun send(role: UiRole, text: String) {
        if (lastRoleKey == role.key) return
        lastRoleKey = role.key
        messages.send(text)
    }
}
