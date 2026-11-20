package cafe.miafetta.tsuki.ui.pages

import cafe.miafetta.tsuki.ui.components.TsukiDropdownPreference
import cafe.miafetta.tsuki.ui.state.ReminderOptions
import cafe.miafetta.tsuki.ui.state.ReminderSettings
import cafe.miafetta.tsuki.ui.state.ThresholdSlider
import cafe.miafetta.tsuki.ui.state.ThresholdStepper
import cafe.miafetta.tsuki.ui.state.UiRole
import cafe.miafetta.tsuki.ui.state.reminderSubject

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import cafe.miafetta.tsuki.core.reminder.ReminderCapability
import cafe.miafetta.tsuki.core.reminder.ReminderMode
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Remove
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme

// What a threshold row shows: the rule's condition, the threshold being shown (ReminderSettings.threshold, which follows
// a slider being dragged) and the metric's limit. Kept while the row folds away after its rule lost the threshold.
private data class ShownThreshold(val mode: ReminderMode, val value: Int, val limit: Int?)

// A metric with conditions to choose from: its condition menu ("关闭" and the conditions, ReminderOptions.choices), and
// under it in the same card, while the condition takes a threshold, the threshold row. Choosing saves at once
// (ReminderSettings.choose). The rows keep their look while a save runs: the save only holds off the next change, so
// no text greys out and back. The threshold row unfolds and folds with the default expand and fade of AnimatedVisibility,
// as the battery saver schedule of Android's settings shows its slider once its condition is chosen, and keeps showing
// the last threshold while it folds away.
@Composable
internal fun ReminderRuleRows(settings: ReminderSettings, role: UiRole, capability: ReminderCapability, active: Boolean,
    modifier: Modifier) {
    val subject = remember(role) { role.reminderSubject() }
    val rule = settings.reminders.rule(subject, capability.metricId)?.takeIf { it.enabled }
    val limit = settings.limit(role, capability.metricId)
    val choices = ReminderOptions.choices(capability, limit, rule?.mode)
    val threshold = rule?.threshold?.let { ShownThreshold(rule.mode, settings.threshold(role, capability.metricId) ?: it, limit) }
    var last by remember { mutableStateOf(threshold) }
    SideEffect { if (threshold != null) last = threshold }
    Column(modifier.fillMaxWidth()) {
        TsukiDropdownPreference(ReminderOptions.title(capability), choices.map(ReminderOptions::choiceLabel),
            choices.indexOf(rule?.mode), { if (active) settings.choose(role, capability, choices[it]) })
        AnimatedVisibility(threshold != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            (threshold ?: last)?.let { ThresholdRow(settings, role, capability, it, active) }
        }
    }
}

// A slider while the range has two ends, "阈值" with the value and "−" "+" while it has no upper end, or "阈值" with the
// value alone when a limit of one leaves a single value. A stored condition the current limit no longer allows keeps
// its row, disabled (ReminderOptions.adjustable).
@Composable
private fun ThresholdRow(settings: ReminderSettings, role: UiRole, capability: ReminderCapability, shown: ShownThreshold,
    active: Boolean) {
    val enabled = ReminderOptions.adjustable(capability, shown.mode, shown.limit)
    val text = ReminderOptions.thresholdText(shown.mode, shown.value)
    val modifier = Modifier.testTag("reminder-threshold")
    val slider = ThresholdSlider.of(capability, shown.mode, shown.limit)
    val stepper = ThresholdStepper.of(capability, shown.mode, shown.limit)
    val nudge = { threshold: Int -> if (active) settings.nudge(role, capability, threshold) }
    when {
        slider != null -> ThresholdSliderRow(slider, shown.value, enabled, modifier,
            onDrag = { if (active) settings.drag(role, capability, it) }, onNudge = nudge,
            onRelease = { settings.release(role, capability) })
        stepper != null -> ThresholdStepRow(stepper, shown.value, text, enabled, modifier, nudge)
        else -> BasicComponent(modifier, title = ReminderOptions.THRESHOLD, enabled = enabled,
            endActions = { ThresholdValue(text, enabled, Modifier.align(Alignment.CenterVertically)) })
    }
}

// The value, then "−" and "+", each tap one step; a button is disabled at its end of the range.
@Composable
private fun ThresholdStepRow(stepper: ThresholdStepper, value: Int, text: String, enabled: Boolean, modifier: Modifier,
    onStep: (Int) -> Unit) {
    BasicComponent(modifier, title = ReminderOptions.THRESHOLD, enabled = enabled, endActions = {
        ThresholdValue(text, enabled, Modifier.align(Alignment.CenterVertically))
        StepButton(false, enabled && stepper.canDecrease(value)) { onStep(stepper.stepped(value, false)) }
        StepButton(true, enabled && stepper.canIncrease(value)) { onStep(stepper.stepped(value, true)) }
    })
}

// "−" or "+". IconButton keeps its icon's color when disabled, so the icon takes the disabled text color itself.
@Composable
private fun StepButton(up: Boolean, enabled: Boolean, onClick: () -> Unit) {
    IconButton(onClick, enabled = enabled) {
        Icon(if (up) MiuixIcons.Add else MiuixIcons.Remove, if (up) ReminderOptions.INCREASE else ReminderOptions.DECREASE,
            tint = if (enabled) LocalContentColor.current else MiuixTheme.colorScheme.disabledOnSecondaryVariant)
    }
}

// The threshold on the right of a row without a slider, styled as SliderPreference styles its valueText.
@Composable
private fun ThresholdValue(text: String, enabled: Boolean, modifier: Modifier) {
    Text(text, modifier, fontSize = MiuixTheme.textStyles.body2.fontSize,
        color = if (enabled) MiuixTheme.colorScheme.onSurfaceVariantActions else MiuixTheme.colorScheme.disabledOnSecondaryVariant)
}
