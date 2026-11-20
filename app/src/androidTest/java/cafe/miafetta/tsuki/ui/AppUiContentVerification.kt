package cafe.miafetta.tsuki.ui

import cafe.miafetta.tsuki.ui.theme.TsukiTextStyles

import android.app.Activity
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import cafe.miafetta.tsuki.core.TrainingState
import cafe.miafetta.tsuki.core.WidgetTraining
import java.io.File

internal fun AppUiVerification.verifyAccountKeyboard(host: Activity, folder: File, name: String) {
    fun passwordField(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isEditable && node.isPassword) return node
        for (index in 0 until node.childCount) node.getChild(index)?.let { passwordField(it)?.let { field -> return field } }
        return null
    }
    val field = Rect().apply { requireNotNull(passwordField(requireNotNull(uiAutomation.rootInActiveWindow))).getBoundsInScreen(this) }
    uiAutomation.shell("input tap ${field.centerX()} ${field.centerY()}")
    frames(host)
    val imeBottom = host.window.decorView.rootWindowInsets.getInsets(android.view.WindowInsets.Type.ime()).bottom
    check(imeBottom > 0) { "Credential keyboard did not appear" }
    checkDialogAction(host, "保存", host.resources.displayMetrics.heightPixels - imeBottom)
    File(folder, "$name-keyboard.txt").writeText("imeBottom=$imeBottom; screenHeight=${host.resources.displayMetrics.heightPixels}")
    uiAutomation.shell("input keyevent BACK")
    frames(host)
    check(!host.window.decorView.rootWindowInsets.isVisible(android.view.WindowInsets.Type.ime()) && dialog.value == "account") {
        "Back did not hide only the keyboard"
    }
}

internal fun AppUiVerification.verifyTrainingCards(host: Activity, folder: File): Int {
    val active = WidgetTraining(TrainingState.TRAINING, "char_test_trainee", "克莱门莎", endsAt = System.currentTimeMillis() + 9_000_000L)
    val samples = listOf(active, active.copy(name = "纯烬艾雅法拉"), active.copy(name = "测".repeat(40)),
        active.copy(state = TrainingState.PAUSED, endsAt = null, remainingSecs = 9_000L),
        active.copy(state = TrainingState.COMPLETE), WidgetTraining(TrainingState.IDLE), WidgetTraining())
    var cases = 0
    for (palette in listOf("LIGHT", "DARK")) {
        val scale = fontScale.value
        var firstHeight = 0f
        var firstSlotHeight = 0f
        for ((index, sample) in samples.withIndex()) {
            runOnMainSync {
                page.intValue = 0
                theme.value = palette
                longNames.value = false
                trainingCase.value = sample
            }
            frames(host)
            scrollToTag(host, "metric-card:训练室")
            runOnMainSync {
                val all = composeNodes(host)
                fun tagged(tag: String) = all.single { it.config.getOrNull(SemanticsProperties.TestTag) == tag }
                val training = tagged("metric-card:训练室").boundsInRoot
                val slot = tagged("training-name-slot").boundsInRoot
                val portrait = tagged("training-portrait").boundsInRoot
                if (index == 0) {
                    firstHeight = training.height
                    firstSlotHeight = slot.height
                }
                check(kotlin.math.abs(slot.height - firstSlotHeight) <= 1) { "Trainee name changed its reserved height" }
                if (index < 3) check(kotlin.math.abs(training.height - firstHeight) <= 1) { "Long trainee name grew the card" }
                check(portrait.left > training.center.x && kotlin.math.abs(portrait.width - portrait.height) <= 1 &&
                    portrait.right <= training.right) {
                    "Training portrait is not a square on the right: $portrait, name $slot, card $training"
                }
                checkTraineeName(all, slot, sample, index)
                if (sample.traineeId != null) checkTrainingDetail(host, tagged("metric-detail:训练室"), slot)
                if (sample.state == TrainingState.PAUSED) checkLegacyPaused(sample, tagged("metric-detail:训练室"))
            }
            if (index in listOf(0, 1, 2, 5)) capture(folder, "training-${palette.lowercase()}-$scale-$index")
            cases++
        }
    }
    runOnMainSync { trainingCase.value = null }
    return cases
}

