package cafe.miafetta.tsuki.ui.state

// The accounts of the reminder settings page ("通知提醒"): what each account's row says, and the role groups of the
// account's own page.
internal object ReminderAccounts {
    // The summary of an account without roles, and the one card of its page.
    const val NO_ROLES = "没有角色"
    const val NO_ROLES_PAGE = "这个账号下没有角色"
    const val NONE_ON = "未开启提醒"

    // The role groups of the account's page, as ReminderRoles orders and titles them: Arknights roles, then Endfield
    // ones, each in the account's order. Only the account's roles are listed together, so only they need telling apart.
    fun options(account: UiAccount): List<ReminderRoleOption> = ReminderRoles.options(account.roles)

    // How many reminders of the account are on: the enabled rules of the metrics its page lists, for its listed roles.
    fun enabled(account: UiAccount, reminders: UiReminders): Int = options(account).sumOf { option ->
        val subject = option.role.reminderSubject()
        ReminderOptions.rows(option.game).count { reminders.rule(subject, it.metricId)?.enabled == true }
    }

    // The summary of the account's row: how many of its reminders are on, kept short as a row summary.
    fun summary(account: UiAccount, reminders: UiReminders): String {
        if (options(account).isEmpty()) return NO_ROLES
        val count = enabled(account, reminders)
        return if (count == 0) NONE_ON else "已开启 $count 项提醒"
    }
}
