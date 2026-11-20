package cafe.miafetta.tsuki.ui.state

import cafe.miafetta.tsuki.core.reminder.ReminderSubject
import cafe.miafetta.tsuki.providers.SklandGame

// One role group of the reminder settings page; [label] is its title.
internal data class ReminderRoleOption(val role: UiRole, val game: SklandGame, val label: String) {
    val key get() = role.key
}

internal object ReminderRoles {
    // Every listed role of a Skland game, game by game in SklandGame order (Arknights, then Endfield) and within a game in
    // the order of the accounts and their roles, labelled "{role}（{game}）". Roles that would read the same get their
    // server and UID appended, as the account card shows them. The debug fixture is no Skland game and is never listed.
    fun options(roles: List<UiRole>): List<ReminderRoleOption> {
        val games = roles.mapNotNull { role -> SklandGame.fromProvider(role.provider)?.let { role to it } }
            .sortedBy { (_, game) -> game.ordinal }
        val names = games.map { (role, game) -> "${role.name}（${game.displayName}）" }
        return games.mapIndexed { index, (role, game) ->
            val name = names[index]
            ReminderRoleOption(role, game, if (names.count { it == name } > 1) "$name${role.server} UID ${role.displayId}" else name)
        }
    }
}

internal fun UiRole.reminderSubject() = ReminderSubject(provider, accountId, id)