// The trainee name stays on one line, centred in its slot, sized between body2 and title2; index 2 is the extreme name.
private fun checkTraineeName(all: List<SemanticsNode>, slot: androidx.compose.ui.geometry.Rect, sample: WidgetTraining,
    index: Int) {
    val name = sample.name ?: sample.status(System.currentTimeMillis())
    val text = all.single { node -> node.boundsInRoot.top >= slot.top && node.boundsInRoot.bottom <= slot.bottom &&
        node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == name } == true }
    val layouts = mutableListOf<TextLayoutResult>()
    check(requireNotNull(text.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action).invoke(layouts))
    val layout = layouts.single()
    check(layout.lineCount == 1) { "Trainee name exceeded its single-line slot" }
    val styles = TsukiTextStyles
    // Compose semantics reports the declared style even after Text.autoSize changes the rendered glyphs.
    val measurer = TextMeasurer(layout.layoutInput.fontFamilyResolver, layout.layoutInput.density,
        layout.layoutInput.layoutDirection)
    val firstGlyph = name.take(1)
    val minGlyph = measurer.measure(firstGlyph, styles.body2).getBoundingBox(0).width
    val maxGlyph = measurer.measure(firstGlyph, styles.title2).getBoundingBox(0).width
    val renderedGlyph = layout.getBoundingBox(0).width
    check(renderedGlyph in (minGlyph - 1)..(maxGlyph + 1)) { "Rendered trainee name exceeds body2/title2 limits" }
    check(kotlin.math.abs(text.boundsInRoot.center.y - slot.center.y) <= 1) { "Trainee name is not vertically centered" }
    if (index == 2) check(layout.isLineEllipsized(layout.lineCount - 1)) {
        "Extreme trainee name did not keep the bounded fallback"
    }
    if (index == 2) check(kotlin.math.abs(renderedGlyph - minGlyph) <= 1) {
        "Extreme name did not stop shrinking at body2: glyph=$renderedGlyph, minimum=$minGlyph"
    }
}

private fun checkTrainingDetail(host: Activity, detail: SemanticsNode, slot: androidx.compose.ui.geometry.Rect) {
    val details = mutableListOf<TextLayoutResult>()
    check(requireNotNull(detail.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action).invoke(details))
    check(details.single().layoutInput.style.fontSize == TsukiTextStyles.body2.fontSize)
    val gap = detail.boundsInRoot.top - slot.bottom
    check(kotlin.math.abs(gap - 8 * host.resources.displayMetrics.widthPixels / 360f) <= 1) {
        "Training detail is not 8dp below the fixed name slot"
    }
}

// PAUSED is only read back from snapshots of earlier versions; a specialisation cannot pause, so it shows as unknown.
private fun checkLegacyPaused(sample: WidgetTraining, detail: SemanticsNode) {
    val now = System.currentTimeMillis()
    check(sample.status(now) == "未知" && sample.time(now) == "时间未知") { "Paused training does not read as unknown" }
    val text = detail.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }
    check(text == "专精时间未知") { "Paused training detail is $text" }
}

internal fun AppUiVerification.scrollToTag(host: Activity, tag: String, pageIndex: Int = 0) {
    repeat(16) {
        var visible = false
        runOnMainSync {
            val nodes = composeNodes(host)
            val list = nodes.single { it.config.getOrNull(SemanticsProperties.TestTag) == "page-list:$pageIndex" }
            val target = nodes.firstOrNull { it.config.getOrNull(SemanticsProperties.TestTag) == tag }
            visible = target != null && target.positionInRoot.y >= list.boundsInRoot.top &&
                target.positionInRoot.y + target.size.height <= list.boundsInRoot.bottom
            val above = target != null && target.positionInRoot.y < list.boundsInRoot.top
            val step = if (above) -list.boundsInRoot.height / 3 else list.boundsInRoot.height / 3
            if (!visible) check(requireNotNull(list.config.getOrNull(SemanticsActions.ScrollBy)?.action)
                .invoke(0f, step)) { "Cannot scroll to $tag" }
        }
        if (visible) return
        frames(host)
    }
    error("Cannot bring $tag into view")
}

internal fun AppUiVerification.checkDialogAction(host: Activity, label: String, visibleBottom: Int) {
    for (attempt in 0..3) {
        val action = requireNotNull(find(label, exact = true, clickable = true))
        val bounds = Rect().apply { action.getBoundsInScreen(this) }
        if (!bounds.isEmpty && bounds.top >= 0 && bounds.bottom <= visibleBottom) return
        var scroll = action
        while (!scroll.isScrollable) scroll = requireNotNull(scroll.parent)
        check(attempt < 3 && scroll.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) {
            "$label cannot scroll into view: $bounds, bottom=$visibleBottom"
        }
        frames(host)
    }
}
