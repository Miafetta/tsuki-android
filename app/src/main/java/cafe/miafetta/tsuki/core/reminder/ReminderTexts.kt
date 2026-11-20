package cafe.miafetta.tsuki.core.reminder

// Notification bodies of one metric, one per mode it offers. PERCENT and VALUE templates take the threshold in place of
// {P} and {T}. Tsuki copy never shows "·" (it is only an internal label delimiter).
data class ReminderTexts(
    val full: String? = null,
    val percent: String? = null,
    val value: String? = null,
    val event: String? = null,
) {
    init {
        val all = listOfNotNull(full, percent, value, event)
        require(all.all { it.isNotBlank() && '·' !in it && '\n' !in it })
        require(percent == null || PERCENT_SLOT in percent)
        require(value == null || VALUE_SLOT in value)
    }

    fun body(mode: ReminderMode, threshold: Int?): String? = when (mode) {
        ReminderMode.FULL -> full
        ReminderMode.PERCENT -> threshold?.let { percent?.replace(PERCENT_SLOT, it.toString()) }
        ReminderMode.VALUE -> threshold?.let { value?.replace(VALUE_SLOT, it.toString()) }
        ReminderMode.EVENT -> event
    }

    private companion object {
        const val PERCENT_SLOT = "{P}"
        const val VALUE_SLOT = "{T}"
    }
}
