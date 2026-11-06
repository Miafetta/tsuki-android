package cafe.miafetta.tsuki.ui.state

internal object UiPreferences {
    const val FILE = "ui"
    const val BLUR = "blur"
    // The last selected role of any game; installed versions saved the Arknights role here.
    const val ROLE = "role"

    // The last selected role of one game, so switching game tabs returns to it.
    fun gameRole(game: String) = "$ROLE.$game"
}
