package cafe.miafetta.tsuki.ui.components

import androidx.compose.runtime.Immutable

// The two heads of the widget sheet, after HyperOS's own add and edit sheets. [title] is the sheet title (null when the
// head draws the game instead), [dismissLabel] names the top-start button and [confirmLabel] is the main button's text.
@Immutable
internal sealed interface WidgetSheetHeader {
    val title: String?
    val dismissLabel: String
    val confirmLabel: String

    // A widget that is not on the desktop yet: back, the game's logo and name, then the widget's name and description.
    data class New(val game: String, val name: String, val description: String) : WidgetSheetHeader {
        override val title: String? get() = null
        override val dismissLabel get() = "返回"
        override val confirmLabel get() = "添加到桌面"
    }

    // An instance that is already placed: close and the title alone.
    data object Edit : WidgetSheetHeader {
        override val title get() = "编辑小部件"
        override val dismissLabel get() = "关闭"
        override val confirmLabel get() = "应用"
    }
}
