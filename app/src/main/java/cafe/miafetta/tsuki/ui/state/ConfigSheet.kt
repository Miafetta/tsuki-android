package cafe.miafetta.tsuki.ui.state

import androidx.compose.runtime.saveable.Saver
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
internal sealed interface ConfigSheet {
    @Serializable data class Closed(val previous: Content? = null) : ConfigSheet
    @Serializable sealed interface Content : ConfigSheet {
        val theme: String
        val diagnostic: Boolean
    }
    // [game] is the Branding game ID of the widget to add; an Edit takes the game from the placed instance's provider.
    @Serializable data class New(override val theme: String = "LIGHT", override val diagnostic: Boolean = false,
        val game: String = "arknights") : Content
    @Serializable data class Edit(val widgetId: Int, override val theme: String = "LIGHT",
        override val diagnostic: Boolean = false) : Content

    val content: Content? get() = if (this is Closed) previous else this as Content
    fun close() = Closed(content)

    companion object {
        val Saver = Saver<ConfigSheet, String>(save = { Json.encodeToString(it) }, restore = { Json.decodeFromString(it) })
    }
}

